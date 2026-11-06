package com.example.model

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.network.BackendApiClient
import com.example.network.PingResult
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class JaringanKantorManager(private val context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("server_config_pref", Context.MODE_PRIVATE)

  private val fusedLocationClient: FusedLocationProviderClient =
    LocationServices.getFusedLocationProviderClient(context)

  // --- Koordinat Kantor Resmi & Geofencing ---
  var kantorLatitude by mutableDoubleStateOf(
    prefs.getString("key_kantor_lat", "-7.250445")?.toDoubleOrNull() ?: -7.250445
  )
    private set

  var kantorLongitude by mutableDoubleStateOf(
    prefs.getString("key_kantor_lng", "112.768845")?.toDoubleOrNull() ?: 112.768845
  )
    private set

  var radiusMaksimalKantorMeter by mutableIntStateOf(
    prefs.getInt("key_kantor_radius", 150)
  )
    private set

  // Bypass / Mode Simulasi (aktif secara default agar demo & testing di emulator tidak terblokir)
  var isBypassRadius by mutableStateOf(
    prefs.getBoolean("key_bypass_radius", true)
  )
    private set

  // --- Realtime GPS Perangkat Saat Ini ---
  var currentLatitude by mutableDoubleStateOf(kantorLatitude)
    private set
  var currentLongitude by mutableDoubleStateOf(kantorLongitude)
    private set
  var akurasiMeter by mutableFloatStateOf(5.0f)
    private set
  var isGpsAktif by mutableStateOf(false)
    private set
  var isFetchingGps by mutableStateOf(false)
    private set
  var isFakeGpsDetected by mutableStateOf(false)
    private set
  var statusGpsDeskripsi by mutableStateOf("Mendeteksi lokasi GPS...")
    private set

  val mapsUrl: String
    get() = "https://www.google.com/maps?q=$currentLatitude,$currentLongitude"

  var isDalamJaringanKantor by mutableStateOf(true)
    private set
  var jarakDariKantorMeter by mutableIntStateOf(15)
    private set

  var namaWifiKantor: String by mutableStateOf("")

  private fun isEmulator(): Boolean {
    return android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.MODEL.contains("google_sdk") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.HARDWARE.contains("goldfish") ||
            android.os.Build.HARDWARE.contains("ranchu")
  }

  private fun loadInitialServerUrl(): String {
    val saved = prefs.getString("key_server_url", null)
    // Mode Jaringan Lokal Otomatis: langsung tanam IP Wi-Fi Server Lokal
    if (!saved.isNullOrBlank() && saved.startsWith("http://192.168.")) {
      return saved
    }
    return "http://192.168.0.2:3000/"
  }

  var serverXamppUrl by mutableStateOf(loadInitialServerUrl())
    private set
  var isTestingKoneksi by mutableStateOf(false)
    private set
  var lastPingResult by mutableStateOf<PingResult?>(null)
    private set

  init {
    BackendApiClient.setServerUrl(serverXamppUrl)
    serverXamppUrl = BackendApiClient.baseUrl
    prefs.edit().putString("key_server_url", serverXamppUrl).apply()
    hitungJarakDanValidasi()
  }

  fun updateServerUrl(urlBaru: String) {
    BackendApiClient.setServerUrl(urlBaru)
    serverXamppUrl = BackendApiClient.baseUrl
    prefs.edit().putString("key_server_url", serverXamppUrl).apply()
    lastPingResult = null
  }

  fun updatePengaturanGpsKantor(lat: Double, lng: Double, radius: Int, bypass: Boolean) {
    kantorLatitude = lat
    kantorLongitude = lng
    radiusMaksimalKantorMeter = radius
    isBypassRadius = bypass

    prefs.edit()
      .putString("key_kantor_lat", lat.toString())
      .putString("key_kantor_lng", lng.toString())
      .putInt("key_kantor_radius", radius)
      .putBoolean("key_bypass_radius", bypass)
      .apply()

    hitungJarakDanValidasi()
  }

  fun setTitikKantorKeLokasiSaatIni() {
    updatePengaturanGpsKantor(currentLatitude, currentLongitude, radiusMaksimalKantorMeter, isBypassRadius)
  }

  fun toggleBypassRadius(bypass: Boolean) {
    isBypassRadius = bypass
    prefs.edit().putBoolean("key_bypass_radius", bypass).apply()
    hitungJarakDanValidasi()
  }

  fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
  }

  private var isTrackingActive = false

  private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      val loc = result.lastLocation ?: return
      updatePosisiGps(loc)
    }
  }

  fun mulaiPelacakanGpsRealtime() {
    if (!hasLocationPermission()) {
      statusGpsDeskripsi = "Izin lokasi GPS belum aktif"
      return
    }
    if (isTrackingActive) return

    try {
      isFetchingGps = true
      fusedLocationClient.removeLocationUpdates(locationCallback)
      fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
        if (loc != null) {
          updatePosisiGps(loc)
        }
      }

      // Interval hemat baterai (15 detik & minimal perpindahan 8 meter) agar HP tidak panas/boros baterai
      val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
        .setMinUpdateIntervalMillis(8_000L)
        .setMinUpdateDistanceMeters(8f)
        .build()

      fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        .addOnSuccessListener {
          isTrackingActive = true
          isGpsAktif = true
          isFetchingGps = false
        }
        .addOnFailureListener { e ->
          Log.w("JaringanKantorManager", "Gagal request location updates: ${e.message}")
          isTrackingActive = false
          isFetchingGps = false
        }
    } catch (e: SecurityException) {
      statusGpsDeskripsi = "Izin akses GPS diperlukan: ${e.message}"
      isTrackingActive = false
      isFetchingGps = false
    } catch (e: Exception) {
      statusGpsDeskripsi = "Gagal mengaktifkan GPS: ${e.message}"
      isTrackingActive = false
      isFetchingGps = false
    }
  }

  fun hentikanPelacakanGps() {
    try {
      fusedLocationClient.removeLocationUpdates(locationCallback)
    } catch (_: Exception) {}
    isTrackingActive = false
    isFetchingGps = false
  }

  fun refreshGps(onComplete: (Boolean) -> Unit = {}) {
    if (!hasLocationPermission()) {
      statusGpsDeskripsi = "Izin lokasi GPS belum aktif"
      onComplete(false)
      return
    }
    isFetchingGps = true
    try {
      fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { loc: Location? ->
          isFetchingGps = false
          if (loc != null) {
            updatePosisiGps(loc)
            onComplete(true)
          } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
              if (lastLoc != null) updatePosisiGps(lastLoc)
              onComplete(lastLoc != null)
            }
          }
        }
        .addOnFailureListener {
          isFetchingGps = false
          onComplete(false)
        }
    } catch (e: Exception) {
      isFetchingGps = false
      onComplete(false)
    }
  }

  private fun updatePosisiGps(loc: Location) {
    currentLatitude = loc.latitude
    currentLongitude = loc.longitude
    akurasiMeter = loc.accuracy
    isGpsAktif = true
    isFetchingGps = false
    val isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      loc.isMock
    } else {
      @Suppress("DEPRECATION")
      loc.isFromMockProvider
    }
    isFakeGpsDetected = !isEmulator() && isMock
    hitungJarakDanValidasi()
  }

  fun hitungJarakDanValidasi() {
    if (isFakeGpsDetected) {
      isDalamJaringanKantor = false
      statusGpsDeskripsi = "Terdeteksi aplikasi lokasi palsu (Fake GPS)"
      return
    }

    // Hitung jarak geografis riil antara HP karyawan dan titik kantor resmi (dalam satuan meter)
    val hasilMeter = FloatArray(1)
    Location.distanceBetween(
      currentLatitude,
      currentLongitude,
      kantorLatitude,
      kantorLongitude,
      hasilMeter
    )
    jarakDariKantorMeter = hasilMeter[0].toInt()

    // Validasi radius sesuai konfigurasi Admin (isBypassRadius bernilai true jika admin mematikan aturan radius)
    isDalamJaringanKantor = isBypassRadius || (jarakDariKantorMeter <= radiusMaksimalKantorMeter)

    statusGpsDeskripsi = when {
      !isGpsAktif -> "Mencari sinyal satelit GPS..."
      isBypassRadius -> "GPS Terkunci (Mode Bebas Lokasi Aktif) • Akurasi ±${akurasiMeter.toInt()} m"
      isDalamJaringanKantor -> "GPS Terkunci (Jarak ${jarakDariKantorMeter}m dari kantor) - Siap Absen"
      else -> "Di luar radius kantor (Jarak ${jarakDariKantorMeter}m, maks ${radiusMaksimalKantorMeter}m)"
    }
  }

  suspend fun tesKoneksiXampp(): PingResult {
    isTestingKoneksi = true
    return try {
      val result = BackendApiClient.pingServer()
      val ping = result.getOrElse { err ->
        PingResult(
          isSuccess = false,
          statusCode = 0,
          responseTimeMs = 0,
          message = err.localizedMessage ?: "Tidak dapat terhubung ke server"
        )
      }
      lastPingResult = ping
      ping
    } finally {
      isTestingKoneksi = false
    }
  }

  fun isTerhubungKoneksi(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
  }

  fun perbaruiStatusJaringan() {
    refreshGps()
  }
}
