# ============================================================
# mixy ProGuard 规则
#
# 环境：
#   minSdk 26 / targetSdk 36
#   AGP 8.7.3 / Kotlin 2.0.21
#   Compose BOM 2024.12.01 / Coil 3.0.4 / Media3 1.5.1
#
# 设计原则：
#   1. 大部分库自带 consumer rules，AGP 会自动应用。这里只做兜底。
#   2. 只 keep 真正需要保留的类，尽量不阻碍 R8 优化。
#   3. 数据类字段名默认混淆（除非未来加 Parcelable 需要稳定序列化）。
# ============================================================


# ---------- 基础 ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# enum 的 values/valueOf 常被反射调用
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ---------- 日志剥离 ----------
# 只剥离 verbose / debug，保留 info / warn / error
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}


# ---------- Kotlin ----------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.coroutines.internal.**
-dontwarn kotlinx.coroutines.debug.**


# ---------- Android 组件 ----------
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider


# ---------- Coil 3 自定义 Fetcher / Keyer ----------
# AudioCover / VideoCover / PdfCover 是 @JvmInline value class，
# Coil 通过 T::class 匹配类型，R8 内联后可能影响运行时匹配。保留整个包。
-keep class com.grillpoet.mixy.image.cover.** { *; }
-keepclassmembers class com.grillpoet.mixy.image.cover.** {
    <init>(...);
}

-dontwarn coil3.**
-dontwarn okio.**


# ---------- Media3 ----------
# Media3 三个模块自带完整 consumer rules，会自动 keep 必要的类。
# 这里只加 -dontwarn，不再 -keep 整个包（否则 APK 会大几百 KB）。
-dontwarn androidx.media3.**
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn com.google.auto.value.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**


# ---------- Compose ----------
-dontwarn androidx.compose.**


# ---------- DataStore ----------
-dontwarn androidx.datastore.**


# ---------- DocumentFile ----------
-dontwarn androidx.documentfile.**


# ---------- Parcelable / Serializable ----------
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


# ---------- 数据模型 ----------
# 当前字段名可以安全混淆。
# 如果未来引入 kotlinx.serialization，取消下面注释：
#
# -keepclassmembers class com.grillpoet.mixy.data.model.** {
#     *** Companion;
# }
# -keepclasseswithmembers class com.grillpoet.mixy.data.model.** {
#     kotlinx.serialization.KSerializer serializer(...);
# }
# -keep,includedescriptorclasses class com.grillpoet.mixy.data.model.**$$serializer { *; }


# ---------- 收尾 ----------
-dontwarn org.slf4j.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.intellij.lang.annotations.**