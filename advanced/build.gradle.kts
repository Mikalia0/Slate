plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.slate.advanced"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.slate.android.advanced"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("SLATE_VERSION_CODE").orNull?.toInt() ?: 6
        versionName = "0.5.0-dev"
    }
    val keyPath = providers.environmentVariable("SLATE_KEYSTORE_PATH").orNull
    val keyPassword = providers.environmentVariable("SLATE_KEYSTORE_PASSWORD").orNull
    if (keyPath != null && keyPassword != null) {
        signingConfigs.create("slateRelease") {
            storeFile = file(keyPath)
            storeType = "PKCS12"
            storePassword = keyPassword
            keyAlias = "slate"
            this.keyPassword = keyPassword
        }
        buildTypes.getByName("release") { signingConfig = signingConfigs.getByName("slateRelease") }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES","META-INF/LICENSE","META-INF/LICENSE.txt","META-INF/NOTICE","META-INF/NOTICE.txt") }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.github.reandroid:ARSCLib:1.4.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.luben:zstd-jni:1.5.6-4@aar")
    implementation("com.github.junrar:junrar:7.5.5")
}
