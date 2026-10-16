
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.fredrick.papertrade"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fredrick.papertrade"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        val backendUrl = providers.gradleProperty("BACKEND_URL")
            .orElse("https://paper-trade-mmhw.onrender.com")
            .get()

        buildConfigField(
            "String",
            "BACKEND_URL",
            "\"${backendUrl.trimEnd('/')}/\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**",
                    "kotlin.**",
                    "kotlinx.**"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
