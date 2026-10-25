

# Mixy

> A simple Android app for browsing and playing local media: music, images, videos, and PDFs.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

Mixy is a local-first media browser. No ads, no accounts, no tracking, no network permission.

[中文说明](#中文说明)

---

## Features

| Type | Capabilities |
|---|---|
| **Music** | Playback service with notification controls, embedded cover art, `.lrc` sidecar lyrics with auto-scroll and seek |
| **Images** | Pinch-to-zoom, swipe between images, double-tap to zoom, GIF / animated WebP support |
| **Videos** | Gesture seek (horizontal swipe), speed control (0.5x–2x), loop modes, picture-in-picture, auto-orientation |
| **PDF** | Vertical scroll or horizontal paging, pinch-to-zoom, page indicator |

**Library**
- Folder browsing with thumbnails
- Search across all media
- Sort by name / date / size / duration, ascending or descending
- List or grid view, configurable column count (landscape auto +1)
- Exclude folders you don't want to see
- Open files from other apps via "Open with" / "Share"
- Dark theme (follows system or manual)

## Tech Stack

- **Kotlin 2.0** + **Jetpack Compose** (Material 3)
- **Media3** 1.5 (ExoPlayer + MediaSessionService)
- **Coil 3** for image loading, with custom fetchers for audio cover / video thumbnail / PDF first page
- **DataStore** for preferences
- **Coroutines + Flow** for async and state
- **MVVM** with a lightweight hand-written DI container (`AppContainer`)

No Hilt, no Room, no network layer.

## Requirements

- Android 8.0 (API 26) or higher
- JDK 21
- Android SDK 36

## Build

```bash
./gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

Release 构建：

```bash
./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/grillpoet/mixy/
├── data/          MediaStore query, PDF scanner, settings, lyrics
├── cover/         Coil fetchers for cover art
├── pdf/           PDF page renderer
├── player/        ExoPlayer wrappers, playback service, session
└── ui/
    ├── folder/    Folder list and content screens
    ├── music/     Music player
    ├── image/     Image viewer
    ├── video/     Video player
    ├── pdf/       PDF viewer
    ├── settings/  Settings and excluded paths
    └── common/    Shared UI components
```

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Read media from device |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ partial photo access |
| `POST_NOTIFICATIONS` | Playback controls in notification |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep music playing in background |

PDF access uses **Storage Access Framework** — you choose a folder once, no storage permission required.

## Privacy

Mixy does not collect any data. No analytics, no crash reporting, no network requests. Everything runs on-device.

## License

MIT — see [LICENSE](LICENSE).

---

<a id="中文说明"></a>

## 中文说明

Mixy 是一个本地媒体浏览器。无广告、无账号、无追踪、无网络权限。

### 功能

| 类型 | 能力 |
|---|---|
| **音乐** | 后台播放（通知栏控制）、内嵌封面、`.lrc` 外挂歌词（自动滚动 + 拖动跳转） |
| **图片** | 双指缩放、左右滑动切换、双击缩放、支持 GIF / 动态 WebP |
| **视频** | 手势快进快退、倍速（0.5x–2x）、循环模式、画中画、自动方向 |
| **PDF** | 纵向滚动或横向翻页、双指缩放、页码指示 |

**媒体库**
- 按文件夹浏览，带封面缩略图
- 全局搜索
- 按名称 / 日期 / 大小 / 时长排序，支持升降序
- 列表或网格视图，可调列数（横屏自动 +1）
- 屏蔽不想看到的文件夹
- 支持从其他 App 通过「打开方式 / 分享」进入
- 深色主题（跟随系统或手动切换）

### 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3）
- Media3 1.5（ExoPlayer + MediaSessionService）
- Coil 3 图片加载，音频封面 / 视频缩略图 / PDF 首页各自自定义 Fetcher
- DataStore 存储设置
- Coroutines + Flow 异步与状态
- MVVM + 手写轻量依赖容器（`AppContainer`）

无 Hilt、无 Room、无网络层。

### 环境要求

- Android 8.0（API 26）及以上
- JDK 21
- Android SDK 36

### 构建

```bash
./gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

### 权限

| 权限 | 用途 |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | 读取设备媒体 |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ 部分照片授权 |
| `POST_NOTIFICATIONS` | 通知栏播放控制 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 后台播放 |

PDF 通过 **Storage Access Framework** 访问，选择一个目录后无需存储权限。

### 隐私

Mixy 不收集任何数据。无统计、无崩溃上报、无网络请求。所有逻辑在本地运行。

### 许可证

MIT — 见 [LICENSE](LICENSE)。