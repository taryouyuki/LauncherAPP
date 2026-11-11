import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "jp.homelauncher.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.homelauncher.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val encoded = providers.gradleProperty("RELEASE_STORE_FILE_B64").orNull
            val storeFilePath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
            if (!encoded.isNullOrBlank()) {
                val decoded = Base64.getDecoder().decode(encoded)
                val temp = layout.buildDirectory.file("release-signing/release.jks").get().asFile
                temp.parentFile.mkdirs()
                temp.writeBytes(decoded)
                storeFile = temp
            } else if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
            }
            storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
            keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
            keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val signingReady = providers.gradleProperty("RELEASE_STORE_PASSWORD").isPresent &&
                providers.gradleProperty("RELEASE_KEY_PASSWORD").isPresent &&
                providers.gradleProperty("RELEASE_KEY_ALIAS").isPresent &&
                (providers.gradleProperty("RELEASE_STORE_FILE_B64").isPresent || providers.gradleProperty("RELEASE_STORE_FILE").isPresent)
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.json:json:20250517")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
