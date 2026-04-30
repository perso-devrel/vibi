import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    // dev 빌드 가속 — Apple Silicon 시뮬레이터만 (shared/build.gradle.kts 와 동일 정책).
    iosSimulatorArm64().binaries.framework {
        baseName = "Cmp"
        isStatic = true
        export(project(":shared"))
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            // navigation-compose multiplatform 은 아직 maven central 에 안정 버전 없음 —
            // 4 화면 정도라 sealed-class 기반 자체 NavHost 사용. M3 잔여 시 결정.

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
        }

        // iosMain: AVFoundation/AVKit 은 Kotlin/Native cinterop 으로 자동 노출 — 별도 의존성 불필요
    }
}

android {
    namespace = "com.dubcast.cmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dubcast.cmp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "BFF_BASE_URL",
            "\"${localProperties.getProperty("BFF_BASE_URL", "https://api.dubcast.example.com/")}\""
        )
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
