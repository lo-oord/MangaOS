package com.mangaos.app.data.extension

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.mangaos.app.domain.model.ExtensionEntry
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.InstalledExtension
import com.mangaos.app.domain.model.ManagedExtension
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.model.TransferState
import com.mangaos.app.domain.repository.ExtensionRepository
import com.mangaos.app.domain.repository.SettingsRepository
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The extension catalogue and its lifecycle: fetch the index, detect what is installed,
 * download an APK, verify it, hand it to the Android package installer, update and remove.
 *
 * Only [ExtensionAllowlist] packages and classifications reach [catalogue], the installed map
 * or [MihonRuntime]. Installed status is always read from PackageManager, never from a UI flag.
 */
class ExtensionStore(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val settings: SettingsRepository,
) : ExtensionRepository {

    private val refreshMutex = Mutex()
    private val installMutex = Mutex()
    private val uninstallMutex = Mutex()
    private val indexFile = File(context.filesDir, "extensions/index.pb")
    private val apkDir = File(context.cacheDir, "extension-apks")

    private val catalogue = MutableStateFlow<List<ManagedExtension>?>(null)
    private val failure = MutableStateFlow<ExtensionFailure?>(null)
    private val refreshFailure = MutableStateFlow<ExtensionFailure?>(null)

    private val _installed = MutableStateFlow<Map<String, InstalledExtension>>(emptyMap())
    override val installed: StateFlow<Map<String, InstalledExtension>> = _installed.asStateFlow()

    private val _transfers = MutableStateFlow<Map<String, TransferState>>(emptyMap())
    override val transfers: StateFlow<Map<String, TransferState>> = _transfers.asStateFlow()

    private val _sources = MutableStateFlow<List<SourceInfo>>(emptyList())
    override val sources: StateFlow<List<SourceInfo>> = _sources.asStateFlow()

    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    override val failures: StateFlow<Map<String, String>> = _failures.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    override val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    override val failureState: StateFlow<ExtensionFailure?> = refreshFailure.asStateFlow()

    override val runtimeAvailable: Boolean = true

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
            MihonRuntime.sources.collect { _sources.value = it }
        }
        scope.launch {
            MihonRuntime.failures.collect { _failures.value = it }
        }
        scope.launch {
            refreshInstalled()
            // "Refresh on open" controls the startup network check only. The cached index is
            // always read, and a first launch without a cache still needs the network.
            if (settings.appSettings.first().autoRefreshOnOpen || !loadCachedIndex()) {
                refresh(force = false)
            }
        }
    }

    private fun loadCachedIndex(): Boolean {
        val payload = runCatching {
            indexFile.takeIf { it.exists() }?.readBytes()
        }.getOrNull() ?: return false
        val entries = parseIndex(payload)
        if (entries.isEmpty()) return false
        catalogue.value = entries
        lastRefreshAt = System.currentTimeMillis()
        return true
    }

    private fun parseIndex(payload: ByteArray): List<ManagedExtension> =
        runCatching { ExtensionAllowlist.filter(MihonIndexParser.parse(payload)) }
            .getOrDefault(emptyList())

    override suspend fun refresh(force: Boolean) {
        refreshMutex.withLock {
            val fresh = System.currentTimeMillis() - lastRefreshAt < CACHE_TTL_MS
            if (!force && fresh && catalogue.value != null) return
            if (catalogue.value == null) failure.value = null
            _refreshing.value = true
            try {
                val downloaded = runCatching {
                    withContext(Dispatchers.IO) { fetchCatalogue() }
                }

                if (downloaded.isSuccess) {
                    val (payload, entries) = downloaded.getOrThrow()
                    runCatching {
                        indexFile.parentFile?.mkdirs()
                        indexFile.writeBytes(payload)
                    }
                    lastRefreshAt = System.currentTimeMillis()
                    failure.value = null
                    refreshFailure.value = null
                    catalogue.value = entries
                    refreshInstalled()
                    return
                }

                val error = downloaded.exceptionOrNull()
                Log.w(TAG, "Could not refresh the extension catalogue", error)
                if (catalogue.value == null) {
                    loadCachedIndex()
                }
                val classified = classifyRefresh(error)
                if (catalogue.value == null) {
                    failure.value = classified
                } else {
                    refreshFailure.value = classified
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * Resolves `repo.json` first (current `index_v2` pointer, legacy `index_v1`) and falls back
     * to the direct protobuf index. JSON and protobuf payloads are both accepted by the parser.
     */
    private fun fetchCatalogue(): Pair<ByteArray, List<ManagedExtension>> {
        val urls = LinkedHashSet<String>()
        val repoPayload = runCatching { fetchBytes(MihonIndexParser.REPO_URL, MAX_REPO_BYTES) }
            .getOrNull()
        repoPayload?.let(MihonIndexParser::indexUrlFromRepo)?.let(urls::add)
        urls += MihonIndexParser.INDEX_URL

        var lastError: Throwable? = null
        for (url in urls) {
            val result = runCatching { fetchBytes(url) }
            if (result.isFailure) {
                lastError = result.exceptionOrNull()
                continue
            }
            val payload = result.getOrThrow()
            val entries = parseIndex(payload)
            if (entries.isNotEmpty()) return payload to entries
        }
        throw lastError ?: IOException("No usable extension index")
    }

    override fun reloadInstalled() {
        scope.launch { refreshInstalled() }
    }

    override fun install(extension: ManagedExtension) {
        if (!ExtensionAllowlist.isAllowedPackage(extension.pkgName)) {
            setTransfer(extension.pkgName, TransferState.Failed(ExtensionFailure.UNSUPPORTED))
            return
        }
        if (!canInstallPackages()) {
            setTransfer(extension.pkgName, TransferState.Failed(ExtensionFailure.PERMISSION))
            return
        }
        val currentTransfer = _transfers.value[extension.pkgName]
        if (currentTransfer is TransferState.Downloading ||
            currentTransfer is TransferState.Installing ||
            currentTransfer is TransferState.Uninstalling
        ) {
            return
        }
        val alreadyInstalled = _installed.value[extension.pkgName]
        if (alreadyInstalled != null && alreadyInstalled.versionCode >= extension.versionCode) {
            return
        }

        scope.launch {
            installMutex.withLock {
                val transfer = _transfers.value[extension.pkgName]
                if (transfer is TransferState.Downloading || transfer is TransferState.Installing) {
                    return@withLock
                }
                setTransfer(extension.pkgName, TransferState.Downloading)
                var downloaded: File? = null
                try {
                    val apk = downloadApk(extension)
                    downloaded = apk
                    setTransfer(extension.pkgName, TransferState.Installing)
                    withTimeout(INSTALL_TIMEOUT_MS) {
                        requestInstall(apk, extension.pkgName)
                    }
                    awaitPackageInstalled(extension.pkgName, extension.versionCode)
                    refreshInstalled()
                    if (_installed.value[extension.pkgName] == null) {
                        throw IOException("The package installer reported success but the package is absent")
                    }
                    clearTransfer(extension.pkgName)
                } catch (error: TimeoutCancellationException) {
                    Log.w(TAG, "Install timed out for ${extension.pkgName}", error)
                    downloaded?.delete()
                    setTransfer(
                        extension.pkgName,
                        TransferState.Failed(ExtensionFailure.INSTALL, "Install timed out"),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Log.w(TAG, "Install failed for ${extension.pkgName}", error)
                    downloaded?.delete()
                    setTransfer(
                        extension.pkgName,
                        TransferState.Failed(classifyInstall(error), error.message),
                    )
                }
            }
        }
    }

    override fun uninstall(pkgName: String) {
        if (!ExtensionAllowlist.isAllowedPackage(pkgName)) return
        val current = _transfers.value[pkgName]
        if (current is TransferState.Uninstalling ||
            current is TransferState.Downloading ||
            current is TransferState.Installing
        ) {
            return
        }
        scope.launch {
            uninstallMutex.withLock {
                setTransfer(pkgName, TransferState.Uninstalling)
                try {
                    withTimeout(UNINSTALL_TIMEOUT_MS) { requestUninstall(pkgName) }
                    awaitPackageRemoved(pkgName)
                    File(apkDir, "$pkgName.apk").delete()
                    refreshInstalled()
                    if (_installed.value.containsKey(pkgName)) {
                        throw IOException("The package is still installed after uninstall")
                    }
                    clearTransfer(pkgName)
                } catch (error: TimeoutCancellationException) {
                    Log.w(TAG, "Uninstall timed out for $pkgName", error)
                    setTransfer(pkgName, TransferState.UninstallFailed)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Log.w(TAG, "Uninstall failed for $pkgName", error)
                    setTransfer(pkgName, TransferState.UninstallFailed)
                    // Last-resort path only when this Android build refuses the installer
                    // API itself. A user cancellation must not open a second dialog.
                    if (error is SecurityException || error is UnsupportedOperationException) {
                        openSystemUninstall(pkgName)
                    }
                }
            }
        }
    }

    override fun canInstallPackages(): Boolean {
        val pm = context.packageManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || pm.canRequestPackageInstalls()
    }

    private suspend fun refreshInstalled() {
        val map = withContext(Dispatchers.IO) {
            queryInstalled().filterKeys(ExtensionAllowlist::isAllowedPackage)
        }
        _installed.value = map
        // The runtime re-instantiates only packages that changed; unchanged sources keep
        // their state (cookies, filter state) across a refresh.
        runCatching { MihonRuntime.reload(context, map.mapValues { it.value.versionCode }) }
            .onFailure { Log.w(TAG, "Could not reload the extension runtime", it) }
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
        val missingFromIndex = installedNow.values
            .filter { it.pkgName !in known && ExtensionAllowlist.isAllowedPackage(it.pkgName) }
            .map { info ->
                val languages = _sources.value
                    .filter { it.pkgName == info.pkgName }
                    .map { it.lang }
                    .distinct()
                ExtensionEntry(
                    extension = ManagedExtension(
                        name = ExtensionAllowlist.displayName(info.pkgName) ?: info.label,
                        pkgName = info.pkgName,
                        apkUrl = "",
                        iconUrl = null,
                        libVersion = "",
                        versionCode = info.versionCode,
                        versionName = info.versionName,
                        languages = languages,
                        contentWarning = 1,
                    ),
                    installed = info,
                )
            }
        return (listed + missingFromIndex)
            .distinctBy { it.extension.pkgName }
            .sortedBy { it.extension.name.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun queryInstalled(): Map<String, InstalledExtension> {
        val pm = context.packageManager
        return runCatching {
            pm.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS)
                .mapNotNull { info ->
                    val app = info.applicationInfo ?: return@mapNotNull null
                    val metadata = app.metaData
                    val declaresExtensionFeature = info.reqFeatures
                        ?.any { it.name == EXTENSION_FEATURE } == true
                    val isExtension = declaresExtensionFeature ||
                        info.packageName.startsWith(EXTENSION_PACKAGE_PREFIX) ||
                        metadata?.containsKey(METADATA_SOURCE_CLASS) == true ||
                        metadata?.containsKey(METADATA_SOURCE_FACTORY) == true
                    if (!isExtension) return@mapNotNull null
                    val label = ExtensionAllowlist.displayName(info.packageName)
                        ?: metadata?.getString(METADATA_NAME)
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
        require(ExtensionAllowlist.isAllowedPackage(extension.pkgName)) {
            "Extension is not in the MangaOS allowlist"
        }
        require(extension.apkUrl.startsWith("https://")) { "Insecure extension URL" }
        apkDir.mkdirs()
        val destination = File(apkDir, "${extension.pkgName}.apk")
        val temporary = File(apkDir, "${extension.pkgName}.apk.download")
        try {
            val bytes = fetchBytes(extension.apkUrl, maxBytes = MAX_APK_BYTES)
            temporary.writeBytes(bytes)

            require(bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                "Downloaded file is not an APK"
            }

            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(
                temporary.absolutePath,
                PackageManager.GET_META_DATA,
            ) ?: throw IOException("Unreadable APK")

            require(info.packageName == extension.pkgName) { "Package name mismatch" }
            val archiveCode = PackageInfoCompat.getLongVersionCode(info)
            require(archiveCode >= extension.versionCode) { "APK is older than the catalogue entry" }
            val metadata = info.applicationInfo?.metaData
            require(
                metadata?.containsKey(METADATA_SOURCE_CLASS) == true ||
                    metadata?.containsKey(METADATA_SOURCE_FACTORY) == true,
            ) { "APK does not declare a Mihon extension source" }

            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination
        } catch (error: Throwable) {
            temporary.delete()
            destination.delete()
            throw error
        }
    }

    /**
     * Runs one install session. `STATUS_PENDING_USER_ACTION` is the normal system confirmation
     * step: launching its intent is required, and only the following SUCCESS status counts.
     */
    private suspend fun requestInstall(apk: File, pkgName: String) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(pkgName)
                setSize(apk.length())
            }
            val sessionId = installer.createSession(params)
            val action = "$ACTION_INSTALL_RESULT.$sessionId"

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val status = intent?.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE,
                    ) ?: PackageInstaller.STATUS_FAILURE
                    val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            runCatching { context.unregisterReceiver(this) }
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirmation = intent
                                ?.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                            if (confirmation == null) {
                                runCatching { context.unregisterReceiver(this) }
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IOException("Installer requested confirmation without an intent"),
                                    )
                                }
                            } else {
                                runCatching {
                                    context.startActivity(
                                        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.onFailure { error ->
                                    runCatching { context.unregisterReceiver(this) }
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(error)
                                    }
                                }
                            }
                        }
                        else -> {
                            runCatching { context.unregisterReceiver(this) }
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException(message ?: "Install failed with status $status"),
                                )
                            }
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_EXPORTED,
            )

            try {
                installer.openSession(sessionId).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("base.apk", 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE
                        } else {
                            0
                        }
                    val callback = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(action).setPackage(context.packageName),
                        flags,
                    )
                    session.commit(callback.intentSender)
                }
            } catch (error: Throwable) {
                runCatching { installer.abandonSession(sessionId) }
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            continuation.invokeOnCancellation {
                runCatching { installer.abandonSession(sessionId) }
                runCatching { context.unregisterReceiver(receiver) }
            }
        }

    private suspend fun requestUninstall(pkgName: String) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val installer = context.packageManager.packageInstaller
            val action = "$ACTION_UNINSTALL_RESULT.$pkgName"
            val requestCode = pkgName.hashCode()

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val status = intent?.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE,
                    ) ?: PackageInstaller.STATUS_FAILURE
                    val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            runCatching { context.unregisterReceiver(this) }
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirmation = intent
                                ?.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                            if (confirmation == null) {
                                runCatching { context.unregisterReceiver(this) }
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IOException("Uninstall requested confirmation without an intent"),
                                    )
                                }
                            } else {
                                runCatching {
                                    context.startActivity(
                                        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.onFailure { error ->
                                    runCatching { context.unregisterReceiver(this) }
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(error)
                                    }
                                }
                            }
                        }
                        else -> {
                            runCatching { context.unregisterReceiver(this) }
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException(message ?: "Uninstall failed with status $status"),
                                )
                            }
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_EXPORTED,
            )

            try {
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                val callback = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    Intent(action).setPackage(context.packageName),
                    flags,
                )
                installer.uninstall(pkgName, callback.intentSender)
            } catch (error: Throwable) {
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }

    private fun openSystemUninstall(pkgName: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private suspend fun awaitPackageInstalled(pkgName: String, minVersionCode: Long) {
        repeat(PACKAGE_POLL_ATTEMPTS) {
            val info = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkgName, 0)
            }.getOrNull()
            if (info != null && PackageInfoCompat.getLongVersionCode(info) >= minVersionCode) {
                return
            }
            delay(PACKAGE_POLL_DELAY_MS)
        }
        throw IOException("The package did not become available after installation")
    }

    private suspend fun awaitPackageRemoved(pkgName: String) {
        repeat(PACKAGE_POLL_ATTEMPTS) {
            val present = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkgName, 0)
            }.isSuccess
            if (!present) return
            delay(PACKAGE_POLL_DELAY_MS)
        }
        throw IOException("The package is still present after uninstallation")
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
            if (code !in 200..299) throw IOException("HTTP $code for $url")
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

    private fun classifyRefresh(error: Throwable?): ExtensionFailure = when (error) {
        is UnknownHostException, is SocketTimeoutException -> ExtensionFailure.OFFLINE
        is IOException -> ExtensionFailure.NETWORK
        else -> ExtensionFailure.INVALID_INDEX
    }

    private fun classifyInstall(error: Throwable?): ExtensionFailure = when (error) {
        is UnknownHostException, is SocketTimeoutException -> ExtensionFailure.NETWORK
        is SecurityException -> ExtensionFailure.PERMISSION
        is IOException -> ExtensionFailure.INSTALL
        else -> ExtensionFailure.INSTALL
    }

    companion object {
        private const val TAG = "ExtensionStore"
        private const val EXTENSION_FEATURE = "tachiyomi.extension"
        private const val EXTENSION_PACKAGE_PREFIX = "eu.kanade.tachiyomi.extension."
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
        private const val METADATA_NAME = "tachiyomix.name"
        private const val ACTION_INSTALL_RESULT = "com.mangaos.app.extension.INSTALL_RESULT"
        private const val ACTION_UNINSTALL_RESULT = "com.mangaos.app.extension.UNINSTALL_RESULT"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        private const val MAX_REPO_BYTES = 256 * 1024
        private const val MAX_INDEX_BYTES = 8 * 1024 * 1024
        private const val MAX_APK_BYTES = 60 * 1024 * 1024
        private const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L
        private const val UNINSTALL_TIMEOUT_MS = 2 * 60 * 1000L
        private const val PACKAGE_POLL_ATTEMPTS = 25
        private const val PACKAGE_POLL_DELAY_MS = 200L
        private const val USER_AGENT = "MangaOS/1.0 (Android)"
    }
}
