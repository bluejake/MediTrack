# MediTrack 用药提醒（com.medilog.app）

面向不熟悉智能设备的普通用户/老年用户的**零配置用药提醒 App**。安装后无需任何设置即可按计划时间推送服药提醒、记录服药情况、提供历史统计，并支持误删撤销。

> 本仓库只含**可编译源码**：`app/` 全量源码与资源、Gradle 配置、单元测试与插桩测试。
> 不含 APK 产物、硬件相关基线、构建/快照脚本或交接文档。详见「与内部版本的差异」。

- 版本：`versionCode = 1` · `versionName = "1.0"`
- 包名：`com.medilog.app`（debug 变体为 `com.medilog.app.debug`，可与正式版并行安装）
- 技术栈：Kotlin + Jetpack Compose（JetBrains Compose 1.12 / Compose Multiplatform 1.12）+ Material 3 + MIUIX 0.9.4 + Liquid Glass（kyant backdrop）
- 最低系统：Android 8.0（API 26） · 目标/编译：API 37（Android 17）

---

## 一、环境要求

| 项 | 要求 |
|---|---|
| JDK | 17（AGP 8.x 要求） |
| Android SDK | Platform 37 + Build-Tools 37.0.0 |
| Gradle | 由 `gradle/wrapper/gradle-wrapper.properties` 指定，无需单独安装 |
| 环境变量 | `ANDROID_HOME` 指向本机 SDK（或以 `local.properties` 的 `sdk.dir` 指定） |

## 二、导入与构建

### 1. Android Studio 导入

1. `File → Open`，选择**本目录（`MediTrack/`，即包含 `settings.gradle.kts` 的目录）**。
2. 首次导入会自动下载 Gradle Wrapper 与依赖（仓库已配置阿里云镜像，国内可直接拉取）。
3. 等待 Gradle Sync 完成 → 选择 `app` 运行配置 → 选择设备 Run。

> 若 `ANDROID_HOME` 未生效，复制 `local.properties.example` 为 `local.properties` 并填入本机 SDK 路径。`local.properties` 已被 `.gitignore` 排除，不会进版本库。

### 2. 命令行构建

```bash
# Debug 包（applicationId = com.medilog.app.debug）
./gradlew :app:assembleDebug

# Release 包（开启 R8 压缩 + 资源缩减，需签名配置，见「签名」）
export ANDROID_KEYSTORE_PROPS=/path/to/keystore.properties   # Git Bash
./gradlew :app:assembleRelease

# 测试
./gradlew :app:testDebugUnitTest          # 纯 JVM 单元测试（Robolectric）
./gradlew :app:connectedDebugAndroidTest  # 真机/模拟器插桩测试（Compose 语义树）
```

## 三、工程结构与模块划分

本工程采用**单应用模块（`:app`）+ 严格分层分包**结构。

```
MediTrack/
├── settings.gradle.kts          # 工程名 + 模块声明（include(":app")）
├── build.gradle.kts             # 根构建脚本：插件统一声明（apply false）
├── gradle.properties            # JVM 参数 / AndroidX / 配置缓存 / 并行构建
├── gradlew, gradlew.bat         # Gradle Wrapper
├── gradle/
│   ├── libs.versions.toml       # 版本目录（所有依赖与插件版本的单一来源）
│   └── wrapper/                 # Wrapper jar + 分发地址
├── app/                         # 唯一应用模块
│   ├── build.gradle.kts         # SDK/签名/构建类型/依赖/构建守卫
│   ├── proguard-rules.pro       # R8 规则
│   └── src/
│       ├── main/                # 主源码（生产）
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/medilog/app/
│       │   │   ├── MainActivity.kt          # 入口：主题下发 + 导航 + 转场
│       │   │   ├── CrashActivity.kt         # 全局崩溃兜底页
│       │   │   ├── ui/                      # 表现层
│       │   │   │   ├── theme/               # 主题（AppTheme/Md3 令牌/Glass 材质）
│       │   │   │   ├── glass/               # 液态玻璃组件库
│       │   │   │   ├── components/          # 通用组件（输入框/顶栏/分段/设置行）
│       │   │   │   ├── motion/              # 页面转场令牌
│       │   │   │   └── screens/             # 各页面
│       │   │   ├── data/                    # 数据层（Repository / DAO / 事务）
│       │   │   ├── model/                   # 领域模型
│       │   │   ├── reminder/                # 提醒调度（AlarmManager/通知）
│       │   │   └── util/                    # 工具（时间/CSV/导航）
│       │   └── res/                        # 资源（drawable/values/mipmap/xml…）
│       ├── test/               # 纯 JVM 单元测试（Robolectric，7 套件 47 用例）
│       └── androidTest/        # 插桩 UI 测试（Compose 语义树，8 用例）
└── local.properties.example    # SDK 路径模板（可选，复制为 local.properties）
```

### 与内部版本的差异

