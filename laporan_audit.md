# Laporan Audit Sistem Presensi Karyawan Pabrik

Berdasarkan hasil pemeriksaan awal secara menyeluruh pada aplikasi Android dan server Node.js, berikut ini adalah temuan, analisis, dan rekomendasi terkait arsitektur, keamanan, dan fungsionalitas sistem.

## Daftar Hasil Audit

| No. | File/Komponen | Temuan | Kategori | Prioritas | Rekomendasi |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `nodejs_backend/server.js` | Endpoint `/api/admin/login` menggunakan username dan password hardcoded (`admin` / `membersuki`) secara teks mentah. Ini berisiko tinggi jika kode bocor. | Perbaiki | P0 | Buat tabel `admin` di database untuk menyimpan kredensial admin yang di-hash (misalnya dengan bcrypt). |
| 2 | `nodejs_backend/server.js` | Endpoint API untuk master data dan log (`/api/admin/*`, `/api/karyawan/*`, `/api/absen`) tidak diamankan dengan token autentikasi (seperti JWT). Siapapun yang mengetahui IP server dapat mengakses dan mengubah data presensi. | Perbaiki | P0 | Terapkan middleware JWT di Node.js untuk memvalidasi token setiap kali ada permintaan API dari Android atau Web Dashboard. |
| 3 | `app/src/main/java/com/example/model/SessionManager.kt` | Fitur pengamanan PIN karyawan di aplikasi Android menggunakan hash `SHA-256` namun salt-nya hardcoded (`FadiyaKencana_SecureSalt_2026`). Lebih aman menggunakan `EncryptedSharedPreferences`. | Perbaiki | P1 | Ubah `SessionManager.kt` agar menggunakan `EncryptedSharedPreferences` bawaan Android Security KTX untuk enkripsi PIN secara native. |
| 4 | `app/src/main/java/com/example/ui/screens/KaryawanDashboardScreen.kt` | Validasi lokasi (Geofencing) dan status keterlambatan dilakukan di sisi klien (Android). Data yang dikirim adalah hasil akhir. Ini rawan dimanipulasi (GPS Spoofing). | Perbaiki | P1 | Validasi radius lokasi dan status "Terlambat / Tepat Waktu" **wajib** dilakukan di server (`server.js`), bukan di HP klien. Klien hanya mengirim koordinat saja. |
| 5 | `nodejs_backend/server.js` | Fungsi simpan foto selfie base64 disimpan langsung di direktori publik `public/uploads` tanpa filter jenis file yang memadai, memungkinkan serangan shell upload. | Perbaiki | P1 | Tambahkan validasi tipe file yang diunggah harus JPEG/PNG, dan buat batas maksimal ukuran file upload base64 di backend. |
| 6 | `app/src/main/java/com/example/ui/screens/SecurityLockScreen.kt` | Autentikasi biometrik sudah mengimplementasikan `androidx.biometric.BiometricPrompt` dengan standar resmi Android (tidak menyimpan data wajah/jari ke database). Ini sangat baik. | Pertahankan | P3 | Tidak perlu diubah, fitur ini sudah sangat tepat dan aman sesuai panduan keamanan Android. |
| 7 | `nodejs_backend/public/index.html` | Antarmuka dashboard admin digabungkan dalam satu file besar HTML. Hal ini menyulitkan pengembangan dan berpotensi memaparkan seluruh logika dashboard ke pengguna luar. | Ubah | P2 | Pecah file menjadi view terpisah (misal EJS atau panel Vue/React) yang hanya bisa dirender jika admin telah melewati sesi autentikasi yang sah. |
| 8 | `database.sql` | Tabel MySQL sudah memiliki `id` (AUTO_INCREMENT) tapi pada endpoint karyawan `id_karyawan` manual (K-001) juga disimpan sebagai VARCHAR Primary Key di `karyawan`. | Ubah | P2 | Gunakan `id` AUTO_INCREMENT dari MySQL sebagai standar agar integritas relasi tabel absensi dan izin lebih konsisten. |
| 9 | `nodejs_backend/database.json` | File database lama format JSON masih tersisa beserta modul pembacaan file-nya di README. | Hapus | P3 | Hapus `database.json` agar ukuran proyek lebih bersih, mengingat sistem sudah bermigrasi permanen ke MySQL. |
| 10| `nodejs_backend/server.js` | Jam server Node.js digunakan sebagai referensi presensi (bukan jam HP). Ini adalah langkah yang sangat tepat untuk mencegah pemalsuan jam. | Pertahankan | P3 | Tetap gunakan `new Date()` dari sistem server backend. Pastikan server (laptop) menggunakan zona waktu yang tepat. |

---

