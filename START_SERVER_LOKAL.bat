@echo off
title Server Absensi PT Fadiya Kencana (Mode Wi-Fi Lokal)
echo ========================================================
echo   SERVER ABSENSI PT FADIYA KENCANA (MODE WI-FI LOKAL)
echo ========================================================
echo.
echo [1/2] Menyiapkan koneksi ke database MySQL Workbench...
echo [2/2] Memulai Server Node.js di IP Wi-Fi: http://192.168.0.2:3000/
echo.
echo ========================================================
echo   SERVER AKTIF! HP karyawan di Wi-Fi yang sama
echo   akan langsung otomatis terhubung tanpa perlu setting.
echo ========================================================
echo.
cd /d %~dp0nodejs_backend
node server.js
pause
