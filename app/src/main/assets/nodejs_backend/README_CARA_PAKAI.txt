========================================================================
PANDUAN MENJALANKAN SERVER BACKEND NODE.JS UNTUK ABSENSI KANTOR:
========================================================================

Kenapa Menggunakan Node.js Lebih Praktis Dibanding XAMPP?
---------------------------------------------------------
1. TIDAK PERLU install Apache dan MySQL.
2. TIDAK PERLU setting phpMyAdmin atau import file SQL secara manual.
3. Database otomatis disimpan secara persisten di file 'database.json'.
4. Jumlah karyawan dimulai dari 0 (kosong) dan otomatis bertambah saat
   karyawan melakukan pendaftaran di smartphone mereka.
5. Server berjalan sangat cepat, ringan, dan modern.


LANGKAH MENJALANKAN DI LAPTOP/KOMPUTER ANDA:
-------------------------------------------

1. Pastikan Node.js sudah terinstal di Komputer/Laptop:
   - Jika belum ada, download gratis di: https://nodejs.org/ (pilih versi LTS).
   - Buka Command Prompt (CMD) atau Terminal, cek dengan ketik: node -v

2. Buka Folder ini di Terminal/CMD:
   - Buka CMD atau Terminal, lalu arahkan ke folder 'nodejs_backend' ini.
     Contoh:
     cd C:\path\ke\proyek\nodejs_backend

3. Pasang Dependencies (Hanya Perlu Sekali):
   Ketik perintah berikut lalu tekan Enter:
   npm install

4. Jalankan Server:
   Ketik perintah:
   node server.js
   (atau: npm start)

   Di layar CMD akan muncul:
   ====================================================
   🚀 SERVER NODE.JS ABSENSI KANTOR BERHASIL AKTIF!
   ====================================================
   • Akses Lokal (Laptop): http://localhost:3000/
   • Akses HP Android   : http://192.168.0.10:3000/
   • File Penyimpanan   : .../database.json
   ====================================================

5. Menghubungkan HP Android:
   - Pastikan HP Android dan Laptop terhubung ke Wi-Fi yang sama.
   - Di aplikasi Android, klik tombol 'Server Node.js' (ikon DNS / Server) dan masukkan:
     http://[IP_LAPTOP_ANDA]:3000/   (Contoh: http://192.168.0.10:3000/)
   - Tekan 'Tes Koneksi' untuk memastikan status terhubung berwarna hijau.
   - Selesai! Kini setiap kali karyawan registrasi dan absen, data langsung
     masuk ke server Node.js dan tersinkronisasi ke seluruh HP Admin & Karyawan.
