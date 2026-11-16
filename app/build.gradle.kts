import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

// ── 签名配置 ────────────────────────────────────────────────────────────
// 安全约定（审计 P0-1）：签名密钥与密码**绝不放在工程目录内**。
// 默认从用户目录读取，可通过以下任一方式覆盖（CI/他人机器）：
//   1) 环境变量 ANDROID_KEYSTORE_PROPS 指向 keystore.properties 的绝对路径
//   2) gradle 属性 -Pmedilog.keystoreProps=<绝对路径>
// 缺失时 release 构建**直接失败**，避免误产出未签名/错误签名的包。
val keystorePropsPath: String? =
    System.getenv("ANDROID_KEYSTORE_PROPS")
        ?: (project.findProperty("medilog.keystoreProps") as String?)
        ?: listOf(
            "${System.getProperty("user.home")}/.android-keys/medilog/keystore.properties",
            "${System.getProperty("user.home")}/.android/medilog-keystore.properties"
        ).firstOrNull { File(it).exists() }

val keystorePropertiesFile = keystorePropsPath?.let { File(it) }
val keystoreProperties = Properties()
if (keystorePropertiesFile?.exists() == true) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

/** 解析 keystore 里的路径：绝对路径直接用，相对路径相对 keystore.properties 所在目录 */
fun resolveStoreFile(value: String): File {
    val f = File(value)
    return if (f.isAbsolute) f else File(keystorePropertiesFile!!.parentFile, value)
}

android {
    namespace = "com.medilog.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.medilog.app"
        minSdk = 26
        targetSdk = 37   // 审计 P1-1：34→37（Play 政策要求；行为变更已回归：edge-to-edge/全屏intent/精确闹钟/通知）
        // 建议 1：接入 Compose UI 自动化测试所需的 instrumented runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val hasAll = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .all { keystoreProperties.containsKey(it) }
            if (keystorePropertiesFile?.exists() == true && hasAll) {
                storeFile = resolveStoreFile(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // 并行调试：以 .debug 后缀安装，不覆盖用户已装生产版（com.medilog.app，原签名未知）
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // 审计 P0-2：开启代码压缩与资源缩减（dex 由 33.9MB 大幅下降）
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // 审计 P2-4：纯 JVM 单测中 android.util.Log 等框架调用返回默认值而不抛异常
        // （TimeUtils 等 util 类在 catch 中记日志，JVM 测试无 Android 运行时）
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/**/LICENSE*.txt",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material.ripple)
    implementation(libs.compose.material3)
    implementation(libs.miuix)
    implementation(libs.kyant.shapes)
    implementation(libs.backdrop)

    // 审计 P2-4：单元测试（纯 JVM，只测 TimeUtils / Medication / CSV 等纯逻辑）
    testImplementation(libs.junit)
    // 建议 2：Robolectric —— 让 SQLite / Context 相关的**数据层**也能在 JVM 上跑单测
    // （此前 deleteMedication 的事务与残行只能在真机/模拟器上验证，且无 sqlite3 / 无 root 时根本查不了库）
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // 建议 1：Compose UI 自动化测试（androidTest）—— 用 testTag 定位，
    // 不再依赖文案与坐标，改文案 / 加语言 / 换分辨率都不会弄坏测试。
    // 没有 ui-test-manifest（JetBrains CMP 未发布该构件），因此测试写在
    // 已声明的 MainActivity 上（createAndroidComposeRule<MainActivity>()）。
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Espresso：ui-test-junit4 传递进来的是 3.5.0，其 InputManagerEventInjectionStrategy
    // 反射 android.hardware.input.InputManager.getInstance()，该方法在 API 37 已移除
    // → 测试一启动就 NoSuchMethodException。显式抬到 3.7.0（已适配新版 InputManager API）。
    androidTestImplementation(libs.androidx.espresso.core)
}

// ── 构建守卫（审计 P0-1）────────────────────────────────────────────────
// release 必须拿到完整签名配置，否则明确失败，绝不静默产出未签名/错签名的包。
afterEvaluate {
    // 审计 P2-1：签名缺失检查改为「配置期求值 + task action 只捕获布尔值」，
    // 避免 android 扩展（间接持有 Project）进入 doFirst 导致配置缓存无法序列化
    val cfg = android.signingConfigs.findByName("release")
    val storeFileMissing = cfg?.storeFile == null
    if (storeFileMissing) {
        val msg =
            "release 构建缺少签名配置。请设置环境变量 ANDROID_KEYSTORE_PROPS " +
                "（指向 keystore.properties 绝对路径），或放到默认位置 " +
                "${System.getProperty("user.home")}/.android-keys/medilog/keystore.properties"
        tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
            .configureEach {
                doFirst { throw GradleException(msg) }
            }
    }
}
