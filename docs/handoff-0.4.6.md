# 車機桌面 0.4.6 (13)

基底：0.4.5，遠端提交 1b97df7c42a9cea215f87913fb7962e24cf2a7ee。沿用套件與正式憑證。

新增：正式更新流程、附近門牌顯示、記住最後實際使用的播放器。請參閱 release-0.4.6.md。

更新權限新增 REQUEST_INSTALL_PACKAGES，僅用於使用者點擊確認安裝，不自動開權限、不靜默安裝。FileProvider 僅暴露 app 私有 updates 子目錄，exported=false，逐一 URI 授權。僅讀指定公開 GitHub repo 的正式 latest release 與 update.json；每日前景檢查一次，不上傳使用資訊或位置。APK 限制120MiB，大小與SHA256驗證，套件精確符合、versionCode升級、憑證SHA256精確符合既有正式憑證才可安裝。

正式發佈：手動執行 Prepare signed update release；缺原本的簽章 Secrets 就失敗，不回退 Debug 金鑰。完成產物稽核後準備草稿 Release，人工檢查並 Publish 後，車機才會收到更新。不要把 Debug 或 unsigned APK 當成可自動更新正式版。

目前限制：既有 GitHub Secrets 缺少，之前交付的是 Debug。程式無法透過不同憑證無損覆蓋到正式版；不能保證現有測試APK可直接安裝這版。需同簽章初始正式安裝，之後才可走正式更新。不得為繞過限制自動卸載／清資料。正式簽章下載與實機安裝尚待驗證。

音樂：FOLLOW_CURRENT 記住最後真正播放中的精確套件，優先恢復其仍存在的 session；FIXED仍遵守明確指定來源。不存在的session不冒充已連接，不送廣播猜測私有命令，不背景強開播放器或保證自動續播。無session時提供一鍵開啟上次播放器。

門牌：使用Geocoder的完整中文addressLine或subThoroughfare；畫面標示附近地址。GPS及地址服務無法保證精準門牌，不推算國道門牌。

未解：原廠方向盤實體鍵整合、車燈背光連動、國道精準道路比對、分割捷徑仍需無障礙服務授權與車機實測。


歌詞：新增獨立 LRCLIB 查詢（官方API文件 https://lrclib.net/docs），預設關閉；設定→App綁定→桌面歌詞，使用者同意傳送歌名／歌手／專輯／時長後才啟用。不讀取KKBOX/YT內部歌詞、不讀畫面、不新增通知或無障礙權限。精確歌名歌手與±2秒時長比對；HTTP429尊重Retry-After；記憶體最多30首結果、不寫檔、不上傳歌詞。查不到／不同版本／斷網時顯示狀態並保留曲目。有LRC時間碼及MediaSession進度才逐行顯示，暫停及拖曳沿用真實播放位置，否則顯示靜態文字。功能預設不連外，實際歌曲收錄、中文命中率與對時仍需實機驗證。公開API不等於授權保證，若要公開商用發行需另確認歌詞供應授權。
