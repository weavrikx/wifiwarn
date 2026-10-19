import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 可选签名: 若仓库根存在 keystore.properties (未纳入版本库) 则启用 release 签名配置
val keystoreProps = rootProject.file("keystore.properties")
val hasReleaseSigning = keystoreProps.exists()
val keystorePropsMap = Properties().apply {
    if (hasReleaseSigning) keystoreProps.inputStream().use { load(it) }
}

android {
    namespace = "dev.weavrik.wifiwarn"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.weavrik.wifiwarn"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePropsMap.getProperty("storeFile"))
                storePassword = keystorePropsMap.getProperty("storePassword")
                keyAlias = keystorePropsMap.getProperty("keyAlias")
                keyPassword = keystorePropsMap.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}