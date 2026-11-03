# 車機桌面 0.4.36（43）

- 設定 → 音樂與音效新增「上車自動續播」，預設關閉。
- 跟隨模式恢復上次實際播放的 App，供 KKBOX、Spotify、YouTube Music 輪流使用；固定模式只允許綁定的播放器。
- 以公開 BOOT_COUNT 與螢幕關閉／喚醒事件判斷，返回桌面不觸發；不代表已取得原廠 ACC 訊號。
- 等待至少 3 秒，最多 60 秒；需通知存取、已連線的系統服務、已知前景、一般音訊模式及可用輸出。已辨識的倒車畫面與通話期間延後。
- 只對同一個 MediaSession 送一次 ACTION_PLAY；其他播放器已開始播放時取消。暫停、停止或手動控制會取消等待，不反覆重播。
- 沒有可控制的 session 時，顯示手動播放／連接上次播放器提示，不強行啟動 App 或切走導航。
- 播放器因網路、登入、背景限制拒絕播放時不重試；三款 App 均待實車驗證。

測試目標：愛客思 AKZ ZX09／UNISOC UIS7870SC／FYT-SYU／Android 13；其他機種可能有相容性問題，尚未完成實車驗收。

正式憑證沿用 B693…DD8。未正式簽署前的 APK 不能作為正式更新。

實作依據：[Android MediaController.TransportControls](https://developer.android.com/reference/android/media/session/MediaController.TransportControls)、[AudioManager](https://developer.android.com/reference/android/media/AudioManager)。音訊模式僅能反映系統公開狀態，原廠藍牙通話與倒車畫面仍須實車核對。
