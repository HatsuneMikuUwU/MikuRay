<div align="center">

# 🩵 MikuRay 🩵

**「みくみくにしてあげる♪」**  
*A super kawaii V2Ray client for Android, powered by Xray core & v2fly core~*  

*一款超可愛的 Android V2Ray 客戶端，支援 Xray core 與 v2fly core ✨*

<br>

[![Platform](https://img.shields.io/badge/android-platform?style=flat&label=platform&labelColor=39C5BB&color=FFFFFF)](https://www.android.com)
[![API](https://img.shields.io/badge/API-24%2B-39C5BB.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Releases](https://img.shields.io/github/v/release/HatsuneMikuUwU/MikuRay?color=39C5BB)](https://github.com/HatsuneMikuUwU/MikuRay/releases)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.4.0-39C5BB.svg)](https://kotlinlang.org)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-39C5BB.svg)](https://www.gnu.org/licenses/gpl-3.0)

<br>

[![Banner](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_banner.png)]()

<br>

**Connect to the internet with the power of the Virtual Diva herself!**  
*用初音未來的力量連上全世界吧～ ネギネギ♪*

</div>

---

## 🌸 Screenshots / 截圖預覽

A peek into MikuRay’s adorable themes~  
*來看看 MikuRay 的超可愛主題吧！*

<details>
  <summary><b>☀️ Light Theme / 淺色主題 (Click to view / 点击查看)</b></summary>

  <br>
  
![ScreenshotLight1](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_light_1.png)

![ScreenshotLight2](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_light_2.png)

![ScreenshotLight3](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_light_3.png)

![ScreenshotLight4](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_light_4.png)

![ScreenshotLight5](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_light_5.png)

</details>

<details>
  <summary><b>🌙 Night Theme / 深色主題 (Click to view / 点击查看)</b></summary>

  <br>

![ScreenshotNight1](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_night_1.png)

![ScreenshotNight2](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_night_2.png)

![ScreenshotNight3](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_night_3.png)

![ScreenshotNight4](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_night_4.png)

![ScreenshotNight5](https://raw.githubusercontent.com/HatsuneMikuUwU/MikuRay/master/image/uwu_screenshot_night_5.png)

</details>

---

## 🎤 Supported Cores / 支援的核心

MikuRay can sing with multiple powerful cores~  
*MikuRay 支援多個核心，讓你的連線更加流暢♪*

| Core | Repository |
| :--- | :--- |
| **🩵 Xray Core** | [XTLS/Xray-core](https://github.com/XTLS/Xray-core) |
| **🩵 v2fly Core** | [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core) |

---

## ネギ Usage / 使用說明

### Geoip & Geosite / 地理 IP 與站點數據

MikuRay uses geo data files for smart routing rules.  
*MikuRay 使用地理數據文件進行分流規則*

- `geoip.dat` and `geosite.dat` files are located in `Android/data/com.v2ray.ang/files/assets`  
  *(path may differ on some Android devices / 路徑可能因設備而異)*
- The **download** feature will fetch an enhanced version from [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat)

> [!NOTE]
> **Downloading requires a working proxy / 下載需要可用的代理**  
> Latest official [domain list](https://github.com/Loyalsoldier/v2ray-rules-dat) and [IP list](https://github.com/Loyalsoldier/geoip) can also be imported manually.  
> Third-party `.dat` files (e.g. [h2y](https://guide.v2fly.org/routing/sitedata.html)) can be placed in the same folder.

---

## 💻 Development Guide / 開發指南

The Android project under the `MikuRay` folder can be compiled directly in Android Studio or using the Gradle wrapper.  
*`MikuRay` 文件夾中的 Android 項目可以直接在 Android Studio 中編譯，或使用 Gradle wrapper。*

> [!NOTE]
> **The v2ray core inside the AAR may be outdated / AAR 中的 v2ray core 可能已過時**  
> The AAR can be recompiled from the Golang projects:
> - [2dust/AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite)
> - [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
>
> For a quick start, read the guide for [Go Mobile](https://github.com/golang/go/wiki/Mobile) and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/).

### Android Emulator & WSA

MikuRay can run on Android Emulators. For **WSA**, VPN permission must be granted manually:  
*MikuRay 可在 Android 模擬器上運行。對於 **WSA**，需手動授予 VPN 權限:*

```bash
appops set [package name] ACTIVATE_VPN allow
```

---

## 🌟 Statistics & Community / 統計 & 社群

Come join the Miku fans and share your connection status~  
*來跟初音粉絲一起聊天吧！*

| Downloads | Commit Activity | Telegram Channel |
| :---: | :---: | :---: |
| [![GitHub All Releases](https://img.shields.io/github/downloads/HatsuneMikuUwU/MikuRay/total?label=downloads-total&logo=github&style=flat-square&color=39C5BB)](https://github.com/HatsuneMikuUwU/MikuRay/releases) | [![GitHub commit activity](https://img.shields.io/github/commit-activity/m/HatsuneMikuUwU/MikuRay?style=flat&logo=Github&color=39C5BB)](https://github.com/HatsuneMikuUwU/MikuRay/commits/master) | [![Telegram](https://img.shields.io/badge/Chat%20on-Telegram-39C5BB?style=flat&logo=telegram&logoColor=white)](https://t.me/uwuowoumuchannel) |

---

## 📖 More Information / 更多資訊

For detailed configuration and usage instructions, please visit our **[Wiki](https://github.com/2dust/v2rayNG/wiki)**.  
*詳細的配置和使用說明，請訪問我們的 **[Wiki](https://github.com/2dust/v2rayNG/wiki)**。*

---

## 💖 Credits / 致謝

This project is built upon the great work of the following open-source communities.  
Thank you for making the internet a freer place — just like Miku makes the world more musical~  
*該項目建立在以下開源項目的出色工作之上，謝謝大家～*

**Application:**
- [v2rayNG](https://github.com/2dust/v2rayNG)

**Core:**
- [XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)

**Android Library:**
- [2dust/AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite)
- [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)

<br>

<div align="center">

**「Thank you for using MikuRay! ネギネギ～♪」**

🩵 Made with love for Hatsune Miku fans 🩵

</div>
