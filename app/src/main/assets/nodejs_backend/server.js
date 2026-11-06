const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const os = require('os');

const app = express();
const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'database.json');
const UPLOADS_DIR = path.join(__dirname, 'public', 'uploads');

// Pastikan folder uploads tersedia untuk simpan foto selfie
if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

// Middleware
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// Log setiap request yang masuk
app.use((req, res, next) => {
  const time = new Date().toLocaleTimeString();
  console.log(`[${time}] ${req.method} ${req.url}`);
  next();
});

// Struktur Database Default
const defaultData = {
  karyawan: [],
  absensi: [],
  pengajuan_izin: [],
  pengaturan_kantor: {
    jam_masuk: "08:00",
    batas_terlambat: "08:00",
    jam_pulang: "17:00",
    pengumuman: "Pengumuman: Jam kerja resmi kantor dimulai pukul 08:00 WIB.",
    total_karyawan: 0
  }
};

// FUNGSI EVALUASI KETERLAMBATAN JAM MASUK (OFFICIAL CUTOFF 08:00 WIB)
function evalStatusMasuk(jamMasuk, batasTerlambat = "08:00", currentStatus = "") {
  if (!jamMasuk || jamMasuk === '-') return 'TEPAT_WAKTU';
  const upperStatus = (currentStatus || '').toUpperCase();
  if (['SAKIT', 'IZIN', 'CUTI'].includes(upperStatus)) {
    return upperStatus; // Pertahankan status izin/sakit
  }

  const cleanAbsen = jamMasuk.replace('.', ':').trim();
  const cleanBatas = (batasTerlambat || "08:00").replace('.', ':').trim();

  const [hA, mA] = cleanAbsen.split(':').map(n => parseInt(n) || 0);
  const [hB, mB] = cleanBatas.split(':').map(n => parseInt(n) || 0);

  const totalMenitAbsen = hA * 60 + mA;
  const totalMenitBatas = hB * 60 + mB;

  return (totalMenitAbsen > totalMenitBatas) ? 'TERLAMBAT' : 'TEPAT_WAKTU';
}

// Fungsi baca database file
function loadDatabase() {
  try {
    if (fs.existsSync(DB_FILE)) {
      const raw = fs.readFileSync(DB_FILE, 'utf-8');
      const data = JSON.parse(raw);
      if (!data.karyawan) data.karyawan = [];
      if (!data.absensi) data.absensi = [];
      if (!data.pengajuan_izin) data.pengajuan_izin = [];
      if (!data.pengaturan_kantor) data.pengaturan_kantor = defaultData.pengaturan_kantor;

      // Evaluasi otomatis status keterlambatan untuk seluruh record absensi berdasarkan Jam Masuk
      const batas = data.pengaturan_kantor.batas_terlambat || "08:00";
      data.absensi.forEach(a => {
        if (a.jam_masuk && a.jam_masuk !== '-') {
          a.status = evalStatusMasuk(a.jam_masuk, batas, a.status);
        }
      });

      return data;
    }
  } catch (err) {
    console.error('Error membaca database.json, menggunakan default:', err.message);
  }
  saveDatabase(defaultData);
  return defaultData;
}

// Fungsi simpan database file
function saveDatabase(data) {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2), 'utf-8');
  } catch (err) {
    console.error('Gagal menulis database.json:', err.message);
  }
}

// ==========================================
// 1. ENDPOINT PING & STATUS
// ==========================================
function handlePing(req, res) {
  const db = loadDatabase();
  res.json({
    status: "success",
    message: "Server Node.js Absensi Kantor Berjalan Normal!",
    engine: "Node.js (Express)",
    total_karyawan: db.karyawan.length,
    timestamp: new Date().toISOString()
  });
}
app.get('/api/ping', handlePing);
app.get('/ping.php', handlePing);