## 1. Ringkasan Kondisi Aplikasi Saat Ini
Secara umum, aplikasi Anda sangat fungsional. Transisi dari file JSON lokal ke **MySQL Database** telah dilakukan dan berhasil membuat sistem siap digunakan dalam skala pabrik.
Secara arsitektur, aplikasi ini berjalan optimal di jaringan lokal (intranet pabrik) dengan Node.js sebagai server. Aplikasi Android sudah mengakomodasi Autentikasi Biometrik standar Android. Namun, aplikasi ini masih memiliki kelemahan kritis di bidang **Keamanan Jaringan dan Keabsahan Data** karena ketiadaan Token JWT dan validasi geofencing server-side.

## 2. Fitur yang Sudah Berfungsi dan Belum Terverifikasi
**Sudah Berfungsi:**
* Registrasi Karyawan (Tersimpan ke MySQL).
* Presensi dengan Kamera Selfie (Gambar base64 divalidasi dan disimpan di server).
* Autentikasi Biometrik Local-Device Android.
* Migrasi Database JSON ke MySQL.

**Belum Terverifikasi (Berisiko):**
* Akurasi Geofencing: Aplikasi saat ini mengizinkan klien mendeklarasikan dirinya valid, yang berpotensi dimanipulasi dengan aplikasi "Fake GPS".
* Manajemen Sesi Admin: Sesi login admin tidak memiliki _cookie_ / JWT kedaluwarsa.

## 3. Daftar Masalah Utama yang Perlu Segera Diperbaiki (P0 & P1)
1. **Keamanan Kredensial Admin (P0):** Mengubah password admin dari *hardcoded* di source code menjadi penyimpanan aman di MySQL menggunakan _bcrypt hashing_.
2. **Ketiadaan Token API (P0):** Melindungi jalur `/api/` menggunakan JSON Web Token (JWT) agar peretas lokal tidak bisa memalsukan data absen langsung ke endpoint port 3000.
3. **Geofence Validation on Server (P1):** Perhitungan jarak koordinat pabrik dengan koordinat pegawai mutlak harus dihitung di `server.js` menggunakan rumus *Haversine*, bukan mempercayai deklarasi dari HP.

## 4. Daftar Kode/Fitur yang Aman Dihapus
* **`nodejs_backend/database.json`**: Data sudah berpindah total ke MySQL.
* Fungsi `loadDatabase()` dan `saveDatabase()` yang ada pada versi lama `server.js` (jika masih tersisa komentar atau sisa kode).

## 5. Fitur yang Perlu Ditambahkan (Saran Prioritas)
* **API Middleware (JWT)**: Untuk melacak dan memverifikasi identitas pengguna (HP maupun Web Dashboard) di setiap HTTP Request.
* **Haversine Formula**: Rumus matematika di Node.js untuk menghitung jarak presisi GPS.

## 6. Rencana Perbaikan (Urutan Prioritas)
1. **Tahap 1 (Security API - P0):** Membuat sistem JWT untuk Admin dan Karyawan, serta Middleware autentikasi di Express.js.
2. **Tahap 2 (Database Admin - P0):** Menambahkan tabel `admins` di MySQL untuk penyimpanan sandi terenkripsi.
3. **Tahap 3 (Geofence & Validasi - P1):** Memindahkan logika batas keterlambatan dan radius jarak sepenuhnya ke Backend.
4. **Tahap 4 (Refactor Android - P2):** Menyesuaikan `BackendApiClient.kt` untuk mengirim JWT Token pada setiap *header request*.

## 7. File yang Akan Terdampak Perubahan
* `nodejs_backend/server.js`
* `nodejs_backend/database.sql`
* `app/src/main/java/com/example/network/BackendApiClient.kt`
* `app/src/main/java/com/example/ui/screens/KaryawanDashboardScreen.kt`
* `app/src/main/java/com/example/model/SessionManager.kt`

## 8. Rencana Pengujian Setelah Perbaikan
* **Uji SQL Injection:** Mencoba login dengan query nakal pada kolom NIK dan password admin.
* **Uji API Tanpa Token:** Mengirim POST request presensi via alat seperti Postman (tanpa aplikasi Android) untuk memastikan server menolak koneksi.
* **Uji GPS Palsu:** Mengubah koordinat GPS menjadi koordinat luar negeri, untuk memastikan backend menolak presensi tersebut walaupun HP menganggapnya sah.

## 9. Risiko Perubahan dan Cara Mencegah Kerusakan
* **Risiko API Terputus:** Saat penerapan JWT, aplikasi Android mungkin akan gagal berkomunikasi (`HTTP 401 Unauthorized`).
* **Cara Mencegah:** Saya akan membuat backup pada `server.js` (dengan nama `server_backup.js`) sebelum modifikasi JWT dimulai. Endpoint login akan dimodifikasi terlebih dahulu agar menghasilkan token, setelah itu diujicobakan ke satu endpoint (`/api/ping`) sebelum diterapkan secara global.