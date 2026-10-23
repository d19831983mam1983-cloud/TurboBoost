-keep class com.example.turboboost.UserService { *; }
-keep class com.example.turboboost.IUserService { *; }
-keep class com.example.turboboost.IUserService$Stub { *; }
-keep class com.example.turboboost.IUserService$Stub$Proxy { *; }
-keepclassmembers class * extends com.example.turboboost.IUserService$Stub {
    public <init>(...);
}
-keep,allowobfuscation class * extends rikka.shizuku.ShizukuProvider { *; }
-keep class rikka.shizuku.** { *; }
