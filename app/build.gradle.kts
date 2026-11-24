plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.slate.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.slate.android"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("SLATE_VERSION_CODE").orNull?.toInt() ?: 6
        versionName = "0.5.0-dev"
    }
    val keystorePath = providers.environmentVariable("SLATE_KEYSTORE_PATH").orNull
    val keystorePassword = providers.environmentVariable("SLATE_KEYSTORE_PASSWORD").orNull
    if (keystorePath != null && keystorePassword != null) {
        signingConfigs.create("slateRelease") {
            storeFile = file(keystorePath)
            storeType = "PKCS12"
            storePassword = keystorePassword
            keyAlias = "slate"
            keyPassword = keystorePassword
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("slateRelease")
        }
    }
    buildTypes.getByName("release") {
        isDebuggable = false
        isMinifyEnabled = false
    }
    buildFeatures { compose = true; aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}
