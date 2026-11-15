
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
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

private val Bg = Color(0xFF090D18)
private val Panel = Color(0xFF151D30)
private val Cyan = Color(0xFF43D9FF)
private val Purple = Color(0xFF9274FF)

private fun checkFilesTxt(path: String): String {
    return try {
        ZipFile(path).use { zip ->
            val entry = zip.getEntry("files.txt")
                ?: return "ERROR: FILES.TXT NOT FOUND"

            if (entry.isDirectory) {
                return "ERROR: FILES.TXT IS A DIRECTORY"
            }

            val content = zip.getInputStream(entry)
                .bufferedReader()
                .use { it.readText().trim() }

            val uri = try {
                URI(content)
            } catch (_: Exception) {
                return "ERROR: INVALID URL"
            }

            val parts = uri.path.split("/")
                .filter { it.isNotBlank() }

            if (
                uri.scheme != "https" ||
                uri.host != "raw.githubusercontent.com" ||
                parts.size < 4 ||
                uri.userInfo != null ||
                uri.port != -1 ||
                uri.query != null ||
                uri.fragment != null
            ) {
                "ERROR: INVALID GITHUB RAW URL"
            } else {
                "CONFIGURATION VALID"
            }
        }
    } catch (_: Exception) {
        "ERROR: CANNOT READ APK"
    }
}

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
    var result by remember { mutableStateOf("Waiting for APK...") }
    var engine by remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        engine = try {
            NativeGuard.status()
        } catch (_: Exception) {
            "Native engine unavailable"
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = context.contentResolver.query(
                    uri, null, null, null, null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                    if (cursor.moveToFirst() && index >= 0)
                        cursor.getString(index)
                    else "Selected APK"
                } ?: "Selected APK"

                val temp = File(context.cacheDir, "viu_check.apk")

                context.contentResolver.openInputStream(uri)?.use {
                    input -> temp.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Cannot read APK")

                val digest = MessageDigest.getInstance("SHA-256")
                temp.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }

                fileName = name
                hash = digest.digest().joinToString("") {
                    "%02x".format(it)
                }
                result = checkFilesTxt(temp.absolutePath)

            } catch (_: Exception) {
                result = "ERROR: APK PROCESSING FAILED"
                hash = ""
            }
        }
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
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Text("VIU SHIELD", color = Cyan, fontSize = 30.sp)
        Text(
            "ANDROID PROTECTION SYSTEM",
            color = Color.LightGray,
            fontSize = 11.sp
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Protection Center",
                    color = Color.White,
                    fontSize = 22.sp
                )
                Text(
                    "APK integrity workspace",
                    color = Color.Gray
                )

                Spacer(Modifier.height(20.dp))

                Text("SELECTED APK", color = Cyan, fontSize = 11.sp)
                Text(fileName, color = Color.White)

                Spacer(Modifier.height(15.dp))

                Button(
                    onClick = {
                        picker.launch(
                            arrayOf("application/vnd.android.package-archive")
                        )
                    },
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

        InfoCard("FILES.TXT CHECK", result)

        InfoCard(
            "SHA-256 FINGERPRINT",
            hash.ifEmpty { "Waiting for file..." }
        )

        InfoCard("NATIVE ENGINE", engine)

        Spacer(Modifier.weight(1f))

        Text(
            "ViU Shield v0.3 • The Nhat (2016)",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun InfoCard(title: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(title, color = Cyan, fontSize = 12.sp)
            Spacer(Modifier.height(9.dp))
            Text(value, color = Color.White, fontSize = 13.sp)
        }
    }
}
