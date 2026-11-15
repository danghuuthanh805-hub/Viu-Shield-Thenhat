
package com.viu.shield

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

private val Bg = Color(0xFF090D18)
private val Panel = Color(0xFF151D30)
private val Cyan = Color(0xFF43D9FF)
private val Purple = Color(0xFF9274FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShieldApp() }
    }
}

@Composable
fun ShieldApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var fileName by remember { mutableStateOf("No APK selected") }
    var hash by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Waiting for APK") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = context.contentResolver
                    .query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0)
                            cursor.getString(index) else "Selected APK"
                    } ?: "Selected APK"

                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                } ?: error("Cannot read APK")

                fileName = name
                hash = digest.digest().joinToString("") {
                    "%02x".format(it)
                }
                status = "Fingerprint generated"
            } catch (e: Exception) {
                status = "Unable to read APK"
                hash = ""
            }
        }
    }

    val nativeStatus = remember {
        try { NativeGuard.status() }
        catch (e: Exception) { "Native library unavailable" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111B35), Bg, Bg)
                )
            )
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Text("VIU SHIELD", color = Cyan, fontSize = 29.sp)
        Text(
            "ANDROID PROTECTION SYSTEM",
            color = Color.LightGray,
            fontSize = 11.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Protection Center", color = Color.White, fontSize = 21.sp)
                Spacer(Modifier.height(8.dp))
                Text("APK integrity workspace", color = Color.Gray)
                Spacer(Modifier.height(20.dp))

                Text("SELECTED FILE", color = Cyan, fontSize = 11.sp)
                Text(fileName, color = Color.White, fontSize = 15.sp)

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Purple
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("＋  Select APK")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("SHA-256 FINGERPRINT", color = Cyan, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    hash.ifEmpty { "Waiting for file..." },
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                Text("STATUS", color = Cyan, fontSize = 12.sp)
                Text(status, color = Color.White)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("NATIVE ENGINE", color = Cyan)
                Spacer(Modifier.height(8.dp))
                Text(nativeStatus, color = Color.White)
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            "ViU Shield • v0.1 • The Nhat (2016)",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
