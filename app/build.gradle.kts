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
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
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

    // Half the release APK was x86 native code that only an emulator can run. Split it so a
    // phone downloads roughly 21 MB instead of 55 MB; the universal APK stays for sideloading.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
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
