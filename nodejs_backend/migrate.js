require('dotenv').config();
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

// Konfigurasi Database MySQL dari file .env
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '', // Isi di file .env jika MySQL Workbench Anda menggunakan password
  database: process.env.DB_NAME || 'db_absensi',
  multipleStatements: true
};

const JSON_FILE = path.join(__dirname, 'database.json');

async function runMigration() {
  console.log('--------------------------------------------------');
  console.log('🚀 MULAI MIGRASI DATA DARI database.json KE MYSQL');
  console.log('--------------------------------------------------');

  if (!fs.existsSync(JSON_FILE)) {
    console.error('❌ File database.json tidak ditemukan!');
    process.exit(1);
  }

  let rawData;
  try {
    rawData = JSON.parse(fs.readFileSync(JSON_FILE, 'utf-8'));
  } catch (e) {
    console.error('❌ Gagal membaca file database.json:', e.message);
    process.exit(1);
  }

  console.log(`📡 Menghubungkan ke MySQL (${dbConfig.user}@${dbConfig.host})...`);
  let connection;
  try {
    connection = await mysql.createConnection(dbConfig);
    console.log('✅ Berhasil terhubung ke MySQL Database:', dbConfig.database);
  } catch (err) {
    console.error('❌ Gagal terhubung ke MySQL:', err.message);
    console.log('\n💡 PETUNJUK PENANGANAN ERROR:');
    console.log('1. Pastikan Anda sudah menjalankan skrip "database.sql" di MySQL Workbench.');
    console.log('2. Jika MySQL Workbench Anda meminta Password saat login, buka file ".env" dan isi DB_PASSWORD=password_anda');
    process.exit(1);
  }

  try {
    // 1. Migrasi Karyawan
    const karyawanList = rawData.karyawan || [];
    console.log(`\n📦 Memindahkan ${karyawanList.length} data karyawan...`);
    for (const k of karyawanList) {
      const nip = (k.nip || k.nik || '').trim();
      if (!nip) continue;
      const id = k.id || `K-${nip}`;
      const idKaryawan = k.id_karyawan || id;
      const nama = k.nama || 'Tanpa Nama';
      const dept = k.departemen || k.dept || 'Produksi Pabrik';
      const jk = k.jenis_kelamin || k.jk || 'L';
      const tempatLahir = k.tempat_lahir || '-';
      const tanggalLahir = k.tanggal_lahir || '-';
      const alamat = k.alamat || '-';
      const deviceId = k.device_id || '-';
      const createdAt = k.created_at ? new Date(k.created_at) : new Date();

      await connection.query(
        `INSERT INTO karyawan (id, id_karyawan, nip, nama, departemen, jenis_kelamin, tempat_lahir, tanggal_lahir, alamat, device_id, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
            nama=VALUES(nama),
            departemen=VALUES(departemen),
            jenis_kelamin=VALUES(jenis_kelamin),
            alamat=VALUES(alamat),
            device_id=VALUES(device_id)`,
        [id, idKaryawan, nip, nama, dept, jk, tempatLahir, tanggalLahir, alamat, deviceId, createdAt]
      );
    }
    console.log('✅ Migrasi Karyawan selesai!');

    // 2. Migrasi Absensi
    const absensiList = rawData.absensi || [];
    console.log(`\n📦 Memindahkan ${absensiList.length} data absensi...`);
    for (const a of absensiList) {
      const nip = a.nip || '';
      if (!nip) continue;
      const idKaryawan = a.id_karyawan || '';
      const nama = a.nama || '';
      const dept = a.departemen || '-';
      const tanggal = a.tanggal || new Date().toISOString().split('T')[0];
      const jamMasuk = a.jam_masuk || '-';
      const jamPulang = a.jam_pulang || '-';
      const status = a.status || 'TEPAT_WAKTU';
      const fotoMasuk = a.foto_masuk || '';
      const fotoPulang = a.foto_pulang || '';
      const createdAt = a.created_at ? new Date(a.created_at) : new Date();

      await connection.query(
        `INSERT INTO absensi (id_karyawan, nip, nama, departemen, tanggal, jam_masuk, jam_pulang, status, foto_masuk, foto_pulang, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [idKaryawan, nip, nama, dept, tanggal, jamMasuk, jamPulang, status, fotoMasuk, fotoPulang, createdAt]
      );
    }
    console.log('✅ Migrasi Absensi selesai!');

    // 3. Migrasi Pengajuan Izin
    const izinList = rawData.pengajuan_izin || [];
    console.log(`\n📦 Memindahkan ${izinList.length} data pengajuan izin...`);
    for (const i of izinList) {
      const nip = i.nip || '';
      if (!nip) continue;
      const idKaryawan = i.id_karyawan || '';
      const nama = i.nama || '';
      const dept = i.departemen || '-';
      const jenisIzin = i.jenis_izin || 'IZIN';
      const tglMulai = i.tanggal_mulai || new Date().toISOString().split('T')[0];
      const tglSelesai = i.tanggal_selesai || tglMulai;
      const alasan = i.alasan || '-';
      const status = i.status || 'PENDING';
      const createdAt = i.created_at ? new Date(i.created_at) : new Date();

      await connection.query(
        `INSERT INTO pengajuan_izin (id_karyawan, nip, nama, departemen, jenis_izin, tanggal_mulai, tanggal_selesai, alasan, status, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [idKaryawan, nip, nama, dept, jenisIzin, tglMulai, tglSelesai, alasan, status, createdAt]
      );
    }
    console.log('✅ Migrasi Pengajuan Izin selesai!');

    // 4. Migrasi Pengaturan Kantor
    const p = rawData.pengaturan_kantor || {};
    console.log('\n📦 Memindahkan Pengaturan Kantor...');
    await connection.query(
      `INSERT INTO pengaturan_kantor (id, jam_masuk, batas_terlambat, jam_pulang, pengumuman)
       VALUES (1, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
          jam_masuk=VALUES(jam_masuk),
          batas_terlambat=VALUES(batas_terlambat),
          jam_pulang=VALUES(jam_pulang),
          pengumuman=VALUES(pengumuman)`,
      [p.jam_masuk || '08:00', p.batas_terlambat || '08:00', p.jam_pulang || '17:00', p.pengumuman || '']
    );
    console.log('✅ Migrasi Pengaturan Kantor selesai!');

    console.log('\n==================================================');
    console.log('🎉 MIGRASI SUKSES 100%! SEMUA DATA TELAH DIPINDAHKAN KE MYSQL.');
    console.log('==================================================\n');

  } catch (err) {
    console.error('❌ Terjadi kesalahan saat migrasi:', err.message);
  } finally {
    await connection.end();
  }
}

runMigration();
