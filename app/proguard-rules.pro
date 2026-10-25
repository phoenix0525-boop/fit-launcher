# FIT 桌面 R8 規則
# 保留行號，實機當機時堆疊仍可對回原始碼。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manifest 宣告的 Activity／Service／Receiver 由 AGP 自動保留；
# 檢測結果以列舉名稱存成 JSON（Status.valueOf / FixKind.valueOf），保留列舉常數名稱。
-keepclassmembers enum tw.fitcar.launcher.check.** { *; }
