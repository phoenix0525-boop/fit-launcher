# FIT 桌面（Android 啟動器 APK）

Honda FIT4 2022 台灣油電／愛客思 AKZ ZX09（UIS7870SC、Android 13、2000×1200）的個人化桌面。
Google 即時地圖與目前位置版（v0.2.0），由第一階段 Web 原型 `launcher-prototype` 移植為原生 Android。

- 語言／框架：Kotlin 2.0.20、Jetpack Compose（BOM 2024.09.00）、AGP 8.5.2、Gradle 8.9
- minSdk 26、targetSdk／compileSdk 34；套件名稱 `tw.fitcar.launcher`
- 不刷韌體、不需 root、不假設 FYT 主板；不使用 `QUERY_ALL_PACKAGES`

> 狀態：GitHub Actions 已在提交 `832bcb5` 完成 `testDebugUnitTest` 與 `assembleDebug`，並產出 debug APK；
> **尚未在 AKZ ZX09 車機實測**。請先從原廠桌面開啟測試，不要直接設為預設首頁。

## 功能

| 區塊 | 內容 | 資料來源 |
|---|---|---|
| 狀態列 | 時間、日期、星期；目前位置（行政區／道路，無地址時顯示座標）；GPS 車速；所有 App、設定 | GPS ＋ Android Geocoder（地址非神盾資料） |
| 左側 70% 導航 | Google Maps SDK 即時定位地圖、回到目前位置、Google 地圖與已綁定導航 App 的全螢幕入口 | 本版只內嵌即時地圖，不提供內嵌逐向導航；不再由首頁觸發有問題的分割路徑 |
| 右上音樂 | 歌名、歌手、專輯、封面、播放／暫停、上下首、進度 | Android MediaSession（需通知存取）；只啟用 App 實際支援的控制，`ACTION_SEEK_TO` 才可拖曳 |
| 歌詞 | 顯示「歌詞資料尚未連接」 | KKBOX 無公開歌詞介面，不抓取、不 OCR |
| 右下捷徑 | 2 頁 × 2×2；點「＋」新增；長按更換、移除、往前／往後移 | 車機實際安裝的 App；重開機保留 |
| 所有 App | 全螢幕清單，右上「返回桌面」 | `MAIN/LAUNCHER` 查詢 |
| 設定 | 實機檢測、顯示與亮度、App 綁定、導航方式、權限、返回原廠桌面、實機資訊、關於 | — |
| 實機檢測 | 自動檢測＋互動測試＋手動檢查，整理「可改進的地方」，可一鍵套用建議、匯出報告 | 見下方說明 |
| 亮度排程 | 日間 07:00–17:00 80%、黃昏 17:00–18:30 50%、夜間 18:30–07:00 20%（可改），日夜主題自動，平滑漸變 | `WRITE_SETTINGS`（使用者手動允許） |

### 實機檢測（設定 → 實機檢測）

裝機後先跑這個，畫面會列出「可改進的地方」，依嚴重程度排列。

| 分頁 | 內容 |
|---|---|
| 可改進的地方 | 所有 ⚠ 可改進／✗ 有問題的項目與建議；有按鈕的可直接套用（例如導航改全螢幕、關閉亮度排程、綁定偵測到的 App） |
| 全部結果 | 依群組列出所有檢測 |
| 自動檢測項目 | 記憶體與儲存是否符合 8 GB／256 GB 標示、CPU 核心與時脈、Android 版本、螢幕 dp 與字體縮放、預設桌面、導航王／KKBOX／神盾偵測與綁定、系統是否支援分割畫面、KKBOX MediaSession 實際欄位與控制、亮度權限與系統自動亮度、GPS 權限、熄火後是休眠還是重開機 |
| 手勢測試 | 雙指上滑／下滑、單指／雙指雙擊：比對前後音量，判斷原車機手勢在 FIT 桌面內是否仍有效（不需實作）或需要 FIT 桌面自行實作 |
| 按鍵測試 | 列出 App 收到的方向盤／面板按鍵 KeyEvent |
| 亮度測試 | 暫時改亮度 4 秒後恢復，檢查是否被系統改回、螢幕是否真的變化（判斷背光是否走 Android 系統亮度） |
| 分割畫面 | 程式自動分割實測＋手動分割結果記錄 |
| GPS 測試 | 首次定位時間、衛星數、是否有速度資料 |
| 手動檢查 | 倒車影像、Home 鍵、熄火再發動、方向盤按鍵、神盾浮動窗、車燈調光、導航王顯示、可讀性、觸控範圍 |
| 匯出報告 | 存成「下載／FIT實機檢測_日期.txt」或分享；不含序號、IMEI |

