import java.util.Properties

plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

if (JavaVersion.current() < JavaVersion.VERSION_21) {
    throw GradleException("Please use JDK ${JavaVersion.VERSION_21} or above")
}

val secureProperties = loadCustomProperties(file("../local.properties"))

fun loadCustomProperties(file: File): Properties {
    val properties = Properties()
    if (file.isFile) {
        properties.load(file.inputStream())
    }
    return properties
}

android {
    compileSdk = libs.versions.android.sdk.compile.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.sdk.min.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21

        }
    }

    kotlin {
        jvmToolchain(21)
    }

    namespace = "org.tiqr.data"
    buildFeatures {
        buildConfig = true
    }

    dependencies {
        implementation(libs.kotlin.stdlib)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.androidx.core)
        implementation(libs.androidx.localBroadcastManager)
        implementation(libs.androidx.lifecycle.livedata)
        implementation(libs.androidx.lifecycle.viewmodel)
        implementation(libs.androidx.lifecycle.scope)
        implementation(libs.androidx.appUpdate)
        implementation(libs.google.android.material)
        implementation(libs.google.guava)


        implementation(libs.dagger.hilt.android)
        ksp(libs.dagger.hilt.compiler)

        api(libs.androidx.biometric)
        api(libs.androidx.camera.view)
        api(libs.androidx.camera.core)
        api(libs.androidx.camera.lifecycle)
        api(libs.androidx.camera.camera2)
        api(libs.androidx.concurrent)
        api(libs.google.mlkit.barcode)
        api(libs.kotlinx.coroutines.playServices)
        api(libs.okhttp.okhttp)
        api(libs.okhttp.logging)

        api(libs.retrofit.retrofit)
        implementation(libs.retrofit.converter.moshi)
        implementation(libs.retrofit.converter.scalars)

        api(libs.moshi.moshi)
        ksp(libs.moshi.codegen)

        api(libs.androidx.room.runtime)
        implementation(libs.androidx.room.ktx)
        implementation(libs.androidx.room.sqlite)
        ksp(libs.androidx.room.compiler)

        api(libs.timber)

        testImplementation(libs.junit)
        testImplementation(libs.androidx.room.testing)
        androidTestImplementation(libs.androidx.testing.junit)
        androidTestImplementation(libs.androidx.testing.epsresso)
        androidTestImplementation(libs.kotlinx.coroutines.test)
    }
}

group = "org.tiqr"
