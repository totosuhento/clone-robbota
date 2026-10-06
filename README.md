# Asisten Marketplace (RobottaClone)

Aplikasi Android (Kotlin + Jetpack Compose) yang membantu seller memasang produk ke
**Facebook Marketplace langsung dari HP**. Asisten mengisi form *Jual Barang* di aplikasi
Facebook secara otomatis lewat AccessibilityService, lalu **berhenti** supaya kamu
memeriksa dan menekan **Publikasikan** sendiri. Setelah itu asisten mencatat produk sebagai
terposting dan menyiapkan produk berikutnya saat kamu menekan "Produk berikutnya".

## Fitur (v2 — tampilan mengikuti video Robotta)

Menu samping dikelompokkan seperti Robotta:

| Menu | Isi |
|---|---|
| **Dashboard** | Ringkasan status (Menunggu / Terposting / Gagal), status asisten ON/OFF, aksi cepat, produk terakhir terposting |
| **Kelola Akun** | Nama toko, nomor WA, penutup deskripsi, lokasi default |
| **BOT FB MARKETPLACE → Auto Posting** | Mulai sesi, progres "Produk 3/10…", log aktivitas |
| **Riset Kata Kunci** | Saran pencarian Google Indonesia (kata dasar + a–z) + ide AI; pilih lalu salin sebagai daftar atau hashtag |
| **Riset Lokasi** | 514 kabupaten/kota se-Indonesia, filter per provinsi, jadikan lokasi default |
| **DATA POSTING → Data Posting** | Satu data = satu produk: foto, judul, harga, kategori, kondisi, deskripsi, hashtag, lokasi; import CSV |
| **Auto Frame** | Bingkai/watermark/label harga untuk foto, simpan ke galeri `Pictures/AutoFrame` |
| **BANTUAN → Tutorial / Pengaturan** | Langkah pemakaian; jeda langkah, batas per sesi, panel melayang, API key Gemini |

Lainnya:

- **Buat Konten AI** (tombol merah di form produk): satu klik menghasilkan 3 pilihan judul, deskripsi,
  hashtag, dan saran kategori untuk produk itu (Gemini; tanpa API key memakai template offline).
- **Panel melayang** di atas aplikasi Facebook selama sesi: status, progres, dan tombol
  *Sudah terbit / Lewati / Berikutnya / Stop*. Bisa digeser dan diperkecil jadi gelembung.
  Tidak butuh izin "tampil di atas aplikasi lain".
- Asisten mendeteksi saat kamu menekan **Publikasikan** dan menandai produk sebagai terposting.
- Notifikasi dengan tombol aksi yang sama.

### Sengaja berbeda dari PRD

Bagian berikut dari PRD **tidak** dibuat, karena fungsinya untuk menghindari deteksi spam
Facebook dan menjadi penyebab utama akun kena batas atau diblokir:

- posting massal tanpa pengawasan (1000+ produk) dan menekan Publikasikan otomatis,
- rotasi banyak akun Facebook,
- jeda acak "biar tidak kelihatan bot",
- "1 foto jadi 1000 varian" (bingkai di sini mengganti foto, bukan memperbanyaknya),
- 10 variasi judul + font Unicode + harga acak untuk memasang produk yang sama berulang kali
  ("anti duplikat"), lokasi acak ke banyak kota, login multi-akun lewat cookies, dan share otomatis ke grup.

Sebagai gantinya: satu akun (yang login di aplikasi Facebook), batas produk per sesi
(default 10, maks 30), dan setiap produk dipublikasikan oleh kamu sendiri.

## Cara mendapatkan APK

### Opsi A — tanpa PC, lewat GitHub Actions (gratis)
1. Buat akun & repository baru di github.com (bisa dari browser HP).
2. Upload seluruh isi folder proyek ini ke repository tersebut (pastikan folder `.github` ikut).
3. Buka tab **Actions** → **Build APK** → **Run workflow**.
4. Tunggu ±5–8 menit, buka hasil run, unduh **asisten-marketplace-debug** di bagian *Artifacts*.
5. Ekstrak zip-nya, install `app-debug.apk` di HP (izinkan "Instal dari sumber tidak dikenal").

