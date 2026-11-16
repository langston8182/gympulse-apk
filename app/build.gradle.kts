import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Optional fixed identity for debug builds (same data on every device); set it in local.properties.
val devUserId: String = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}.getProperty("gympulse.devUserId", "").trim()

// The preprod variant uses the x-user-id header by default; `gympulse.useCognito=true` in local.properties
// tests the real Google/Cognito login against the preprod pool instead.
val localProperties: Properties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val debugUsesCognito: Boolean = localProperties.getProperty("gympulse.useCognito", "false").trim().toBoolean()

// Release signing: set these four properties in local.properties (never committed). Without them the
// release build is produced unsigned, which is enough to check that it compiles.
val releaseStoreFile: String? = localProperties.getProperty("gympulse.release.storeFile")?.trim()?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.gympulse.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gympulse.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProperties.getProperty("gympulse.release.storePassword")
                keyAlias = localProperties.getProperty("gympulse.release.keyAlias")
                keyPassword = localProperties.getProperty("gympulse.release.keyPassword")
            }
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("preprod") {
            dimension = "env"
            // Installable à côté de la prod : identifiant, nom, icône et schéma de redirection OAuth distincts.
            applicationIdSuffix = ".preprod"
            versionNameSuffix = "-preprod"
            manifestPlaceholders["appLabel"] = "GymPulse préprod"
            manifestPlaceholders["appAuthRedirectScheme"] = "gympulse-preprod"
            buildConfigField("String", "COGNITO_REDIRECT_URI", "\"gympulse-preprod://auth/callback\"")
            buildConfigField("String", "API_BASE_URL", "\"https://gympulse-api-preprod.cyrilmarchive.com/v1/\"")
            buildConfigField("String", "API_ENV", "\"preprod\"")
            // L'en-tête x-user-id n'est accepté que par l'API de preprod.
            buildConfigField("boolean", "USE_DEV_USER_HEADER", (!debugUsesCognito).toString())
            buildConfigField("String", "DEV_USER_ID", "\"$devUserId\"")
            // Pool Cognito de preprod (identifiants publics, pas des secrets).
            buildConfigField("String", "COGNITO_DOMAIN", "\"eu-west-3auhsvinhh.auth.eu-west-3.amazoncognito.com\"")
            buildConfigField("String", "COGNITO_CLIENT_ID", "\"4kde7q4ski01ie894ataj9coii\"")
        }
        create("prod") {
            dimension = "env"
            manifestPlaceholders["appLabel"] = "GymPulse"
            // Redirection OAuth gympulse://auth/callback, gérée par AppAuth.
            manifestPlaceholders["appAuthRedirectScheme"] = "gympulse"
            buildConfigField("String", "COGNITO_REDIRECT_URI", "\"gympulse://auth/callback\"")
            buildConfigField("String", "API_BASE_URL", "\"https://gympulse-api.cyrilmarchive.com/v1/\"")
            buildConfigField("String", "API_ENV", "\"prod\"")
            // La prod exige un vrai JWT : l'en-tête x-user-id est refusé par l'API.
            buildConfigField("boolean", "USE_DEV_USER_HEADER", "false")
            buildConfigField("String", "DEV_USER_ID", "\"\"")
            // Pool Cognito de prod (identifiants publics, pas des secrets).
            buildConfigField("String", "COGNITO_DOMAIN", "\"eu-west-3ownbhrzcn.auth.eu-west-3.amazoncognito.com\"")
            buildConfigField("String", "COGNITO_CLIENT_ID", "\"7stqfkerbqboff8q2khp4cskr1\"")
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.appauth)
    implementation(libs.osmdroid)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
