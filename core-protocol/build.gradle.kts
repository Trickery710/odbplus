plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    kotlin("kapt")
}


android {
    namespace = "com.odbplus.core.protocol"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    kotlin {
        // Set the JVM toolchain to version 17
        jvmToolchain(17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(project(":core-transport"))
    implementation(libs.javax.inject)

    // Add these Hilt dependencies
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

