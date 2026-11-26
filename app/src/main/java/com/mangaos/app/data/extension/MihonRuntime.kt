package com.mangaos.app.data.extension

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.mangaos.app.domain.model.SourceInfo
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

/**
 * Real Mihon extension runtime.
 *
 * Keiyoushi extensions are ordinary APKs whose shared dependencies are `compileOnly`: they
 * contain only their own source classes and resolve the whole source API, OkHttp, Jsoup,
 * RxJava, Injekt and the network helpers from the host. MangaOS therefore:
 *
 * 1. ships the real Mihon source API and host implementations (`source-api` +
 *    `core-common`, Apache-2.0),
 * 2. registers the services extensions inject (context, [NetworkHelper], JSON, QuickJS),
 * 3. loads each installed extension APK with a delegate-last class loader so its DEX is used
 *    while shared API types keep resolving to this app's copies,
 * 4. instantiates every class declared in `tachiyomi.extension.class` (or the sources of a
 *    `tachiyomi.extension.factory`), and
 * 5. exposes the resulting [Source] instances to the repository.
 *
 * Loading is isolated per extension: a malformed or incompatible extension is reported and
 * skipped without affecting the other sources. Instances are cached by package + version so
 * reopening the app does not recreate sources unnecessarily.
 */
object MihonRuntime {

    private const val TAG = "MihonRuntime"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_LEGACY_NAME = "tachiyomi.extension.name"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    private const val METADATA_LIB = "tachiyomix.extensionLib"

    private val loadMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var hostReady = false

    private var networkReady = false

    private data class LoadedExtension(
        val pkgName: String,
        val name: String,
        val versionCode: Long,
        val versionName: String,
        val isNsfw: Boolean,
        val sourceDir: String,
        val classLoader: ClassLoader,
        val sources: List<Source>,
    )

    private val loaded = LinkedHashMap<String, LoadedExtension>()
    private val sourceRegistry = ConcurrentHashMap<Long, Source>()
    private val sourceLocks = ConcurrentHashMap<Long, Mutex>()

    private val _sources = MutableStateFlow<List<SourceInfo>>(emptyList())
    val sources: StateFlow<List<SourceInfo>> = _sources.asStateFlow()

    /** package name -> human readable load failure, for installed extensions. */
    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    val failures: StateFlow<Map<String, String>> = _failures.asStateFlow()

    private val _generation = MutableStateFlow(0)

    /** Increments whenever the registry changes, so screens can drop stale results. */
    val generation: StateFlow<Int> = _generation.asStateFlow()

    fun initialize(context: Context) {
        if (hostReady) return
        synchronized(this) {
            if (hostReady) return
            val application = context.applicationContext as Application
            appContext = application
            Injekt.addSingleton<Application>(application)
            Injekt.addSingleton<Context>(application)
            hostReady = true
        }
    }

