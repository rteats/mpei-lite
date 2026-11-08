import java.util.zip.ZipFile
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// R8 remains an explicit release build property; CI controls the production setting.
val optimizeRelease = providers.gradleProperty("OPTIMIZE_RELEASE").orNull == "true"
// Local and CI builds use the same private key, supplied through this ignored file.
val localSigningFile = rootProject.file("local-signing.properties")
val localSigning = Properties().apply {
    if (localSigningFile.isFile) {
        localSigningFile.inputStream().use(::load)
    }
}
val appSigningReady = listOf(
    "storeFile", "storePassword", "keyAlias", "keyPassword", "versionCode",
).all { !localSigning.getProperty(it).isNullOrBlank() }
val localVersionCode = if (appSigningReady) {
    localSigning.getProperty("versionCode").toIntOrNull()
        ?: error("local-signing.properties: versionCode must be an integer")
} else null
if (localSigningFile.isFile && !appSigningReady) {
    error("local-signing.properties is incomplete; see local-signing.properties.example")
}
val appVersionCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull()
    ?: localVersionCode ?: 1
val appVersionName = providers.gradleProperty("VERSION_NAME").orNull
    ?: localSigning.getProperty("versionName", "local.$appVersionCode")

android {
    namespace = "com.rteats.mpeineo"
    compileSdk { version = release(37) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "com.rteats.mpeilite"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "MPEI Lite"
        buildConfigField("String", "UPDATE_CHANNEL", "\"rolling\"")
    }

    signingConfigs {
        if (appSigningReady) {
            create("appKey") {
                storeFile = file(localSigning.getProperty("storeFile"))
                check(storeFile!!.isFile) { "App signing keystore not found: $storeFile" }
                storeType = localSigning.getProperty("storeType", "PKCS12")
                storePassword = localSigning.getProperty("storePassword")
                keyAlias = localSigning.getProperty("keyAlias")
                keyPassword = localSigning.getProperty("keyPassword")
            }
        }


    }

    buildTypes {
        debug {
            if (appSigningReady) {
                signingConfig = signingConfigs.getByName("appKey")
            }
        }
        release {
            if (appSigningReady) {
                signingConfig = signingConfigs.getByName("appKey")
            }
            isMinifyEnabled = optimizeRelease
            isShrinkResources = optimizeRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/NOTICE.md",
            "META-INF/LICENSE.md",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material 3 Expressive APIs (floating toolbar, loading indicator, animated toggles)
    // are newer than the version selected by the April 2026 Compose BOM.
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")
    // Real JSON for JVM contract tests; Android runtime supplies org.json.
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-accessibility")
    androidTestImplementation("com.squareup.leakcanary:leakcanary-android-instrumentation:2.14")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}


// The Android Gradle Plugin must know the *generated asset directory* during
// variant configuration. Writing a file into src/main/assets during mergeAssets
// is too late: the merger's inputs have already been snapshotted.
abstract class PrefetchLefortovoMapTask : DefaultTask() {
    @get:InputFile
    abstract val scriptFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun fetch() {
        // AAPT treats *.gz specially and strips the suffix. Use a neutral name
        // so the APK stores the original gzip stream for Android to inflate.
        val destination = outputDirectory.file("lefortovo_overpass.osmdata").get().asFile
        destination.parentFile.mkdirs()
        execOperations.exec {
            commandLine(
                "python3",
                scriptFile.get().asFile.absolutePath,
                "--output",
                destination.absolutePath,
            )
        }
    }
}

// Only release variants get the map snapshot. Registering the directory via
// the Android Components API wires asset merging to task generation correctly.
val prefetchLefortovoMap = tasks.register<PrefetchLefortovoMapTask>("prefetchLefortovoMap") {
    group = "build"
    description = "Generate a full Lefortovo map source for offline release APKs"
    scriptFile.set(rootProject.layout.projectDirectory.file("scripts/prefetch_lefortovo.py"))
    outputDirectory.set(layout.buildDirectory.dir("generated/lefortovoAssets"))
}
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(
        prefetchLefortovoMap,
        PrefetchLefortovoMapTask::outputDirectory,
    )
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        val apk = layout.buildDirectory.file(
            "outputs/apk/release/app-release.apk",
        ).get().asFile
        check(apk.isFile) { "Release APK missing: ${apk.absolutePath}" }
        ZipFile(apk).use { zipped ->
            val mapAssets = zipped.entries().asSequence()
                .map { it.name }.filter { it.contains("lefortovo") }.toList()
            check(zipped.getEntry("assets/lefortovo_overpass.osmdata") != null) {
                "Release APK is missing the bundled Lefortovo OSM asset; found $mapAssets"
            }
        }
    }
}
