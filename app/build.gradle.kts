plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-parcelize")
    id("dagger.hilt.android.plugin")
}

android {
    compileSdk = libs.versions.android.sdk.compile.get().toInt()
    buildToolsVersion = libs.versions.android.buildTools.get()

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
        manifestPlaceholders["tiqr_config_protocol_compatibility_mode"] =  "true"
        manifestPlaceholders["tiqr_config_enforce_challenge_hosts"] = "tiqr.nl,surfconext.nl"
        manifestPlaceholders["tiqr_config_enroll_path_param"] = "tiqrenroll"
        manifestPlaceholders["tiqr_config_auth_path_param"] = "tiqrauth"
        manifestPlaceholders["tiqr_config_enroll_scheme"] = "tiqrenroll"
        manifestPlaceholders["tiqr_config_auth_scheme"] = "tiqrauth"
        manifestPlaceholders["tiqr_config_token_exchange_enabled"] = "false"

        // only package supported languages
        resourceConfigurations += listOf("en", "da", "de", "es", "fr", "fy", "hr", "ja", "lt", "nl", "no", "ro", "sk", "sl", "sr", "tr" )
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    packagingOptions {
        resources.excludes.addAll(
            arrayOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        )
    }

    buildFeatures {
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kapt {
        correctErrorTypes = true
        useBuildCache = true
        javacOptions {
            option("-Xmaxerrs", 1000)
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        isAbortOnError = false
    }
}

dependencies {

    repositories {
        google()
        mavenCentral()
    }

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
    implementation(libs.androidx.concurrent)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.livedata)
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
    kapt(libs.dagger.hilt.compiler)

    implementation(libs.permission)
    implementation(libs.coil)
    implementation(libs.betterLink)

    api(libs.moshi.moshi)
    kapt(libs.moshi.codegen)

    api(libs.okhttp.okhttp)
    api(libs.okhttp.logging)

    api(libs.retrofit.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.retrofit.converter.scalars)

    api(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testing.core)
    androidTestImplementation(libs.androidx.testing.junit)
    androidTestImplementation(libs.androidx.testing.rules)
    androidTestImplementation(libs.androidx.testing.epsresso)
    androidTestImplementation(libs.androidx.testing.uiautomator)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.dagger.hilt.testing)
    kaptAndroidTest(libs.dagger.hilt.compiler)
}

// Disable analytics
configurations {
    all {
        exclude(group = "com.google.firebase", module = "firebase-core")
        exclude(group = "com.google.firebase", module = "firebase-analytics")
        exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
    }
}
