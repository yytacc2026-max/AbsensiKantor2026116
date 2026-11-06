require('dotenv').config();
const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const os = require('os');
const mysql = require('mysql2/promise');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const cookieParser = require('cookie-parser');
const cron = require('node-cron');

const JWT_SECRET = process.env.JWT_SECRET || 'fallback_secret_key';

const app = express();
app.use(cookieParser());
const PORT = process.env.PORT || 3000;
const UPLOADS_DIR = path.join(__dirname, 'public', 'uploads');

// Pastikan folder uploads tersedia untuk simpan foto selfie
if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

// Konfigurasi Koneksi Database MySQL
const pool = mysql.createPool({
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '', // Sesuaikan jika MySQL Workbench Anda memiliki password
  database: process.env.DB_NAME || 'db_absensi',
  waitForConnections: true,
  connectionLimit: 20,
  queueLimit: 0
});

// Cek Koneksi Awal ke MySQL & Pastikan Seluruh Tabel Tersedia saat Server Aktif
(async () => {
  try {
    const conn = await pool.getConnection();
    console.log('✅ Connected successfully to MySQL Database: db_absensi');

    await conn.query(`
      CREATE TABLE IF NOT EXISTS karyawan (
        id VARCHAR(20) PRIMARY KEY,
        id_karyawan VARCHAR(20) NOT NULL,
        nip VARCHAR(50) NOT NULL UNIQUE,
        nama VARCHAR(100) NOT NULL,
        departemen VARCHAR(100) DEFAULT 'Produksi Pabrik',
        jenis_kelamin VARCHAR(10) DEFAULT 'L',
        tempat_lahir VARCHAR(100) DEFAULT '-',
        tanggal_lahir VARCHAR(50) DEFAULT '-',
        alamat VARCHAR(255) DEFAULT '-',
        device_id VARCHAR(100) DEFAULT '-',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);

    await conn.query(`
      CREATE TABLE IF NOT EXISTS absensi (
        id INT AUTO_INCREMENT PRIMARY KEY,
        id_karyawan VARCHAR(20) NOT NULL,
        nip VARCHAR(50) NOT NULL,
        nama VARCHAR(100) NOT NULL,
        departemen VARCHAR(100) DEFAULT 'Produksi Pabrik',
        tanggal DATE NOT NULL,
        jam_masuk VARCHAR(20) DEFAULT '-',
        jam_pulang VARCHAR(20) DEFAULT '-',
        status VARCHAR(30) DEFAULT 'TEPAT_WAKTU',
        foto_masuk VARCHAR(255) DEFAULT '-',
        foto_pulang VARCHAR(255) DEFAULT '-',
        latitude DOUBLE DEFAULT 0,
        longitude DOUBLE DEFAULT 0,
        maps_url VARCHAR(255) DEFAULT '-',
        maps_url_pulang VARCHAR(255) DEFAULT '-',
        catatan VARCHAR(255) DEFAULT '-',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);

    await conn.query(`
      CREATE TABLE IF NOT EXISTS pengajuan_izin (
        id INT AUTO_INCREMENT PRIMARY KEY,
        id_karyawan VARCHAR(20) NOT NULL,
        nip VARCHAR(50) NOT NULL,
        nama VARCHAR(100) NOT NULL,
        departemen VARCHAR(100) DEFAULT 'Produksi Pabrik',
        jenis_izin VARCHAR(50) NOT NULL,
        tanggal_mulai DATE NOT NULL,
        tanggal_selesai DATE NOT NULL,
        alasan TEXT,
        bukti_foto VARCHAR(255) DEFAULT '-',
        status VARCHAR(30) DEFAULT 'Pending',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);

    await conn.query(`
      CREATE TABLE IF NOT EXISTS pengaturan_kantor (
        id INT PRIMARY KEY,
        jam_masuk VARCHAR(10) DEFAULT '08:00',
        batas_terlambat VARCHAR(10) DEFAULT '08:00',
        jam_pulang VARCHAR(10) DEFAULT '17:00',
        pengumuman TEXT,
        lokasi_nama VARCHAR(150) DEFAULT 'Kantor Pusat PT Fadiya Kencana',
        lokasi_latitude DECIMAL(10,8) DEFAULT -7.58854000,
        lokasi_longitude DECIMAL(11,8) DEFAULT 110.79566000,
        radius_meter INT DEFAULT 100,
        wajib_dalam_radius TINYINT DEFAULT 0
      )
    `);
    await conn.query(`
      INSERT IGNORE INTO pengaturan_kantor (id, jam_masuk, batas_terlambat, jam_pulang, pengumuman, lokasi_nama, lokasi_latitude, lokasi_longitude, radius_meter, wajib_dalam_radius)
      VALUES (1, '08:00', '08:00', '17:00', 'Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.', 'Kantor Pusat PT Fadiya Kencana', -7.58854000, 110.79566000, 100, 0)
    `);

    await conn.query(`
      CREATE TABLE IF NOT EXISTS admins (
        id INT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(50) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        role VARCHAR(30) DEFAULT 'administrator',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);
    const [adminRows] = await conn.query('SELECT COUNT(*) as total FROM admins');
    if ((adminRows[0].total || 0) === 0) {
      const defaultHash = await bcrypt.hash(process.env.ADMIN_DEFAULT_PASS || 'admin', 10);
      await conn.query(
        'INSERT IGNORE INTO admins (username, password_hash, role) VALUES (?, ?, ?)',
        ['admin', defaultHash, 'administrator']
      );
    }

    const alterQueries = [
      "ALTER TABLE absensi ADD COLUMN catatan VARCHAR(255) DEFAULT '-'",
      "ALTER TABLE absensi ADD COLUMN maps_url_pulang VARCHAR(255) DEFAULT '-'",
      "ALTER TABLE pengajuan_izin ADD COLUMN bukti_foto VARCHAR(255) DEFAULT '-'",
      "ALTER TABLE karyawan ADD COLUMN device_id VARCHAR(100) DEFAULT '-'",
      "ALTER TABLE pengaturan_kantor ADD COLUMN lokasi_nama VARCHAR(150) DEFAULT 'Kantor Pusat PT Fadiya Kencana'",
      "ALTER TABLE pengaturan_kantor ADD COLUMN lokasi_latitude DECIMAL(10,8) DEFAULT -7.58854000",
      "ALTER TABLE pengaturan_kantor ADD COLUMN lokasi_longitude DECIMAL(11,8) DEFAULT 110.79566000",
      "ALTER TABLE pengaturan_kantor ADD COLUMN radius_meter INT DEFAULT 100",
      "ALTER TABLE pengaturan_kantor ADD COLUMN wajib_dalam_radius TINYINT DEFAULT 0"
    ];
    for (const q of alterQueries) {
      try { await conn.query(q); } catch (_) {}
    }

    // Auto-sinkronisasi awal: jika ada data di absensi/izin yang NIP-nya belum terdaftar di tabel karyawan, daftarkan otomatis
    try {
      const [missingAbsen] = await conn.query(`
        SELECT a.nip, MAX(a.nama) AS nama, MAX(a.departemen) AS departemen, MAX(a.id_karyawan) AS id_karyawan
        FROM absensi a
        LEFT JOIN karyawan k ON a.nip = k.nip
        WHERE k.nip IS NULL AND a.nip IS NOT NULL AND a.nip != '' AND a.nip != '-'
        GROUP BY a.nip
      `);
      for (const m of missingAbsen) {
        const idK = m.id_karyawan || `K-${m.nip}`;
        await conn.query(
          `INSERT IGNORE INTO karyawan (id, id_karyawan, nip, nama, departemen, jenis_kelamin, tempat_lahir, tanggal_lahir, alamat, device_id, created_at)
           VALUES (?, ?, ?, ?, ?, 'L', '-', '-', '-', '-', NOW())`,
          [idK, idK, m.nip, m.nama || 'Karyawan', m.departemen || 'Produksi Pabrik']
        );
        console.log(`🔄 [DB INIT SYNC] NIP ${m.nip} (${m.nama}) otomatis disinkronkan ke Master Karyawan.`);
      }
    } catch (e) {
      console.warn('Gagal sync data awal absensi -> karyawan:', e.message);
    }

    conn.release();
  } catch (err) {
    console.error('⚠️ WARN: Gagal terhubung ke MySQL Database!', err.message);
    console.error('💡 Petunjuk: Pastikan MySQL Service sudah aktif dan jalankan skrip "database.sql" di MySQL Workbench.');
  }
})();

// Middleware
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Proteksi Web Admin (Intersepsi file index.html)
app.get(['/', '/index.html'], (req, res) => {
  const token = req.cookies.admin_token;
  if (!token) {
    return res.redirect('/login.html');
  }

  jwt.verify(token, JWT_SECRET, (err, decoded) => {
    if (err || decoded.role !== 'administrator') {
      return res.redirect('/login.html');
    }
    res.sendFile(path.join(__dirname, 'views', 'index.html'));
  });
});

app.use(express.static(path.join(__dirname, 'public')));

// Log setiap request yang masuk
app.use((req, res, next) => {
  const time = new Date().toLocaleTimeString();
  console.log(`[${time}] ${req.method} ${req.url}`);
  next();
});

// ==========================================
// MIDDLEWARE AUTENTIKASI JWT (SATPAM API)
// ==========================================
function verifyToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  if (!authHeader) {
    return res.status(401).json({ status: "error", message: "Akses Ditolak! Token keamanan tidak ditemukan. Harap login kembali di aplikasi Android Anda." });
  }

  const token = authHeader.split(' ')[1]; // Format: "Bearer <token>"
  if (!token) {
    return res.status(401).json({ status: "error", message: "Akses Ditolak! Format token tidak valid." });
  }

  jwt.verify(token, JWT_SECRET, (err, decoded) => {
    if (err) {
      return res.status(401).json({ status: "error", message: "Sesi Anda telah kedaluwarsa atau token tidak valid. Harap login ulang." });
    }
    // Simpan data karyawan (seperti NIP) ke request untuk digunakan di endpoint selanjutnya
    req.user = decoded;
    next();
  });
}

// FUNGSI EVALUASI KETERLAMBATAN JAM MASUK (OFFICIAL CUTOFF 08:00 WIB)
function evalStatusMasuk(jamMasuk, batasTerlambat = "08:00", currentStatus = "") {
  if (!jamMasuk || jamMasuk === '-') return 'TEPAT_WAKTU';
  const upperStatus = (currentStatus || '').toUpperCase();
  if (['SAKIT', 'IZIN', 'CUTI'].includes(upperStatus)) {
    return upperStatus;
  }

  const cleanAbsen = jamMasuk.replace('.', ':').trim();
  const cleanBatas = (batasTerlambat || "08:00").replace('.', ':').trim();

  const [hA, mA] = cleanAbsen.split(':').map(n => parseInt(n) || 0);
  const [hB, mB] = cleanBatas.split(':').map(n => parseInt(n) || 0);

  const totalMenitAbsen = hA * 60 + mA;
  const totalMenitBatas = hB * 60 + mB;

  return (totalMenitAbsen > totalMenitBatas) ? 'TERLAMBAT' : 'TEPAT_WAKTU';
}

// Helper Hitung Jarak Geografis (Haversine Formula dalam Meter)
function hitungJarakMeter(lat1, lon1, lat2, lon2) {
  const p1 = parseFloat(lat1);
  const l1 = parseFloat(lon1);
  const p2 = parseFloat(lat2);
  const l2 = parseFloat(lon2);

  if (isNaN(p1) || isNaN(l1) || isNaN(p2) || isNaN(l2)) return 0;
  if (p1 === 0 && l1 === 0) return 0;

  const R = 6371000; // Radius bumi dalam meter
  const dLat = (p2 - p1) * Math.PI / 180;
  const dLon = (l2 - l1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(p1 * Math.PI / 180) * Math.cos(p2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

// Helper Ambil Pengaturan Kantor
async function getPengaturanKantor() {
  try {
    const [rows] = await pool.query('SELECT * FROM pengaturan_kantor WHERE id = 1');
    if (rows.length > 0) {
      const r = rows[0];
      return {
        id: r.id,
        jam_masuk: r.jam_masuk || "08:00",
        batas_terlambat: r.batas_terlambat || "08:00",
        jam_pulang: r.jam_pulang || "17:00",
        pengumuman: r.pengumuman || "Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.",
        lokasi_nama: r.lokasi_nama || "Kantor Pusat PT Fadiya Kencana",
        lokasi_latitude: (r.lokasi_latitude !== null && r.lokasi_latitude !== undefined) ? parseFloat(r.lokasi_latitude) : -7.58854,
        lokasi_longitude: (r.lokasi_longitude !== null && r.lokasi_longitude !== undefined) ? parseFloat(r.lokasi_longitude) : 110.79566,
        radius_meter: (r.radius_meter !== null && r.radius_meter !== undefined) ? parseInt(r.radius_meter) : 100,
        wajib_dalam_radius: (r.wajib_dalam_radius !== null && r.wajib_dalam_radius !== undefined) ? parseInt(r.wajib_dalam_radius) : 0
      };
    }
  } catch (e) {
    console.error('Gagal membaca pengaturan kantor:', e.message);
  }
  return {
    jam_masuk: "08:00",
    batas_terlambat: "08:00",
    jam_pulang: "17:00",
    pengumuman: "Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.",
    lokasi_nama: "Kantor Pusat PT Fadiya Kencana",
    lokasi_latitude: -7.58854,
    lokasi_longitude: 110.79566,
    radius_meter: 100,
    wajib_dalam_radius: 0
  };
}

// ==========================================
// 1. ENDPOINT PING & STATUS
// ==========================================
async function handlePing(req, res) {
  try {
    const [rows] = await pool.query('SELECT COUNT(*) as total FROM karyawan');
    const total = rows[0].total || 0;
    res.json({
      status: "success",
      message: "Server Node.js Absensi Kantor (MySQL) Berjalan Normal!",
      engine: "Node.js (Express + MySQL)",
      total_karyawan: total,
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}
app.get('/api/ping', handlePing);
app.get('/ping.php', handlePing);

// ==========================================
// 2. ENDPOINT REGISTRASI & CEK LOGIN KARYAWAN
// ==========================================
async function handleRegisterKaryawan(req, res) {
  try {
    const nip = (req.body.nip || req.body.nik || req.query.nip || req.query.nik || '').trim();
    const nama = (req.body.nama || req.query.nama || '').trim();
    const departemen = (req.body.departemen || req.body.dept || req.body.jabatan || req.query.departemen || 'Produksi Pabrik').trim();
    const jk = (req.body.jenis_kelamin || req.body.jk || 'L').trim();
    const tempatLahir = (req.body.tempat_lahir || '-').trim();
    const tanggalLahir = (req.body.tanggal_lahir || '-').trim();
    const alamat = (req.body.alamat || '-').trim();
    const deviceId = req.body.device_id || '-';

    if (!nip || !nama) {
      return res.status(400).json({ status: "error", message: "NIP/NIK dan Nama Lengkap wajib diisi!" });
    }

    // Cek Duplikat NIP
    const [existing] = await pool.query('SELECT * FROM karyawan WHERE nip = ?', [nip]);
    if (existing.length > 0) {
      console.log(`❌ [DUPLIKAT NIP DITOLAK] NIP ${nip} sudah terdaftar atas nama '${existing[0].nama}'.`);
      return res.status(400).json({
        status: "error",
        message: `Pendaftaran Ditolak: NIP/NIK '${nip}' sudah terdaftar atas nama '${existing[0].nama}'. NIP bersifat unik dan tidak bisa digunakan karyawan lain!`
      });
    }

    const [maxRows] = await pool.query('SELECT MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)) as max_id FROM karyawan WHERE id LIKE "K-%"');
    const maxNum = (maxRows[0].max_id || 0) + 1;
    const idKaryawan = `K-${String(maxNum).padStart(3, '0')}`;

    await pool.query(
      `INSERT INTO karyawan (id, id_karyawan, nip, nama, departemen, jenis_kelamin, tempat_lahir, tanggal_lahir, alamat, device_id, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())`,
      [idKaryawan, idKaryawan, nip, nama, departemen, jk, tempatLahir, tanggalLahir, alamat, deviceId]
    );

    const [newTotal] = await pool.query('SELECT COUNT(*) as total FROM karyawan');
    const totalKaryawan = newTotal[0].total;

    console.log(`✅ [DAFTAR BARU] Karyawan ${nama} (${idKaryawan} - NIP: ${nip}) berhasil ditambahkan ke MySQL. Total: ${totalKaryawan}`);

    const karyawanBaru = {
      id: idKaryawan,
      id_karyawan: idKaryawan,
      nip: nip,
      nik: nip,
      nama: nama,
      jk: jk,
      jenis_kelamin: jk,
      tempat_lahir: tempatLahir,
      tanggal_lahir: tanggalLahir,
      alamat: alamat,
      dept: departemen,
      departemen: departemen,
      device_id: deviceId
    };

    // Generate JWT Token (Berlaku 30 Hari)
    const token = jwt.sign({ id: idKaryawan, nip: nip, role: 'karyawan' }, JWT_SECRET, { expiresIn: '30d' });

    res.json({
      status: "success",
      message: `Karyawan baru (${nama}) dengan NIP ${nip} berhasil didaftarkan ke MySQL.`,
      is_existing: false,
      total_karyawan: totalKaryawan,
      token: token,
      data: karyawanBaru
    });
  } catch (err) {
    console.error('Gagal mendaftarkan karyawan:', err.message);
    res.status(500).json({ status: "error", message: err.message });
  }
}

app.post('/api/karyawan/register', handleRegisterKaryawan);
app.post('/api/karyawan', (req, res) => {
  const aksi = req.body.aksi || 'tambah';
  if (aksi === 'hapus') {
    return handleDeleteKaryawan(req, res);
  }
  return handleRegisterKaryawan(req, res);
});
app.post('/api/admin/karyawan', handleRegisterKaryawan);

// CEK & LOGIN KARYAWAN BERDASARKAN NIK / NIP + VALIDASI DEVICE BINDING & SINKRONISASI PROFIL
async function handleCekKaryawan(req, res) {
  try {
    const nip = (req.query.nip || req.query.nik || req.body.nip || req.body.nik || '').trim();
    const deviceId = (req.query.device_id || req.body.device_id || '').trim();
    const clientAlamat = (req.query.alamat || req.body.alamat || '').trim();
    const clientTtl = (req.query.ttl || req.body.ttl || '').trim();
    const clientJk = (req.query.jk || req.query.jenis_kelamin || req.body.jk || req.body.jenis_kelamin || '').trim();

    if (!nip) {
      return res.status(400).json({ status: "error", message: "Silakan masukkan NIK / NIP karyawan terlebih dahulu!" });
    }

    const [rows] = await pool.query('SELECT * FROM karyawan WHERE nip = ?', [nip]);
    if (rows.length > 0) {
      const k = rows[0];

      // Sinkronisasi cerdas: Jika di database alamat/TTL masih '-' tapi HP mengirimkan data yang valid, update ke database!
      const updateFields = [];
      const updateParams = [];

      if ((!k.alamat || k.alamat === '-') && clientAlamat && clientAlamat !== '-') {
        updateFields.push('alamat = ?');
        updateParams.push(clientAlamat);
        k.alamat = clientAlamat;
      }

      if ((!k.tempat_lahir || k.tempat_lahir === '-') && clientTtl && clientTtl !== '-') {
        let tLahir = clientTtl;
        let tglLahir = '-';
        if (clientTtl.includes(',')) {
          const p = clientTtl.split(',');
          tLahir = p[0].trim() || '-';
          tglLahir = p.slice(1).join(',').trim() || '-';
        }
        updateFields.push('tempat_lahir = ?, tanggal_lahir = ?');
        updateParams.push(tLahir, tglLahir);
        k.tempat_lahir = tLahir;
        k.tanggal_lahir = tglLahir;
      }

      if ((!k.jenis_kelamin || k.jenis_kelamin === '-' || k.jenis_kelamin === 'L') && clientJk && clientJk !== '-') {
        updateFields.push('jenis_kelamin = ?');
        updateParams.push(clientJk);
        k.jenis_kelamin = clientJk;
      }

      if (updateFields.length > 0) {
        updateParams.push(k.id);
        await pool.query(`UPDATE karyawan SET ${updateFields.join(', ')} WHERE id = ?`, updateParams);
        console.log(`🔄 [SYNC PROFIL HP -> MYSQL] Profil NIP ${nip} (${k.nama}) otomatis diperbarui dari HP: ${updateFields.join(', ')}`);
      }

      // Validasi pengikatan perangkat (1 HP = 1 Karyawan) jika deviceId dikirim
      if (deviceId && deviceId !== '-') {
        const storedDevice = (k.device_id || '-').trim();
        if (storedDevice && storedDevice !== '-' && storedDevice !== deviceId) {
          console.log(`🔒 [DEVICE LOCKED] NIP ${nip} mencoba login dari perangkat berbeda (${deviceId} != ${storedDevice})`);
          return res.status(403).json({
            status: "error",
            message: `Akun NIP '${nip}' (${k.nama}) sudah terikat pada perangkat HP lain. Silakan hubungi Admin untuk menekan tombol 'Reset HP'.`
          });
        }
        if (!storedDevice || storedDevice === '-') {
          await pool.query('UPDATE karyawan SET device_id = ? WHERE id = ?', [deviceId, k.id]);
          k.device_id = deviceId;
        }
      }

      k.nik = k.nip;
      k.dept = k.departemen;
      const tmp = (k.tempat_lahir && k.tempat_lahir !== '-') ? k.tempat_lahir : '';
      const tgl = (k.tanggal_lahir && k.tanggal_lahir !== '-') ? k.tanggal_lahir : '';
      k.ttl = (tmp || tgl) ? `${tmp}${tmp && tgl ? ', ' : ''}${tgl}` : '-';

      console.log(`🔍 [CEK LOGIN NIK] Data NIK ${nip} ditemukan: ${k.nama} (${k.departemen}) | TTL: ${k.ttl} | Alamat: ${k.alamat}`);

      // Generate JWT Token (Berlaku 30 Hari)
      const token = jwt.sign({ id: k.id, nip: k.nip, role: 'karyawan' }, JWT_SECRET, { expiresIn: '30d' });

      res.json({
        status: "success",
        message: `Data Karyawan '${k.nama}' ditemukan!`,
        token: token,
        data: k
      });
    } else {
      res.status(404).json({
        status: "error",
        message: `NIK / NIP '${nip}' belum terdaftar di database MySQL kantor. Silakan lakukan Pendaftaran Karyawan Baru!`
      });
    }
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}

app.get('/api/karyawan/cek', handleCekKaryawan);
app.post('/api/karyawan/login', handleCekKaryawan);
app.get('/cek_karyawan.php', handleCekKaryawan);

// ==========================================
// SINKRONISASI KARYAWAN (ABSENSI/IZIN -> MASTER KARYAWAN)
// ==========================================
async function syncMissingKaryawan() {
  try {
    // 1. Ambil data dari absensi yang belum terdaftar di karyawan
    const [missingAbsensi] = await pool.query(`
      SELECT a.nip, MAX(a.nama) AS nama, MAX(a.departemen) AS departemen, MAX(a.id_karyawan) AS id_karyawan
      FROM absensi a
      LEFT JOIN karyawan k ON a.nip = k.nip
      WHERE k.nip IS NULL AND a.nip IS NOT NULL AND a.nip != '' AND a.nip != '-'
      GROUP BY a.nip
    `);

    for (const m of missingAbsensi) {
      const idK = m.id_karyawan || `K-${m.nip}`;
      await pool.query(
        `INSERT IGNORE INTO karyawan (id, id_karyawan, nip, nama, departemen, jenis_kelamin, tempat_lahir, tanggal_lahir, alamat, device_id, created_at)
         VALUES (?, ?, ?, ?, ?, 'L', '-', '-', '-', '-', NOW())`,
        [idK, idK, m.nip, m.nama || 'Karyawan', m.departemen || 'Produksi Pabrik']
      );
      console.log(`🔄 [AUTO-SYNC] NIP ${m.nip} (${m.nama}) dari absensi otomatis disinkronkan ke Master Karyawan.`);
    }

    // 2. Ambil data dari izin yang belum terdaftar di karyawan
    const [missingIzin] = await pool.query(`
      SELECT p.nip, MAX(p.nama) AS nama, MAX(p.departemen) AS departemen, MAX(p.id_karyawan) AS id_karyawan
      FROM pengajuan_izin p
      LEFT JOIN karyawan k ON p.nip = k.nip
      WHERE k.nip IS NULL AND p.nip IS NOT NULL AND p.nip != '' AND p.nip != '-'
      GROUP BY p.nip
    `);

    for (const m of missingIzin) {
      const idK = m.id_karyawan || `K-${m.nip}`;
      await pool.query(
        `INSERT IGNORE INTO karyawan (id, id_karyawan, nip, nama, departemen, jenis_kelamin, tempat_lahir, tanggal_lahir, alamat, device_id, created_at)
         VALUES (?, ?, ?, ?, ?, 'L', '-', '-', '-', '-', NOW())`,
        [idK, idK, m.nip, m.nama || 'Karyawan', m.departemen || 'Produksi Pabrik']
      );
      console.log(`🔄 [AUTO-SYNC] NIP ${m.nip} (${m.nama}) dari izin otomatis disinkronkan ke Master Karyawan.`);
    }
  } catch (err) {
    console.error('Error auto sync missing karyawan:', err.message);
  }
}

// GET DAFTAR KARYAWAN + REKAP BULANAN
async function handleGetKaryawan(req, res) {
  try {
    await syncMissingKaryawan();
    const bulan = (req.query.bulan || new Date().toISOString().substring(0, 7)).trim(); // YYYY-MM
    const [karyawanList] = await pool.query('SELECT * FROM karyawan ORDER BY created_at DESC');

    const result = [];
    for (const k of karyawanList) {
      k.nik = k.nip;
      k.dept = k.departemen;
      const tmp = (k.tempat_lahir && k.tempat_lahir !== '-') ? k.tempat_lahir : '';
      const tgl = (k.tanggal_lahir && k.tanggal_lahir !== '-') ? k.tanggal_lahir : '';
      k.ttl = (tmp || tgl) ? `${tmp}${tmp && tgl ? ', ' : ''}${tgl}` : '-';

      const [absensiBulan] = await pool.query(
        'SELECT * FROM absensi WHERE nip = ? AND DATE_FORMAT(tanggal, "%Y-%m") = ?',
        [k.nip, bulan]
      );

      const totalHadir = absensiBulan.filter(a => a.jam_masuk && a.jam_masuk !== '-').length;
      const totalIzin = absensiBulan.filter(a => ['SAKIT', 'IZIN', 'CUTI'].includes((a.status || '').toUpperCase())).length;
      const totalTerlambat = absensiBulan.filter(a => (a.status || '').toUpperCase() === 'TERLAMBAT').length;

      result.push({
        ...k,
        total_hadir_bulan_ini: totalHadir,
        total_izin_bulan_ini: totalIzin,
        total_terlambat_bulan_ini: totalTerlambat,
        bulan_rekap: bulan
      });
    }

    res.json({
      status: "success",
      bulan: bulan,
      total: result.length,
      data: result
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}
app.get('/api/karyawan', handleGetKaryawan);
app.get('/api/admin/karyawan', handleGetKaryawan);

// HAPUS KARYAWAN
async function handleDeleteKaryawan(req, res) {
  try {
    const target = (req.params.nip || req.params.id || req.body.nip || req.body.id_karyawan || '').trim();
    const [existing] = await pool.query('SELECT * FROM karyawan WHERE nip = ? OR id = ? OR id_karyawan = ?', [target, target, target]);

    if (existing.length > 0) {
      const deleted = existing[0];
      await pool.query('DELETE FROM karyawan WHERE id = ?', [deleted.id]);
      await pool.query('DELETE FROM absensi WHERE nip = ?', [deleted.nip]);
      await pool.query('DELETE FROM pengajuan_izin WHERE nip = ? OR id_karyawan = ?', [deleted.nip, deleted.id_karyawan]);

      const [newTotal] = await pool.query('SELECT COUNT(*) as total FROM karyawan');
      console.log(`🗑️ [HAPUS] Karyawan ${deleted.nama} (${deleted.nip}) beserta data absensi & izin dihapus dari MySQL.`);

      return res.json({
        status: "success",
        message: `Karyawan ${deleted.nama} (NIP: ${deleted.nip}) berhasil dihapus.`,
        total_karyawan: newTotal[0].total
      });
    }

    res.status(404).json({
      status: "error",
      message: `Karyawan dengan NIP/ID '${target}' tidak ditemukan.`
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}
app.delete('/api/admin/karyawan/:nip', handleDeleteKaryawan);
app.delete('/api/karyawan/:id', handleDeleteKaryawan);

// RESET HP / UNBIND DEVICE ID
app.put('/api/admin/karyawan/:nip/reset-device', async (req, res) => {
  try {
    const nip = req.params.nip;
    const [result] = await pool.query('UPDATE karyawan SET device_id = "-" WHERE nip = ?', [nip]);
    if (result.affectedRows > 0) {
      console.log(`📱 [RESET HP] Tautan HP untuk NIP ${nip} telah di-reset di MySQL.`);
      return res.json({ status: "success", message: `Berhasil mereset tautan HP untuk NIP ${nip}` });
    }
    res.status(404).json({ status: "error", message: "Karyawan tidak ditemukan" });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// EDIT DATA KARYAWAN OLEH ADMIN
app.put('/api/admin/karyawan/:nip', async (req, res) => {
  try {
    const targetNip = (req.params.nip || '').trim();
    const nama = (req.body.nama || '').trim();
    const departemen = (req.body.departemen || req.body.dept || req.body.jabatan || 'Produksi Pabrik').trim();
    const jk = (req.body.jenis_kelamin || req.body.jk || 'L').trim();
    const tempatLahir = (req.body.tempat_lahir || '-').trim();
    const tanggalLahir = (req.body.tanggal_lahir || '-').trim();
    const alamat = (req.body.alamat || '-').trim();

    let finalTempat = tempatLahir;
    let finalTanggal = tanggalLahir;
    const ttlInput = (req.body.ttl || '').trim();
    if ((finalTempat === '-' || finalTanggal === '-') && ttlInput && ttlInput !== '-') {
      if (ttlInput.includes(',')) {
        const p = ttlInput.split(',');
        if (finalTempat === '-') finalTempat = p[0].trim() || '-';
        if (finalTanggal === '-') finalTanggal = p.slice(1).join(',').trim() || '-';
      } else if (ttlInput.includes('-')) {
        const p = ttlInput.split('-');
        if (finalTempat === '-') finalTempat = p[0].trim() || '-';
        if (finalTanggal === '-') finalTanggal = p.slice(1).join('-').trim() || '-';
      } else if (finalTempat === '-') {
        finalTempat = ttlInput;
      }
    }

    if (!nama) {
      return res.status(400).json({ status: "error", message: "Nama lengkap karyawan tidak boleh kosong." });
    }

    const [existing] = await pool.query('SELECT * FROM karyawan WHERE nip = ? OR id = ?', [targetNip, targetNip]);
    if (existing.length === 0) {
      return res.status(404).json({ status: "error", message: "Data karyawan tidak ditemukan." });
    }

    const k = existing[0];
    await pool.query(
      `UPDATE karyawan
       SET nama = ?, departemen = ?, jenis_kelamin = ?, tempat_lahir = ?, tanggal_lahir = ?, alamat = ?
       WHERE id = ?`,
      [nama, departemen, jk, finalTempat, finalTanggal, alamat, k.id]
    );

    // Sinkronkan nama & departemen pada riwayat absensi dan izin agar konsisten
    await pool.query('UPDATE absensi SET nama = ?, departemen = ? WHERE nip = ?', [nama, departemen, k.nip]);
    await pool.query('UPDATE pengajuan_izin SET nama = ?, departemen = ? WHERE nip = ?', [nama, departemen, k.nip]);

    console.log(`✏️ [EDIT KARYAWAN] Data NIP ${k.nip} (${nama}) berhasil diperbarui.`);
    return res.json({
      status: "success",
      message: `Data karyawan ${nama} (NIP: ${k.nip}) berhasil diperbarui.`
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// ==========================================
// 3. ENDPOINT ABSENSI (FOTO SELFIE & STRICT VALIDASI TERLAMBAT)
// ==========================================
async function handleAbsen(req, res) {
  try {
    const nip = (req.body.nip || req.body.nik || '').trim();
    const nama = (req.body.nama || '').trim();
    const id_karyawan = req.body.id_karyawan || req.body.karyawan_id || (nip ? `K-${nip}` : 'K-001');
    const departemen = req.body.departemen || req.body.dept || 'Produksi Pabrik';
    const rawTipe = (req.body.tipe || 'MASUK').toUpperCase();
    const tipe = (rawTipe === 'KELUAR' || rawTipe === 'PULANG') ? 'PULANG' : 'MASUK';
    const jam = req.body.jam || new Date().toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
    const today = req.body.tanggal || new Date().toISOString().split('T')[0];
    const fotoSelfieBase64 = req.body.foto_selfie || req.body.foto || '';
    const catatanAbsen = (req.body.catatan || '-').trim();

    const config = await getPengaturanKantor();
    const batasTerlambat = config.batas_terlambat || "08:00";
    const computedStatus = evalStatusMasuk(jam, batasTerlambat, req.body.status || req.body.status_absen || '');

    if (!nama && !nip) {
      return res.status(400).json({ status: "error", message: "Data absensi tidak lengkap (NIP/Nama wajib)" });
    }

    // Resolusi Koordinat GPS Presensi
    let userLat = parseFloat(req.body.latitude !== undefined ? req.body.latitude : (req.body.lat !== undefined ? req.body.lat : 0));
    let userLng = parseFloat(req.body.longitude !== undefined ? req.body.longitude : (req.body.lng !== undefined ? req.body.lng : 0));
    const mapsUrlInput = (req.body.maps_url || req.body.mapsUrl || '-').trim();

    if ((isNaN(userLat) || userLat === 0) && mapsUrlInput && mapsUrlInput.includes('google.com')) {
      const match = mapsUrlInput.match(/([+-]?\d+\.\d+)\s*,\s*([+-]?\d+\.\d+)/);
      if (match) {
        userLat = parseFloat(match[1]);
        userLng = parseFloat(match[2]);
      }
    }

    // Validasi Geofencing Kantor
    const officeLat = config.lokasi_latitude !== undefined ? config.lokasi_latitude : -7.58854;
    const officeLng = config.lokasi_longitude !== undefined ? config.lokasi_longitude : 110.79566;
    const radiusMaks = config.radius_meter !== undefined ? config.radius_meter : 100;
    const wajibRadius = (config.wajib_dalam_radius === 1 || config.wajib_dalam_radius === '1' || config.wajib_dalam_radius === true);
    let jarakMeter = 0;

    if (!isNaN(userLat) && !isNaN(userLng) && (userLat !== 0 || userLng !== 0)) {
      jarakMeter = hitungJarakMeter(userLat, userLng, officeLat, officeLng);
    }

    if (wajibRadius) {
      if (isNaN(userLat) || isNaN(userLng) || (userLat === 0 && userLng === 0)) {
        return res.status(400).json({
          status: "error",
          message: `Presensi ditolak! Titik GPS tidak terdeteksi. Kantor (${config.lokasi_nama || 'Kantor Pusat'}) mewajibkan presensi di dalam radius ${radiusMaks} meter. Pastikan GPS HP aktif.`
        });
      }
      if (jarakMeter > radiusMaks) {
        return res.status(400).json({
          status: "error",
          message: `Presensi ditolak! Posisi Anda berada ${jarakMeter} meter dari ${config.lokasi_nama || 'Kantor Pusat'} (Batas radius maksimal: ${radiusMaks} meter). Silakan lakukan presensi di area kantor.`
        });
      }
    }

    const clientAlamat = (req.body.alamat || req.body.domisili || '').trim();
    const clientTempat = (req.body.tempat_lahir || '').trim();
    const clientTanggal = (req.body.tanggal_lahir || '').trim();
    const clientTtl = (req.body.ttl || req.body.tempat_tanggal_lahir || '').trim();
    const clientJk = (req.body.jenis_kelamin || req.body.jk || '').trim();

    let finalTempatLahir = clientTempat || '-';
    let finalTanggalLahir = clientTanggal || '-';
    if (finalTempatLahir === '-' && clientTtl && clientTtl !== '-') {
      if (clientTtl.includes(',')) {
        const p = clientTtl.split(',');
        finalTempatLahir = p[0].trim() || '-';
        finalTanggalLahir = p.slice(1).join(',').trim() || '-';
      } else {
        finalTempatLahir = clientTtl.trim();
      }
    }

    // Auto-register atau Auto-sync profil ke Master Karyawan saat presensi masuk
    if (nip && nip !== '-') {
      const [existingK] = await pool.query('SELECT * FROM karyawan WHERE nip = ?', [nip]);
      if (existingK.length === 0) {
        const autoId = id_karyawan || `K-${nip}`;
        await pool.query(
          `INSERT INTO karyawan (id, id_karyawan, nip, nama, departemen, jenis_kelamin, tempat_lahir, tanggal_lahir, alamat, device_id, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '-', NOW())
           ON DUPLICATE KEY UPDATE nama = VALUES(nama), departemen = VALUES(departemen)`,
          [autoId, autoId, nip, nama || 'Karyawan', departemen || 'Produksi Pabrik', clientJk || 'L', finalTempatLahir, finalTanggalLahir, clientAlamat || '-']
        );
        console.log(`✅ [AUTO-REGISTER PRESENSI] Karyawan ${nama || nip} (NIP: ${nip}) otomatis didaftarkan ke Master Karyawan saat presensi.`);
      } else {
        const k = existingK[0];
        const updateFields = [];
        const updateParams = [];

        if ((!k.alamat || k.alamat === '-') && clientAlamat && clientAlamat !== '-') {
          updateFields.push('alamat = ?');
          updateParams.push(clientAlamat);
          k.alamat = clientAlamat;
        }
        if ((!k.tempat_lahir || k.tempat_lahir === '-') && finalTempatLahir && finalTempatLahir !== '-') {
          updateFields.push('tempat_lahir = ?');
          updateParams.push(finalTempatLahir);
          k.tempat_lahir = finalTempatLahir;
        }
        if ((!k.tanggal_lahir || k.tanggal_lahir === '-') && finalTanggalLahir && finalTanggalLahir !== '-') {
          updateFields.push('tanggal_lahir = ?');
          updateParams.push(finalTanggalLahir);
          k.tanggal_lahir = finalTanggalLahir;
        }
        if ((!k.jenis_kelamin || k.jenis_kelamin === '-' || k.jenis_kelamin === 'L') && clientJk && clientJk !== '-') {
          updateFields.push('jenis_kelamin = ?');
          updateParams.push(clientJk);
          k.jenis_kelamin = clientJk;
        }

        if (updateFields.length > 0) {
          updateParams.push(k.id);
          await pool.query(`UPDATE karyawan SET ${updateFields.join(', ')} WHERE id = ?`, updateParams);
          console.log(`🔄 [SYNC PROFIL PRESENSI] Profil NIP ${nip} (${nama}) diperbarui dari presensi mobile: ${updateFields.join(', ')}`);
        }
      }
    }

    // Simpan foto selfie
    let savedFotoUrl = '-';
    if (fotoSelfieBase64 && fotoSelfieBase64.length > 50) {
      try {
        const cleanBase64 = fotoSelfieBase64.replace(/^data:image\/\w+;base64,/, '');
        const buffer = Buffer.from(cleanBase64, 'base64');
        const filename = `selfie_${nip || id_karyawan}_${today}_${tipe.toLowerCase()}_${Date.now()}.jpg`;
        const filepath = path.join(UPLOADS_DIR, filename);
        fs.writeFileSync(filepath, buffer);
        savedFotoUrl = `/uploads/${filename}`;
      } catch (err) {
        console.error('Gagal menyimpan foto selfie:', err.message);
      }
    }

    // Pastikan Today menyesuaikan zona waktu lokal (WIB)
    const tzOffset = (new Date()).getTimezoneOffset() * 60000;
    const localToday = (new Date(Date.now() - tzOffset)).toISOString().split('T')[0];
    const finalDate = req.body.tanggal || localToday;

    // Cek record presensi hari ini
    const [records] = await pool.query(
      'SELECT * FROM absensi WHERE (nip = ? OR (nama = ? AND nama != "")) AND tanggal = ?',
      [nip, nama, finalDate]
    );

    let record = records.length > 0 ? records[0] : null;

    if (tipe === 'MASUK') {
      if (record && record.jam_masuk && record.jam_masuk !== '-') {
        return res.status(400).json({
          status: "error",
          message: `Anda (${nama || nip}) sudah melakukan Absen Masuk hari ini pada pukul ${record.jam_masuk}. Absen masuk hanya bisa 1 kali sehari!`
        });
      }

      if (record) {
        await pool.query(
          `UPDATE absensi SET jam_masuk = ?, status = ?, foto_masuk = IF(? != '-', ?, foto_masuk), latitude = IF(? != 0, ?, latitude), longitude = IF(? != 0, ?, longitude), maps_url = IF(? != '-', ?, maps_url), catatan = IF(? != '-', ?, catatan) WHERE id = ?`,
          [jam, computedStatus, savedFotoUrl, savedFotoUrl, userLat, userLat, userLng, userLng, mapsUrlInput, mapsUrlInput, catatanAbsen, catatanAbsen, record.id]
        );
        record.jam_masuk = jam;
        record.status = computedStatus;
        if (mapsUrlInput !== '-') record.maps_url = mapsUrlInput;
        if (userLat !== 0) record.latitude = userLat;
        if (userLng !== 0) record.longitude = userLng;
        if (catatanAbsen !== '-') record.catatan = catatanAbsen;
      } else {
        const [insertRes] = await pool.query(
          `INSERT INTO absensi (id_karyawan, nip, nama, departemen, tanggal, jam_masuk, jam_pulang, status, foto_masuk, foto_pulang, latitude, longitude, maps_url, maps_url_pulang, catatan, created_at)
           VALUES (?, ?, ?, ?, ?, ?, '-', ?, ?, '-', ?, ?, ?, '-', ?, NOW())`,
          [id_karyawan, nip || "-", nama || "Karyawan", departemen, finalDate, jam, computedStatus, savedFotoUrl, userLat, userLng, mapsUrlInput, catatanAbsen]
        );
        record = {
          id: insertRes.insertId,
          id_karyawan,
          nip: nip || "-",
          nama: nama || "Karyawan",
          departemen,
          tanggal: finalDate,
          jam_masuk: jam,
          jam_pulang: "-",
          status: computedStatus,
          foto_masuk: savedFotoUrl,
          latitude: userLat,
          longitude: userLng,
          maps_url: mapsUrlInput,
          maps_url_pulang: '-',
          catatan: catatanAbsen
        };
      }
    } else {
      // PULANG
      if (!record || !record.jam_masuk || record.jam_masuk === '-') {
        return res.status(400).json({
          status: "error",
          message: "Anda belum melakukan Absen Masuk hari ini! Lakukan absen masuk terlebih dahulu."
        });
      }

      if (record.jam_pulang && record.jam_pulang !== '-') {
        return res.status(400).json({
          status: "error",
          message: `Anda sudah melakukan Absen Keluar hari ini pada pukul ${record.jam_pulang}. Absen keluar hanya bisa 1 kali sehari!`
        });
      }

      await pool.query(
        `UPDATE absensi SET jam_pulang = ?, foto_pulang = IF(? != '-', ?, foto_pulang), maps_url_pulang = IF(? != '-', ?, maps_url_pulang), catatan = IF(? != '-', ?, catatan) WHERE id = ?`,
        [jam, savedFotoUrl, savedFotoUrl, mapsUrlInput, mapsUrlInput, catatanAbsen, catatanAbsen, record.id]
      );
      record.jam_pulang = jam;
      if (mapsUrlInput !== '-') record.maps_url_pulang = mapsUrlInput;
      if (catatanAbsen !== '-') record.catatan = catatanAbsen;
    }

    console.log(`⏱️ [ABSEN ${tipe}] ${nama || nip} - ${finalDate} ${jam} (Status: ${computedStatus}, Jarak: ${jarakMeter}m)`);

    res.json({
      status: "success",
      message: `Absen ${tipe} berhasil dicatat pada pukul ${jam} (Status: ${computedStatus}).`,
      foto_url: savedFotoUrl,
      jarak_meter: jarakMeter,
      radius_maks: radiusMaks,
      lokasi_kantor: config.lokasi_nama,
      data: record
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}

app.post('/api/absen', verifyToken, handleAbsen);
app.post('/api/mobile/presensi', verifyToken, handleAbsen);

// ==========================================
// 4. ENDPOINT PENGAJUAN IZIN / SAKIT (MOBILE & ADMIN)
// ==========================================
function parseDateToIso(rawDate) {
  const tzOffset = (new Date()).getTimezoneOffset() * 60000;
  const fallbackToday = (new Date(Date.now() - tzOffset)).toISOString().split('T')[0];
  if (!rawDate) return fallbackToday;
  if (rawDate instanceof Date) {
    const off = rawDate.getTimezoneOffset() * 60000;
    return (new Date(rawDate.getTime() - off)).toISOString().split('T')[0];
  }
  if (typeof rawDate !== 'string') return fallbackToday;
  const cleaned = rawDate.trim();
  // Jika format dd/MM/yyyy dari Android
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(cleaned)) {
    const [d, m, y] = cleaned.split('/');
    return `${y}-${m}-${d}`;
  }
  if (/^\d{4}-\d{2}-\d{2}/.test(cleaned)) {
    return cleaned.substring(0, 10);
  }
  return fallbackToday;
}

// Helper membuat daftar tanggal harian (inklusif) untuk izin multi-hari (maks 31 hari)
function getDateRangeIso(startIso, endIso) {
  const dates = [];
  const start = new Date(`${startIso}T00:00:00`);
  const end = new Date(`${endIso}T00:00:00`);
  if (isNaN(start.getTime())) return [startIso];
  if (isNaN(end.getTime()) || end < start) return [startIso];

  const curr = new Date(start);
  let count = 0;
  while (curr <= end && count < 31) {
    const y = curr.getFullYear();
    const m = String(curr.getMonth() + 1).padStart(2, '0');
    const d = String(curr.getDate()).padStart(2, '0');
    dates.push(`${y}-${m}-${d}`);
    curr.setDate(curr.getDate() + 1);
    count++;
  }
  return dates.length > 0 ? dates : [startIso];
}

// Pengajuan izin dari aplikasi HP Karyawan (Status awal: Pending, menunggu persetujuan Admin)
async function handleMobileIzin(req, res) {
  try {
    const idKaryawanReq = (req.body.karyawan_id || req.body.id_karyawan || '').trim();
    const nip = (req.body.nip || req.body.nik || '').trim();
    const nama = (req.body.nama || '').trim();
    const jenisIzin = (req.body.jenis_izin || req.body.jenis || 'Sakit').trim();
    const tglMulai = parseDateToIso(req.body.tanggal_mulai || req.body.tanggal);
    const tglSelesai = parseDateToIso(req.body.tanggal_selesai || tglMulai);
    const alasan = (req.body.alasan || req.body.keterangan || '-').trim();
    const buktiFotoBase64 = req.body.bukti_foto || '';

    let savedBuktiUrl = '-';
    if (buktiFotoBase64 && buktiFotoBase64.length > 50) {
      try {
        const cleanBase64 = buktiFotoBase64.replace(/^data:image\/\w+;base64,/, '');
        const buffer = Buffer.from(cleanBase64, 'base64');
        const filename = `izin_${nip || idKaryawanReq || 'k'}_${Date.now()}.jpg`;
        const filepath = path.join(UPLOADS_DIR, filename);
        fs.writeFileSync(filepath, buffer);
        savedBuktiUrl = `/uploads/${filename}`;
      } catch (err) {
        console.error('Gagal menyimpan foto bukti izin:', err.message);
      }
    }

    const [karyawanRows] = await pool.query(
      'SELECT * FROM karyawan WHERE nip = ? OR id = ? OR id_karyawan = ? OR (nama = ? AND nama != "")',
      [nip, idKaryawanReq, idKaryawanReq, nama]
    );
    const karyawanObj = karyawanRows.length > 0 ? karyawanRows[0] : null;

    const finalNama = karyawanObj ? karyawanObj.nama : (nama || "Karyawan");
    const finalNip = karyawanObj ? karyawanObj.nip : (nip || "-");
    const finalId = karyawanObj ? karyawanObj.id : (idKaryawanReq || `K-${finalNip}`);
    const finalDept = karyawanObj ? karyawanObj.departemen : "Produksi Pabrik";

    // Jika karyawan belum terdaftar di master karyawan, daftarkan otomatis agar data sinkron
    if (!karyawanObj && finalNip && finalNip !== '-') {
      await pool.query(
        `INSERT INTO karyawan (id, id_karyawan, nip, nama, departemen, jenis_kelamin, tempat_lahir, tanggal_lahir, alamat, device_id, created_at)
         VALUES (?, ?, ?, ?, ?, 'L', '-', '-', '-', '-', NOW())
         ON DUPLICATE KEY UPDATE nama = VALUES(nama), departemen = VALUES(departemen)`,
        [finalId, finalId, finalNip, finalNama, finalDept]
      );
      console.log(`✅ [AUTO-REGISTER IZIN] Karyawan ${finalNama} (NIP: ${finalNip}) otomatis didaftarkan ke Master Karyawan saat pengajuan izin.`);
    }

    const [insertIzin] = await pool.query(
      `INSERT INTO pengajuan_izin (id_karyawan, nip, nama, departemen, jenis_izin, tanggal_mulai, tanggal_selesai, alasan, bukti_foto, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Pending', NOW())`,
      [finalId, finalNip, finalNama, finalDept, jenisIzin, tglMulai, tglSelesai, alasan, savedBuktiUrl]
    );

    console.log(`📩 [PENGAJUAN IZIN MOBILE] ${finalNama} (${finalNip}) - ${jenisIzin} (${tglMulai} s/d ${tglSelesai}) [Pending]`);

    res.json({
      status: "success",
      message: `Permohonan ${jenisIzin} berhasil dikirim dan menunggu persetujuan Admin.`,
      data: {
        id: insertIzin.insertId,
        id_karyawan: finalId,
        nip: finalNip,
        nama: finalNama,
        jenis_izin: jenisIzin,
        tanggal_mulai: tglMulai,
        tanggal_selesai: tglSelesai,
        status: "Pending"
      }
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}

// Input izin langsung oleh Admin (Langsung Disetujui)
async function handleInputIzin(req, res) {
  try {
    const nip = (req.body.nip || req.body.nik || '').trim();
    const nama = (req.body.nama || '').trim();
    const jenisIzin = (req.body.jenis_izin || req.body.jenis || 'Sakit').trim();
    const tanggal = parseDateToIso(req.body.tanggal || req.body.tanggal_mulai);
    const alasan = (req.body.alasan || req.body.keterangan || 'Diinput oleh Admin').trim();

    const [karyawanRows] = await pool.query('SELECT * FROM karyawan WHERE nip = ? OR nama = ?', [nip, nama]);
    const karyawanObj = karyawanRows.length > 0 ? karyawanRows[0] : null;

    const finalNama = karyawanObj ? karyawanObj.nama : (nama || "Karyawan");
    const finalNip = karyawanObj ? karyawanObj.nip : (nip || "-");
    const finalId = karyawanObj ? karyawanObj.id : `K-${finalNip}`;
    const finalDept = karyawanObj ? karyawanObj.departemen : "Produksi Pabrik";

    const [insertIzin] = await pool.query(
      `INSERT INTO pengajuan_izin (id_karyawan, nip, nama, departemen, jenis_izin, tanggal_mulai, tanggal_selesai, alasan, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Disetujui (Admin)', NOW())`,
      [finalId, finalNip, finalNama, finalDept, jenisIzin, tanggal, tanggal, alasan]
    );

    // Upsert ke tabel absensi agar rekap langsung tercatat sebagai IZIN/SAKIT
    const [existingAbsen] = await pool.query('SELECT * FROM absensi WHERE nip = ? AND tanggal = ?', [finalNip, tanggal]);
    if (existingAbsen.length > 0) {
      await pool.query('UPDATE absensi SET status = ? WHERE id = ?', [jenisIzin.toUpperCase(), existingAbsen[0].id]);
    } else {
      await pool.query(
        `INSERT INTO absensi (id_karyawan, nip, nama, departemen, tanggal, jam_masuk, jam_pulang, status, created_at)
         VALUES (?, ?, ?, ?, ?, '-', '-', ?, NOW())`,
        [finalId, finalNip, finalNama, finalDept, tanggal, jenisIzin.toUpperCase()]
      );
    }

    console.log(`📝 [IZIN ADMIN] ${finalNama} (${finalNip}) - ${jenisIzin} tanggal ${tanggal}`);

    res.json({
      status: "success",
      message: `Berhasil mencatat ${jenisIzin} untuk ${finalNama} (${finalNip}) pada tanggal ${tanggal}.`,
      data: {
        id: insertIzin.insertId,
        nip: finalNip,
        nama: finalNama,
        jenis_izin: jenisIzin,
        tanggal: tanggal,
        status: "Disetujui (Admin)"
      }
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}

app.post('/api/admin/izin', handleInputIzin);
app.post('/api/izin', handleInputIzin);
app.post('/api/mobile/izin', handleMobileIzin);

// GET DAFTAR IZIN
app.get('/api/admin/izin', async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT * FROM pengajuan_izin ORDER BY created_at DESC');
    res.json({
      status: "success",
      total: rows.length,
      data: rows
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// UPDATE STATUS IZIN (SETUJUI / TOLAK OLEH ADMIN)
app.put('/api/admin/izin/:id/status', async (req, res) => {
  try {
    const izinId = parseInt(req.params.id);
    const statusPersetujuan = (req.body.status || 'Disetujui').trim();

    const [izinRows] = await pool.query('SELECT * FROM pengajuan_izin WHERE id = ?', [izinId]);
    if (izinRows.length === 0) {
      return res.status(404).json({ status: "error", message: "Data izin tidak ditemukan" });
    }
    const izinObj = izinRows[0];

    await pool.query('UPDATE pengajuan_izin SET status = ? WHERE id = ?', [statusPersetujuan, izinId]);

    // Jika disetujui oleh Admin, otomatis catat ke tabel absensi untuk seluruh rentang tanggal (tanggal_mulai s/d tanggal_selesai)
    if (statusPersetujuan.toLowerCase().startsWith('disetujui')) {
      const tglMulaiIso = parseDateToIso(izinObj.tanggal_mulai);
      const tglSelesaiIso = parseDateToIso(izinObj.tanggal_selesai || izinObj.tanggal_mulai);
      const daftarTanggal = getDateRangeIso(tglMulaiIso, tglSelesaiIso);
      const statusAbsen = (izinObj.jenis_izin || 'IZIN').toUpperCase();

      for (const tglIzin of daftarTanggal) {
        const [existingAbsen] = await pool.query('SELECT * FROM absensi WHERE nip = ? AND tanggal = ?', [izinObj.nip, tglIzin]);
        if (existingAbsen.length > 0) {
          await pool.query('UPDATE absensi SET status = ? WHERE id = ?', [statusAbsen, existingAbsen[0].id]);
        } else {
          await pool.query(
            `INSERT INTO absensi (id_karyawan, nip, nama, departemen, tanggal, jam_masuk, jam_pulang, status, created_at)
             VALUES (?, ?, ?, ?, ?, '-', '-', ?, NOW())`,
            [izinObj.id_karyawan || `K-${izinObj.nip}`, izinObj.nip || '-', izinObj.nama || 'Karyawan', izinObj.departemen || 'Produksi Pabrik', tglIzin, statusAbsen]
          );
        }
      }
    }

    console.log(`📝 [UPDATE STATUS IZIN] ID ${izinId} diubah menjadi: ${statusPersetujuan}`);
    return res.json({
      status: "success",
      message: `Pengajuan izin berhasil di-${statusPersetujuan.toLowerCase()}`
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// HAPUS DATA PENGAJUAN IZIN OLEH ADMIN
app.delete('/api/admin/izin/:id', async (req, res) => {
  try {
    const izinId = parseInt(req.params.id);
    if (!izinId || isNaN(izinId)) {
      return res.status(400).json({ status: "error", message: "ID izin tidak valid." });
    }

    const [existing] = await pool.query('SELECT * FROM pengajuan_izin WHERE id = ?', [izinId]);
    if (existing.length === 0) {
      return res.status(404).json({ status: "error", message: "Data izin tidak ditemukan." });
    }

    const item = existing[0];
    await pool.query('DELETE FROM pengajuan_izin WHERE id = ?', [izinId]);
    console.log(`🗑️ [HAPUS IZIN] Izin ID ${izinId} milik ${item.nama} (${item.nip}) dihapus dari MySQL.`);

    return res.json({
      status: "success",
      message: `Data permohonan izin ${item.nama} berhasil dihapus.`
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// KOREKSI / EDIT STATUS & JAM PRESENSI HARIAN OLEH ADMIN
app.put('/api/admin/absensi/:id', async (req, res) => {
  try {
    const absenId = parseInt(req.params.id);
    const jamMasuk = (req.body.jam_masuk || '-').trim();
    const jamPulang = (req.body.jam_pulang || '-').trim();
    const statusBaru = (req.body.status || 'TEPAT_WAKTU').trim().toUpperCase();

    const [rows] = await pool.query('SELECT * FROM absensi WHERE id = ?', [absenId]);
    if (rows.length === 0) {
      return res.status(404).json({ status: "error", message: "Data presensi tidak ditemukan." });
    }

    await pool.query(
      'UPDATE absensi SET jam_masuk = ?, jam_pulang = ?, status = ? WHERE id = ?',
      [jamMasuk || '-', jamPulang || '-', statusBaru, absenId]
    );

    console.log(`✏️ [KOREKSI PRESENSI] ID ${absenId} (${rows[0].nama}) diubah: Masuk=${jamMasuk}, Pulang=${jamPulang}, Status=${statusBaru}`);
    return res.json({
      status: "success",
      message: `Data presensi ${rows[0].nama} berhasil dikoreksi.`
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// HAPUS DATA PRESENSI TERTENTU OLEH ADMIN
app.delete('/api/admin/absensi/:id', async (req, res) => {
  try {
    const absenId = parseInt(req.params.id);
    if (!absenId || isNaN(absenId)) {
      return res.status(400).json({ status: "error", message: "ID presensi tidak valid." });
    }

    const [existing] = await pool.query('SELECT * FROM absensi WHERE id = ?', [absenId]);
    if (existing.length === 0) {
      return res.status(404).json({ status: "error", message: "Data presensi tidak ditemukan." });
    }

    const item = existing[0];
    await pool.query('DELETE FROM absensi WHERE id = ?', [absenId]);
    console.log(`🗑️ [HAPUS PRESENSI] Presensi ID ${absenId} milik ${item.nama} (${item.nip}) berhasil dihapus oleh Admin.`);

    return res.json({
      status: "success",
      message: `Data presensi ${item.nama} (NIP: ${item.nip}) berhasil dihapus.`
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// ==========================================
// 5. ENDPOINT DASHBOARD & REKAP ABSENSI ADMIN
// ==========================================
async function handleAdminAbsensi(req, res) {
  try {
    await syncMissingKaryawan();
    const today = new Date();
    // Gunakan zona waktu server lokal saat ini untuk mendapatkan tanggal "hari ini" yang akurat
    const tzOffset = today.getTimezoneOffset() * 60000;
    const localISOTime = (new Date(today.getTime() - tzOffset)).toISOString().slice(0, -1);
    const todayStr = localISOTime.split('T')[0];
    const bulanFilter = (req.query.bulan || todayStr.substring(0, 7)).trim(); // YYYY-MM

    const [absensiRows] = await pool.query('SELECT * FROM absensi ORDER BY id DESC');
    const [karyawanRows] = await pool.query('SELECT * FROM karyawan');
    const config = await getPengaturanKantor();

    const absensiHariIni = absensiRows.filter(a => {
      const t = a.tanggal ? (typeof a.tanggal === 'string' ? a.tanggal : a.tanggal.toISOString().split('T')[0]) : '';
      return t === todayStr;
    });

    const absensiBulanIni = absensiRows.filter(a => {
      const t = a.tanggal ? (typeof a.tanggal === 'string' ? a.tanggal : a.tanggal.toISOString().split('T')[0]) : '';
      return t.startsWith(bulanFilter);
    });

    const totalMaster = karyawanRows.length;
    const hadirHariIni = absensiHariIni.filter(a => a.jam_masuk && a.jam_masuk !== '-').length;
    const terlambatHariIni = absensiHariIni.filter(a => (a.status || '').toUpperCase() === 'TERLAMBAT').length;

    const totalHadirBulanIni = absensiBulanIni.filter(a => a.jam_masuk && a.jam_masuk !== '-').length;
    const totalIzinBulanIni = absensiBulanIni.filter(a => ['SAKIT', 'IZIN', 'CUTI'].includes((a.status || '').toUpperCase())).length;
    const totalTerlambatBulanIni = absensiBulanIni.filter(a => (a.status || '').toUpperCase() === 'TERLAMBAT').length;

    const totalTerikatDevice = karyawanRows.filter(k => k.device_id && k.device_id !== '-').length;

    const mappedAbsensi = absensiRows.map(a => {
      let cleanTgl = todayStr;
      if (a.tanggal) {
        if (typeof a.tanggal === 'string') {
          cleanTgl = a.tanggal.split('T')[0].split(' ')[0];
        } else if (a.tanggal instanceof Date) {
          // Parse as local string to avoid UTC shift
          const tzOff = a.tanggal.getTimezoneOffset() * 60000;
          cleanTgl = (new Date(a.tanggal.getTime() - tzOff)).toISOString().split('T')[0];
        } else {
          cleanTgl = String(a.tanggal).split('T')[0].split(' ')[0];
        }
      }
      return {
        ...a,
        tanggal: cleanTgl,
        foto_masuk_url: a.foto_masuk,
        foto_pulang_url: a.foto_pulang
      };
    });

    const mappedHariIni = mappedAbsensi.filter(a => a.tanggal === todayStr);

    res.json({
      status: "success",
      tanggal_hari_ini: todayStr,
      bulan: bulanFilter,
      total_karyawan: totalMaster,
      total_karyawan_master: totalMaster,
      total_hadir: hadirHariIni,
      total_terlambat: terlambatHariIni,
      total_hadir_bulan_ini: totalHadirBulanIni,
      total_izin_bulan_ini: totalIzinBulanIni,
      total_terlambat_bulan_ini: totalTerlambatBulanIni,
      total_terikat_device: totalTerikatDevice,
      config: {
        ...config,
        total_karyawan: totalMaster
      },
      data_hari_ini: mappedHariIni,
      data: mappedAbsensi
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
}

app.get('/api/admin/absensi', handleAdminAbsensi);

// ==========================================
// 6. ENDPOINT REKAPITULASI PAYROLL BULANAN
// ==========================================
app.get('/api/admin/rekap-payroll', async (req, res) => {
  try {
    const today = new Date();
    const tzOffset = today.getTimezoneOffset() * 60000;
    const localISOTime = (new Date(today.getTime() - tzOffset)).toISOString().slice(0, -1);
    const todayStr = localISOTime.split('T')[0];
    const bulan = (req.query.bulan || todayStr.substring(0, 7)).trim(); // YYYY-MM
    const hariKerjaDefault = parseInt(req.query.hari_kerja || 22);
    const deptFilter = (req.query.dept || 'SEMUA').trim();

    const config = await getPengaturanKantor();
    const batasTerlambatStr = (config.batas_terlambat || config.jam_masuk || '08:00').replace('.', ':').trim();
    const jamPulangStr = (config.jam_pulang || '17:00').replace('.', ':').trim();
    const [hB, mB] = batasTerlambatStr.split(':').map(Number);
    const [hPStd, mPStd] = jamPulangStr.split(':').map(Number);

    const [karyawanRows] = await pool.query('SELECT * FROM karyawan ORDER BY nama ASC');
    const [absensiBulan] = await pool.query(
      'SELECT * FROM absensi WHERE DATE_FORMAT(tanggal, "%Y-%m") = ?',
      [bulan]
    );
    const [izinBulan] = await pool.query(
      'SELECT * FROM pengajuan_izin WHERE DATE_FORMAT(tanggal_mulai, "%Y-%m") = ? OR DATE_FORMAT(created_at, "%Y-%m") = ?',
      [bulan, bulan]
    );

    let filteredKaryawan = karyawanRows;
    if (deptFilter !== 'SEMUA') {
      filteredKaryawan = karyawanRows.filter(k => (k.departemen || '') === deptFilter);
    }

    const rekapData = [];
    let grandTotalHadir = 0;
    let grandTotalTerlambat = 0;
    let grandTotalMenitTerlambat = 0;
    let grandTotalIzin = 0;
    let grandTotalAlpa = 0;

    for (let i = 0; i < filteredKaryawan.length; i++) {
      const k = filteredKaryawan[i];
      const nip = k.nip || k.nik || '-';

      // Absensi karyawan di bulan ini
      const empAbsen = absensiBulan.filter(a => a.nip === nip || (a.nama && a.nama.toLowerCase() === k.nama.toLowerCase()));
      // Izin karyawan di bulan ini
      const empIzin = izinBulan.filter(iz => (iz.nip === nip || (iz.nama && iz.nama.toLowerCase() === k.nama.toLowerCase())) && (iz.status || '').toLowerCase() === 'disetujui');

      let totalHadir = 0;
      let totalTepatWaktu = 0;
      let totalTerlambat = 0;
      let totalMenitTerlambat = 0;

      empAbsen.forEach(a => {
        if (a.jam_masuk && a.jam_masuk !== '-') {
          totalHadir++;
          const cleanMasuk = a.jam_masuk.replace('.', ':').trim();
          const [hM, mM] = cleanMasuk.split(':').map(Number);
          if (!isNaN(hM) && !isNaN(mM) && !isNaN(hB) && !isNaN(mB)) {
            const menitMasuk = hM * 60 + mM;
            const menitBatas = hB * 60 + mB;
            if (menitMasuk > menitBatas) {
              totalTerlambat++;
              totalMenitTerlambat += (menitMasuk - menitBatas);
            } else {
              totalTepatWaktu++;
            }
          } else {
            if ((a.status || '').toUpperCase() === 'TERLAMBAT') {
              totalTerlambat++;
            } else {
              totalTepatWaktu++;
            }
          }
        }
      });

      // Hitung Izin: dari absensi (status SAKIT/IZIN/CUTI) + pengajuan izin yang disetujui
      const tglIzinAbsen = empAbsen.filter(a => ['SAKIT', 'IZIN', 'CUTI'].includes((a.status || '').toUpperCase())).map(a => String(a.tanggal).split('T')[0]);
      let totalIzin = tglIzinAbsen.length;
      empIzin.forEach(iz => {
        const tglM = iz.tanggal_mulai ? String(iz.tanggal_mulai).split('T')[0] : '';
        if (tglM && !tglIzinAbsen.includes(tglM)) {
          totalIzin++;
        }
      });

      // Hitung Alpa (Tanpa Keterangan)
      const alpaCalc = Math.max(0, hariKerjaDefault - (totalHadir + totalIzin));

      // Persentase kehadiran terhadap hari kerja efektif
      const persentaseHadir = hariKerjaDefault > 0 ? Math.min(100, Math.round(((totalHadir + totalIzin) / hariKerjaDefault) * 100)) : 100;

      // Evaluasi kedisiplinan
      let evaluasiDisiplin = 'Sangat Baik';
      if (alpaCalc > 2 || totalTerlambat > 4) {
        evaluasiDisiplin = 'Perlu Peringatan';
      } else if (alpaCalc > 0 || totalTerlambat > 2) {
        evaluasiDisiplin = 'Cukup';
      } else if (totalTerlambat > 0) {
        evaluasiDisiplin = 'Baik';
      }

      grandTotalHadir += totalHadir;
      grandTotalTerlambat += totalTerlambat;
      grandTotalMenitTerlambat += totalMenitTerlambat;
      grandTotalIzin += totalIzin;
      grandTotalAlpa += alpaCalc;

      rekapData.push({
        no: i + 1,
        id_karyawan: k.id_karyawan || k.id,
        nip: nip,
        nama: k.nama,
        departemen: k.departemen || 'Produksi Pabrik',
        hari_kerja_efektif: hariKerjaDefault,
        total_hadir: totalHadir,
        total_tepat_waktu: totalTepatWaktu,
        total_terlambat: totalTerlambat,
        total_menit_terlambat: totalMenitTerlambat,
        total_izin: totalIzin,
        total_alpa: alpaCalc,
        persentase_kehadiran: `${persentaseHadir}%`,
        evaluasi_disiplin: evaluasiDisiplin
      });
    }

    const rataKehadiran = filteredKaryawan.length > 0 
      ? Math.round(rekapData.reduce((acc, r) => acc + parseInt(r.persentase_kehadiran), 0) / filteredKaryawan.length) 
      : 100;

    res.json({
      status: "success",
      bulan: bulan,
      hari_kerja_efektif: hariKerjaDefault,
      departemen_filter: deptFilter,
      config: {
        batas_terlambat: batasTerlambatStr,
        lokasi_nama: config.lokasi_nama || 'Kantor Pusat PT Fadiya Kencana'
      },
      summary: {
        total_karyawan: filteredKaryawan.length,
        rata_kehadiran_persen: rataKehadiran,
        total_hadir_kumulatif: grandTotalHadir,
        total_terlambat_kumulatif: grandTotalTerlambat,
        total_menit_terlambat_kumulatif: grandTotalMenitTerlambat,
        total_izin_kumulatif: grandTotalIzin,
        total_alpa_kumulatif: grandTotalAlpa
      },
      data: rekapData
    });
  } catch (err) {
    console.error('Gagal mengambil rekap payroll:', err.message);
    res.status(500).json({ status: "error", message: err.message });
  }
});

// ENDPOINT KHUSUS RIWAYAT ABSENSI KARYAWAN (RINGAN & HEMAT KUOTA UNTUK APLIKASI HP)
app.get('/api/karyawan/riwayat', async (req, res) => {
  try {
    const nip = (req.query.nip || req.query.nik || '').trim();
    const idKaryawan = (req.query.id_karyawan || req.query.id || '').trim();
    if (!nip && !idKaryawan) {
      return res.status(400).json({ status: "error", message: "Parameter NIP atau ID Karyawan diperlukan" });
    }

    const [rows] = await pool.query(
      'SELECT * FROM absensi WHERE nip = ? OR id_karyawan = ? ORDER BY tanggal DESC, id DESC LIMIT 60',
      [nip, idKaryawan]
    );

    const tzOffset = (new Date()).getTimezoneOffset() * 60000;
    const todayStr = (new Date(Date.now() - tzOffset)).toISOString().split('T')[0];

    const mapped = rows.map(a => {
      let cleanTgl = todayStr;
      if (a.tanggal) {
        if (typeof a.tanggal === 'string') {
          cleanTgl = a.tanggal.split('T')[0].split(' ')[0];
        } else if (a.tanggal instanceof Date) {
          const tzOff = a.tanggal.getTimezoneOffset() * 60000;
          cleanTgl = (new Date(a.tanggal.getTime() - tzOff)).toISOString().split('T')[0];
        } else {
          cleanTgl = String(a.tanggal).split('T')[0].split(' ')[0];
        }
      }
      return {
        ...a,
        tanggal: cleanTgl
      };
    });

    return res.json({
      status: "success",
      total: mapped.length,
      data: mapped
    });
  } catch (err) {
    return res.status(500).json({ status: "error", message: err.message });
  }
});

// ENDPOINT PENGATURAN JAM KERJA & PENGUMUMAN KANTOR
app.get('/api/admin/config', async (req, res) => {
  try {
    const cfg = await getPengaturanKantor();
    res.json({
      status: "success",
      data: cfg
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

app.post('/api/admin/config', async (req, res) => {
  try {
    const jamMasuk = (req.body.jam_masuk || req.body.jamMasukKerja || '08:00').trim();
    const batasTerlambat = (req.body.batas_terlambat || req.body.batasTerlambat || jamMasuk).trim();
    const jamPulang = (req.body.jam_pulang || req.body.jamPulangKerja || '17:00').trim();
    const pengumuman = (req.body.pengumuman || 'Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.').trim();
    const lokasiNama = (req.body.lokasi_nama || req.body.lokasiNama || 'Kantor Pusat PT Fadiya Kencana').trim();
    const lokasiLat = parseFloat(req.body.lokasi_latitude !== undefined ? req.body.lokasi_latitude : (req.body.lokasiLatitude !== undefined ? req.body.lokasiLatitude : -7.58854));
    const lokasiLng = parseFloat(req.body.lokasi_longitude !== undefined ? req.body.lokasi_longitude : (req.body.lokasiLongitude !== undefined ? req.body.lokasiLongitude : 110.79566));
    const radiusMeter = parseInt(req.body.radius_meter !== undefined ? req.body.radius_meter : (req.body.radiusMeter !== undefined ? req.body.radiusMeter : 100));
    const wajibRadius = parseInt(req.body.wajib_dalam_radius !== undefined ? req.body.wajib_dalam_radius : (req.body.wajibDalamRadius !== undefined ? req.body.wajibDalamRadius : 0));

    await pool.query(
      `INSERT INTO pengaturan_kantor (id, jam_masuk, batas_terlambat, jam_pulang, pengumuman, lokasi_nama, lokasi_latitude, lokasi_longitude, radius_meter, wajib_dalam_radius)
       VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         jam_masuk = VALUES(jam_masuk),
         batas_terlambat = VALUES(batas_terlambat),
         jam_pulang = VALUES(jam_pulang),
         pengumuman = VALUES(pengumuman),
         lokasi_nama = VALUES(lokasi_nama),
         lokasi_latitude = VALUES(lokasi_latitude),
         lokasi_longitude = VALUES(lokasi_longitude),
         radius_meter = VALUES(radius_meter),
         wajib_dalam_radius = VALUES(wajib_dalam_radius)`,
      [jamMasuk, batasTerlambat, jamPulang, pengumuman, lokasiNama, isNaN(lokasiLat) ? -7.58854 : lokasiLat, isNaN(lokasiLng) ? 110.79566 : lokasiLng, isNaN(radiusMeter) ? 100 : radiusMeter, isNaN(wajibRadius) ? 0 : wajibRadius]
    );

    console.log(`⚙️ [CONFIG UPDATED] Jam Masuk: ${jamMasuk}, Batas Terlambat: ${batasTerlambat}, Jam Pulang: ${jamPulang}, Lokasi: ${lokasiNama} (${lokasiLat}, ${lokasiLng}), Radius: ${radiusMeter}m, Wajib: ${wajibRadius}`);
    res.json({
      status: "success",
      message: "Pengaturan kantor (jam kerja & titik lokasi) berhasil diperbarui!",
      data: {
        jam_masuk: jamMasuk,
        batas_terlambat: batasTerlambat,
        jam_pulang: jamPulang,
        pengumuman: pengumuman,
        lokasi_nama: lokasiNama,
        lokasi_latitude: isNaN(lokasiLat) ? -7.58854 : lokasiLat,
        lokasi_longitude: isNaN(lokasiLng) ? 110.79566 : lokasiLng,
        radius_meter: isNaN(radiusMeter) ? 100 : radiusMeter,
        wajib_dalam_radius: isNaN(wajibRadius) ? 0 : wajibRadius
      }
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// ENDPOINT ADMIN LOGIN
app.post('/api/admin/login', async (req, res) => {
  const username = (req.body.username || '').trim();
  const password = (req.body.password || '').trim();

  try {
    let isValid = false;
    let role = 'administrator';

    const [rows] = await pool.query('SELECT * FROM admins WHERE username = ?', [username]);
    if (rows.length > 0) {
      const adminRow = rows[0];
      role = adminRow.role || 'administrator';
      const storedHash = adminRow.password_hash || '';
      if (storedHash.startsWith('$2')) {
        isValid = await bcrypt.compare(password, storedHash);
      } else {
        isValid = (password === storedHash);
      }
    }

    // Fallback darurat HANYA jika database admins belum diinisialisasi / kosong
    if (!isValid && rows.length === 0 && username === 'admin' && (password === 'admin' || password === 'membersuki')) {
      isValid = true;
    }

    if (isValid) {
      const token = jwt.sign({ username, role }, JWT_SECRET, { expiresIn: '12h' });

      // Set cookie untuk browser Web Admin
      res.cookie('admin_token', token, {
        httpOnly: true, // Tidak bisa dibaca via javascript browser
        maxAge: 12 * 60 * 60 * 1000 // 12 jam
      });

      return res.json({
        status: "success",
        message: "Login Admin Berhasil",
        token: token,
        data: { username, role }
      });
    }

    return res.status(401).json({
      status: "error",
      message: "Username atau Password Admin salah!"
    });
  } catch (err) {
    return res.status(500).json({
      status: "error",
      message: err.message
    });
  }
});

// ENDPOINT ADMIN LOGOUT
app.post('/api/admin/logout', (req, res) => {
  res.clearCookie('admin_token');
  return res.json({
    status: "success",
    message: "Berhasil keluar dari sesi Admin"
  });
});

// ENDPOINT UBAH PASSWORD ADMIN
app.put('/api/admin/password', async (req, res) => {
  try {
    const passLama = (req.body.password_lama || '').trim();
    const passBaru = (req.body.password_baru || '').trim();

    if (!passLama || !passBaru || passBaru.length < 4) {
      return res.status(400).json({
        status: "error",
        message: "Kata sandi baru minimal harus 4 karakter."
      });
    }

    const [rows] = await pool.query('SELECT * FROM admins WHERE username = "admin" LIMIT 1');
    let isValid = false;
    if (rows.length > 0) {
      const storedHash = rows[0].password_hash || '';
      if (storedHash.startsWith('$2')) {
        isValid = await bcrypt.compare(passLama, storedHash);
      } else {
        isValid = (passLama === storedHash);
      }
    }
    // Fallback darurat HANYA jika database admins belum ada isinya
    if (!isValid && rows.length === 0 && (passLama === 'admin' || passLama === 'membersuki')) {
      isValid = true;
    }

    if (!isValid) {
      return res.status(401).json({
        status: "error",
        message: "Kata sandi lama yang Anda masukkan tidak sesuai."
      });
    }

    const newHash = await bcrypt.hash(passBaru, 10);
    await pool.query(
      'INSERT INTO admins (username, password_hash, role) VALUES ("admin", ?, "administrator") ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)',
      [newHash]
    );

    console.log('🔐 [ADMIN PASSWORD] Kata sandi administrator berhasil diperbarui.');
    return res.json({
      status: "success",
      message: "Kata sandi Administrator berhasil diperbarui."
    });
  } catch (err) {
    return res.status(500).json({ status: "error", message: err.message });
  }
});

// ==========================================
// ASISTEN AI HRD (TANYA JAWAB DATA KEHADIRAN BERBASIS GEMINI)
// ==========================================
// Middleware khusus Admin: menerima cookie Web Admin ATAU header Bearer dari Admin HP
function verifyAdmin(req, res, next) {
  const authHeader = req.headers['authorization'] || '';
  const token = (authHeader.startsWith('Bearer ') ? authHeader.split(' ')[1] : null) || req.cookies.admin_token;
  if (!token) {
    return res.status(401).json({ status: "error", message: "Akses ditolak. Silakan login sebagai Administrator." });
  }
  jwt.verify(token, JWT_SECRET, (err, decoded) => {
    if (err || !decoded || decoded.role !== 'administrator') {
      return res.status(401).json({ status: "error", message: "Sesi Admin tidak valid atau kedaluwarsa. Silakan login ulang." });
    }
    req.admin = decoded;
    next();
  });
}

function toIsoDateLocal(value) {
  if (!value) return '-';
  if (value instanceof Date) {
    const off = value.getTimezoneOffset() * 60000;
    return new Date(value.getTime() - off).toISOString().split('T')[0];
  }
  return String(value).split('T')[0].split(' ')[0];
}

// Menyusun ringkasan data HR yang dikirim ke AI.
// Demi privasi, HANYA nama, NIP, departemen, tanggal, jam & status yang dikirim
// (tanpa foto, koordinat GPS, alamat, tanggal lahir, maupun device ID).
async function susunKonteksDataHr() {
  const tzOffset = (new Date()).getTimezoneOffset() * 60000;
  const todayStr = (new Date(Date.now() - tzOffset)).toISOString().split('T')[0];

  const config = await getPengaturanKantor();
  const [karyawanRows] = await pool.query('SELECT nip, nama, departemen FROM karyawan ORDER BY nama');
  const [absensiRows] = await pool.query(
    `SELECT nip, nama, departemen, tanggal, jam_masuk, jam_pulang, status
     FROM absensi
     WHERE tanggal >= DATE_SUB(CURDATE(), INTERVAL 62 DAY)
     ORDER BY tanggal DESC, nama
     LIMIT 4000`
  );
  const [izinRows] = await pool.query(
    `SELECT nip, nama, departemen, jenis_izin, tanggal_mulai, tanggal_selesai, alasan, status
     FROM pengajuan_izin
     WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 62 DAY)
     ORDER BY created_at DESC
     LIMIT 500`
  );

  const bersih = (v) => String(v ?? '-').replace(/[|\r\n]+/g, ' ').trim() || '-';

  const daftarKaryawan = karyawanRows
    .map(k => `${bersih(k.nip)}|${bersih(k.nama)}|${bersih(k.departemen)}`)
    .join('\n');
  const daftarAbsensi = absensiRows
    .map(a => `${toIsoDateLocal(a.tanggal)}|${bersih(a.nip)}|${bersih(a.nama)}|${bersih(a.departemen)}|${bersih(a.jam_masuk)}|${bersih(a.jam_pulang)}|${bersih(a.status).toUpperCase()}`)
    .join('\n');
  const daftarIzin = izinRows
    .map(i => `${bersih(i.nip)}|${bersih(i.nama)}|${bersih(i.departemen)}|${bersih(i.jenis_izin)}|${toIsoDateLocal(i.tanggal_mulai)}|${toIsoDateLocal(i.tanggal_selesai)}|${bersih(i.alasan).slice(0, 120)}|${bersih(i.status)}`)
    .join('\n');

  return `
Tanggal hari ini: ${todayStr}
Aturan jam kerja kantor: Jam masuk ${config.jam_masuk}, batas terlambat ${config.batas_terlambat}, jam pulang ${config.jam_pulang}.
Arti status presensi: TEPAT_WAKTU = hadir tepat waktu, TERLAMBAT = hadir terlambat, IZIN/SAKIT/CUTI = tidak masuk dengan izin, ALPA = tidak masuk tanpa keterangan, LUPA PULANG = absen masuk tanpa absen pulang.
Karyawan yang terdaftar tetapi tidak punya baris presensi pada suatu hari kerja berarti BELUM ABSEN pada hari tersebut.

[DAFTAR KARYAWAN] (${karyawanRows.length} orang) format: NIP|Nama|Departemen
${daftarKaryawan || '(kosong)'}

[DATA PRESENSI 62 HARI TERAKHIR] (${absensiRows.length} baris) format: Tanggal|NIP|Nama|Departemen|JamMasuk|JamPulang|Status
${daftarAbsensi || '(kosong)'}

[PENGAJUAN IZIN 62 HARI TERAKHIR] (${izinRows.length} baris) format: NIP|Nama|Departemen|Jenis|TglMulai|TglSelesai|Alasan|StatusPersetujuan
${daftarIzin || '(kosong)'}
`.trim();
}

// Cache daftar model Gemini aktif
let cachedGeminiModels = null;
let lastModelFetchTime = 0;

async function getAvailableGeminiModels(apiKey) {
  const now = Date.now();
  if (cachedGeminiModels && (now - lastModelFetchTime < 60 * 60 * 1000)) {
    return cachedGeminiModels;
  }
  try {
    const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`);
    if (res.ok) {
      const data = await res.json();
      const valid = (data.models || [])
        .filter(m => Array.isArray(m.supportedGenerationMethods) && m.supportedGenerationMethods.includes('generateContent'))
        .map(m => (m.name || '').replace(/^models\//, ''))
        .filter(name => !name.startsWith('gemini-1.') && !name.startsWith('gemini-2.0') && !name.startsWith('gemini-2.5'));
      
      if (valid.length > 0) {
        const priority = ['gemini-flash-lite-latest', 'gemini-3.5-flash-lite', 'gemini-3.5-flash', 'gemini-3.8-flash', 'gemini-flash-latest'];
        const sorted = [
          ...priority.filter(p => valid.includes(p)),
          ...valid.filter(v => !priority.includes(v))
        ];
        cachedGeminiModels = sorted;
        lastModelFetchTime = now;
        return cachedGeminiModels;
      }
    }
  } catch (e) {
    console.warn('⚠️ Gagal mengambil daftar model dinamis dari Google:', e.message);
  }
  return ['gemini-flash-lite-latest', 'gemini-3.5-flash-lite', 'gemini-3.5-flash', 'gemini-3.8-flash'];
}

app.post('/api/admin/ai/tanya', verifyAdmin, async (req, res) => {
  try {
    const pertanyaan = String(req.body.pertanyaan || '').trim();
    if (!pertanyaan) {
      return res.status(400).json({ status: "error", message: "Pertanyaan tidak boleh kosong." });
    }
    if (pertanyaan.length > 1000) {
      return res.status(400).json({ status: "error", message: "Pertanyaan terlalu panjang (maksimal 1000 karakter)." });
    }

    const apiKey = (process.env.GEMINI_API_KEY || '').trim();
    if (!apiKey) {
      return res.status(503).json({
        status: "error",
        message: "Asisten AI belum diaktifkan. Tambahkan GEMINI_API_KEY pada file .env server lalu restart server."
      });
    }
    // Daftar model alternatif aktif: memprioritaskan model Flash terbaru yang cepat & gratis
    const modelUtama = (process.env.GEMINI_MODEL || 'gemini-flash-lite-latest').trim();
    const dynamicModels = await getAvailableGeminiModels(apiKey);
    const candidateModels = Array.from(new Set([
      modelUtama,
      ...dynamicModels,
      'gemini-flash-lite-latest',
      'gemini-3.5-flash-lite',
      'gemini-3.5-flash',
      'gemini-3.8-flash'
    ])).filter(name => !name.startsWith('gemini-1.') && !name.startsWith('gemini-2.0') && !name.startsWith('gemini-2.5'));

    const konteks = await susunKonteksDataHr();
    const instruksiSistem = `Anda adalah "Asisten AI HRD" untuk PT. Fadiya Kencana (perusahaan outsourcing).
Tugas Anda menjawab pertanyaan Admin/HRD tentang kehadiran, keterlambatan, izin, dan rekap karyawan HANYA berdasarkan data di bawah ini.
Aturan:
- Jawab dalam Bahasa Indonesia yang sopan, ringkas, dan profesional.
- Hitung dengan teliti. Sebutkan nama dan NIP bila menyebut karyawan.
- Gunakan daftar bernomor atau tabel Markdown sederhana bila datanya berupa daftar.
- Jika data tidak cukup untuk menjawab, katakan dengan jujur, jangan mengarang.
- Jangan menjawab pertanyaan di luar topik HR/kehadiran perusahaan ini.

=== DATA HR TERKINI ===
${konteks}`;

    // Riwayat percakapan singkat (maks 6 pesan terakhir) agar AI paham pertanyaan lanjutan
    const riwayat = Array.isArray(req.body.riwayat) ? req.body.riwayat.slice(-6) : [];
    const contents = riwayat
      .filter(m => m && typeof m.teks === 'string' && m.teks.trim())
      .map(m => ({
        role: m.role === 'model' ? 'model' : 'user',
        parts: [{ text: m.teks.slice(0, 4000) }]
      }));
    contents.push({ role: 'user', parts: [{ text: pertanyaan }] });

    let jawaban = null;
    let errorTerakhir = null;

    // Loop mencoba model secara bergantian jika salah satu model sedang sibuk (high demand)
    for (const currentModel of candidateModels) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 20000);
      try {
        const aiRes = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(currentModel)}:generateContent?key=${apiKey}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-goog-api-key': apiKey
          },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: instruksiSistem }] },
            contents,
            generationConfig: { temperature: 0.2, maxOutputTokens: 2048 }
          }),
          signal: controller.signal
        });
        clearTimeout(timeout);

        const aiJson = await aiRes.json().catch(() => ({}));
        if (aiRes.ok) {
          jawaban = (aiJson?.candidates?.[0]?.content?.parts || [])
            .map(p => p.text || '')
            .join('')
            .trim();
          console.log(`🤖 [ASISTEN AI] Berhasil dijawab oleh [${currentModel}]`);
          break;
        } else {
          const detail = aiJson?.error?.message || `HTTP ${aiRes.status}`;
          errorTerakhir = detail;
          console.warn(`⚠️ [ASISTEN AI] Model [${currentModel}] gagal/sibuk: ${detail}. Beralih ke model alternatif...`);
          // Beri jeda sangat singkat sebelum mencoba model berikutnya
          await new Promise(r => setTimeout(r, 400));
        }
      } catch (errSub) {
        clearTimeout(timeout);
        errorTerakhir = errSub.name === 'AbortError' ? 'Waktu tunggu habis' : errSub.message;
        console.warn(`⚠️ [ASISTEN AI] Model [${currentModel}] error: ${errorTerakhir}. Beralih ke model alternatif...`);
      }
    }

    if (!jawaban && errorTerakhir) {
      console.error('❌ [ASISTEN AI] Seluruh model alternatif gagal:', errorTerakhir);
      return res.status(502).json({
        status: "error",
        message: `Layanan AI sedang sibuk: ${errorTerakhir}. Silakan coba lagi beberapa saat.`
      });
    }

    console.log(`🤖 [ASISTEN AI] ${req.admin.username}: "${pertanyaan.slice(0, 80)}"`);
    return res.json({
      status: "success",
      jawaban: jawaban || "Maaf, AI tidak memberikan jawaban. Coba ulangi dengan pertanyaan yang lebih spesifik."
    });
  } catch (err) {
    const msg = err.name === 'AbortError'
      ? 'Waktu tunggu jawaban AI habis. Periksa koneksi internet server lalu coba lagi.'
      : err.message;
    return res.status(500).json({ status: "error", message: msg });
  }
});

// RESET SEMUA DATA (RESET KE 0)
app.delete('/api/admin/reset-all', async (req, res) => {
  try {
    await pool.query('TRUNCATE TABLE karyawan');
    await pool.query('TRUNCATE TABLE absensi');
    await pool.query('TRUNCATE TABLE pengajuan_izin');
    console.log(`⚠️ [RESET TOTAL] Seluruh tabel database MySQL di-reset ke 0.`);
    res.json({
      status: "success",
      message: "Seluruh data MySQL (Karyawan, Absensi, Izin) berhasil dibersihkan ke 0!"
    });
  } catch (err) {
    res.status(500).json({ status: "error", message: err.message });
  }
});

// ==========================================
// CRON JOB: OTOMATISASI ALPA & LUPA PULANG (23:59)
// ==========================================
cron.schedule('59 23 * * *', async () => {
  console.log('⏰ Menjalankan Cron Job: Pengecekan Absensi Harian (23:59 WIB)...');
  try {
    const today = new Date();
    const tzOffset = today.getTimezoneOffset() * 60000;
    const localISOTime = (new Date(today.getTime() - tzOffset)).toISOString().slice(0, -1);
    const todayStr = localISOTime.split('T')[0];

    const [karyawanList] = await pool.query('SELECT id_karyawan, nip, nama, departemen FROM karyawan');
    const [absensiHariIni] = await pool.query('SELECT * FROM absensi WHERE tanggal = ?', [todayStr]);

    let countAlpa = 0;
    let countLupaPulang = 0;

    for (const k of karyawanList) {
      const absenRecord = absensiHariIni.find(a => a.nip === k.nip);

      if (!absenRecord) {
        // Karyawan tidak absen sama sekali hari ini (ALPA)
        await pool.query(
          `INSERT INTO absensi (id_karyawan, nip, nama, departemen, tanggal, jam_masuk, jam_pulang, status, foto_masuk, foto_pulang, latitude, longitude, maps_url, maps_url_pulang, created_at)
           VALUES (?, ?, ?, ?, ?, '-', '-', 'ALPA', '-', '-', 0, 0, '-', '-', NOW())`,
          [k.id_karyawan, k.nip, k.nama, k.departemen, todayStr]
        );
        countAlpa++;
      } else {
        // Karyawan absen masuk, tapi jam pulangnya masih "-" (LUPA PULANG)
        const upperStatus = (absenRecord.status || '').toUpperCase();
        if (absenRecord.jam_masuk && absenRecord.jam_masuk !== '-' && absenRecord.jam_pulang === '-' && !['SAKIT', 'IZIN', 'CUTI', 'ALPA'].includes(upperStatus)) {
          await pool.query(
            `UPDATE absensi SET status = 'LUPA PULANG' WHERE id = ?`,
            [absenRecord.id]
          );
          countLupaPulang++;
        }
      }
    }

    console.log(`✅ Cron Job Selesai: Ditambahkan ${countAlpa} status ALPA, diubah ${countLupaPulang} status LUPA PULANG.`);
  } catch (err) {
    console.error('❌ Gagal menjalankan Cron Job Absensi:', err.message);
  }
});

// ==========================================
// MENJALANKAN SERVER
// ==========================================
function getLocalIp() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return 'localhost';
}

const localIp = getLocalIp();

const server = app.listen(PORT, '0.0.0.0', () => {
  console.log('====================================================');
  console.log('🚀 SERVER ABSENSI PT FADIYA KENCANA (BERHASIL AKTIF)');
  console.log('====================================================');
  console.log(`• Web Admin Lokal    : http://localhost:${PORT}/`);
  console.log(`• Akses Wi-Fi Kantor : http://${localIp}:${PORT}/`);
  console.log(`• Jalur Online Publik: Terhubung via Cloudflare Tunnel`);
  console.log(`• Engine Database    : MySQL (db_absensi)`);
  console.log('====================================================\n');
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error('\n====================================================');
    console.error(`⚠️  [PORT BENTROK] Port ${PORT} sedang dipakai oleh proses lain!`);
    console.error('====================================================');
    console.error(`Penyebab: Ada proses Node.js atau aplikasi lain yang masih aktif di port ${PORT}.`);
    console.error('\n💡 Solusi Cepat untuk membebaskan port:');
    console.error(`   Jalankan perintah ini di PowerShell:`);
    console.error(`   Stop-Process -Id (Get-NetTCPConnection -LocalPort ${PORT}).OwningProcess -Force`);
    console.error('\n   Setelah itu, jalankan kembali: node server.js');
    console.error('====================================================\n');
    process.exit(1);
  } else {
    console.error('❌ Terjadi kesalahan pada server:', err);
    process.exit(1);
  }
});
