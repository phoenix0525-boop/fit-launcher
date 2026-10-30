# 原廠式導航嵌入評估（2026-10-31）

## 使用者目標

左側桌面地圖區可顯示真正 Google 地圖或導航王，保留右側音樂與桌面控制，而非只有導航通知或另開全螢幕。

## 已知證據與限制

既有原廠韌體分析記錄顯示 com.syu.launcher 的 EmbeddedWindow 經 CarTaskView／TaskViewManager 使用 WMShell TaskView.startActivity，將外部導航工作放入指定矩形。這是既有分析結果；本次未重新下載或反組譯原廠 APK。

Android 13 原始碼將 MANAGE_ACTIVITY_TASKS 定為 signature|recents，ACTIVITY_EMBEDDING 為 signature|privileged。一般設為預設 HOME 並不等於成為系統 Recents 或已取得這些權限。原廠 APK 的 shared UID／宣告權限亦不能證明目前第三方桌面有相同權限。

第一手來源：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-13.0.0_r1/core/res/AndroidManifest.xml

這項限制不代表所有廠商介面必定不可用；現有 PackageManager 只讀檢查也不足以證明可用或完全不可用。

## 可行路線

| 路線 | 能達到的結果 | 目前狀態 |
| --- | --- | --- |
| 原廠 TaskView 宿主或受權限保護的橋接服務 | 在桌面矩形區呈現實際導航 App | 需找到宿主建立、啟動、尺寸變動、焦點、釋放與返回介面，並確認第三方呼叫權限；尚未完成 |
| 一般 Android Activity Embedding | 受系統／被嵌入 App 信任條件限制的活動嵌入 | 不能假設 Google 地圖或導航王版本允許；本次未驗證其 manifest 或信任契約 |
| 系統分割畫面 | 桌面與實際導航並排 | 現有實驗入口；位置與比例由系統決定，不能算桌面內嵌 |
| 自有地圖 SDK／現有免費底圖 | 自己的地圖與跟隨定位 | 已有免費底圖；加入 Google Maps SDK 也不等於嵌入 Google 地圖 App 的導航 |

Google 地圖與導航王若經原廠 TaskView，可望共用宿主，但每款 App 的可調整大小、生命週期與返回行為都需分開驗證，不能從其中一款推定另一款。

## 下一階段實作前需要解決

1. 比對原廠 launcher 的宿主呼叫與系統服務是否有可授權第三方使用的既有橋接入口。不得僅用音量 com.syu.ms.toolkit 服務推定能嵌入。
2. 在實車先記錄只讀權限檢查結果、原廠 launcher／導航 App 版本；系統權限不可用時不以反射硬試隱藏 API。
3. 找到可用宿主後才做嵌入 PoC；驗證觸控、GPS／導航播報、尺寸、切換 App、設定返回、熄火喚醒與任務釋放。
4. 失敗應提供明確退出／全螢幕入口，不能留黑色無法操作的導航卡片。未完成前不新增看似可用的「內嵌」開關。

本次完成的是地圖面積調整及上述整合路線評估，沒有新增系統權限、沒有寫入原廠韌體、沒有建立導航嵌入宿主。
