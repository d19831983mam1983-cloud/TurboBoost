-keep class com.example.turboboost.UserService { *; }
-keep class com.example.turboboost.IUserService { *; }
-keep class com.example.turboboost.IUserService$Stub { *; }
-keep class com.example.turboboost.IUserService$Stub$Proxy { *; }
-keepclassmembers class * extends com.example.turboboost.IUserService$Stub {
    public <init>(...);
}

-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

-keep class com.example.turboboost.App { *; }
-keep class com.example.turboboost.CrashHandler { *; }
-keep class com.example.turboboost.SplashScreen { *; }
-keep class com.example.turboboost.SettingsScreen { *; }
-keep class com.example.turboboost.LauncherScreen { *; }
-keep class com.example.turboboost.ThemePalette { *; }
-keep class com.example.turboboost.ThemePalette$Theme { *; }
-keep class com.example.turboboost.GameProfile { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
