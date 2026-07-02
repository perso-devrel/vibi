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

    // expect/actual classes 가 Kotlin 2.x 에서 아직 Beta — flag 로 명시 opt-in 해서
    // PurchaseLauncher 등 sealed/interface expect class 의 Beta 경고 suppress.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // 시뮬레이터 + Apple Silicon 실기기. binaryOption("bundleId") 미지정 시
    // K/N 이 baseName ("Cmp") 를 bundle ID 로 fallback → "Cannot infer bundle ID"
    // 경고. iOS bundle ID 규약 (reverse-DNS) 에 맞춰 명시.
    iosSimulatorArm64().binaries.framework {
        baseName = "Cmp"
        isStatic = true
        binaryOption("bundleId", "com.vibi.cmp")
        export(project(":shared"))
    }
    iosArm64().binaries.framework {
        baseName = "Cmp"
        isStatic = true
        binaryOption("bundleId", "com.vibi.cmp")
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
            // VibiApplication 의 MobileAds.initialize 용 (shared 는 implementation 이라 전이 노출 안 됨).
            implementation(libs.play.services.ads)
        }

        // iosMain: AVFoundation/AVKit 은 Kotlin/Native cinterop 으로 자동 노출 — 별도 의존성 불필요
    }
}

android {
    namespace = "com.vibi.cmp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vibi.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "BFF_BASE_URL",
            "\"${localProperties.getProperty("BFF_BASE_URL", "https://api.vibi.example.com/")}\""
        )
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    // 업로드 키 서명. 비밀 값은 git 미추적 local.properties 에서 읽는다 (BFF_BASE_URL 과 동일 패턴).
    // 값이 없으면 (예: CI 없이 debug 만 빌드) signingConfig 를 비워 둬 debug 빌드가 안 깨지게 함.
    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 최소화는 첫 출시 안정성 우선으로 꺼 둠. 안정화 후 keep 규칙 정비하며 켤 것.
            isMinifyEnabled = false
            // local.properties 에 서명 정보가 있을 때만 release signingConfig 연결.
            if (localProperties.getProperty("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// Compose Compiler stability config — List/Map/StateFlow 등을 stable 로 선언해 TimelineUiState
// 처럼 List 다수 필드를 가진 data class 도 stable 로 인식, 같은 값 emission 시 composable skip.
// 효과: 매 state emission 마다 전체 트리 recompose 가 → 변경 필드 의존 subtree 만 recompose.
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}
