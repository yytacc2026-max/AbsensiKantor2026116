@echo off
title Server Absensi PT Fadiya Kencana + Cloudflare Tunnel
echo ========================================================
echo   SERVER ABSENSI PT FADIYA KENCANA (MODE INTERNET / 5G)
echo ========================================================
echo.
echo [1/2] Menjalankan Server Node.js Backend di Port 3000...
start "NodeJS Backend Absensi" cmd /k "cd /d %~dp0nodejs_backend && node server.js"
timeout /t 3 /nobreak >nul
echo.
echo [2/2] Membuka Cloudflare Tunnel untuk Akses Bebas Jaringan (Paket Data 5G)...
echo.
echo CATATAN:
echo Salin alamat https://xxxx.trycloudflare.com yang muncul di bawah ini,
echo lalu masukkan ke kolom Pengaturan Server di aplikasi HP Anda.
echo ========================================================
echo.
"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:3000
pause