// ==========================================
// 2. ENDPOINT REGISTRASI & CEK LOGIN KARYAWAN (NIP STRICT UNIQUE PRIMARY KEY)
// ==========================================
function handleRegisterKaryawan(req, res) {
  const db = loadDatabase();
  const nip = (req.body.nip || req.body.nik || req.query.nip || req.query.nik || '').trim();
  const nama = (req.body.nama || req.query.nama || '').trim();
  const departemen = (req.body.departemen || req.body.dept || req.body.jabatan || req.query.departemen || 'Produksi Pabrik').trim();
  const jk = (req.body.jenis_kelamin || req.body.jk || 'L').trim();
  const tempatLahir = (req.body.tempat_lahir || '-').trim();
  const tanggalLahir = (req.body.tanggal_lahir || '-').trim();

  if (!nip || !nama) {
    return res.status(400).json({
      status: "error",
      message: "NIP/NIK dan Nama Lengkap wajib diisi!"
    });
  }

  // VALIDASI MUTLAK PRIMARY KEY (NIP UNIK):
  let existing = db.karyawan.find(k => (k.nip && k.nip === nip) || (k.nik && k.nik === nip));
  if (existing) {
    console.log(`❌ [DUPLIKAT NIP DITOLAK] NIP ${nip} sudah milik '${existing.nama}'. Percobaan oleh '${nama}' ditolak.`);
    return res.status(400).json({
      status: "error",
      message: `Pendaftaran Ditolak: NIP/NIK '${nip}' sudah terdaftar atas nama '${existing.nama}'. NIP bersifat unik (Primary Key) dan tidak bisa digunakan oleh karyawan lain!`
    });
  }

  const nextNum = db.karyawan.length + 1;
  const idKaryawan = `K-${String(nextNum).padStart(3, '0')}`;

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
    ttl: `${tempatLahir}, ${tanggalLahir}`,
    dept: departemen,
    departemen: departemen,
    device_id: req.body.device_id || '-',
    created_at: new Date().toISOString()
  };

  db.karyawan.push(karyawanBaru);
  db.pengaturan_kantor.total_karyawan = db.karyawan.length;
  saveDatabase(db);

  console.log(`✅ [DAFTAR BARU] Karyawan ${nama} (${idKaryawan} - NIP: ${nip}) berhasil ditambahkan. Total sekarang: ${db.karyawan.length}`);

  res.json({
    status: "success",
    message: `Karyawan baru (${nama}) dengan NIP ${nip} berhasil didaftarkan ke sistem Node.js.`,
    is_existing: false,
    total_karyawan: db.karyawan.length,
    data: karyawanBaru
  });
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

// CEK & LOGIN KARYAWAN BERDASARKAN NIK / NIP (AUTO-FILL DATA)
function handleCekKaryawan(req, res) {
  const db = loadDatabase();
  const nip = (req.query.nip || req.query.nik || req.body.nip || req.body.nik || '').trim();

  if (!nip) {
    return res.status(400).json({ status: "error", message: "Silakan masukkan NIK / NIP karyawan terlebih dahulu!" });
  }

  const k = db.karyawan.find(item => item.nip === nip || item.nik === nip);
  if (k) {
    console.log(`🔍 [CEK LOGIN NIK] Data NIK ${nip} ditemukan: ${k.nama} (${k.dept || k.departemen})`);
    res.json({
      status: "success",
      message: `Data Karyawan '${k.nama}' ditemukan!`,
      data: k
    });
  } else {
    res.status(404).json({
      status: "error",
      message: `NIK / NIP '${nip}' belum terdaftar di database kantor. Silakan lakukan Pendaftaran Karyawan Baru!`
    });
  }
}

app.get('/api/karyawan/cek', handleCekKaryawan);
app.post('/api/karyawan/login', handleCekKaryawan);
app.get('/cek_karyawan.php', handleCekKaryawan);