### Opsi B — Android Studio di PC
1. Buka folder `RobottaClone` di Android Studio (Ladybug 2024.2 atau lebih baru, JDK 17).
2. Tunggu Gradle sync selesai.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. APK ada di `app/build/outputs/apk/debug/app-debug.apk`. Install: `adb install app-debug.apk`.

## Cara pakai

1. **Aktifkan aksesibilitas**: Pengaturan HP → Aksesibilitas → *Aplikasi terinstal* →
   **Asisten Marketplace – Isi Form Otomatis** → Aktifkan.
   Android 13+: jika tombolnya abu-abu, buka **Info Aplikasi → ⋮ (kanan atas) → Izinkan setelan terbatas**, lalu ulangi.
2. Izinkan **notifikasi** saat diminta.
3. Isi **Kelola Akun** (nama toko, WA) dan pilih lokasi lewat **Riset Lokasi**.
4. **Data Posting** → tambah produk (atau import CSV), tambahkan **foto**, tekan **Buat Konten AI**.
5. **Auto Posting** → **Mulai sesi**. Jangan sentuh layar sementara asisten mengisi.
6. Saat panel melayang menunjukkan "Siap": periksa isian di Facebook, lengkapi yang
   ditandai, lalu tekan **Publikasikan** (dan *Berikutnya* jika Facebook memintanya).
7. Tekan **Berikutnya** di panel melayang, notifikasi, atau aplikasi.

Pastikan aplikasi Facebook sudah login, dan bahasa Facebook Indonesia atau Inggris.

## Jika suatu langkah selalu gagal

Facebook sering mengubah tampilan. Semua teks tombol/kolom yang dicari ada di satu file:
`app/src/main/java/com/robotta/automation/FbLabels.kt`.
Buka form Jual Barang di HP, catat teks yang tampil (mis. "Judul", "Harga", "Tambahkan foto"),
tambahkan ke daftar yang sesuai, lalu build ulang.

Tips lain:
- Naikkan **Jeda antar langkah** di Pengaturan untuk HP yang lambat.
- Matikan **Pilih foto otomatis** jika asisten salah memilih foto; kamu cukup memilih foto
  teratas di galeri (folder `Pictures/MarketAsisten`) dan asisten melanjutkan sendiri.
- Kategori harus ditulis sama dengan yang ada di daftar kategori Facebook di HP-mu.
- Lihat log detail: `adb logcat -s MarketAutomation AutomationEngine`.

## Struktur kode

```
app/src/main/java/com/robotta/
├── MainActivity.kt, RobottaApp.kt
├── ui/            Navigation (menu samping), DashboardScreen, MainScreen (Auto Posting),
│                  ProductListScreen + AddProductScreen (Data Posting), AccountScreen,
│                  KeywordScreen, LocationScreen, AutoFrameScreen, TutorialScreen,
│                  AutomationScreen (pengaturan), AppViewModel, Components, theme/
├── automation/    MarketAutomationService (AccessibilityService), FloatingPanel, AutomationEngine,
│                  ActionStep (+status & log), NodeFinder, FbLabels, EngineActionReceiver
├── data/          AppDatabase (v2 + migrasi), ProductDao, AccountDao, SettingsStore,
│                  CsvImporter, Regions, entities/
├── image/         FrameProcessor, PhotoStore, GalleryExporter, ImageUtils
├── ai/            TitleGenerator + AiContent (Gemini), KeywordResearch
assets/wilayah.txt 514 kabupaten/kota (data 34 provinsi; provinsi pemekaran Papua 2022 masih di bawah Papua)
└── util/          NotificationHelper
```

## Catatan penting

- Otomatisasi aplikasi Facebook melanggar Ketentuan Facebook; tetap ada risiko akun dibatasi
  walau setiap posting kamu publikasikan sendiri. Pasang produk asli, hindari duplikat,
  dan jangan memasang terlalu banyak tawaran sekaligus.
- Nama "Robotta" adalah merek pihak lain. Jika aplikasi ini akan dibagikan atau dijual,
  ganti `applicationId` di `app/build.gradle.kts` dan nama aplikasi di `res/values/strings.xml`.
- API key Gemini disimpan di HP (SharedPreferences) dan hanya dikirim ke Google.
