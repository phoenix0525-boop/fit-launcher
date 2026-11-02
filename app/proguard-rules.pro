# App-specific release rules. AndroidX and MapLibre publish their own consumer rules.
# Keep JNI entry points referenced by native MapLibre code while allowing R8 to optimize everything else.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 0.4.35: Shizuku instantiates the shell user service by class name in another process.
-keep class tw.fitcar.launcher.system.ShellUserService { <init>(...); *; }