檢測不會改系統設定；只有亮度測試會暫時改亮度並自動恢復。互動測試與手動檢查結果會保存，重開仍在。

### 導航分割畫面怎麼運作

Android 的桌面（HOME）畫面本身不能進入分割畫面，所以「開始導航」會：

1. 開啟本 App 的一般畫面「FIT 側欄」（音樂＋捷徑）。
2. 由側欄以 `FLAG_ACTIVITY_LAUNCH_ADJACENT` 把導航王開在旁邊。
3. 約 2 秒後檢查側欄是否在分割畫面中。不是 → 導航王已是全螢幕（自動退回），側欄自行關閉，並記錄結果。

70/30 比例由系統分割線決定，App 無法強制；可用手指拖曳中間分隔線。

### 亮度寫入時機

為避免和原廠車燈調光反覆搶控制，只在這些時候寫入系統亮度：時段邊界、開機／時間或時區變更、使用者在設定頁操作。
手動調整亮度會暫時覆寫自動排程，到下一個時段邊界自動恢復（或按「恢復自動」）。

## 專案結構

```
app/src/main/java/tw/fitcar/launcher/
  MainActivity.kt        桌面（HOME）＋畫面切換
  CompanionActivity.kt   分割畫面用的 30% 側欄
  Diagnostics.kt         設定 → 實機資訊
  check/                 實機檢測：自動檢測、互動測試、手動檢查、報告匯出
  data/                  Schedule（純邏輯）、Prefs（設定保存）、AppRepository（App 清單）
  media/                 MediaSession 讀取、通知存取服務
  nav/                   導航開啟策略
  system/                亮度控制與排程、開機接收器、GPS 車速
  ui/                    主題色票、元件、主畫面、App 清單、設定頁
app/src/test/            Schedule／捷徑單元測試
  .github/workflows/build-apk.yml  GitHub Actions 正式編譯流程
  build-apk.github-workflow.yml    同內容的可見備份，避免上傳時漏掉隱藏資料夾
```

## 如何編譯 APK（三選一）

**A. Android Studio（電腦上）**
1. 安裝 Android Studio（含 Android SDK 34）。
2. File → Open，選 `fit-launcher-android` 資料夾，等 Gradle 同步完成。
3. Build → Build App Bundle(s) / APK(s) → Build APK(s)。
4. 產出：`app/build/outputs/apk/debug/app-debug.apk`。

建議先把資料夾複製到本機磁碟（例如 `C:\dev\fit-launcher-android`）再開啟，避免 Google 雲端硬碟同步 build 暫存檔。

**B. GitHub Actions（不用裝 Android Studio）**
把專案內容（包含 `.github` 隱藏資料夾）推到 GitHub 私人 repo；Actions 會自動跑測試並產出 APK（Actions → 最新一次 → Artifacts → `fit-launcher-debug-apk`）。若網頁上傳漏掉 `.github`，可依根目錄的 `build-apk.github-workflow.yml` 重建 `.github/workflows/build-apk.yml`。

**C. 讓 Claude 直接編譯**
在 claude.ai 網頁版 Settings → Capabilities 允許：`dl.google.com`、`maven.google.com`、`services.gradle.org`、`plugins.gradle.org`、`repo.maven.apache.org`、`repo1.maven.org`。

