import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.aliemrevezir.evtv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aliemrevezir.evtv"
        minSdk = 21
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.$versionCode"
    }

    // Stick'in ABI'si doğrulanana kadar iki ayrı APK (x86_64: Intel Mac emülatörü).
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // İmza anahtarı GitHub sırlarından gelir; yoksa debug anahtarıyla imzalanır.
    val imzaDosyasi = System.getenv("EVTV_IMZA_DOSYASI")
    signingConfigs {
        if (imzaDosyasi != null) {
            create("evtv") {
                storeFile = file(imzaDosyasi)
                storePassword = System.getenv("EVTV_IMZA_SIFRESI")
                keyAlias = "evtv"
                keyPassword = System.getenv("EVTV_IMZA_SIFRESI")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("evtv") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.aliemrevezir.evtv:cekirdek")
    implementation(libs.libvlc)
}
