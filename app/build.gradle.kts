plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tvremote.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvremote.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        vectorDrawables.useSupportLibrary = true
    }

    // The demo signing key is intentionally not committed. If it is absent,
    // release builds fall back to the debug key so contributors can still build.
    val releaseKeystore = rootProject.file("keystore/tvremote.jks")
    signingConfigs {
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = "tvremote"
                keyAlias = "tvremote"
                keyPassword = "tvremote"
            }
        }
        getByName("debug").apply {
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = "tvremote"
                keyAlias = "tvremote"
                keyPassword = "tvremote"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