    /**
     * Builds every service a Mihon extension may inject. Keiyoushi sources resolve all of these
     * from the host: a missing binding fails the first source call even though the APK loaded.
     * This mirrors DropSauce's host bridge instead of registering only [NetworkHelper].
     */
    private fun ensureHostNetwork(context: Context) {
        if (networkReady) return
        synchronized(this) {
            if (networkReady) return
            val application = context.applicationContext as Application
            val sharedPreferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context)
            val preferenceStore = AndroidPreferenceStore(sharedPreferences)
            val networkPreferences = NetworkPreferences(preferenceStore, isDebugBuild = false)
            // Do not force a DoH provider. Mihon leaves this at the user's/default choice; forcing
            // Cloudflare DoH makes every source unreachable on networks where it is blocked.
            val networkHelper = NetworkHelper(GlobalScope, context, networkPreferences)
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
            val xml = XML.v1 {
                policy {
                    ignoreUnknownChildren()
                    autoPolymorphic = true
                }
                xmlDeclMode = XmlDeclMode.Charset
                xmlVersion = XmlVersion.XML10
                setIndent(2)
            }
            Injekt.addSingleton<NetworkHelper>(networkHelper)
            Injekt.addSingleton<OkHttpClient>(networkHelper.client)
            Injekt.addSingleton<CookieJar>(networkHelper.client.cookieJar)
            Injekt.addSingleton<AndroidCookieJar>(networkHelper.cookieJar)
            Injekt.addSingleton<SharedPreferences>(sharedPreferences)
            Injekt.addSingleton<PreferenceStore>(preferenceStore)
            Injekt.addSingleton<Json>(json)
            Injekt.addSingleton<StringFormat>(json)
            Injekt.addSingleton<SerialFormat>(json)
            Injekt.addSingleton<ProtoBuf>(ProtoBuf)
            Injekt.addSingleton<XML>(xml)
            Injekt.addSingleton<JavaScriptEngine>(JavaScriptEngine(application))
            networkReady = true
        }
    }

    /**
     * Re-scans the given installed extension packages and rebuilds the source registry.
     * Packages whose version did not change keep their existing instances.
     */
    suspend fun reload(context: Context, installedPackages: Map<String, Long>) = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            // A missing network service must not prevent already-installed sources from loading.
            runCatching { ensureHostNetwork(context) }
                .onFailure { Log.w(TAG, "Could not initialise the shared network services", it) }
            val packageManager = context.packageManager
            val nextLoaded = LinkedHashMap<String, LoadedExtension>()
            val nextFailures = LinkedHashMap<String, String>()

            for (pkgName in installedPackages.keys
                .filter(ExtensionAllowlist::isAllowedPackage)
                .sorted()
            ) {
                val existing = loaded[pkgName]
                val installedVersion = installedPackages[pkgName]
                if (existing != null &&
                    existing.versionCode == installedVersion &&
                    existing.sourceDir.isNotBlank() &&
                    File(existing.sourceDir).exists()
                ) {
                    nextLoaded[pkgName] = existing
                    continue
                }
                val result = runCatching { loadPackage(context, packageManager, pkgName) }
                val loadedNow = result.getOrNull()
                if (loadedNow != null && loadedNow.isNsfw) {
                    nextFailures[pkgName] = "Blocked: NSFW extension"
                } else if (loadedNow != null) {
                    nextLoaded[pkgName] = loadedNow.copy(
                        sources = loadedNow.sources.filter {
                            ExtensionAllowlist.isAllowedRuntimeSource(pkgName, loadedNow.isNsfw)
                        },
                    )
                    if (nextLoaded[pkgName]?.sources.isNullOrEmpty()) {
                        nextLoaded.remove(pkgName)
                        nextFailures[pkgName] = "No allowlisted source in this extension"
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val message = error?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
                        ?: "Unknown extension load error"
                    Log.w(TAG, "Could not load extension $pkgName", error)
                    nextFailures[pkgName] = message
                }
            }

            loaded.clear()
            loaded.putAll(nextLoaded)

            sourceRegistry.clear()
            sourceLocks.clear()
            nextLoaded.values.forEach { extension ->
                extension.sources.forEach { source -> sourceRegistry[source.id] = source }
            }

            _failures.value = nextFailures
            _sources.value = nextLoaded.values
                .flatMap { extension ->
                    extension.sources.map { source ->
                        SourceInfo(
                            id = source.id.toString(),
                            name = source.name,
                            lang = source.langOrAll(),
                            pkgName = extension.pkgName,
                            extensionName = extension.name,
                            versionName = extension.versionName,
                            isNsfw = extension.isNsfw,
                            supportsLatest = source.supportsLatest,
                        )
                    }
                }
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
            _generation.value = _generation.value + 1
        }
    }

    private fun Source.langOrAll(): String =
        (this as? eu.kanade.tachiyomi.source.CatalogueSource)?.lang?.takeIf { it.isNotBlank() } ?: "all"

    private fun loadPackage(
        context: Context,
        packageManager: PackageManager,
        pkgName: String,
    ): LoadedExtension {
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
        val packageInfo = packageManager.getPackageInfo(pkgName, flags)
        val appInfo = packageInfo.applicationInfo ?: error("Missing application info")
        val metaData = appInfo.metaData ?: error("Extension has no manifest metadata")

        val classNameList = metaData.getString(METADATA_SOURCE_CLASS)
            ?.split(';', ':', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val factoryList = metaData.getString(METADATA_SOURCE_FACTORY)
            ?.split(';', ':', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (classNameList.isEmpty() && factoryList.isEmpty()) {
            error("Extension does not declare $METADATA_SOURCE_CLASS")
        }

        val name = metaData.getString(METADATA_NAME)
            ?: metaData.getString(METADATA_LEGACY_NAME)
            ?: runCatching { appInfo.loadLabel(packageManager).toString() }.getOrNull()
            ?: pkgName
        val isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1
        val libVersion = metaData.getFloat(METADATA_LIB, 0f)
        if (libVersion > 0f) {
            Log.i(TAG, "Loading $name ($pkgName) extension API $libVersion")
        }

        val classLoader = createExtensionClassLoader(
            dexPath = appInfo.sourceDir,
            librarySearchPath = appInfo.nativeLibraryDir,
            parent = context.classLoader,
        )

        val sourceClasses = (classNameList + factoryList).map { className ->
            if (className.startsWith(".")) pkgName + className else className
        }

        val instantiated = mutableListOf<Source>()
        val classErrors = mutableListOf<String>()
        sourceClasses.forEach { className ->
            runCatching {
                val clazz = Class.forName(className, false, classLoader)
                when (val instance = clazz.getDeclaredConstructor().newInstance()) {
                    is Source -> listOf(instance)
                    is SourceFactory -> instance.createSources()
                    else -> error("Class $className is not a Source or SourceFactory")
                }
            }.onSuccess { instantiated += it }
                .onFailure { error ->
                    classErrors += "$className: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                    Log.w(TAG, "Could not instantiate $className from $pkgName", error)
                }
        }

        if (instantiated.isEmpty()) {
            error(
                if (classErrors.isEmpty()) {
                    "Extension provides no usable sources"
                } else {
                    classErrors.joinToString("; ")
                },
            )
        }

        return LoadedExtension(
            pkgName = pkgName,
            name = name,
            versionCode = packageInfo.longVersionCodeCompat(),
            versionName = packageInfo.versionName.orEmpty(),
            isNsfw = isNsfw,
            sourceDir = appInfo.sourceDir.orEmpty(),
            classLoader = classLoader,
            sources = instantiated,
        )
    }

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    fun source(sourceId: String): Source? = sourceId.toLongOrNull()?.let { sourceRegistry[it] }

    fun headers(sourceId: String): Map<String, String> {
        val source = source(sourceId) as? HttpSource ?: return emptyMap()
        return runCatching {
            source.headers.toMultimap().mapValues { entry -> entry.value.firstOrNull().orEmpty() }
        }.getOrDefault(emptyMap())
    }

    /** Serializes calls per source: extension sources are stateful and not thread-safe. */
    suspend fun <T> withSourceLock(sourceId: String, block: suspend () -> T): T {
        val key = sourceId.toLongOrNull()
        if (key == null) return block()
        val lock = sourceLocks.getOrPut(key) { Mutex() }
        return lock.withLock { block() }
    }

    fun isInstalled(installedPackages: Set<String>, pkgName: String): Boolean = pkgName in installedPackages

    fun loadedSourceCount(pkgName: String): Int = loaded[pkgName]?.sources?.size ?: 0
}

/**
 * Creates a class loader whose delegation order is: boot/system classes, then the extension
 * APK, then the host app. This keeps framework and shared API types single-instance while
 * letting an extension resolve its own classes even if the app ships a different library
 * version.
 */
private fun createExtensionClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
): ClassLoader = ChildFirstPathClassLoader(dexPath, librarySearchPath, parent)

