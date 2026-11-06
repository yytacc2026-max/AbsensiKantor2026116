# Perbaikan Alur Login & Metode Autentikasi Aplikasi Presensi Karyawan

Rencana arsitektur dan implementasi untuk memodernisasi alur autentikasi aplikasi presensi karyawan pabrik Fadiya Kencana: pengguna dapat memilih metode login (Sidik Jari, Pengenalan Wajah, atau PIN) saat pertama kali setup, metode disimpan secara persisten dan aman, langsung digunakan otomatis pada pembukaan aplikasi berikutnya, serta dilengkapi menu Pengaturan Keamanan untuk ganti metode.

### User Review & Critical Decisions

> [!IMPORTANT]
> Keputusan penting yang telah dikonfirmasi oleh pengguna pada sesi tanya-jawab:
> - **Proteksi Percobaan PIN**: 5 kali salah memasukkan PIN akan mengunci aplikasi sementara selama 30 detik (menghindari brute-force).
> - **Lokasi Menu Ganti Metode Login**: Ditempatkan di dalam menu Profil & Pengaturan Keamanan pada Dashboard Karyawan (memerlukan re-autentikasi sebelum mengganti metode).
> - **Keamanan Biometrik Murni**: Menggunakan AndroidX Biometric resmi tanpa menyimpan data biometrik mentah pada server kantor dan tanpa mengaktifkan kunci pola bawaan perangkat yang dapat menutupi antarmuka aplikasi.

---

### 1. Overview & Core Concept

- **Tujuan Utama**: Memberikan alur masuk aplikasi yang cepat, aman, dan dapat diandalkan bagi pekerja pabrik dengan tiga opsi autentikasi: **Sidik Jari (Fingerprint)**, **Pengenalan Wajah (Face Biometric)**, atau **PIN 6-Digit**.
- **Target Pengguna**: Karyawan dan staf lapangan yang membutuhkan proses absensi cepat di awal dan akhir giliran kerja (shift), tanpa gangguan pop-up sistem yang tidak diinginkan.
- **Nilai Tambah**: Pengaturan sekali di awal, langsung digunakan otomatis setiap membuka aplikasi, aman terhadap percobaan berulang (brute-force lockout), dan fleksibel untuk diganti melalui pengaturan keamanan di dashboard.

---

### 2. User Experience & Visual Design

#### Alur Pengguna (User Journey)

1. **Splash & Status Check**:
   - Saat aplikasi dibuka, sistem memeriksa status konfigurasi keamanan (`SessionManager.getAuthMethod()`).
   - **Kondisi A (Belum Pernah Setup)**: Aplikasi menampilkan layar indah **"Pilih Metode Login"** dengan 3 kartu pilihan interaktif (Sidik Jari, Pengenalan Wajah, PIN).
   - **Kondisi B (Sudah Setup)**: Langsung mengaktifkan metode yang dipilih:
     - Jika *Sidik Jari*: Langsung membuka prompt biometrik sidik jari resmi Android.
     - Jika *Wajah*: Langsung membuka autentikasi biometrik wajah resmi Android.
     - Jika *PIN*: Langsung menampilkan papan angka PIN 6-digit.

2. **Layar Pilih Metode Login (Onboarding Keamanan)**:
   - Header bertema Teal Industrial dengan logo Fadiya Kencana.
   - Kartu 1: **Sidik Jari (Fingerprint)** – Ikon sidik jari, deteksi ketersediaan sensor HP otomatis.
   - Kartu 2: **Pengenalan Wajah (Face ID)** – Ikon scan wajah biometrik, validasi dukungan sensor wajah perangkat.
   - Kartu 3: **PIN Keamanan (6 Digit)** – Ikon gembok pelindung, selalu tersedia di semua perangkat.

3. **Alur Setup & Login PIN**:
   - **Pembuatan**: Pengguna memasukkan 6 digit PIN, lalu konfirmasi ulang PIN.
   - **Penyimpanan**: PIN disimpan dengan hashing SHA-256 + salt lokal aman (bukan plaintext).
   - **Login Rutin**: Menampilkan indikator 6 titik dan keypad angka responsif.
   - **Perlindungan**: Indikator sisa percobaan (5 kali), hitung mundur lockout 30 detik jika salah 5 kali berturut-turut.

4. **Alur Setup & Login Biometrik (Sidik Jari & Wajah)**:
   - Memeriksa kapabilitas hardware melalui `BiometricManager.Authenticators.BIOMETRIC_STRONG or BIOMETRIC_WEAK`.
   - Menggunakan prompt resmi `BiometricPrompt` dengan judul yang ramah pengguna.
   - Menyediakan tombol alternatif "Gunakan PIN" sebagai fallback jika biometrik bermasalah atau kotor saat jam kerja pabrik.

