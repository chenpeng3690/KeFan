# 课返 release 混淆规则

# 保留注解、泛型签名、内部类信息（Room / Hilt / 序列化依赖）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room：数据库实现与实体字段
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.kefan.app.data.local.entity.** { *; }

# Hilt 入口
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# 枚举（模板来源、状态等按名称读取）
-keepclassmembers enum com.kefan.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp / Okio 可选依赖
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# EncryptedSharedPreferences：Tink 通过反射加载加密实现
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