// GET Daftar Karyawan dengan Hitungan Rekap Bulanan Akurat
function handleGetKaryawan(req, res) {
  const db = loadDatabase();
  const bulan = (req.query.bulan || new Date().toISOString().substring(0, 7)).trim(); // YYYY-MM

  const karyawanListWithStats = db.karyawan.map(k => {
    const nipTarget = k.nip || k.nik || '';
    const namaTarget = (k.nama || '').toLowerCase();

    const absensiBulanIni = db.absensi.filter(a => {
      const matchNip = nipTarget && (a.nip === nipTarget || a.nik === nipTarget);
      const matchName = namaTarget && a.nama && a.nama.toLowerCase() === namaTarget;
      const isMonth = a.tanggal && a.tanggal.startsWith(bulan);
      return (matchNip || matchName) && isMonth;
    });

    const totalHadir = absensiBulanIni.filter(a => a.jam_masuk && a.jam_masuk !== '-').length;
    const totalIzin = absensiBulanIni.filter(a => a.tipe === 'IZIN' || ['SAKIT', 'IZIN', 'CUTI'].includes((a.status || '').toUpperCase())).length;
    const totalTerlambat = absensiBulanIni.filter(a => a.status === 'TERLAMBAT').length;

    return {
      ...k,
      total_hadir_bulan_ini: totalHadir,
      total_izin_bulan_ini: totalIzin,
      total_terlambat_bulan_ini: totalTerlambat,
      bulan_rekap: bulan
    };
  });

  res.json({
    status: "success",
    bulan: bulan,
    total: db.karyawan.length,
    data: karyawanListWithStats
  });
}
app.get('/api/karyawan', handleGetKaryawan);
app.get('/api/admin/karyawan', handleGetKaryawan);

// HAPUS Karyawan (NIP or ID)
function handleDeleteKaryawan(req, res) {
  const db = loadDatabase();
  const target = (req.params.nip || req.params.id || req.body.nip || req.body.id_karyawan || '').trim();

  const idx = db.karyawan.findIndex(k => k.nip === target || k.nik === target || k.id === target || k.id_karyawan === target);
  if (idx >= 0) {
    const deleted = db.karyawan.splice(idx, 1)[0];
    db.pengaturan_kantor.total_karyawan = db.karyawan.length;
    saveDatabase(db);
    console.log(`🗑️ [HAPUS] Karyawan ${deleted.nama} (${target}) dihapus.`);
    return res.json({
      status: "success",
      message: `Karyawan ${deleted.nama} (NIP: ${target}) berhasil dihapus.`,
      total_karyawan: db.karyawan.length
    });
  }

  res.status(404).json({
    status: "error",
    message: `Karyawan dengan NIP/ID '${target}' tidak ditemukan.`
  });
}
app.delete('/api/admin/karyawan/:nip', handleDeleteKaryawan);
app.delete('/api/karyawan/:id', handleDeleteKaryawan);

// RESET HP / UNBIND DEVICE ID
app.put('/api/admin/karyawan/:nip/reset-device', (req, res) => {
  const db = loadDatabase();
  const nip = req.params.nip;
  const k = db.karyawan.find(item => item.nip === nip || item.nik === nip);
  if (k) {
    k.device_id = '-';
    saveDatabase(db);
    console.log(`📱 [RESET HP] Tautan HP untuk ${k.nama} (${nip}) telah di-reset.`);
    return res.json({ status: "success", message: `Berhasil mereset tautan HP untuk ${k.nama}` });
  }
  res.status(404).json({ status: "error", message: "Karyawan tidak ditemukan" });
});

