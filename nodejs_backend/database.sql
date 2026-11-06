-- ============================================================
-- SKRIP DATABASE MYSQL UNTUK APLIKASI ABSENSI KANTOR
-- Petunjuk:
-- 1. Buka MySQL Workbench
-- 2. Hubungkan ke Local Instance (root)
-- 3. Buka/Salin file ini dan Jalankan (tombol Petir ⚡)
-- ============================================================

CREATE DATABASE IF NOT EXISTS db_absensi;
USE db_absensi;

-- 1. Tabel Karyawan
CREATE TABLE IF NOT EXISTS karyawan (
    id VARCHAR(50) PRIMARY KEY,
    id_karyawan VARCHAR(50),
    nip VARCHAR(50) NOT NULL UNIQUE,
    nama VARCHAR(100) NOT NULL,
    departemen VARCHAR(100) DEFAULT 'Produksi Pabrik',
    jenis_kelamin VARCHAR(20) DEFAULT 'L',
    tempat_lahir VARCHAR(100) DEFAULT '-',
    tanggal_lahir VARCHAR(50) DEFAULT '-',
    alamat TEXT,
    device_id VARCHAR(100) DEFAULT '-',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tabel Absensi / Presensi
CREATE TABLE IF NOT EXISTS absensi (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_karyawan VARCHAR(50),
    nip VARCHAR(50) NOT NULL,
    nama VARCHAR(100) NOT NULL,
    departemen VARCHAR(100) DEFAULT '-',
    tanggal DATE NOT NULL,
    jam_masuk VARCHAR(10) DEFAULT '-',
    jam_pulang VARCHAR(10) DEFAULT '-',
    status VARCHAR(30) DEFAULT 'TEPAT_WAKTU',
    foto_masuk LONGTEXT,
    foto_pulang LONGTEXT,
    latitude DECIMAL(10,8) DEFAULT 0,
    longitude DECIMAL(11,8) DEFAULT 0,
    maps_url VARCHAR(255) DEFAULT '-',
    maps_url_pulang VARCHAR(255) DEFAULT '-',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tabel Pengajuan Izin / Sakit
CREATE TABLE IF NOT EXISTS pengajuan_izin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_karyawan VARCHAR(50),
    nip VARCHAR(50) NOT NULL,
    nama VARCHAR(100) NOT NULL,
    departemen VARCHAR(100) DEFAULT '-',
    jenis_izin VARCHAR(50) NOT NULL,
    tanggal_mulai DATE NOT NULL,
    tanggal_selesai DATE NOT NULL,
    alasan TEXT,
    status VARCHAR(30) DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 4. Tabel Pengaturan Kantor
CREATE TABLE IF NOT EXISTS pengaturan_kantor (
    id INT PRIMARY KEY DEFAULT 1,
    jam_masuk VARCHAR(10) DEFAULT '08:00',
    batas_terlambat VARCHAR(10) DEFAULT '08:00',
    jam_pulang VARCHAR(10) DEFAULT '17:00',
    pengumuman TEXT,
    lokasi_nama VARCHAR(150) DEFAULT 'Kantor Pusat PT Fadiya Kencana',
    lokasi_latitude DECIMAL(10,8) DEFAULT -7.58854000,
    lokasi_longitude DECIMAL(11,8) DEFAULT 110.79566000,
    radius_meter INT DEFAULT 100,
    wajib_dalam_radius TINYINT DEFAULT 0
);

-- Insert Pengaturan Default
INSERT INTO pengaturan_kantor (id, jam_masuk, batas_terlambat, jam_pulang, pengumuman)
VALUES (1, '08:00', '08:00', '17:00', 'Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.')
ON DUPLICATE KEY UPDATE id=1;


-- 5. Tabel Admin
CREATE TABLE IF NOT EXISTS admins (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) DEFAULT 'administrator',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
