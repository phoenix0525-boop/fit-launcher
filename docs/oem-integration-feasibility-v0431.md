# 原廠整合可行性分析（0.4.31，Claude）

範圍：原廠 Binder 音量、TaskView 導航嵌入、DSP 參數自動套用。本文件只判斷「第三方桌面能否合法取得通道」與「能否讀回驗證」，
**不宣稱任何一項可用，也沒有發送任何硬體命令**。

## 證據來源與等級

| 等級 | 來源 | 用途 |
|---|---|---|
| A 實測 | 0.4.22–0.4.24 實車音量對照紀錄（原廠鍵 34→36 聲音變小；桌面 36→36、36→34 聲音不變） | 證明 Android MUSIC 數值不等於實際音量 |
| B 靜態反組譯 | Codex 0.4.30 交接：`com.syu.launcher`、`com.syu.ms`、SystemUI 的 manifest 與 dex 指令 | 推測呼叫鏈；**未經執行驗證** |
| C 公開 API 規則 | Android 13 權限保護等級、隱藏 API 限制 | 判斷第三方 App 是否可能取得 |
| D 本版只讀檢查 | 設定 → 音量與音效 →「原廠整合可行性（只讀檢查）」 | 在實車讀出元件是否匯出、需要的權限與保護等級；**尚未在車上執行** |

## 1. 原廠 Binder 音量（`com.syu.ms` / action `com.syu.ms.toolkit`）

- B：SoundController module 4 → command 0 → `IRemoteModule.cmd`；[-2] 降、[-1] 升、0..36 絕對值。
- 限制：
  - 能否 bind 取決於服務是否 `exported` 與 `android:permission`。原廠服務常與系統共用 UID，若權限為 signature／privileged，第三方 App 無法取得（C）。
  - 即使能 bind，命令格式只來自反組譯；沒有唯讀查詢方法可讀回目前音量，無法驗證送出的命令是否只改音量、不影響 MCU 其他狀態。
  - Android MUSIC 數值不能當讀回（A）。
- 結論：**目前不可宣稱可用，也不實作送出命令**。下一步：在車上執行 D 取得 exported／permission；若為不需權限的匯出服務，
  先只找「查詢目前音量」類的唯讀命令並與聽感比對，確認可讀回後再另行評估（需使用者同意）。

## 2. TaskView／WMShell 導航嵌入

- B：原廠桌面透過 TaskView 將 App 嵌入指定 Rect，並具系統 task 權限與 shared UID。
- C：`android.window.TaskView` 與 WMShell 為隱藏／系統 API，需要 `MANAGE_ACTIVITY_TASKS`（signature|privileged）。
  一般安裝的第三方桌面無法取得，也不能用反射繞過（違反 Android 非 SDK 介面限制且不穩定）。
- 結論：**不可行**。維持「分割畫面優先、失敗改全螢幕」（決策 15）。D 會在車上回報該權限的保護等級作為佐證。

## 3. DSP 參數自動套用（`com.syu.eq`）

- 已知參數（照片）：前聲道高通 62 Hz／12 dB/oct、低通直通、Loud 關、EQ Q 3.2；各頻段增益無數字。
- 限制：原廠 EQ APK 尚未分析；參數儲存位置、格式與寫入通道未知；沒有讀回方法就無法備份與還原，寫錯可能影響喇叭。
- 結論：**不實作自動套用**，只開啟原廠 App。D 會列出 `com.syu.eq` 未受權限保護的匯出服務／提供者數量；
  若為 0 即確定不可行；若大於 0，仍需先做到「讀回 → 備份 → 還原」三步驗證才考慮寫入。

## 本版實作的安全邊界

- 只呼叫 `PackageManager` 的 metadata 查詢（`resolveService`、`getPackageInfo`、`getPermissionInfo`）與 `checkSelfPermission`。
- 不 bind、不 startService、不呼叫 ContentProvider、不寫入任何設定。
- 新增 `<queries>` 套件可見性 `com.syu.ms`（只為讀 metadata），不使用 `QUERY_ALL_PACKAGES`。
