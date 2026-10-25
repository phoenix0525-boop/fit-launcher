# 原廠桌面參考與實機待驗證項目

來源：使用者提供的 Launcher3.apk，2026-10-25。

## 靜態檢查結果
- package: com.android.launcher30；versionName: 1.0；versionCode: 25090312。
- minSdkVersion: 16；targetSdkVersion: 26；platformBuildVersionName: 8.1.0。
- 宣告 sharedUserId=android.uid.system。這是原廠 APK 的宣告，不代表 FIT 桌面可取得相同權限。
- classes.dex 包含 FYT/SYU 套件、活動及廣播名稱。字串只能證明 APK 內有參考，不能證明實機已安裝或允許呼叫。

## 本次變更
在 App 清單、綁定及捷徑選單既有資料來源中補上：
- com.syu.radio/com.syu.radio.Launch
- com.syu.bt/com.syu.bt.BtAct
- com.syu.bt/com.syu.bt.act.ActBtAvStart
- com.syu.music/com.syu.music.MAct
- com.syu.settings/com.syu.settings.MainActivity
- com.syu.eq 與 com.syu.btav：只使用實機 PackageManager 回傳的啟動入口。

僅列出存在、啟用、exported 且呼叫端具備所需權限的原廠入口；一般 LAUNCHER 入口優先。
已經存在一般入口的套件不重複補列。未自動綁定任何 App。
不覆蓋原廠桌面、不複製系統 UID、不呼叫私有車控服務、不改倒車或 CANBUS 邏輯。
保留既有日系介面、MediaSession、導航分割策略與亮度排程。

## 安裝問題尚未確診
FIT 桌面仍為 minSdk 26 / targetSdk 34。原廠 APK 的最低版本與編譯版本都不能作為車機實際版本證據。
仍需失敗 FIT APK 本體、車機實際 SDK 或安裝器錯誤代碼，才可判斷版本、簽章、政策或其他原因。
此更新不是已驗證的安裝修復。

## 驗證
GitHub Actions 執行既有單元測試及 debug APK 編譯。
實機仍須確認：原廠 App 能開啟；移除/停用後不出現在補列清單；
無 SYU 套件的裝置仍正常；返回原廠桌面及倒車影像正常。
導航王嵌入地圖、KKBOX 歌詞不因原廠套件字串而自動取得。
