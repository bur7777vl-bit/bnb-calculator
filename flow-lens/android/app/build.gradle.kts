plugins {
    id("com.android.application")
}

android {
    namespace = "com.bnbflowlens.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bnbflowlens.app"
        minSdk = 26
        targetSdk = 31
        versionCode = 3
        versionName = "1.1.1"
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
        debug {
            signingConfig = signingConfigs.getByName("flowLensDev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("flowLensDev")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
