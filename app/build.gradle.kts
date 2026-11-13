plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

/**
 * Номер сборки = число коммитов в истории. Одинаков для сборки с компьютера и на
 * GitHub (там checkout с fetch-depth: 0), растёт с каждым коммитом — по нему
 * приложение понимает, что вышло обновление. Нет git — 1.
 */
val gitCommitCount: Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
} catch (e: Exception) {
    1
}

/**
 * Ключ подписи. Обновление ставится поверх, только если APK подписан тем же ключом,
 * что и установленное приложение. Путь к ключу — переменная окружения
 * UCHET_KEYSTORE_FILE (на GitHub — из секрета) или свойство uchet.keystore в
 * ~/.gradle/gradle.properties. Не задан — обычный debug-ключ этого компьютера.
 * Пароли/алиас по умолчанию — как у стандартного debug.keystore.
 */
val uchetKeystore: String? = System.getenv("UCHET_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    ?: (findProperty("uchet.keystore") as String?)
fun signingValue(env: String, prop: String, default: String): String =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: (findProperty(prop) as String?) ?: default

android {
    namespace = "com.uchet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uchet"
        minSdk = 26
        targetSdk = 34
        versionCode = gitCommitCount
        versionName = "1.0.$gitCommitCount"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Откуда приложение берёт обновления (релизы GitHub).
        buildConfigField("String", "UPDATE_REPO", "\"cheden123/uchet\"")
    }

    signingConfigs {
        if (uchetKeystore != null) {
            create("shared") {
                storeFile = file(uchetKeystore)
                storePassword = signingValue("UCHET_KEYSTORE_PASSWORD", "uchet.storePassword", "android")
                keyAlias = signingValue("UCHET_KEY_ALIAS", "uchet.keyAlias", "androiddebugkey")
                keyPassword = signingValue("UCHET_KEY_PASSWORD", "uchet.keyPassword", "android")
            }
        }
    }

    buildTypes {
        debug {
            if (uchetKeystore != null) signingConfig = signingConfigs.getByName("shared")
        }
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
        buildConfig = true
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
