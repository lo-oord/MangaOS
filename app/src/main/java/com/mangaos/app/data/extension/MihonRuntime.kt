package com.mangaos.app.data.extension

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.mangaos.app.domain.model.SourceInfo
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.File
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import tachiyomi.core.common.preference.AndroidPreferenceStore
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
            val app = context.applicationContext
            appContext = app
            Injekt.addSingleton(app)
            (app as? android.app.Application)?.let { Injekt.addSingleton(it) }
            hostReady = true
        }
    }

    private fun requireContext(): Context =
        appContext ?: throw IllegalStateException("MihonRuntime.initialize() was not called")

    /**
     * Builds the shared services extensions inject. Must run off the main thread because it
     * creates the cookie jar and the OkHttp client.
     */
    private fun ensureHostNetwork(context: Context) {
        if (networkReady) return
        synchronized(this) {
            if (networkReady) return
            val preferenceStore = AndroidPreferenceStore(context)
            val networkPreferences = NetworkPreferences(preferenceStore, isDebugBuild = false)
            if (networkPreferences.dohProvider.get() == -1) {
                // Resolve through a public DoH provider instead of the device resolver,
                // which is what Mihon does when DoH is unset.
                networkPreferences.dohProvider.set(PREF_DOH_CLOUDFLARE)
            }
            val networkHelper = NetworkHelper(GlobalScope, context, networkPreferences)
            Injekt.addSingleton(networkHelper)
            Injekt.addSingleton(networkHelper.client)
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
            Injekt.addSingleton(JavaScriptEngine(context))
            networkReady = true
        }
    }

    /**
     * Re-scans the given installed extension packages and rebuilds the source registry.
     * Packages whose version did not change keep their existing instances.
     */
    suspend fun reload(context: Context, installedPackages: Map<String, Long>) = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            ensureHostNetwork(context)
            val packageManager = context.packageManager
            val nextLoaded = LinkedHashMap<String, LoadedExtension>()
            val nextFailures = LinkedHashMap<String, String>()

            for (pkgName in installedPackages.keys.sorted()) {
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
                if (loadedNow != null) {
                    nextLoaded[pkgName] = loadedNow
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
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val factoryList = metaData.getString(METADATA_SOURCE_FACTORY)
            ?.split(";")
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
): ClassLoader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    DelegateLastClassLoader(dexPath, librarySearchPath, parent)
} else {
    DelegateLastPathClassLoader(dexPath, librarySearchPath, parent)
}

private class DelegateLastPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        val className = name ?: throw ClassNotFoundException("null class name")
        findLoadedClass(className)?.let { return it }
        val boot = bootClassLoader
        if (boot != null) {
            try {
                return boot.loadClass(className)
            } catch (_: ClassNotFoundException) {
                // Fall through to the extension DEX.
            }
        }
        return try {
            findClass(className)
        } catch (dexError: ClassNotFoundException) {
            try {
                parent.loadClass(className)
            } catch (_: ClassNotFoundException) {
                throw dexError
            }
        }
    }

    override fun getResource(name: String?): URL? =
        bootClassLoader?.getResource(name) ?: findResource(name) ?: parent?.getResource(name)

    override fun getResources(name: String?): Enumeration<URL> {
        val resourceName = name ?: return Collections.emptyEnumeration()
        val resources = buildList {
            bootClassLoader?.getResources(resourceName)?.let { addAll(it.toList()) }
            findResources(resourceName)?.let { addAll(it.toList()) }
            parent?.getResources(resourceName)?.let { addAll(it.toList()) }
        }
        return Collections.enumeration(resources)
    }
}
