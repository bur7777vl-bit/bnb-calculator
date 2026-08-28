plugins {
    id("com.android.application")
}

android {
    namespace = "com.bnbflowlens.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bnbflowlens.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("flowLensDev") {
            storeFile = file("../flow-lens-dev.jks")
            storePassword = "android"
            keyAlias = "flowlens"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("flowLensDev")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