/**
 * Loads framework/host classes from the app first, then the extension's own DEX, then the
 * remaining parent classes. The source APK references the Mihon API as compileOnly, so host
 * types must stay single-instance while the extension's generated source class must win over
 * any same-named class elsewhere.
 */
private class ChildFirstPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val systemClassLoader: ClassLoader? = getSystemClassLoader()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        var loadedClass = findLoadedClass(name)

        val systemLoader = systemClassLoader
        if (loadedClass == null && systemLoader != null) {
            loadedClass = runCatching { systemLoader.loadClass(name) }.getOrNull()
        }

        if (loadedClass == null) {
            loadedClass = runCatching { findClass(name) }.getOrElse {
                super.loadClass(name, resolve)
            }
        }

        if (resolve) {
            resolveClass(loadedClass)
        }

        return loadedClass
    }

    override fun getResource(name: String): URL? =
        systemClassLoader?.getResource(name) ?: findResource(name) ?: super.getResource(name)

    override fun getResources(name: String): Enumeration<URL> {
        val systemUrls = systemClassLoader?.getResources(name)
        val localUrls = findResources(name)
        val parentUrls = parent?.getResources(name)
        val urls = buildList {
            while (systemUrls?.hasMoreElements() == true) add(systemUrls.nextElement())
            while (localUrls?.hasMoreElements() == true) add(localUrls.nextElement())
            while (parentUrls?.hasMoreElements() == true) add(parentUrls.nextElement())
        }
        return Collections.enumeration(urls)
    }

    override fun getResourceAsStream(name: String): InputStream? = try {
        getResource(name)?.openStream()
    } catch (_: IOException) {
        null
    }
}
