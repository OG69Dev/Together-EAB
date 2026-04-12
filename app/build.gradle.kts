import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.og69.eab"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        val versionName = this.versionName
        this.outputs.all {
            val impl = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (impl != null) {
                impl.outputFileName = "TogetherEAB V${versionName}.apk"
            }
        }
    }
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

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    /*
     * FCM (push) — add when you have a Firebase project:
     *   id("com.google.gms.google-services") version "4.4.2" apply false  // root build.gradle.kts
     *   id("com.google.gms.google-services")  // app plugin
     *   implementation(platform("com.google.firebase:firebase-bom:33.x.x"))
     *   implementation("com.google.firebase:firebase-messaging-ktx")
     * Place google-services.json under app/ and send tokens to your Worker.
     */
}