5. **Menu Profil & Pengaturan Keamanan di Dashboard**:
   - Tombol pengaturan keamanan di header dashboard karyawan.
   - Dialog/Layar ganti metode login dengan verifikasi metode lama terlebih dahulu.
   - Opsi reset atau upgrade metode login (misal dari PIN ke Sidik Jari).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Penyimpanan Hash PIN vs Plaintext**
  - *Pendekatan*: Menggunakan enkripsi hash SHA-256 dengan salt unik per perangkat yang disimpan di SharedPreferences privat.
  - *Alasan*: Memenuhi standar kepatuhan keamanan industri, mencegah kebocoran kredensial jika data cadangan diekstrak.
- **Decision 2: Penggunaan AndroidX Biometric Resmi**
  - *Pendekatan*: Memanfaatkan AndroidX Biometric API murni tanpa kamera selfie buatan sendiri untuk biometrik login.
  - *Alasan*: Kamera selfie buatan sendiri rawan spoofing foto cetak dan memakan daya tinggi; API resmi menjamin privasi hardware (TrustZone/TEE) dan tidak mengirim data sidik jari ke server mana pun.
- **Decision 3: Pemisahan Kamera Absen dan Biometrik Login**
  - *Pendekatan*: Autentikasi buka aplikasi memakai metode login yang dipilih (Fingerprint/Face/PIN), sedangkan saat tombol "Absen Masuk" / "Absen Pulang" ditekan, kamera selfie presensi dan koordinat GPS kantor tetap bekerja penuh sebagaimana sistem yang sudah ada.
  - *Alasan*: Menjamin integritas bukti fisik kehadiran (selfie di lokasi kerja) sekaligus menjaga keamanan akses aplikasi.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   App Startup Flow                     │
└───────────────────────────┬────────────────────────────┘
                            │
              ┌─────────────▼─────────────┐
              │ Is Auth Method Configured?│
              └──────┬─────────────┬──────┘
                     │ No          │ Yes
        ┌────────────▼──────┐      └──────────────┬───────────────┐
        │ Choose Method UI  │                     │               │
        │ [Fingerprint]     │                     │               │
        │ [Face Biometric]  │                     │               │
        │ [PIN 6-Digit]     │                     │               │
        └────────────┬──────┘                     │               │
                     │ Setup Complete             │               │
                     └────────────┬───────────────┘               │
                                  │                               │
       ┌──────────────────────────┴───────────────────────────────▼─────────┐
       │                       Active Auth Flow                             │
       │  ┌─────────────────┐   ┌─────────────────┐   ┌──────────────────┐  │
       │  │   Fingerprint   │   │  Face Biometric │   │   PIN 6-Digit    │  │
       │  │ (AndroidX Bio)  │   │ (AndroidX Bio)  │   │(Keypad+5 Attempts│  │
       │  └────────┬────────┘   └────────┬────────┘   └────────┬─────────┘  │
       └───────────┼─────────────────────┼─────────────────────┼────────────┘
                   │ Success             │ Success             │ Success
                   └─────────────────────┼─────────────────────┘
                                         ▼
                        ┌─────────────────────────────────┐
                        │    Role Selection / Dashboard   │
                        │ - Realtime Clock & Status       │
                        │ - Clock In (Camera + GPS)       │
                        │ - Clock Out (Camera + GPS)      │
                        │ - Security Settings (Change PIN)│
                        └─────────────────────────────────┘
```

#### Struktur State & Model Keamanan Baru

1. **`AuthMethod` (Enum)**:
   - `NONE`: Belum dikonfigurasi (pertama kali buka).
   - `PIN`: Menggunakan 6-digit PIN dengan batasan percobaan.
   - `FINGERPRINT`: Menggunakan sensor sidik jari perangkat.
   - `FACE`: Menggunakan sensor biometrik wajah perangkat.

2. **`SessionManager` Extensions**:
   - `getAuthMethod(): AuthMethod` & `saveAuthMethod(method: AuthMethod)`
   - `saveHashedPin(rawPin: String)` & `verifyPin(inputPin: String): Boolean`
   - `getFailedPinAttempts(): Int` & `recordFailedPinAttempt(): Boolean` (returns true if locked)
   - `getLockoutRemainingSeconds(): Long` & `resetPinAttempts()`

3. **Komponen Layar yang Dibuat / Diperbarui**:
   - `ChooseAuthMethodScreen.kt`: Tampilan modern pemilihan 3 metode dengan deteksi hardware otomatis.
   - `SetupPinScreen.kt`: Tampilan pembuatan PIN 6-digit + konfirmasi ulang.
   - `SecurityLockScreen.kt`: Dirombak untuk merender metode login aktif (Biometric Prompt atau PIN Keypad) dengan visual lockout 30 detik jika salah 5 kali.
   - `KaryawanDashboardScreen.kt`: Penambahan menu aksi "Pengaturan Keamanan & Ganti Metode" pada top bar / profil.
