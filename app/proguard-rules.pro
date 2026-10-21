# 保留注解与行号，便于崩溃时定位
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable

# Room 生成的实体与 DAO（Room 自带 consumer rules，这里再兜一层）
-keep class com.chunting.reader.data.db.** { *; }

# 协程
-dontwarn kotlinx.coroutines.**

# media3 / ExoPlayer
-dontwarn androidx.media3.**

# 忽略无害的注解警告
-dontwarn org.jetbrains.annotations.**
