plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is driven by environment variables so no secret ever lives in the repo.
// CI decodes the keystore and exports these; without them the release build stays unsigned
// and the release workflow publishes it with an explicit -UNSIGNED suffix.
val releaseKeystoreFile = System.getenv("VOZ_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }

val releaseSigningReady: Boolean = releaseKeystoreFile?.exists() == true &&
    !System.getenv("VOZ_KEYSTORE_PASSWORD").isNullOrBlank() &&
    !System.getenv("VOZ_KEY_ALIAS").isNullOrBlank() &&
    !System.getenv("VOZ_KEY_PASSWORD").isNullOrBlank()

android {
    namespace = "dev.auxdesign.voz"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.auxdesign.voz"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-rc1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = System.getenv("VOZ_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VOZ_KEY_ALIAS")
                keyPassword = System.getenv("VOZ_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Signing is applied only when the CI secrets are present; otherwise the
            // artifact is produced unsigned rather than falling back to a debug key.
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    bundle {
        // Spoken replies switch language at runtime, so every language must ship in every install.
        language {
            enableSplit = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // The cloud planner (and therefore HTTP) exists only in debug/pilot builds.
    debugImplementation(libs.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
