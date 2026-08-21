import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Signing material lives in local.properties, which is not in version control — a signing key
 * in a repository is a signing key anyone can use to publish something claiming to be this app.
 * With no key configured the release build still runs and produces an unsigned APK, so a fresh
 * checkout that has never been signed is not a broken checkout.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val keystorePath: String? = localProperties.getProperty("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "az.pixclean"
    compileSdk = 36

    defaultConfig {
        applicationId = "az.pixclean"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
        vectorDrawables { useSupportLibrary = true }

        // No phone runs x86. Those libraries exist for emulators, Chromebooks and
        // Windows Subsystem for Android, and they were 27 MB of the 52 MB universal
        // APK — more than half the download, for hardware none of its users have.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = localProperties.getProperty("KEY_ALIAS")
                keyPassword = localProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    // Per-ABI builds for anyone who wants the smallest file; the universal one carries both
    // remaining architectures and is what the stable download link points at, because someone
    // following a link does not know their CPU.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
    // .tflite must not be compressed or it cannot be memory-mapped.
    androidResources { noCompress += "tflite" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    implementation(libs.mlkit.face.detection)
    implementation(libs.tensorflow.lite)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
