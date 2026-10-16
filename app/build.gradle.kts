import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Firma de release: se lee de keystore.properties (no va a git). Por defecto busca
 * D:/Projects/garaje-keys/keystore.properties; se puede cambiar con la variable GARAJE_KEYSTORE.
 */
val keystoreProps = Properties().apply {
    val path = System.getenv("GARAJE_KEYSTORE") ?: "D:/Projects/garaje-keys/keystore.properties"
    val file = rootProject.file("keystore.properties").takeIf { it.exists() } ?: file(path)
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.arianna.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arianna.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"

        // true → repositorios falsos (datos locales). false → backend real (ver docs/API.md).
        // Datos falsos en el teléfono (sin backend): ./gradlew installDebug -Parianna.fakes=true
        buildConfigField("boolean", "USE_FAKES", (findProperty("arianna.fakes") ?: "false").toString())
        buildConfigField("String", "API_URL", "\"https://garage-back-me4r.onrender.com/api\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Fotos: carga y caché de imágenes, y orientación EXIF de las fotos de cámara.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.exifinterface)

    // Notificaciones push (Firebase Cloud Messaging).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}

// Firebase: el plugin solo se aplica si existe app/google-services.json (se descarga de la consola
// de Firebase y no va a git). Sin ese archivo la app compila igual, pero sin notificaciones push.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}
