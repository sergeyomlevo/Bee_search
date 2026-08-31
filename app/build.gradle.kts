plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.beesearch.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.beesearch.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "BEE_MAP_STYLE_URL",
            "\"https://maps.invalid.bee-search/field/v1/" +
                "central-russia-poc-20260830/style-v3/style.json\"",
        )
    }

    buildTypes {
        debug {
            val developmentStyleUrl = providers.gradleProperty("beeMapStyleUrl")
                .orElse(
                    "http://10.0.2.2:8080/field/v1/" +
                        "central-russia-poc-20260830/style-v3/style.json",
                )
                .get()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            buildConfigField("String", "BEE_MAP_STYLE_URL", "\"$developmentStyleUrl\"")
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.maplibre.android)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    // Aligns the debug app runtime with Room 2.8 MigrationTestHelper's serialization version.
    // Without it, DataStore contributes incompatible serialization 1.7.3 and migration tests fail.
    debugRuntimeOnly(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