| 项 | 说明 |
|---|---|
| 字体 | 源码的 `SfPro` 别名已改为 `FontFamily.SansSerif`（系统无衬线字体）。原内部版本打包了子集化 Apple San Francisco 字体（`.ttf`），该字体受 Apple OS 软件许可协议约束、不允许随第三方开源项目再分发，故公开版本移除。127 处调用通过别名保持不变；中文原本就由系统字体渲染，功能与交互不受影响，仅 Latin 字形有细微差异。 |
| 未包含 | APK 构建产物、`_rebuild/` 快照脚本、`tools/` 测试驱动、真机测试报告与内部交接文档；这些属于构建输出或研发过程材料，不影响从源码复现构建。 |
| 版本标识 | `versionCode=1` / `versionName=1.0` 保留不变。 |

### 为什么保持单模块

本工程当前约 1.1 万行 Kotlin，**单一 `:app` 模块 + 分层分包**已满足可维护性要求，且能保证「克隆即可编译运行」。若后续需要拆分（例如把设计系统、数据层独立成库模块给第二个 App 复用），推荐方案是在本目录新增 `:core:designsystem` / `:core:data`，按以下顺序迁移，每一步单独验证编译：

1. `:core:designsystem` ← `ui/theme` + `ui/glass` + `ui/components`
2. `:core:data` ← `data` + `model` + `reminder`
3. `:app` 只保留 `MainActivity`、`CrashActivity`、`ui/screens`、`ui/motion`，并 `implementation(project(":core:designsystem"))`

不建议在无复用诉求时提前拆模块：会增加构建配置与 ProGuard 规则的维护面。

## 四、源码分层约定

| 层 | 目录 | 职责 | 禁止 |
|---|---|---|---|
| 表现层 | `ui/` | Compose UI、主题令牌、动效、导航 | 直接访问 SQLite |
| 数据层 | `data/` | 唯一数据入口：事务化增删改、快照还原、闹钟清理 | 持有 View / Compose 状态 |
| 调度层 | `reminder/` | 精确闹钟、通知渠道、全屏提醒 | 业务判断 |
| 模型层 | `model/` | 纯数据类 | 依赖 Android SDK |
| 工具层 | `util/` | 纯函数（时间格式、CSV 编解码、导航跳转） | 全局状态 |

## 五、签名配置

- 签名密钥与密码**不在工程目录内**（`.gitignore` 已排除 `*.jks`、`keystore.properties`）。
- release 构建按以下顺序查找 `keystore.properties`：
  1. 环境变量 `ANDROID_KEYSTORE_PROPS`（绝对路径）
  2. Gradle 属性 `-Pmedilog.keystoreProps=<绝对路径>`
  3. `~/.android-keys/medilog/keystore.properties`
  4. `~/.android/medilog-keystore.properties`
- 三者都找不到时 **release 构建直接失败**（构建守卫），绝不静默产出未签名包。
- `keystore.properties` 字段：`storeFile`（可为相对该文件的相对路径）、`storePassword`、`keyAlias`、`keyPassword`。

## 六、双主题体系

App 支持两套视觉体系，通过设置页「主题风格」切换，运行时热切换（无需重启）：

- **MATERIAL（Material Design 3）**：严格 MD3 令牌（色彩角色 / 形状标度 / 排印标度 / 状态层 / 动效令牌），不透明 surface + elevation 色调角色。
- **MIUIX**：液态玻璃视觉（kyant backdrop 模糊 + 折射 + 高光），保留 iOS 风格玻璃质感。

主题实现位于 `ui/theme/`：`Md3.kt`（MD3 令牌单一来源）、`Theme.kt`（`mediTextStyle` 在 MATERIAL 模式下把行内字号吸附到 MD3 排印标度）、`AppTheme.kt`（按 mode 下发 `MaterialTheme` 或 `MiuixTheme`）。

## 七、测试体系

| 层级 | 位置 | 规模 | 命令 |
|---|---|---|---|
| 纯 JVM 单元测试 | `app/src/test/` | 7 套件 / 47 用例 | `./gradlew :app:testDebugUnitTest` |
| 插桩 UI 测试 | `app/src/androidTest/` | 8 用例（Compose 语义树，用 `testTag` 定位） | `./gradlew :app:connectedDebugAndroidTest` |
| 端到端回归 | 未收录（内部 adb 驱动脚本，依赖真机环境） | — | — |

测试基础设施说明：JetBrains Compose Multiplatform 未发布 `ui-test-manifest`，因此 Compose UI 测试用 `createComposeRule()` + 在 `app/src/debug/AndroidManifest.xml` 声明的 `ComponentActivity` 宿主，而非 `createAndroidComposeRule<MainActivity>()`。

## 八、常用目录速查

- 改主题令牌 → `app/src/main/java/com/medilog/app/ui/theme/Md3.kt`
- 改对话框/按钮/卡片 → `app/src/main/java/com/medilog/app/ui/glass/GlassComponents.kt`
- 改页面 → `app/src/main/java/com/medilog/app/ui/screens/`
- 改数据/删除逻辑 → `app/src/main/java/com/medilog/app/data/`
- 改提醒调度 → `app/src/main/java/com/medilog/app/reminder/`
- 依赖版本 → `gradle/libs.versions.toml`
