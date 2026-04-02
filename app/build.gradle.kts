plugins {
    id("com.android.application")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

if (JavaVersion.current() < JavaVersion.VERSION_21) {
    throw GradleException("Please use JDK ${JavaVersion.VERSION_21} or above")
}

android {
    compileSdk = libs.versions.android.sdk.compile.get().toInt()

    defaultConfig {
        applicationId = "org.tiqr.sample"
        versionCode = 1
        versionName = "1.0.0"

        minSdk = libs.versions.android.sdk.min.get().toInt()
        targetSdk = libs.versions.android.sdk.target.get().toInt()

        testInstrumentationRunner = "org.tiqr.authenticator.runner.HiltAndroidTestRunner"

        // Tiqr config
        manifestPlaceholders["tiqr_config_token_exchange_base_url"] = "https://tx.tiqr.org/"
        manifestPlaceholders["tiqr_config_protocol_version"] = "2"
        manifestPlaceholders["tiqr_config_protocol_compatibility_mode"] = "true"
        manifestPlaceholders["tiqr_config_enforce_challenge_hosts"] =
            "tiqr.nl,surfconext.nl,eduid.nl,tiqr.org"
        manifestPlaceholders["tiqr_config_enroll_path_param"] = "tiqrenroll"
        manifestPlaceholders["tiqr_config_auth_path_param"] = "tiqrauth"
        manifestPlaceholders["tiqr_config_enroll_scheme"] = "tiqrenroll"
        manifestPlaceholders["tiqr_config_auth_scheme"] = "tiqrauth"
        manifestPlaceholders["tiqr_config_token_exchange_enabled"] = "false"
        manifestPlaceholders["tiqr_config_in_app_update_check_enabled"] = "true"

        // only package supported languages
        resourceConfigurations += listOf(
            "en",
            "da",
            "de",
            "es",
            "fr",
            "fy",
            "hr",
            "ja",
            "lt",
            "nl",
            "no",
            "ro",
            "sk",
            "sl",
            "sr",
            "tr"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }
    }

    packaging {
        resources.excludes.addAll(
            arrayOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        )
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    sourceSets {
        // Adds exported schema location as test app assets.
        getByName("androidTest").assets.srcDir("$rootDir/data/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = false
    }
    namespace = "org.tiqr.sample"
}

kotlin {
    jvmToolchain(21)
}

dependencies {

    implementation(project(":core"))
    implementation(project(":data"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.playServices)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.localBroadcastManager)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.splashscreen)
    implementation(libs.google.android.material)
    implementation(libs.google.mlkit.barcode)
    implementation(libs.google.firebase.messaging)

    implementation(libs.dagger.hilt.android)
    implementation(libs.dagger.hilt.fragment)
    implementation(libs.androidx.room.testing)
    ksp(libs.dagger.hilt.compiler)

    implementation(libs.permission)
    implementation(libs.coil)
    implementation(libs.betterLink)

    api(libs.moshi.moshi)
    ksp(libs.moshi.codegen)

    api(libs.okhttp.okhttp)
    api(libs.okhttp.logging)

    api(libs.retrofit.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.retrofit.converter.scalars)

    api(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testing.core)
    androidTestImplementation(libs.androidx.testing.junit)
    androidTestImplementation(libs.androidx.testing.rules)
    androidTestImplementation(libs.androidx.testing.epsresso)
    androidTestImplementation(libs.androidx.testing.uiautomator)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.dagger.hilt.testing)
    kspAndroidTest(libs.dagger.hilt.compiler)
}

// Disable analytics
configurations {
    all {
        exclude(group = "com.google.firebase", module = "firebase-core")
        exclude(group = "com.google.firebase", module = "firebase-analytics")
        exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
    }
}