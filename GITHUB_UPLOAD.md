# GitHub 私人倉庫上傳檢查

1. 先解壓縮上傳包，不要把 ZIP 本身直接丟進倉庫。
2. 將解壓後的「內容」放在 GitHub 倉庫根目錄；根目錄應直接看見 `app`、`gradle`、`gradlew`、`settings.gradle.kts`。
3. 確認倉庫中存在 `.github/workflows/build-apk.yml`。若網頁上傳漏掉隱藏資料夾，依根目錄的 `build-apk.github-workflow.yml` 重建該檔案。
4. 到 GitHub 的 Actions 頁面執行 `Build APK`，或推送一次變更讓它自動執行。
5. 工作流程會先跑 `testDebugUnitTest`，成功後才執行 `assembleDebug`；完成後下載 Artifact `fit-launcher-debug-apk`。

> 此專案目前產出的是個人側載用 debug APK。安裝後先從原廠桌面開啟測試，不要立刻設成預設桌面；倒車影像、Home 鍵、休眠喚醒與方向盤按鍵都確認正常後，再切換預設首頁。
