import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing is supplied either by environment variables (GitHub Actions) or by a
 * local, git-ignored `keystore.properties` file. No secret material lives in this file.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("MANGAOS_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("MANGAOS_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("MANGAOS_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("MANGAOS_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.mangaos.app"

    // The Mihon extension API (source-api / core-common 1.1.0) is published against
    // Android 17.2. The app compiles against the same platform so extension classes and
    // their host implementations share one ABI.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.mangaos.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        resourceConfigurations += listOf("ar", "en", "ja")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    // Background download queue and the periodic new-chapter check.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Mihon extension runtime. The API itself is compileOnly for extensions, so the host
    // must ship the real implementations and every shared library they link against.
    implementation(libs.mihon.source.api) {
        exclude(group = "com.github.verizonkert", module = "core-common")
        // source-api references @Stable only; keep the app's own Compose runtime version.
        exclude(group = "androidx.compose")
    }
    implementation(libs.mihon.core.common) {
        // These modules belong to the publisher's own app shell and are not on any public
        // repository. The network/source code paths used here do not reference them.
        exclude(group = "mihon-kit-project")
        exclude(group = "mihon-kit-project.core")
        exclude(group = "ca.mpreg")
        // Runtime-only helpers for features MangaOS does not call (archive import, storage
        // providers, Apollo). They are not on the browse/search/read path, and leaving them
        // in would make dependency resolution depend on JitPack's build service.
        exclude(group = "com.github.mihon", module = "unifile")
        exclude(group = "me.zhanghai.android.libarchive", module = "library")
        exclude(group = "com.github.gpanther", module = "java-nat-sort")
        exclude(group = "com.apollographql.apollo", module = "apollo-runtime")
        exclude(group = "com.github.zhanghai.quickjs-java", module = "quickjs-android")
    }
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.dnsoverhttps)
    // Modern Keiyoushi extensions reference OkHttp's zstd codecs directly. The extension
    // APK compiles them as host-provided classes, so the host must ship this artifact.
    implementation(libs.okhttp.zstd)
    implementation(libs.okio)
    implementation(libs.jsoup)
    implementation(libs.injekt)
    implementation(libs.rxjava)
    implementation(libs.kotlinx.serialization.json)
    // Extensions use the okio bridge for page/list JSON and the protobuf/XML formats when
    // their source declares them. These are host dependencies, not bundled in the APK.
    implementation(libs.kotlinx.serialization.json.okio)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.xmlutil.core)
    implementation(libs.xmlutil.serialization)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.quickjs.android)
    // Loaded by core-common's Cloudflare/WebView interceptors at runtime.
    implementation(libs.androidx.webkit)
    implementation(libs.logcat)
    // Resource type used by the i18n messages those interceptors reference.
    implementation(libs.moko.resources)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
