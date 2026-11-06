package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.model.JaringanKantorManager
import com.example.model.SessionManager
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.ChooseAuthMethodScreen
import com.example.ui.screens.KaryawanDashboardScreen
import com.example.ui.screens.KaryawanLoginScreen
import com.example.ui.screens.KaryawanRegisterScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.SecurityLockScreen
import com.example.ui.screens.SetupPinScreen
import com.example.ui.theme.AbsensiKantorTheme

class MainActivity : FragmentActivity() {
  private lateinit var jaringanManager: JaringanKantorManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    jaringanManager = JaringanKantorManager(applicationContext)
    val sessionManager = SessionManager(applicationContext)

    // Load Token JWT yang tersimpan
    com.example.network.BackendApiClient.jwtToken = sessionManager.getJwtToken()

    setContent {
      AbsensiKantorTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          var isAuthMethodConfigured by remember { mutableStateOf(sessionManager.isAuthMethodConfigured()) }
          var setupAuthScreen by remember { mutableStateOf("choose_method") }
          var isUnlocked by remember { mutableStateOf(false) }

          // JIKA BELUM PERNAH MEMILIH METODE LOGIN (SETUP PERTAMA KALI)
          if (!isAuthMethodConfigured) {
            when (setupAuthScreen) {
              "choose_method" -> {
                ChooseAuthMethodScreen(
                  onAuthMethodSelected = { _ ->
                    isAuthMethodConfigured = true
                    isUnlocked = true
                  },
                  onNavigateToPinSetup = {
                    setupAuthScreen = "setup_pin"
                  }
                )
              }
              "setup_pin" -> {
                SetupPinScreen(
                  onPinSetupSuccess = {
                    isAuthMethodConfigured = true
                    isUnlocked = true
                  },
                  onBack = {
                    setupAuthScreen = "choose_method"
                  }
                )
              }
            }
          } else if (!isUnlocked) {
            // JIKA SUDAH PERNAH MEMILIH METODE: TAMPILKAN METODE LOGIN YANG TERSIMPAN
            SecurityLockScreen(
              onUnlockSuccess = { isUnlocked = true }
            )
          } else {
            // SUDAH TERVERIFIKASI / LOGIN BERHASIL -> AKSES APLIKASI UTAMA
            var isRegistered by remember { mutableStateOf(sessionManager.isSudahTerdaftar()) }
            var currentScreen by remember {
              mutableStateOf(if (sessionManager.isSudahTerdaftar()) "karyawan_dashboard" else "role_selection")
            }

            val karyawanTerdaftar = remember(isRegistered, currentScreen) { sessionManager.getKaryawanTerdaftar() }

            when (currentScreen) {
              "role_selection" -> {
                RoleSelectionScreen(
                  karyawanTerdaftar = karyawanTerdaftar,
                  onOpenDashboard = { currentScreen = "karyawan_dashboard" },
                  onRegisterNew = { currentScreen = "karyawan_register" },
                  onSelectLogin = { currentScreen = "karyawan_login" },
                  onSelectAdmin = { currentScreen = "admin_login" },
                  jaringanManager = jaringanManager
                )
              }
              "karyawan_login" -> {
                KaryawanLoginScreen(
                  onLoginSuccess = { karyawanFound ->
                    sessionManager.simpanKaryawanTerdaftar(karyawanFound)
                    isRegistered = true
                    currentScreen = "karyawan_dashboard"
                  },
                  onBack = { currentScreen = "role_selection" }
                )
              }
              "karyawan_register" -> {
                KaryawanRegisterScreen(
                  onRegisterSuccess = { karyawanBaru ->
                    sessionManager.simpanKaryawanTerdaftar(karyawanBaru)
                    isRegistered = true
                    currentScreen = "karyawan_dashboard"
                  },
                  onBack = { currentScreen = "role_selection" },
                  jaringanManager = jaringanManager
                )
              }
              "karyawan_dashboard" -> {
                if (karyawanTerdaftar != null) {
                  KaryawanDashboardScreen(
                    karyawan = karyawanTerdaftar,
                    onLogout = {
                      sessionManager.hapusPendaftaran()
                      com.example.network.BackendApiClient.jwtToken = null
                      isRegistered = false
                      currentScreen = "role_selection"
                    },
                    onBack = { currentScreen = "role_selection" },
                    jaringanManager = jaringanManager,
                    onChangeAuthMethod = {
                      currentScreen = "change_auth_method"
                    }
                  )
                } else {
                  currentScreen = "role_selection"
                }
              }
              "change_auth_method" -> {
                ChooseAuthMethodScreen(
                  onAuthMethodSelected = { _ ->
                    currentScreen = "karyawan_dashboard"
                  },
                  onNavigateToPinSetup = {
                    currentScreen = "setup_pin_change"
                  },
                  onBack = {
                    currentScreen = "karyawan_dashboard"
                  }
                )
              }
              "setup_pin_change" -> {
                SetupPinScreen(
                  onPinSetupSuccess = {
                    currentScreen = "karyawan_dashboard"
                  },
                  onBack = {
                    currentScreen = "change_auth_method"
                  }
                )
              }
              "admin_login" -> {
                AdminLoginScreen(
                  onLoginSuccess = { currentScreen = "admin_dashboard" },
                  onBack = { currentScreen = "role_selection" }
                )
              }
              "admin_dashboard" -> {
                AdminDashboardScreen(
                  onLogout = { currentScreen = "role_selection" },
                  jaringanManager = jaringanManager
                )
              }
              else -> {
                RoleSelectionScreen(
                  karyawanTerdaftar = karyawanTerdaftar,
                  onOpenDashboard = { currentScreen = "karyawan_dashboard" },
                  onRegisterNew = { currentScreen = "karyawan_register" },
                  onSelectLogin = { currentScreen = "karyawan_login" },
                  onSelectAdmin = { currentScreen = "admin_login" },
                  jaringanManager = jaringanManager
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (::jaringanManager.isInitialized && jaringanManager.hasLocationPermission()) {
      jaringanManager.mulaiPelacakanGpsRealtime()
    }
  }

  override fun onPause() {
    super.onPause()
    if (::jaringanManager.isInitialized) {
      jaringanManager.hentikanPelacakanGps()
    }
  }
}
