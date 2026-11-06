package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AuthMethod
import com.example.model.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fadiya Kencana", appName)
  }

  @Test
  fun `test auth method persistence and default state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = SessionManager(context)

    // Awal mula belum diatur
    sessionManager.resetAuthMethod()
    assertEquals(AuthMethod.NONE, sessionManager.getAuthMethod())
    assertFalse(sessionManager.isAuthMethodConfigured())

    // Simpan metode PIN
    sessionManager.simpanAuthMethod(AuthMethod.PIN)
    assertEquals(AuthMethod.PIN, sessionManager.getAuthMethod())
    assertTrue(sessionManager.isAuthMethodConfigured())

    // Ganti ke Sidik Jari
    sessionManager.simpanAuthMethod(AuthMethod.FINGERPRINT)
    assertEquals(AuthMethod.FINGERPRINT, sessionManager.getAuthMethod())

    // Ganti ke Wajah
    sessionManager.simpanAuthMethod(AuthMethod.FACE)
    assertEquals(AuthMethod.FACE, sessionManager.getAuthMethod())
  }

  @Test
  fun `test pin hashing and verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = SessionManager(context)

    // Simpan PIN baru 654321
    sessionManager.simpanSecurityPin("654321")

    // Verifikasi PIN yang benar
    assertTrue(sessionManager.verifyPin("654321"))

    // Verifikasi PIN yang salah
    assertFalse(sessionManager.verifyPin("111111"))
    assertFalse(sessionManager.verifyPin("000000"))
  }

  @Test
  fun `test failed pin attempts and lockout protection`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = SessionManager(context)

    sessionManager.resetFailedPinAttempts()
    sessionManager.simpanSecurityPin("123456")

    assertEquals(0, sessionManager.getFailedPinAttempts())
    assertEquals(0L, sessionManager.getLockoutRemainingSeconds())

    // Percobaan salah 1-4 kali
    for (i in 1..4) {
      val isLocked = sessionManager.recordFailedPinAttempt()
      assertFalse("Percobaan ke-$i seharusnya belum mengunci aplikasi", isLocked)
      assertEquals(i, sessionManager.getFailedPinAttempts())
      assertEquals(0L, sessionManager.getLockoutRemainingSeconds())
    }

    // Percobaan ke-5 -> Harus mengunci aplikasi selama 30 detik
    val isLocked5th = sessionManager.recordFailedPinAttempt()
    assertTrue("Percobaan ke-5 harus mengunci aplikasi", isLocked5th)
    assertTrue("Sisa waktu lockout harus > 0 detik", sessionManager.getLockoutRemainingSeconds() > 0)

    // Reset percobaan
    sessionManager.resetFailedPinAttempts()
    assertEquals(0, sessionManager.getFailedPinAttempts())
    assertEquals(0L, sessionManager.getLockoutRemainingSeconds())
  }

  @Test
  fun `test daily attendance auto reset when date changes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = SessionManager(context)
    val prefs = context.getSharedPreferences("absensi_session_pref", Context.MODE_PRIVATE)

    sessionManager.simpanAbsensiHariIni(
      com.example.model.AbsensiHariIni(
        jamMasuk = "07:55 WIB",
        sudahAbsenMasuk = true
      )
    )
    assertTrue(sessionManager.getAbsensiHariIni().sudahAbsenMasuk)

    // Simulasikan pergantian hari (tanggal kemarin)
    prefs.edit().putString("key_absensi_tanggal", "2020-01-01").apply()
    val afterRollover = sessionManager.getAbsensiHariIni()
    assertFalse("Status absen masuk harus otomatis di-reset saat berganti hari", afterRollover.sudahAbsenMasuk)
  }
}
