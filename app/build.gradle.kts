plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.uchet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uchet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
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
        // flatMapLatest во ViewModel'ях — ExperimentalCoroutinesApi; разрешаем глобально,
        // чтобы не размечать каждый файл и не получать предупреждения сборки.
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // Robolectric: тестам нужны ресурсы/ассеты приложения.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Схемы Room — ассеты для MigrationTestHelper. Только debug-сборка
    // (в неё же идут unit-тесты), в release-APK схемы не попадают.
    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Apache POI ships duplicate META-INF services.
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

// Unit-тесты гоняем только в debug: там лежат схемы Room (ассеты) для MigrationTest,
// а release-вариант запускал бы тот же код (R8 к unit-тестам не применяется) и падал
// бы без схем. Так `./gradlew test` = testDebugUnitTest.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach {
    enabled = false
}

// Room: export schemas (must be committed to VCS).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Apache POI — XLSX export.
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Юнит-тесты чистой логики (buildSpoPipeRows и т.п.) — запускаются `./gradlew test`.
    testImplementation("junit:junit:4.13.2")
    // Тест миграций Room на JVM (Robolectric) — без эмулятора/устройства.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
}
