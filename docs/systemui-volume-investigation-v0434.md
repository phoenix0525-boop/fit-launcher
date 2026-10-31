# 原廠下拉音量控制：重新核對 SystemUI APK

來源：公版_7870_20250729/AllAppUpdateA13/priv-app/SystemUI/SystemUI.apk。
本次確實下載 APK、解析 DEX 指令，未安裝或執行原廠程式。
SHA256：3b8ad38a5af4da4d53d9d17d3f7ad61010dd18c9c14a74e1fbeaa73817c60fc4
大小：34,550,183 bytes。

## 下拉滑桿實際路徑
`com.syu.view.VolLayout$9.onProgressChanged(SeekBar, int, boolean)`：
1. 檢查 VolLayout.showing、靜音狀態及目前 mVol。
2. `AutoController.getController().getAuto()` 取得 `com.syu.remote.Remote`。
3. `Remote.commad(4, 0, new int[]{progress})`。commad 是原廠方法拼字。
4. Remote.commad 將 module/code/ints 封成 Remote.Command 送 EventBus；Remote.Module.command 再呼叫 IRemoteModule.cmd。
5. Remote.getAutoTools 預設 action=com.syu.ms.toolkit、package=com.syu.ms。

這與本專案 VolumeControl.kt 的 setStreamVolume(STREAM_MUSIC)／adjustStreamVolume(STREAM_MUSIC) 不同。符合使用者「數值動、聲音不動；原廠下拉有效」的更正，不能再把 Android 值下降當作修好。

## 已找到回讀線索
`VolLayout.onBusEvent(Message)`：module=4 時，code=2 讀 ints[0] 更新 mVol，code=3 更新 mute。這是模組回呼，不是 Android AudioManager 數值。
`CarService` 以 module=4 註冊 SOUND_LOOK_CODE=[2,3]；VolLayout.onAttachedToWindow 亦有對應觀察代碼。

## 下一步
檢查 com.syu.ms 的 ToolkitService、IRemoteToolkit、IRemoteModule、IModuleCallback 完整協議及權限檢查；先做限時唯讀綁定／回呼觀察。第三方 App 能否拿到模組及回呼仍未驗證，服務 exported 不等於 Binder 一定允許。
收到有效原廠音量回呼後，才提供使用者主動單次降低測試，記錄模組回讀與聽感，不自動增加、不執行不明的負值命令、不猜 transaction code。尚未整合此控制，音量不得列已修復。

原廠 APK 證據不代表車機目前安裝版本一定相同，需實車確認版本。
