plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.slate.android"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig {
        applicationId = "com.slate.android"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("SLATE_VERSION_CODE").orNull?.toInt() ?: 6
        versionName = rootProject.file("VERSION").readText().trim()
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
        // Keep production builds unminified until framework callback compatibility
        // has been verified on devices (LayoutInflater.Factory2 startup crash).
        val optimizeRelease = providers.gradleProperty("slateOptimizeRelease").orNull == "true"
        isMinifyEnabled = optimizeRelease
        isShrinkResources = optimizeRelease
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
    }
    buildTypes.create("benchmark") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
    }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES","META-INF/LICENSE","META-INF/LICENSE.txt","META-INF/NOTICE","META-INF/NOTICE.txt") }
    // The upstream 7zz PIE is executed from the installed, read-only native library directory.
    packaging { jniLibs.useLegacyPackaging = true }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/sevenzip/jniLibs"))
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/sevenzip/assets"))
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
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.android.tools.build:apksig:8.7.3")
    implementation("io.github.reandroid:ARSCLib:1.4.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.luben:zstd-jni:1.5.6-4@aar")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    testImplementation("junit:junit:4.13.2")
}

/* Compile the standalone Android-only provider payload at build time, not from an opaque binary. */
val slateProviderClasses = layout.buildDirectory.dir("intermediates/slateProvider/classes")
val slateProviderAssets = layout.buildDirectory.dir("generated/slateProviderAssets")
val slateSdk = androidComponents.sdkComponents.sdkDirectory
val compileSlateProvider by tasks.registering(JavaCompile::class) {
    source(fileTree("src/provider/java") { include("**/*.java") })
    classpath = files(slateSdk.map { it.file("platforms/android-35/android.jar") })
    destinationDirectory.set(slateProviderClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}
val dexSlateProvider by tasks.registering(JavaExec::class) {
    dependsOn(compileSlateProvider)
    inputs.dir(slateProviderClasses)
    outputs.dir(slateProviderAssets)
    classpath = files(slateSdk.map { it.file("build-tools/35.0.0/lib/d8.jar") })
    mainClass.set("com.android.tools.r8.D8")
    doFirst {
        val out = slateProviderAssets.get().dir("slate-provider").asFile
        out.mkdirs()
        val classes = slateProviderClasses.get().asFile.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        check(classes.isNotEmpty())
        setArgs(listOf("--min-api", "26", "--lib", slateSdk.get().file("platforms/android-35/android.jar").asFile.absolutePath, "--output", out.absolutePath) + classes.map { it.absolutePath })
    }
}
android.sourceSets.getByName("main").assets.srcDir(slateProviderAssets)
tasks.named("preBuild") { dependsOn(dexSlateProvider) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(dexSlateProvider) }
