import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties


// -----------------------------------------------------------------------------
// Read secrets from local.properties
// -----------------------------------------------------------------------------
//
// local.properties is ignored by Git, so the Gemini API key will not be
// accidentally committed to GitHub.
//
// Expected local.properties entry:
//
// GEMINI_API_KEY=your_real_api_key_here
//

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")

    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { inputStream ->
            load(inputStream)
        }
    }
}

val geminiApiKey: String =
    localProperties.getProperty("GEMINI_API_KEY", "")



plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}



android {
    namespace = "com.example.localecoscanner"

    compileSdk = 36


    defaultConfig {
        applicationId = "com.example.localecoscanner"

        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        // ---------------------------------------------------------------------
        // Gemini API Key
        // ---------------------------------------------------------------------
        //
        // Generates:
        //
        // BuildConfig.GEMINI_API_KEY
        //
        // which can be accessed from MainActivity.kt.
        //

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"$geminiApiKey\""
        )
    }


    buildTypes {

        debug {
            isMinifyEnabled = false
        }


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

        // Required because we created BuildConfig.GEMINI_API_KEY above.
        buildConfig = true
    }
}



// -----------------------------------------------------------------------------
// Kotlin compiler configuration
// -----------------------------------------------------------------------------

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}



dependencies {

    // -------------------------------------------------------------------------
    // AndroidX Core
    // -------------------------------------------------------------------------

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Needed for androidx.lifecycle.compose.LocalLifecycleOwner
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.activity.compose)


    // -------------------------------------------------------------------------
    // Jetpack Compose
    // -------------------------------------------------------------------------

    implementation(
        platform(libs.androidx.compose.bom)
    )

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)


    // -------------------------------------------------------------------------
    // CameraX
    // -------------------------------------------------------------------------

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)


    // -------------------------------------------------------------------------
    // Google Gemini Generative AI SDK
    // -------------------------------------------------------------------------

    implementation(libs.google.generativeai)


    // -------------------------------------------------------------------------
    // Unit Tests
    // -------------------------------------------------------------------------

    testImplementation(libs.junit)


    // -------------------------------------------------------------------------
    // Android Instrumentation Tests
    // -------------------------------------------------------------------------

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )


    // -------------------------------------------------------------------------
    // Compose Debug Tools
    // -------------------------------------------------------------------------

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}