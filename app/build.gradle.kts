plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// google-services.json принадлежит владельцу и не хранится в Git. Без файла приложение собирается,
// а Firebase-обёртки работают как no-op.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val admobAppId: String? = providers.gradleProperty("ADMOB_APP_ID").orNull
val admobRewardedUnitId: String? = providers.gradleProperty("ADMOB_REWARDED_UNIT_ID").orNull
// Официальные тестовые идентификаторы Google. Реальных production-ID в репозитории нет.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedUnitId = "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "com.akynex.logicmix"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.akynex.logicmix"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$testRewardedUnitId\"")
            buildConfigField("boolean", "ADS_TEST_MODE", "true")
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = admobAppId ?: testAppId
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"${admobRewardedUnitId ?: testRewardedUnitId}\"")
            buildConfigField("boolean", "ADS_TEST_MODE", (admobAppId == null || admobRewardedUnitId == null).toString())
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
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("org.json:json:20240303")
}

// Релиз с тестовыми рекламными идентификаторами публиковать нельзя.
tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "packageRelease")) {
        doFirst {
            check(admobAppId != null && admobRewardedUnitId != null) {
                "Release requires -PADMOB_APP_ID and -PADMOB_REWARDED_UNIT_ID (real AdMob IDs from the owner)."
            }
        }
    }
}
