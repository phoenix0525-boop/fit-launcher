# App-specific release rules. AndroidX and MapLibre publish their own consumer rules.
# Keep JNI entry points referenced by native MapLibre code while allowing R8 to optimize everything else.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
