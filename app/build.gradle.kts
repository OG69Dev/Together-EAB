import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "dev.og69.eab"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.og69.eab"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.0.10"
        val ghOwner = (localProperties.getProperty("github.owner") ?: "").replace("\"", "\\\"")
        val ghRepo = (localProperties.getProperty("github.repo") ?: "").replace("\"", "\\\"")
        val ghToken = (localProperties.getProperty("github.token") ?: "").replace("\"", "\\\"")
        buildConfigField("String", "GITHUB_OWNER", "\"$ghOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$ghRepo\"")
        buildConfigField("String", "GITHUB_TOKEN", "\"$ghToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
}

base {
    archivesName = "TogetherEAB V${android.defaultConfig.versionName}"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("io.github.webrtc-sdk:android:144.7559.01")

    /*
     * FCM (push) — add when you have a Firebase project:
     *   id("com.google.gms.google-services") version "4.4.2" apply false  // root build.gradle.kts
     *   id("com.google.gms.google-services")  // app plugin
     *   implementation(platform("com.google.firebase:firebase-bom:33.x.x"))
     *   implementation("com.google.firebase:firebase-messaging-ktx")
     * Place google-services.json under app/ and send tokens to your Worker.
     */
}
