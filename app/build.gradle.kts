plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// --- Versioning -------------------------------------------------------------
// versionCode must increase monotonically for Android to accept an in-place
// update. We derive it from the git commit count (env override wins in CI so a
// shallow/odd checkout can't drift). versionName is a human-readable "1.0.<code>".
fun gitCommitCount(): Int = try {
    val out = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().readText().trim()
    out.toIntOrNull() ?: 1
} catch (_: Exception) {
    1
}

val appVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: gitCommitCount()
val appVersionName = System.getenv("VERSION_NAME") ?: "1.0.$appVersionCode"

// GitHub repo the in-app update checker queries (releases/latest).
val githubOwner = "RainbowCockroach"
val githubRepo = "album-studio-android"

android {
    namespace = "com.rainbowcockroach.albumstudio.toprint"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rainbowcockroach.albumstudio.toprint"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GITHUB_OWNER", "\"$githubOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing key comes from env (set by CI from GitHub Secrets). When the
    // env vars are absent (local `assembleRelease`), the config stays empty and the
    // release build is simply unsigned — CI is the source of published APKs.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.coil.compose)

    // Upload engine + networking
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.exifinterface)

    // Local persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Settings
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
