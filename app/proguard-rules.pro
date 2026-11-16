# ── MediTrack R8 规则 ──────────────────────────────────────────────────
# 项目不使用反射 / Gson / Moshi / kotlinx.serialization 反射式序列化，
# 数据层是裸 SQLite（Cursor 按列名取值，列名是字符串常量，不依赖类名），
# 因此 keep 规则可以保持克制 —— 只保留"名字被框架按约定查找"的部分。

# —— Android 组件（Manifest 里按类名反射创建，必须保留）——
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Service

# —— 自定义 Application 与入口 ——
-keep class com.medilog.app.App { *; }

# —— Compose：编译器生成的 composable 元数据（避免导航/重组链路被裁）——
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
# JetBrains Compose（CMP）坐标，与 androidx 包名不同，同样要保
-keepclassmembers class * {
    @org.jetbrains.compose.runtime.Composable <methods>;
}

# —— 资源与 R 类：资源缩减需要保留 R 字段的静态引用关系 ——
-keepclassmembers class **.R$* {
    public static <fields>;
}
-keep class **.R$* { *; }

# —— 第三方 UI 库：Miuix / Kyant Shapes / Backdrop 均为编译期调用，无反射入口 ——
# 若后续出现 ClassNotFoundException，按报错在此补 -keep。
-dontwarn top.yukonga.miuix.**
-dontwarn com.kyant.**

# —— 崩溃处理器需要栈信息可读（保留行号，便于线上排障）——
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# —— 去掉无用日志（保留 warn 以上）：配合 R8 的 assumenosideeffects ——
# 注意：仅对 release 生效；debug 不受 minify 影响
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# —— 数据模型：字段名不参与反射，允许重命名 ——
# （DoseLog / Medication / Schedule 若将来接入序列化，需在此补 @Keep 或 keep 规则）
