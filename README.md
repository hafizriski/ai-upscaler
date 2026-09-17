# AI Upscaler Offline for Android

Aplikasi upscaler AI offline menggunakan TensorFlow Lite + Real-ESRGAN.

## Build

Build APK otomatis via GitHub Actions:
1. Push ke branch `main`
2. Buka tab **Actions**
3. Download APK dari **Artifacts**

## Struktur

- `app/src/main/java/` — Source code Kotlin
- `app/src/main/assets/models/` — Taruh model `.tflite` di sini
- `.github/workflows/build.yml` — Konfigurasi build otomatis

## Cara Tambah Model AI

Letakkan file `.tflite` di folder `app/src/main/assets/models/` lalu push.

## Lisensi

MIT
