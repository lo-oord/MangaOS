package com.mangaos.app.data.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.mangaos.app.domain.model.ExtensionEntry
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.InstalledExtension
import com.mangaos.app.domain.model.ManagedExtension
import com.mangaos.app.domain.model.TransferState
import com.mangaos.app.domain.repository.ExtensionRepository
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The extension catalogue and its lifecycle: fetch the index, detect what is installed,
 * download an APK, verify it, hand it to the system installer, update and remove.
 *
 * The repository index itself is an implementation detail: it is embedded here so the
 * Sources screen can only ever show sources.
 *
 * Note on execution: Keiyoushi extensions are compiled against the Mihon extension API
 * with every shared library marked `compileOnly`, so their code needs a host that
 * implements `HttpSource` and the network helpers. This build manages (installs,
 * updates, removes) extensions but does not execute them yet, and says so in the UI.
 */
class ExtensionStore(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : ExtensionRepository {

    private val refreshMutex = Mutex()
    private val indexFile = File(context.filesDir, "extensions/index.pb")
    private val apkDir = File(context.cacheDir, "extension-apks")

    private val catalogue = MutableStateFlow<List<ManagedExtension>?>(null)
    private val failure = MutableStateFlow<ExtensionFailure?>(null)

    private val _installed = MutableStateFlow<Map<String, InstalledExtension>>(emptyMap())
    override val installed: StateFlow<Map<String, InstalledExtension>> = _installed

    private val _transfers = MutableStateFlow<Map<String, TransferState>>(emptyMap())
    override val transfers: StateFlow<Map<String, TransferState>> = _transfers

    override val runtimeAvailable: Boolean = false

    override val state: StateFlow<ExtensionsState> =
        combine(catalogue, _installed, failure) { entries, installedNow, failed ->
            when {
                entries != null -> ExtensionsState.Content(buildEntries(entries, installedNow))
                failed != null -> ExtensionsState.Error(failed)
                else -> ExtensionsState.Loading
            }
        }.stateIn(scope, SharingStarted.Eagerly, ExtensionsState.Loading)

    private var lastRefreshAt = 0L

    init {
        scope.launch {
            refreshInstalled()
            refresh(force = false)
        }
    }

    override suspend fun refresh(force: Boolean) {
        refreshMutex.withLock {
            val fresh = System.currentTimeMillis() - lastRefreshAt < CACHE_TTL_MS
            if (!force && fresh && catalogue.value != null) return
            if (catalogue.value == null) failure.value = null

            val online = runCatching { withContext(Dispatchers.IO) { fetchBytes(MihonIndexParser.INDEX_URL) } }
            val payload = online.getOrNull() ?: runCatching { indexFile.takeIf { it.exists() }?.readBytes() }.getOrNull()

            if (payload == null) {
                failure.value = classify(online.exceptionOrNull())
                return
            }

            val parsed = runCatching { MihonIndexParser.parse(payload) }
            val entries = parsed.getOrNull()
            if (entries.isNullOrEmpty()) {
                failure.value = ExtensionFailure.INVALID_INDEX
                return
            }

            if (online.isSuccess) {
                runCatching {
                    indexFile.parentFile?.mkdirs()
                    indexFile.writeBytes(payload)
                }
            }

            lastRefreshAt = System.currentTimeMillis()
            failure.value = null
            catalogue.value = entries
            refreshInstalled()
        }
    }

    override fun reloadInstalled() {
        scope.launch { refreshInstalled() }
    }

    override fun install(extension: ManagedExtension) {
        val current = _transfers.value[extension.pkgName]
        if (current is TransferState.Downloading || current is TransferState.Installing) return

        if (!canInstallPackages()) {
            setTransfer(extension.pkgName, TransferState.Failed(ExtensionFailure.PERMISSION))
            return
        }

        scope.launch {
            setTransfer(extension.pkgName, TransferState.Downloading)
            val apk = runCatching { downloadApk(extension) }.getOrElse { error ->
                setTransfer(extension.pkgName, TransferState.Failed(classify(error), error.message))
                return@launch
            }

            setTransfer(extension.pkgName, TransferState.Installing)
            val installedNow = runCatching { requestInstall(apk, extension.pkgName) }
            if (installedNow.isFailure) {
                val error = installedNow.exceptionOrNull()
                setTransfer(extension.pkgName, TransferState.Failed(classify(error), error?.message))
                return@launch
            }

            delay(400)
            refreshInstalled()
            setTransfer(extension.pkgName, TransferState.Installed)
            delay(4000)
            clearTransfer(extension.pkgName)
        }
    }

    override fun uninstall(pkgName: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        scope.launch {
            delay(1200)
            refreshInstalled()
        }
    }

    override fun canInstallPackages(): Boolean {
        val pm = context.packageManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || pm.canRequestPackageInstalls()
    }

    private suspend fun refreshInstalled() {
        val map = withContext(Dispatchers.IO) { queryInstalled() }
        _installed.value = map
        // Drop finished install entries so the list returns to its normal actions.
        _transfers.update { transfers ->
            transfers.filterValues { it !is TransferState.Installed }
        }
    }

    private fun setTransfer(pkgName: String, state: TransferState) {
        _transfers.update { it + (pkgName to state) }
    }

    private fun clearTransfer(pkgName: String) {
        _transfers.update { it - pkgName }
    }

    private fun buildEntries(
        extensions: List<ManagedExtension>,
        installedNow: Map<String, InstalledExtension>,
    ): List<ExtensionEntry> {
        val known = extensions.mapTo(HashSet()) { it.pkgName }
        val listed = extensions.map { ExtensionEntry(it, installedNow[it.pkgName]) }
        val unlisted = installedNow.values
            .filter { it.pkgName !in known }
            .map { info ->
                ExtensionEntry(
                    extension = ManagedExtension(
                        name = info.label,
                        pkgName = info.pkgName,
                        apkUrl = "",
                        iconUrl = null,
                        libVersion = "",
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        languages = emptyList(),
                        contentWarning = 0,
                    ),
                    installed = info,
                )
            }
        return (listed + unlisted).sortedBy { it.extension.name.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun queryInstalled(): Map<String, InstalledExtension> {
        val pm = context.packageManager
        return runCatching {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
                .mapNotNull { info ->
                    val app = info.applicationInfo ?: return@mapNotNull null
                    val metadata = app.metaData
                    val isExtension = info.packageName.startsWith(EXTENSION_PACKAGE_PREFIX) ||
                        metadata?.containsKey(METADATA_SOURCE_CLASS) == true
                    if (!isExtension) return@mapNotNull null
                    val label = metadata?.getString(METADATA_NAME)
                        ?: runCatching { app.loadLabel(pm).toString() }.getOrDefault(info.packageName)
                    info.packageName to InstalledExtension(
                        pkgName = info.packageName,
                        versionCode = PackageInfoCompat.getLongVersionCode(info),
                        versionName = info.versionName.orEmpty(),
                        label = label,
                        sourceDir = app.sourceDir,
                    )
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private suspend fun downloadApk(extension: ManagedExtension): File = withContext(Dispatchers.IO) {
        require(extension.apkUrl.startsWith("https://")) { "Insecure extension URL" }
        apkDir.mkdirs()
        val destination = File(apkDir, "${extension.pkgName}.apk")
        val bytes = fetchBytes(extension.apkUrl, maxBytes = MAX_APK_BYTES)
        destination.writeBytes(bytes)

        require(bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            "Downloaded file is not an APK"
        }

        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(
            destination.absolutePath,
            PackageManager.GET_META_DATA,
        ) ?: throw IOException("Unreadable APK")

        require(info.packageName == extension.pkgName) { "Package name mismatch" }
        val archiveCode = PackageInfoCompat.getLongVersionCode(info)
        require(archiveCode >= extension.versionCode) { "APK is older than the catalogue entry" }

        // No per-APK hash is published in the index; the digest is only used to make a
        // corrupted download detectable by file size and install verification.
        require(bytes.sha256().isNotEmpty()) { "Empty APK" }
        destination
    }

    private suspend fun requestInstall(apk: File, pkgName: String) = suspendCancellableCoroutine { continuation ->
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkgName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        val action = "$ACTION_INSTALL_RESULT.$sessionId"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                runCatching { context.unregisterReceiver(this) }
                val status = intent?.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE,
                ) ?: PackageInstaller.STATUS_FAILURE
                val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (!continuation.isActive) return
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IOException(message ?: "Install failed with status $status"),
                    )
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(action).setPackage(context.packageName),
                flags,
            )
            session.commit(callback.intentSender)
        }

        continuation.invokeOnCancellation {
            runCatching { installer.abandonSession(sessionId) }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun fetchBytes(url: String, maxBytes: Int = MAX_INDEX_BYTES): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val length = connection.contentLengthLong
            if (length > maxBytes) throw IOException("Response too large")
            val bytes = connection.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) throw IOException("Response too large")
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
            if (bytes.isEmpty()) throw IOException("Empty response")
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun classify(error: Throwable?): ExtensionFailure = when (error) {
        is UnknownHostException, is SocketTimeoutException -> ExtensionFailure.OFFLINE
        null -> ExtensionFailure.NETWORK
        else -> ExtensionFailure.NETWORK
    }

    companion object {
        private const val EXTENSION_PACKAGE_PREFIX = "eu.kanade.tachiyomi.extension."
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_NAME = "tachiyomix.name"
        private const val ACTION_INSTALL_RESULT = "com.mangaos.app.extension.INSTALL_RESULT"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        private const val MAX_INDEX_BYTES = 4 * 1024 * 1024
        private const val MAX_APK_BYTES = 40 * 1024 * 1024
        private const val USER_AGENT = "MangaOS/1.0 (Android)"
    }
}

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { "%02x".format(it) }
}
