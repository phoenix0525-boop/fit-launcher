# 0.4.34 導航與音量調查交接

基底：v0.4.33／88faf6c516176cca08ff912463a74a346dba3f33。
分支：codex/navigation-audio-investigation-v0434。候選 0.4.34／41，尚未發布。

已改：Google 通知與底圖分區、增加矩形不重疊斷言、修正嵌入可行性過度判定。
已查：原廠 SystemUI 下拉音量透過 Remote.commad(4,0,[progress])；module 4 callback code 2 為 mVol、code 3 為 mute，詳見調查文件。
使用者最新更正：桌面及 Android 數值變化沒有實際聲音效果，原廠下拉選單有效。先前報告的系統降低聽感成功不能推定此路徑可用。

待辦：完成 Binder 協議及讀回診斷；找原廠導航宿主／橋接，確定可用後才加入內嵌／全螢幕選擇；Google 實際導航通知缺失須另外診斷。
ADB：使用者詢問一鍵預設與恢復，尚未做工具、無車機 ADB 連線；原廠 HOME 的完整 Activity 尚未讀回，不猜。

限制：不刷機、不 root、不猜私有協議、不改系統簽章、不替換既有 Release、不合併 main、不 force push；保留正式憑證 pin。
測試：本機政策檢查及 git diff --check；Gradle 因下載網路不可達未能執行，模擬器與實車仍待驗證。

## CI 回驗完成

已驗證的程式提交：e62ab30ffd4d14788f5f67dde588094b54ab622c；tree=ee11e48675616de267fa820ebcf579a31d7f1657，與原本機 082b839 的 tree 相同。
- Build APK：41215628735，success；單元測試、Debug APK、Release 稽核通過。
- UI：41215628727，success；Android 13 XML 結果 114 tests、0 failures、0 errors、0 skipped；Android 29 視窗測試 2 項通過。
- 已目視核對日間、夜間、行車三張 Google 導航提示截圖，提示文字完整，與測試底圖分區。測試使用假通知及底圖替身，不能取代原生地圖及真實通知的實車驗收。
- 尚未發布新版 Release；音量 Binder 及真正導航嵌入仍未整合。

此後僅同步驗證文件，不更動已測程式。