// ==========================================
// 3. ENDPOINT ABSENSI (FOTO SELFIE & STRICT VALIDASI TERLAMBAT)
// ==========================================
function handleAbsen(req, res) {
  const db = loadDatabase();
  const nip = (req.body.nip || req.body.nik || '').trim();
  const nama = (req.body.nama || '').trim();
  const id_karyawan = req.body.id_karyawan || req.body.karyawan_id || (nip ? `K-${nip}` : 'K-001');
  const departemen = req.body.departemen || req.body.dept || 'Produksi Pabrik';
  const rawTipe = (req.body.tipe || 'MASUK').toUpperCase();
  const tipe = (rawTipe === 'KELUAR' || rawTipe === 'PULANG') ? 'PULANG' : 'MASUK';
  const jam = req.body.jam || new Date().toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });
  const today = req.body.tanggal || new Date().toISOString().split('T')[0];
  const fotoSelfieBase64 = req.body.foto_selfie || req.body.foto || '';

  const batasTerlambat = db.pengaturan_kantor.batas_terlambat || "08:00";
  const computedStatus = evalStatusMasuk(jam, batasTerlambat, req.body.status || req.body.status_absen || '');

  if (!nama && !nip) {
    return res.status(400).json({ status: "error", message: "Data absensi tidak lengkap (NIP/Nama wajib)" });
  }

  // Simpan foto selfie jika dikirimkan dari HP
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

  // Cari record presensi hari ini STRICT BERDASARKAN NIP ATAU NAMA
  let record = db.absensi.find(a => {
    const matchNip = (nip && (a.nip === nip || a.nik === nip));
    const matchName = (nama && a.nama && a.nama.toLowerCase() === nama.toLowerCase());
    return (matchNip || matchName) && a.tanggal === today;
  });

  if (tipe === 'MASUK') {
    if (record && record.jam_masuk && record.jam_masuk !== '-') {
      return res.status(400).json({
        status: "error",
        message: `Anda (${nama || nip}) sudah melakukan Absen Masuk hari ini pada pukul ${record.jam_masuk}. Absen masuk hanya bisa 1 kali sehari!`
      });
    }

    if (record) {
      record.jam_masuk = jam;
      record.tipe = 'MASUK';
      record.status = computedStatus;
      if (savedFotoUrl !== '-') record.foto_masuk_url = savedFotoUrl;
      if (!record.nip || record.nip === '-') record.nip = nip;
      if (!record.nik || record.nik === '-') record.nik = nip;
    } else {
      record = {
        id: db.absensi.length + 1,
        id_karyawan: id_karyawan,
        nip: nip || "-",
        nik: nip || "-",
        nama: nama || "Karyawan",
        dept: departemen,
        departemen: departemen,
        tanggal: today,
        jam_masuk: jam,
        jam_pulang: "-",
        tipe: "MASUK",
        status: computedStatus,
        foto_masuk_url: savedFotoUrl,
        foto_pulang_url: "-",
        created_at: new Date().toISOString()
      };
      db.absensi.unshift(record);
    }
  } else {
    // PULANG / KELUAR
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

    record.jam_pulang = jam;
    record.tipe = 'PULANG';
    if (savedFotoUrl !== '-') record.foto_pulang_url = savedFotoUrl;
  }

  saveDatabase(db);
  console.log(`⏱️ [ABSEN ${tipe}] ${nama || nip} - ${today} ${jam} (Status: ${record.status}, Foto: ${savedFotoUrl !== '-'})`);

  res.json({
    status: "success",
    message: `Absen ${tipe} berhasil dicatat pada pukul ${jam} (Status: ${record.status}).`,
    foto_url: savedFotoUrl,
    data: record
  });
}
app.post('/api/absen', handleAbsen);
app.post('/api/mobile/presensi', handleAbsen);

// ==========================================
// 4. ENDPOINT INPUT IZIN / SAKIT OLEH ADMIN
// ==========================================
function handleInputIzin(req, res) {
  const db = loadDatabase();
  const nip = (req.body.nip || req.body.nik || '').trim();
  const nama = (req.body.nama || '').trim();
  const jenisIzin = (req.body.jenis_izin || req.body.jenis || 'Sakit').trim();
  const tanggal = (req.body.tanggal || new Date().toISOString().split('T')[0]).trim();
  const alasan = (req.body.alasan || req.body.keterangan || 'Diinput oleh Admin').trim();

  const karyawanObj = db.karyawan.find(k => k.nip === nip || k.nik === nip || (nama && k.nama.toLowerCase() === nama.toLowerCase()));
  const finalNama = karyawanObj ? karyawanObj.nama : (nama || "Karyawan");
  const finalNip = karyawanObj ? (karyawanObj.nip || karyawanObj.nik) : (nip || "-");
  const finalId = karyawanObj ? karyawanObj.id : `K-${finalNip}`;
  const finalDept = karyawanObj ? (karyawanObj.dept || karyawanObj.departemen) : "Produksi Pabrik";

  const itemIzin = {
    id: db.pengajuan_izin.length + 1,
    id_karyawan: finalId,
    nip: finalNip,
    nama: finalNama,
    jenis_izin: jenisIzin,
    alasan: alasan,
    tanggal: tanggal,
    tanggal_diajukan: new Date().toISOString().replace('T', ' ').substring(0, 19),
    status_persetujuan: "Disetujui (Admin)"
  };
  db.pengajuan_izin.unshift(itemIzin);

  let existingAbsen = db.absensi.find(a => ((a.nip && a.nip === finalNip) || (a.nik && a.nik === finalNip)) && a.tanggal === tanggal);
  if (existingAbsen) {
    existingAbsen.tipe = 'IZIN';
    existingAbsen.status = jenisIzin.toUpperCase();
    existingAbsen.alasan = alasan;
  } else {
    db.absensi.unshift({
      id: db.absensi.length + 1,
      id_karyawan: finalId,
      nip: finalNip,
      nik: finalNip,
      nama: finalNama,
      dept: finalDept,
      departemen: finalDept,
      tanggal: tanggal,
      jam_masuk: "-",
      jam_pulang: "-",
      tipe: "IZIN",
      status: jenisIzin.toUpperCase(),
      alasan: alasan,
      foto_masuk_url: "-",
      foto_pulang_url: "-",
      created_at: new Date().toISOString()
    });
  }

  saveDatabase(db);
  console.log(`📝 [IZIN ADMIN] ${finalNama} (${finalNip}) - ${jenisIzin} tanggal ${tanggal} (${alasan})`);

  res.json({
    status: "success",
    message: `Berhasil mencatat ${jenisIzin} untuk ${finalNama} (${finalNip}) pada tanggal ${tanggal}.`,
    data: itemIzin
  });
}