## 安裝與實機測試步驟

1. 把 APK 複製到隨身碟或手機，在車機以檔案管理開啟安裝（需允許「安裝未知來源 App」）。
2. **先不要設為預設桌面**。從原廠桌面點「FIT 桌面」圖示開啟測試。
3. 設定 → App 綁定：選導航王、KKBOX、神盾。
4. 設定 → 權限：開啟通知存取、允許定位、允許修改系統設定。
5. 播放 KKBOX，進入 設定 → 實機檢測，依序做手勢、按鍵、亮度、分割畫面、GPS 測試。
6. 確認沒有問題後，才到「返回原廠桌面」→「變更預設桌面」選 FIT 桌面，完成「手動檢查」（倒車影像最優先）。
7. 「匯出報告」存到下載資料夾，把文字檔交給 AI 決定下一版要改什麼。

### 退路（隨時可以回原廠桌面）

- FIT 桌面內：設定 → 返回原廠桌面 → 開啟原廠桌面／變更預設桌面。
- 系統設定：應用程式 → 預設應用程式 → 主畫面應用程式 → 選原廠桌面。
- 或直接解除安裝 FIT 桌面，系統會自動回原廠桌面。

## 尚未做／刻意不做

- 導航王地圖嵌入（需勤崴正式 SDK）、神盾資料重繪、KKBOX 歌詞：沒有合法資料來源前不做。
- 雙指音量手勢：先實測原車機能力是否沿用，消失才實作。
- 捷徑拖曳排序：第一版以「往前移／往後移」代替，實機確認操作習慣後再評估。
- 發布簽章：release 暫用 debug 簽章，僅供個人側載。

## v0.2.0 地圖設定與驗收

- 本版將首頁左側改為 Google Maps SDK 即時定位地圖，拖曳後停止跟隨，按「回到目前位置」恢復。
- 上方原神盾卡片改為「目前位置」。Android Geocoder 約每 30 秒且移動至少 100 公尺才重新查詢；查詢失敗顯示座標，不假裝有道路資訊。定位失效顯示「定位中」。不保存行車位置歷史。
- 神盾仍可從所有 App／捷徑使用，其浮動窗及語音由神盾自行負責。本版不讀取神盾資料。
- 地圖缺金鑰／Google Play 服務時顯示提示；地圖載入逾 20 秒提示檢查網路、授權、計費。不宣稱能從 SDK 自動判定金鑰錯誤。
- 本版未修改歌詞，未修復設定／檢測頁內的分割畫面實驗功能；首頁導航入口已改用既有正常的全螢幕方式。

### 金鑰

在 Google Cloud 啟用 Maps SDK for Android 與計費，建立限制為 Android 應用程式的金鑰：套件 `tw.fitcar.launcher` ＋實際 APK 簽章 SHA-1，API 限制僅允許 Maps SDK for Android。

- 本機：在不提交版本控制的 `local.properties` 加入 `MAPS_API_KEY=你的金鑰`，或設定環境變數 `MAPS_API_KEY`。
- GitHub Actions：設定 repository secret `MAPS_API_KEY`。不要貼在 issue、程式碼或聊天中。
- 未設定金鑰仍可編譯，畫面會提示尚未設定。須確認 APK 簽章指紋；CI 臨時 debug 簽章可能隨執行環境改變，正式持續更新前應固定簽章。

### 必須實機確認

1. 有效金鑰時能載入地圖；缺金鑰時仍能使用音樂、捷徑與外部導航。
2. GPS 首次定位、移動跟隨、手動拖曳、回到目前位置、GPS 關閉與失去定位。
3. 地名／道路能更新；地址服務失敗改顯示座標；無權限時不顯示舊地址。
4. Google 地圖／導航王全螢幕開啟並返回 FIT；切頁、休眠喚醒後地圖恢復。
5. 車機 1333×800 dp、字體 125% 版面與 Google 標示完整。
