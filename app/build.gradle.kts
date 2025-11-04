import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Загрузка паролей из keystore.properties (если файл существует)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { input ->
        keystoreProperties.load(input)
    }
}

android {
    namespace = "com.example.chonline"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.taonline.app"  // Уникальный ID для RuStore
        minSdk = 24
        targetSdk = 35
        versionCode = 6  // Увеличено для новой публикации
        versionName = "1.4"  // Обновлена версия

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Используем keystore файл если он существует
            val keystoreFile = rootProject.file("taonline-release-key.jks")
            if (keystoreFile.exists() && keystorePropertiesFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String? ?: ""
                keyAlias = "taonline-key"
                keyPassword = keystoreProperties["KEY_PASSWORD"] as String? ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Используем подпись если keystore настроен
            if (keystorePropertiesFile.exists() && rootProject.file("taonline-release-key.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("io.coil-kt:coil-compose:2.2.2") // Для загрузки изображений
    implementation("androidx.activity:activity-compose:1.7.2") // Для Activity Result API
    implementation ("androidx.work:work-runtime-ktx:2.9.0")

}