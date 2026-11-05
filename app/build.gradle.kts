import java.util.zip.ZipFile
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

val appVersionCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val appVersionName = providers.gradleProperty("VERSION_NAME").orNull ?: "2026.10.31.0"
// R8 remains an explicit release build property; CI controls the production setting.
val optimizeRelease = providers.gradleProperty("OPTIMIZE_RELEASE").orNull == "true"

val devStoreFile = providers.environmentVariable("ANDROID_DEV_KEYSTORE_FILE").orNull
val devStorePassword = providers.environmentVariable("ANDROID_DEV_KEYSTORE_PASSWORD").orNull
val devKeyAlias = providers.environmentVariable("ANDROID_DEV_KEY_ALIAS").orNull
val devKeyPassword = providers.environmentVariable("ANDROID_DEV_KEY_PASSWORD").orNull
val devStoreType = providers.environmentVariable("ANDROID_DEV_KEYSTORE_TYPE").orNull ?: "PKCS12"
val devSigningReady = listOf(
    devStoreFile,
    devStorePassword,
    devKeyAlias,
    devKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.rteats.mpeineo"
    compileSdk { version = release(37) { minorApiLevel = 0 } }

    defaultConfig {
        applicationId = "com.rteats.mpeineo"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (devSigningReady) {
            create("ciDev") {
                storeFile = rootProject.file(devStoreFile!!)
                storePassword = devStorePassword
                keyAlias = devKeyAlias
                keyPassword = devKeyPassword
                storeType = devStoreType
            }
        }


    }

    flavorDimensions += "channel"
    productFlavors {
        create("rolling") {
            dimension = "channel"
            // Keep the former Dev application ID and signing identity.
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "MPEI Lite"
            buildConfigField("String", "UPDATE_CHANNEL", "\"rolling\"")
            if (devSigningReady) {
                signingConfig = signingConfigs.getByName("ciDev")
            }
        }
    }

    buildTypes {
        release {
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

tasks.matching { it.name == "assembleRollingRelease" }.configureEach {
    doLast {
        val apk = layout.buildDirectory.file(
            "outputs/apk/rolling/release/app-rolling-release.apk",
        ).get().asFile
        check(apk.isFile) { "Rolling release APK missing: ${apk.absolutePath}" }
        ZipFile(apk).use { zipped ->
            val mapAssets = zipped.entries().asSequence()
                .map { it.name }.filter { it.contains("lefortovo") }.toList()
            check(zipped.getEntry("assets/lefortovo_overpass.osmdata") != null) {
                "Rolling release APK is missing the bundled Lefortovo OSM asset; found $mapAssets"
            }
        }
    }
}
