# 建置與測試紀錄

日期：2026-11-24  
專案：Android 平板版「影片合併器」  
Package：`com.videomerger.tablet`

## 結果摘要

| 項目 | 結果 | 說明 |
|---|---|---|
| Android 專案原始碼 | 已建立 | Kotlin / Compose 專案結構及核心檔案已輸出 |
| Manifest / 資源 XML 解析 | 通過 | 以 Python XML parser 解析 4 個 XML 檔 |
| 無 INTERNET 權限 | 通過 | Manifest 未宣告 `android.permission.INTERNET` |
| 無廣泛儲存權限 | 通過 | 未宣告 `MANAGE_EXTERNAL_STORAGE` 等廣泛存取權限 |
| 未強制固定方向 | 通過 | Activity 未設定 `screenOrientation` |
| 檔名解析器自測 | 通過 | 一般／舊版格式、大小寫、嚴格日期時間、錯誤副檔名及排序案例 |
| JUnit / Gradle test | 未執行 | 缺少 Gradle 及 Android SDK |
| Gradle Sync | 未執行 | 缺少 Gradle／Android SDK，且無法下載工具鏈 |
| Kotlin Android 編譯 | 未執行 | 無 Android classpath / SDK |
| Android Lint | 未執行 | 無 Android Gradle Plugin runtime |
| Debug APK | 未產生 | 無可用 Android 建置工具鏈 |
| Release APK | 未產生 | 無可用 Android 建置工具鏈 |
| APK 安裝／啟動 | 未測試 | 無 adb、模擬器或連接裝置 |
| MediaExtractor／MediaMuxer 真實影片串接 | 未測試 | 未提供實際影片素材，也無 Android 裝置 |
| Luna Ultra 原始 MP4 | 尚待裝置實測驗證 | 本次沒有實際相機 MP4 素材 |

## 本次環境限制

本次容器可用工具：

- OpenJDK 21.0.12
- Kotlin CLI 1.9.0
- `keytool`

缺少：

- Android SDK / `android.jar`
- `sdkmanager`
- Android Studio
- 系統 Gradle
- `gradle-wrapper.jar`
- `adb` / Android Emulator

本環境亦無法連線至 Android／Gradle 發佈伺服器下載缺少的工具。因此無法執行 `clean`、`test`、`lint`、`assembleDebug`、`assembleRelease`。沒有建立假 APK，也沒有把原始碼檔案改名成 APK。

## 已執行的檢查細節

### 純 Kotlin 檔名解析器自測

使用本機 Kotlin CLI 對專案中的 `ParsedVideoName.kt` 與 `VideoNameParser.kt` 編譯，配合臨時測試程式執行以下案例：

- `VID_20261124_083000_017.mp4` 一般格式。
- `VID_20261124_084000_00_002.MP4` 舊版格式及大小寫容錯。
- 非法日期（例如 2026-02-30）。
- 不合法舊版標記（非 `00`）。
- 非 MP4 副檔名。
- 日期時間及序號排序。

輸出：`PASS: standard / legacy filename, case tolerance, strict date-time, invalid-name, and ordering checks`。

這是獨立 parser 自測，不代表 Android app 已編譯或 JUnit 任務已通過。

### XML 與權限靜態檢查

Python XML parser 成功解析 Manifest 與資源 XML 共 4 個檔案，並用字串／屬性斷言確認：

- 沒有 `INTERNET`。
- 沒有 `MANAGE_EXTERNAL_STORAGE`。
- 沒有固定 `screenOrientation`。
- 有宣告本專案使用的前景服務權限。

這不是 Android Lint 或 APK Manifest merger 檢查。

## 尚待完成的驗證

1. 在符合 API 37 的 Android Studio / SDK 環境中完成 Gradle Sync、JUnit、Lint、Debug 與 Release build。
2. 確認 Compose API、Kotlin / AndroidX 依賴版本及 Android API 37 Manifest 於實際工具鏈可用。
3. 確認 Android 14 `specialUse` 及 Android 15+ `mediaProcessing` 前景服務在目標裝置與發佈政策下適用。
4. 在裝置上驗證 SAF 外接記憶卡授權、重新插拔及大量目錄列舉。
5. 使用實際 MP4 驗證 MediaExtractor sample order、MediaMuxer track format、B-frame PTS、音訊時間戳、片段邊界、旋轉資訊及輸出播放相容性。
6. 驗證儲存空間耗盡、手動停止、程序被終止、`IS_PENDING` 殘留及冷啟動清單管理。
7. 使用至少一組實際 Luna Ultra 原始影片完成相機素材驗證。

## Release 簽章材料

本次另外建立 `VideoMerger-release.jks`，alias 為 `videomerger-release`，並驗證 keystore 可由 `keytool -list` 正常讀取。它尚未被用來簽署 APK，因為本次無法建置 APK。Keystore 和密碼檔另行交付，沒有放入專案 ZIP。
