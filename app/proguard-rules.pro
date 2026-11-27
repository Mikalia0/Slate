# JNI callbacks and native method names are resolved by name.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keep class com.slate.android.NativeSevenZipSink { *; }
-keep class com.slate.android.gpu.** { *; }
# Shizuku user services and hidden API bridge are instantiated outside the app process.
-keep class com.slate.android.storage.**UserService { *; }
-keep class com.slate.android.storage.**$Stub { *; }
-keep class rikka.shizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
# Preserve reflection-heavy archive, APK and signing libraries until release runtime tests cover them.
-keep class com.reandroid.** { *; }
-keep class com.android.apksig.** { *; }
-keep class com.github.luben.zstd.** { *; }
-keep class com.github.junrar.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
# Commons Compress contains optional codec integrations not shipped by Slate.
-dontwarn org.brotli.dec.**
-dontwarn org.apache.commons.compress.compressors.pack200.**
-dontwarn org.objectweb.asm.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.slf4j.**
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
