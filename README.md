# AirPlay & DLNA Receiver for Android TV / Google TV

> **100% Free, Open-Source & Ad-Free AirPlay + DLNA/UPnP Cast Receiver for Android TV**  
> *Forked and customized for clean, true borderless full-screen mirroring with pitch-black margins.*

---

## English

### Overview
A clean, lightweight, open-source Apple AirPlay screen mirroring receiver built specifically for Android TV, Google TV, and TV boxes. Unlike commercial alternatives (e.g. AirScreen, CastReceiver) that suffer from subscription paywalls, timeouts, and regional ad-blocking interruptions, this receiver runs **100% locally on your LAN without internet access, tracking, or ads**.

### Key Features
- **AirPlay Mirroring**: Full-screen mirroring from Mac, iPhone, and iPad. Zero decorative borders, moulding frames, or background wallpaper leakage. Letterboxing margins are rendered in pitch-black (`#000000`), perfectly matching OLED/LED TV bezels.
- **DLNA / UPnP Media Cast (new in 1.2)**: Built-in MediaRenderer — cast any video/music/photo from Android players (KMPlayer, VLC, BubbleUPnP, nPlayer, etc.) straight to the TV. Powered by libVLC, so **AVI/XviD/DivX, MKV and other formats the stock cast receiver chokes on play fine**.
- **Hardware-Accelerated AirPlay Decoding**: Video stream (H.264) decoded natively via Android `MediaCodec` direct to `SurfaceView`.
- **Synchronized Audio**: Hardware-accelerated AAC-ELD audio decryption and low-latency playback via `AudioTrack`.
- **Zero Configuration**: AirPlay via mDNS (`_airplay._tcp:7000` / `_raop._tcp:5000`), DLNA via SSDP/UPnP. Instantly discoverable in macOS Control Center, iOS Screen Mirroring and any DLNA control point on the network.
- **TV Remote Friendly**: Supports standard Android TV remote D-Pad controls: OK pauses, ←/→ seek ±30s, ↑/↓ volume, long-OK/menu opens app info. Controls auto-hide during playback.
- **Companion to [OpenDisplay](https://github.com/alexqem/OpenDisplay)**: Pairs with OpenDisplay on Apple Silicon Mac for 1-click HiDPI / Ultra Sharp (1440p / 4K) stream scaling.

### Installation
Download the pre-built `airplay-tv-receiver.apk` from [Releases](https://github.com/alexqem/airplay-tv-receiver/releases) and install via ADB:
```bash
adb install -r airplay-tv-receiver.apk
```
Or transfer to your TV via flash drive / file manager and open it.

---

## Русский

### Описание
Быстрый, полностью бесплатный и открытый AirPlay-ресивер для Android TV и Google TV. В отличие от коммерческих приложений (AirScreen, CastReceiver и др.), которые прерывают просмотр рекламой, блокируются из-за сетевых ограничений или требуют платную подписку (VIP), этот ресивер работает **исключительно внутри домашней локальной сети без интернета, рекламы и ограничений по времени**.

### Основные возможности
- **AirPlay-зеркалирование**: Полноэкранный повтор экрана с Mac, iPhone и iPad. Убраны все декоративные рамки, градиенты и фоновые обои. Поля при несовпадении пропорций (letterbox) — идеально черные (`#000000`), без засветов при просмотре фильмов.
- **DLNA / UPnP приёмник (новое в 1.2)**: Встроенный MediaRenderer — кастуйте любое видео/музыку/фото с Android-планшетов и телефонов (KMPlayer, VLC, BubbleUPnP, nPlayer и др.) прямо на ТВ. Движок libVLC «переваривает» **AVI/XviD/DivX, MKV и другие форматы, которые встроенный Chromecast-приёмник не берёт** (в нём будет только звук).
- **Аппаратное ускорение AirPlay (H.264)**: Поток видео декодируется нативным чипом телевизора через `MediaCodec` на `SurfaceView` с минимальным нагревом и нагрузкой на CPU.
- **Синхронный звук**: Декодирование аудиопотока AAC-ELD без рассинхрона через `AudioTrack`.
- **Мгновенное обнаружение**: AirPlay анонсируется по Bonjour mDNS (`_airplay._tcp:7000`, `_raop._tcp:5000`), DLNA — по SSDP/UPnP. Появляется в меню «Повтор экрана» на Mac/iOS и в списке устройств любого DLNA-плеера.
- **Управление пультом**: Полноценная поддержка пульта Android TV (D-Pad): ОК — пауза, ←/→ — перемотка ±30с, ↑/↓ — громкость, долгое ОК/меню — «О приложении». Интерфейс автоматически скрывается при начале трансляции.
- **Идеально работает с [OpenDisplay](https://github.com/alexqem/OpenDisplay)**: Поддерживает режим Ultra Sharp (1440p / 4K) и переключение HiDPI в 1 клик с Mac.

### Установка
Скачайте готовый `airplay-tv-receiver.apk` со страницы [Releases](https://github.com/alexqem/airplay-tv-receiver/releases) и установите через ADB:
```bash
adb install -r airplay-tv-receiver.apk
```
Либо установите APK с флешки через любой файловый менеджер на ТВ.

---

### Keywords / Ключевые слова
`airplay`, `dlna`, `upnp`, `chromecast-alternative`, `android-tv`, `google-tv`, `screen-mirroring`, `airplay-receiver`, `dlna-renderer`, `avi-xvid`, `libvlc`, `media-codec`, `h264`, `opendisplay`, `ad-free`, `open-source`, `аирплей`, `длна`, `трансляция-экрана`, `повтор-экрана`, `андроид-тв`, `каст-avi`.

### License
GPL-3.0 License. Based on [GodMingFeng/airplay-android](https://github.com/GodMingFeng/airplay-android) & [serezhka/java-airplay-lib](https://github.com/serezhka/java-airplay-lib).