app.post('/api/admin/izin', handleInputIzin);
app.post('/api/izin', handleInputIzin);
app.post('/api/mobile/izin', handleInputIzin);

// GET Daftar Izin
app.get('/api/admin/izin', (req, res) => {
  const db = loadDatabase();
  res.json({
    status: "success",
    total: db.pengajuan_izin.length,
    data: db.pengajuan_izin
  });
});

// ==========================================
// 5. ENDPOINT DASHBOARD & REKAP ABSENSI ADMIN
// ==========================================
function handleAdminAbsensi(req, res) {
  const db = loadDatabase();
  const today = new Date().toISOString().split('T')[0];
  const bulanFilter = (req.query.bulan || today.substring(0, 7)).trim(); // YYYY-MM

  const absensiHariIni = db.absensi.filter(a => a.tanggal === today);
  const absensiBulanIni = db.absensi.filter(a => a.tanggal && a.tanggal.startsWith(bulanFilter));

  const totalMaster = db.karyawan.length;
  const hadirHariIni = absensiHariIni.filter(a => a.jam_masuk && a.jam_masuk !== '-').length;
  const terlambatHariIni = absensiHariIni.filter(a => a.status === 'TERLAMBAT').length;

  const totalHadirBulanIni = absensiBulanIni.filter(a => a.jam_masuk && a.jam_masuk !== '-').length;
  const totalIzinBulanIni = absensiBulanIni.filter(a => a.tipe === 'IZIN' || ['SAKIT', 'IZIN', 'CUTI'].includes((a.status || '').toUpperCase())).length;
  const totalTerlambatBulanIni = absensiBulanIni.filter(a => (a.status || '').toUpperCase() === 'TERLAMBAT').length;

  const totalTerikatDevice = db.karyawan.filter(k => k.device_id && k.device_id !== '-').length;

  res.json({
    status: "success",
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
      ...db.pengaturan_kantor,
      total_karyawan: totalMaster
    },
    data: db.absensi
  });
}

app.get('/api/admin/absensi', handleAdminAbsensi);

// RESET SEMUA DATA (RESET KE 0)
app.delete('/api/admin/reset-all', (req, res) => {
  const db = loadDatabase();
  db.karyawan = [];
  db.absensi = [];
  db.pengajuan_izin = [];
  db.pengaturan_kantor.total_karyawan = 0;
  saveDatabase(db);
  console.log(`⚠️ [RESET TOTAL] Seluruh database di-reset ke 0.`);
  res.json({
    status: "success",
    message: "Seluruh data (Karyawan, Absensi, Izin) berhasil dibersihkan ke 0!"
  });
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

app.listen(PORT, '0.0.0.0', () => {
  console.log('====================================================');
  console.log('🚀 SERVER NODE.JS ABSENSI KANTOR BERHASIL AKTIF!');
  console.log('====================================================');
  console.log(`• Web Admin Dashboard: http://localhost:${PORT}/`);
  console.log(`• Akses HP Android   : http://${localIp}:${PORT}/`);
  console.log(`• File Penyimpanan   : ${DB_FILE}`);
  console.log('====================================================\n');
});
