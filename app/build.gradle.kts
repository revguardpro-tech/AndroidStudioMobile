plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
android {
    namespace   = "com.androidstudiomobile"
    compileSdk  = 35
    defaultConfig {
        applicationId  = "com.androidstudiomobile"
        minSdk         = 26
        targetSdk      = 35
        versionCode    = 30
        versionName    = "3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }
    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += listOf("-Xcontext-receivers") }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/LICENSE*","META-INF/NOTICE*","META-INF/*.kotlin_module") }
}
dependencies {
    // ── Core Compose ──────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    implementation(libs.compose.navigation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // ── Storage ───────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Async ─────────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Networking (Bloco 7 Play Console, Bloco 8 Remote Build) ───────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ── Git ───────────────────────────────────────────────────────────────────
    implementation(libs.jgit)

    // ── Image loading ─────────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Desugaring ────────────────────────────────────────────────────────────
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // ── Debug / Testing ───────────────────────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
