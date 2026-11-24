# Android 平板版「影片合併器」

**Package：** `com.videomerger.tablet`  
**顯示名稱：** 影片合併器  
**版本：** 1.0.0（versionCode 1）  
**最低 Android：** 12 / API 31  
**compileSdk / targetSdk：** 37  
**語言／介面：** Kotlin、Jetpack Compose Material 3、繁體中文  
**執行時網路：** 不需要；Manifest 未宣告 `INTERNET`。


## 專案內容

- `app/src/main`：Android Manifest、Compose UI、SAF、MediaStore、MediaExtractor／MediaMuxer 引擎、Foreground Service、設定及資料模型。
- `app/src/test`：影片檔名解析器的 JUnit 測試。
- `gradle/wrapper/gradle-wrapper.properties`：指定 Gradle 9.6.0。
- `gradlew`、`gradlew.bat`：本交付環境的啟動檢查腳本。**這兩個是要求本機已安裝 Gradle 的 bootstrap 腳本，不是官方 Gradle Wrapper 啟動器**；因環境無法下載 Wrapper JAR，本 ZIP 未包含 `gradle-wrapper.jar`。
- `BUILD_REPORT.md`：本次實際可執行檢查及未執行測試清單。

## 主要技術設計

- 來源以 `ACTION_OPEN_DOCUMENT_TREE` / SAF 持續讀取授權；每日分析只掃描第一層。
- 自訂合併用 `ACTION_OPEN_DOCUMENT` 多選影片，保留系統回傳順序，另提供清單排序控制。
- 只檢查必要的影片／音訊軌道，不解碼、不重新編碼。
- 合併引擎使用 `MediaExtractor` + `MediaMuxer`，以串流樣本方式寫入；輸出直接建立於 MediaStore `Movies/VideoMerger/`，先設 `IS_PENDING=1`，完成後才發布。
- 每個輸出名稱均先查詢既有名稱並增加序號，避免覆寫。
- 中斷或寫入錯誤時不發布半成品；MediaStore pending 項目及本機小型索引供下次開啟後辨識為疑似未完成。
- Android 15 以上採 `mediaProcessing` FGS；Android 14 採 `specialUse` subtype fallback；舊版使用當時可用的前景服務方式。服務通知提供停止動作。

**重要限制：** MediaMuxer 不提供回滾已寫入樣本的機制。引擎因此將來源在首次寫入前的錯誤列為可略過；一旦可能已有樣本寫入，則整個輸出會失敗並保留為疑似未完成，不會宣稱成功。不同片段的實際 codec 設定、B-frame 時間戳順序、音訊延遲及裝置 MediaMuxer 行為仍需以實際相機檔案測試；目前沒有使用者提供的 Luna Ultra MP4 素材，不能宣稱已完成相機素材驗證。

## 影片合併規則

本程式採用直接串流複製（Stream Copy）方式合併 MP4，不進行影片解碼、重新編碼或畫質轉換。

### 支援格式

- 不限定固定解析度，可合併 1080p、2K、4K 等不同解析度的影片。
- 不限定固定 FPS，可合併 30 FPS、60 FPS 等不同影格率的影片。
- 但同一批待合併影片必須具有相容的影片與音訊格式。
- 不同解析度、影格率或影音編碼設定的影片，不可混合合併。
- 若來源影片缺少必要的格式資訊，導致無法確認相容性，也可能拒絕合併。

### 格式檢查

每次合併前，程式會檢查該次合併工作中來源影片的格式資訊。

若同一批影片存在不相容的格式，將拒絕該次合併工作，並顯示：

`影片來源存在多種格式，無法輸出`

程式不會自動拆分影片、挑選部分影片輸出，也不會透過重新編碼來解決格式差異。

### 音訊處理

- 沒有音訊軌道的影片依既有規則略過。
- 有音訊軌道的影片才會納入合併。
- 納入合併的影片必須符合相同的影音格式相容性要求。

## 建置環境

建議先安裝：

1. Android Studio 具備 Android 17 / API 37 SDK 支援的版本。
2. JDK 17 或更新版本（本次執行環境的 Java 為 21）。
3. Android SDK Platform API 37，以及 Android SDK Build-Tools（由 Android Studio SDK Manager 安裝）。
4. Gradle 9.6.0（首次產生官方 Wrapper 時需要本機 Gradle）。
5. 可連線下載 Gradle Plugin、Kotlin Plugin 與 Maven dependencies 的網路。**App 執行時不需要網路，但首次建置需要取得建置工具與依賴套件。**

