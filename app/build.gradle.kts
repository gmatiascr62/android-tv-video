import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun gitCommitSha(): String {
    val envSha = System.getenv("GITHUB_SHA")
    if (!envSha.isNullOrBlank()) return envSha
    return try {
        val out = ByteArrayOutputStream()
        exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = out
        }
        out.toString().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

android {
    namespace = "com.gmatiascr62.androidtvvideo"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gmatiascr62.androidtvvideo"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BUILD_SHA", "\"${gitCommitSha()}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
