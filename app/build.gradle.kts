plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Снимки экранов (отдельный workflow shots.yml): gradle :app:testDebugUnitTest -Pshots --tests 'ru.shturman.shots.*'
// Без -Pshots ничего из этого в сборку не попадает.
val shots = project.hasProperty("shots")

android {
    namespace = "ru.shturman"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.shturmani"
        minSdk = 26
        targetSdk = 34
        // Номер сборки приходит из GitHub Actions (-PbuildNumber=...): по нему приложение узнаёт, что вышла новая версия
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
        buildConfigField("int", "BUILD_NUMBER", build.toString())
    }

    // Постоянный ключ подписи: нужен, чтобы вход через Google узнавал приложение при каждой сборке
    signingConfigs {
        getByName("debug") {
            storeFile = file("shturman-debug.keystore")
            storePassword = "android"
            keyAlias = "shturman"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    if (shots) {
        testOptions.unitTests.isIncludeAndroidResources = true
        sourceSets.getByName("test").java.srcDir("src/shots/kotlin")
    }
}

tasks.withType<Test>().configureEach {
    if (shots) systemProperty("roborazzi.test.record", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Карта на основе OpenStreetMap (MapLibre)
    implementation("org.maplibre.gl:android-sdk:11.5.2")

    // Valhalla на телефоне: маршруты считаются прямо в приложении
    implementation("io.github.rallista:valhalla-mobile:0.6.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Вход через Google (Firebase Authentication)
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Аватарка из Google-аккаунта
    implementation("io.coil-kt:coil-compose:2.7.0")

    if (shots) {
        testImplementation(composeBom)
        testImplementation("androidx.compose.ui:ui-test-junit4")
        testImplementation("androidx.test.ext:junit:1.2.1")
        testImplementation("org.robolectric:robolectric:4.14.1")
        testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
        testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
    }
}