目前設定版本：

| 元件 | 版本 |
|---|---:|
| Android Gradle Plugin | 9.3.1 |
| Gradle | 9.6.0 |
| Kotlin / Compose Compiler Plugin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| Compose Material 3 | 1.5.0 |
| compileSdk / targetSdk | 37 |
| minSdk | 31 |

AGP 9 預設內建 Kotlin；本專案為使用獨立 Kotlin 2.4.21 plugin，設定 `android.builtInKotlin=false`。這是 AGP 9 的相容切換設定，未來升級至 AGP 10 前需再遷移。

API 37 在本文件日期的 Android SDK 文件中仍列為 Preview 平台。若需以 API 37 編譯，請使用支援該 SDK 的 Android Studio，並透過 SDK Manager 安裝相應平台；不要把 API 37 Preview 誤認為已正式穩定發布的 Android 平台。

## Windows 建置步驟

### 1. 產生真正的 Gradle Wrapper

在已安裝 Gradle 9.6.0 並可連線下載 Gradle Plugin 的 PowerShell 中，進入專案根目錄：

```powershell
gradle wrapper --gradle-version 9.6.0 --distribution-type bin
```

此命令應產生 `gradlew`、`gradlew.bat` 和 `gradle/wrapper/gradle-wrapper.jar`。請確認 `gradle-wrapper.jar` 已出現；本 ZIP 附的 bootstrap 腳本不等同官方 Wrapper。

### 2. 設定 Release 簽章

本次已另行建立本機 Release keystore：`VideoMerger-release.jks`。請把它保存到安全且長期不會移動的位置，不要上傳 GitHub，也不要放進專案 ZIP。

建議將以下屬性放在使用者層級的 `%USERPROFILE%\.gradle\gradle.properties`（不是公開專案檔）。將路徑改成 keystore 真正存放位置，密碼請使用隨附的簽章資訊檔：

```properties
RELEASE_STORE_FILE=C:/Users/你的帳號/secure/VideoMerger-release.jks
RELEASE_STORE_PASSWORD=請填入隨附密碼
RELEASE_KEY_ALIAS=videomerger-release
RELEASE_KEY_PASSWORD=請填入隨附密碼
```

若沒有設定這四個屬性，`release` variant 將不會套用本機簽章設定。請妥善備份 `.jks` 及密碼；未來更新同一 Package Name 的 App 必須使用同一把簽章金鑰，遺失後不能用新金鑰直接覆蓋更新既有安裝。

### 3. 編譯與測試

```powershell
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

成功後 APK 預期路徑：

- Debug：`app\build\outputs\apk\debug\app-debug.apk`
- Release：`app\build\outputs\apk\release\app-release.apk`

完成後可將 Release APK 改名為 `VideoMerger-release.apk`。**此路徑僅是成功建置後的預期輸出位置；本次沒有產生該 APK。**

### 4. Android Studio 開啟

在產生真正 Wrapper 後，以 Android Studio 選擇 `File > Open`，開啟專案根目錄 `VideoMerger-Android`，待 Gradle Sync 完成後再執行測試與 Build。首次 Sync 需要下載依賴套件。

## 真機驗證提醒

- 請先用至少兩段短小、同解析度、同來源設備的 4K MP4 測試，確認每軌時間戳、音訊同步及輸出可播放。
- 再測試大檔案、長時間影片、記憶卡拔除、空間不足、停止工作、程序被終止及重新啟動後 pending 輸出辨識。
- Android 15+ `mediaProcessing` 前景服務受系統時間額度約束，需遵守 `Service.onTimeout()` 終止流程；前景服務不保證程序永不被終止。
- 使用不同 codec、profile、frame rate、音訊格式或時間戳特性的來源片段時，無法保證原生封裝器可無條件串接。本專案不會自動轉碼以掩蓋不相容。

## 安全與簽章

- `VideoMerger-release.jks` 與密碼檔為私有簽章材料，**未包含在 Android Studio 專案 ZIP**。
- 請保留至少兩份離線備份，勿提交至公開版控。
- App 執行時不要求網路權限；下載 Gradle、Android Plugin 和 dependencies 是建置階段的網路行為，兩者不同。
