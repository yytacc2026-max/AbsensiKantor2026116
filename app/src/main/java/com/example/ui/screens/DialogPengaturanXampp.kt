package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.JaringanKantorManager
import com.example.network.PingResult
import com.example.ui.theme.*
import kotlinx.coroutines.launch

// Dialog pengaturan koneksi Server & Info GPS Realtime
@Composable
fun DialogPengaturanXampp(
  jaringanManager: JaringanKantorManager,
  onDismiss: () -> Unit
) {
  var urlInput by remember { mutableStateOf(jaringanManager.serverXamppUrl) }
  var pingResult by remember { mutableStateOf<PingResult?>(jaringanManager.lastPingResult) }
  var isLoading by remember { mutableStateOf(false) }

  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Dns,
          contentDescription = null,
          tint = TealPrimary,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Pengaturan Server & GPS",
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          color = TextDark
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "Atur alamat server untuk sinkronisasi data presensi dan lokasi GPS secara real-time.",
          fontSize = 12.sp,
          color = TextMuted,
          lineHeight = 16.sp
        )

        OutlinedTextField(
          value = urlInput,
          onValueChange = {
            urlInput = it
            pingResult = null
          },
          label = { Text("Alamat Server") },
          placeholder = { Text("http://192.168.0.2:3000/") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors = absensiTextFieldColors(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_xampp_url")
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          AssistChip(
            onClick = {
              urlInput = "http://192.168.0.2:3000/"
              pingResult = null
            },
            label = { Text("📡 Wi-Fi Kantor", fontSize = 11.sp) }
          )
          AssistChip(
            onClick = {
              if (!urlInput.startsWith("https://")) {
                urlInput = "https://"
              }
              pingResult = null
            },
            label = { Text("🌐 Paket Data (Cloudflare)", fontSize = 11.sp) }
          )
        }

        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = BgLight),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "📌 Panduan Koneksi:",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = TextDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "• Wi-Fi Kantor: HP dan Laptop di 1 Wi-Fi yang sama (http://192.168.0.2:3000/)\n• Paket Data (5G/4G): Gunakan URL Cloudflare Tunnel publik dari PC (https://...trycloudflare.com)",
              fontSize = 10.5.sp,
              color = TextMuted,
              lineHeight = 14.sp
            )
          }
        }

        Button(
          onClick = {
            coroutineScope.launch {
              isLoading = true
              jaringanManager.updateServerUrl(urlInput)
              val res = jaringanManager.tesKoneksiXampp()
              pingResult = res
              isLoading = false
            }
          },
          enabled = !isLoading && urlInput.isNotBlank(),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("btn_tes_ping_xampp")
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              color = Color.White,
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Memeriksa koneksi...", fontSize = 13.sp)
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cek Koneksi Server", fontSize = 13.sp)
          }
        }

        if (pingResult != null) {
          val res = pingResult!!
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (res.isSuccess) StatusGreenBg else StatusRedBg
            ),
            border = BorderStroke(
              1.dp,
              if (res.isSuccess) StatusGreen.copy(alpha = 0.4f) else StatusRed.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = if (res.isSuccess) StatusGreen else StatusRed,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = if (res.isSuccess) "Server Terhubung" else "Gagal Terhubung",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (res.isSuccess) StatusGreen else StatusRed
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (res.isSuccess) "Koneksi ke server presensi aktif (${res.responseTimeMs} ms)." else res.message,
                fontSize = 11.sp,
                color = TextDark
              )
            }
          }
        }

        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = TealContainer.copy(alpha = 0.35f)),
          border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.MyLocation, contentDescription = null, tint = TealDark, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Titik Lokasi GPS Realtime", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TealDark)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "Koordinat: %.5f, %.5f (±%d m)".format(
                jaringanManager.currentLatitude,
                jaringanManager.currentLongitude,
                jaringanManager.akurasiMeter.toInt()
              ),
              fontSize = 11.sp,
              color = TextDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = { jaringanManager.refreshGps() },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Perbarui Lokasi GPS", fontSize = 11.sp)
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          jaringanManager.updateServerUrl(urlInput)
          onDismiss()
        },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
      ) {
        Text("Simpan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Tutup", color = TextMuted)
      }
    }
  )
}
