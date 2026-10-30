# 車機桌面 0.4.31（38）交接 — Claude（2026-10-31）

- 基底：`codex/settings-integration-v0430` `86ed74c`（0.4.30，PR #28）
- 分支：`claude/settings-review-fixes-v0431`（本環境無 GitHub 推送權限，以 bundle 交付）
- 狀態：**未編譯 APK、未跑 Android 單元全集、未跑 Android13 UI／API29 回歸、未上車**。

## 審查發現與修正

| # | 問題（證據） | 修正 |
|---|---|---|
| 1 | 設定進入檢測或選 App 再返回，分類回到第一頁、捲動與天氣授權碼草稿遺失（SettingsScreen 離開組合，rememberSaveable 被丟棄） | Activity 持有 `SettingsNavState`；子畫面返回保留，從桌面開啟重設；切換分類捲動歸零；授權碼草稿只在記憶體 |
| 2 | 側欄 14 個分類，返回時選取項可能在可見範圍外 | `SectionNavLayout` 以 BringIntoViewRequester 捲到選取分類；可接收外部 ScrollState |
| 3 | 蜘蛛人夜間（淺底）沿用深色夜間狀態色，對比 1.5–1.9:1；多種夜間 danger 4.2–4.4；日間狀態字在 surface 4.2–4.47 | 蜘蛛人夜間深色狀態字；`NightStatusDanger／Success`；日間 success #4F6D60、danger #9A524D；新增全風格對比測試 |
| 4 | 切換播放器使所有結果（含背光）變待測（情境字串全文比對） | `ResultValidity.relevantFields`：每項只比對相關欄位；未知項目維持全比對 |
| 5 | 已回報聽感仍寫「請另行回報」；「Android 數值未改變」被寫成「未生效」；音量檢測一律 INFO，控制失敗被 0 問題掩蓋 | `AudioVerdicts`：送出控制＋沒有變化＝有問題；未回報／不確定＝待測；觀察原廠＝資訊；文字不再以 Android 數值判定實際聲音 |
| 6 | 報告有待測項目仍寫「目前沒有」 | `ReportSummary.headline` 註明待測數與「不代表全部正常」 |
| 7 | 設定頁以數字 4／7／8 開檢測分頁 | `CheckerTabs` 名稱常數＋測試 |
| 8 | DSP 入口不存在／停用時按鈕仍可按 | 先查狀態（不存在、停用、無畫面），失敗不崩潰並提示 |
| 9 | 「關於」把機型寫成 Honda Fit 搭載、條件與待驗證混在一起 | `DeviceSupport`：適用條件／已回報實測機型 AKZ ZX09／待驗證功能三段；目前裝置不在清單時明說 |
| 10 | Binder 音量、TaskView、DSP 自動套用缺可行性證據 | `OemIntegrationProbe` 只讀 metadata（不 bind、不送命令）；分析見 `docs/oem-integration-feasibility-v0431.md` |

## 本環境驗證

- Kotlin 2.0.20 編譯純邏輯與其測試（以 JUnit／coroutines 替身）：46/46 通過
  （ResultValidity、ReportSummary、AudioVerdict、AudioProbe、VolumeVerification、SettingsDiff、OemAssessment、DeviceSupport、CheckerTabs）。
- `OemIntegrationProbe.kt`、`DeviceSupport.kt` 以 android-34.jar 編譯無錯誤。
- 修改的 Compose 檔案：kotlinc 無語法錯誤（無 Compose 類別庫，型別未檢查）。
- 配色：以 Python 依 Theme.kt 實際色碼重算，7 種風格 × 日夜 × 狀態色在 surface／surfaceStrong 皆 ≥ 4.5；選取分類 9 種強調色皆 ≥ 4.5。
- 專案 Python 檢查 7 項通過（release artifact 檢查需 APK，未執行）；`git diff --check` 通過。

## 測試變更（刻意改變語意，不是放寬）

- `AudioProbeTest.ignoredRequestPreserves36To36AsNotApplied`：不再要求「未生效」，改要求「Android 數值未改變」且提示聽感。
- `LargeFontLayoutTest` 音量檢測：回報「沒有變化」後應為 FAIL 並出現在可改進項目；關於頁版本字串改新格式。
- `HiddenCharacterThemeLayoutTest`：關於頁改驗三段標題與 AKZ ZX09。
- 新增 `SettingsNavigationLayoutTest`：返回保留狀態、切換歸零；1333×800、1024×600、853×480、720×1280 @125% 截圖與裁切檢查。

## 下一步（Codex）

1. 驗證 bundle SHA256 與 `git bundle verify`，匯入並推送分支，開 draft PR（base `codex/settings-integration-v0430`）。
2. 跑 Build APK（單元測試全集）與 UI layout（Android13 全部、API29 視窗）。編譯錯誤只做最小修正，逐一 commit；不刪測試、不放寬門檻。
3. 檢查 `ui-screenshots` 中 `settings-*-125.png`。

## 實車測項

1. 設定 → 天氣輸入一半授權碼 → 實機檢測 → 返回：仍在天氣頁、草稿還在。
2. 綁定播放器後回設定：仍在 App 綁定頁。
3. 換播放器後看背光測試仍為原結果，KKBOX 播放一致性變待測。
4. 音量檢測「系統目前音量」回報沒有變化 → 可改進的地方出現該項。
5. 蜘蛛人夜間看權限頁「● 已允許」是否清楚。
6. 音量與音效 →「檢查並記入報告」，匯出報告提供三項可行性判讀。
7. 原廠 DSP 按鈕可開啟；關於頁目前裝置顯示與已回報機型相同。

## 不可改決定（同前）

不 root、不刷韌體、不送未驗證硬體命令、不複製原廠 APK／韌體；正式簽章策略不變、不發布線上更新；
保留其他風格、焦糖棕、英雄紅與角色隱藏流程；不合併 main 或舊基底；私鑰、密碼、氣象授權碼、OTA secret 不上傳。
