import java.util.Properties

val cameraXVersion = "1.6.1"
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val frankServerUrl = providers.gradleProperty("frankServerUrl")
    .orElse(providers.provider { localProperties.getProperty("frank.server.url") })
    .orElse("http://192.168.0.83:3000/")
    .get()
val frankAccessToken = providers.gradleProperty("frankAccessToken")
    .orElse(providers.provider { localProperties.getProperty("frank.access.token") })
    .orElse("")
    .get()

fun quotedBuildValue(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.idumet.frank"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.idumet.frank"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FRANK_SERVER_URL", quotedBuildValue(frankServerUrl))
        buildConfigField("String", "FRANK_ACCESS_TOKEN", quotedBuildValue(frankAccessToken))
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.camera:camera-core:${cameraXVersion}")
    implementation("androidx.camera:camera-camera2:${cameraXVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraXVersion}")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.2.0")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
}
