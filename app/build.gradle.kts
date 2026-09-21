plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Numéro de build injecté par la CI (`-PbuildNumber=${{ github.run_number }}`).
// En local il vaut 0 : un APK local n'a jamais à se croire plus récent qu'une release.
val buildNumber: Int = (project.findProperty("buildNumber") as String?)?.toIntOrNull()
    ?: System.getenv("BUILD_NUMBER")?.toIntOrNull()
    ?: 0

// Keystore fixe déposée par la CI (secret KEYSTORE_BASE64). Sans elle — build local,
// fork sans secrets — on retombe sur la clé debug : le build passe, mais l'APK produit
// ne pourra pas se réinstaller par-dessus une version signée avec la vraie clé.
val releaseKeystore: File? = System.getenv("NICO_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let(::File)
    ?.takeIf { it.exists() }

android {
    namespace = "com.nico.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nico.assistant"
        minSdk = 26
        targetSdk = 35
        // versionCode suit le numéro de build : Android refuse d'installer un APK
        // dont le versionCode est inférieur à celui déjà en place.
        versionCode = buildNumber.coerceAtLeast(1)
        versionName = "1.0.$buildNumber"

        // Lu par update/BuildInfo.kt pour comparer avec la dernière release GitHub.
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("NICO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NICO_KEY_ALIAS")
                keyPassword = System.getenv("NICO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric a besoin des ressources et du manifest fusionnés pour
            // faire tourner Room sur la JVM, sans appareil ni émulateur.
            isIncludeAndroidResources = true
        }
    }
}

// Schéma Room exporté à chaque build : indispensable pour écrire les migrations plus tard.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Socle de données (lot 1)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Accès système sans root (lot 6)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
}
