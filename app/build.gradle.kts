plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.projector.home"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.projector.home"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "5.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "cinemahome123"
                keyAlias = "cinemahome"
                keyPassword = "cinemahome123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (file("release.jks").exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
}
