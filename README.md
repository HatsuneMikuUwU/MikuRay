# MikuRay

A V2Ray client for Android, support Xray core and v2fly core.  
*一款支援 Xray core 與 v2fly core 的 Android V2Ray 客戶端*

[![Platform](https://img.shields.io/badge/android-platform?style=flat&label=platform&labelColor=21262d&color=6e7681)](https://www.android.com) [![API](https://img.shields.io/badge/API-24%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop) [![Releases](https://img.shields.io/github/v/release/HatsuneMikuUwU/MikuRay)](https://github.com/HatsuneMikuUwU/MikuRay/releases)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.3.21-blue.svg)](https://kotlinlang.org) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0) 

---

## Supported Cores / 支援的核心

MikuRay supports multiple cores to power your proxy connections.  
*MikuRay 支援多個核心以驅動您的代理連接*

| Core | Repository |
| :--- | :--- |
| **Xray Core** | [XTLS/Xray-core](https://github.com/XTLS/Xray-core) |
| **v2fly Core** | [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core) |

---

## Usage / 使用說明

### Geoip and Geosite / 地理 IP 與站點數據

MikuRay uses geo data files for routing rules.  
*MikuRay 使用地理數據文件進行分流規則*

- `geoip.dat` and `geosite.dat` files are located in `Android/data/com.v2ray.ang/files/assets`  
  *(path may differ on some Android devices / 路徑可能因設備而異)*
- The **download** feature will fetch an enhanced version from [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat)

> [!NOTE]
> **Downloading requires a working proxy / 下載需要可用的代理**  
> Latest official [domain list](https://github.com/Loyalsoldier/v2ray-rules-dat) and [IP list](https://github.com/Loyalsoldier/geoip) can also be imported manually.  
> Third-party `.dat` files (e.g. [h2y](https://guide.v2fly.org/routing/sitedata.html)) can be placed in the same folder.

---

## Development Guide / 開發指南

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

## Statistics & Community / 統計 & 社群

| Downloads | Commit Activity | Telegram Channel |
| :---: | :---: | :---: |
| [![GitHub All Releases](https://img.shields.io/github/downloads/HatsuneMikuUwU/MikuRay/total?label=downloads-total&logo=github&style=flat-square)](https://github.com/HatsuneMikuUwU/MikuRay/releases) | [![GitHub commit activity](https://img.shields.io/github/commit-activity/m/HatsuneMikuUwU/MikuRay?style=flat&logo=Github)](https://github.com/HatsuneMikuUwU/MikuRay/commits/master) | [![Telegram](https://img.shields.io/badge/Chat%20on-Telegram-2CA5E0?style=flat&logo=telegram&logoColor=white)](https://t.me/uwuowoumuchannel) |

---

## More Information / 更多資訊

For detailed configuration and usage instructions, please visit our **[Wiki](https://github.com/2dust/v2rayNG/wiki)**.  
*詳細的配置和使用說明，請訪問我們的 **[Wiki](https://github.com/2dust/v2rayNG/wiki)**。*

---

## Credits / 致謝

This project is built upon the great work of the following open-source communities:  
*該項目建立在以下開源項目的出色工作之上:*

**Application:**
- [v2rayNG](https://github.com/2dust/v2rayNG)

**Core:**
- [XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)

**Android Library:**
- [2dust/AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite)
- [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
