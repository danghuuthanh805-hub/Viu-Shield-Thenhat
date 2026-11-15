
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

enum class ConfigStatus {
    FILE_NOT_FOUND,
    INVALID_URL,
    VALID,
    READ_ERROR
}

data class CheckResult(
    val status: ConfigStatus,
    val message: String
)

fun checkFilesTxt(apkPath: String): CheckResult {
    return try {
        ZipFile(apkPath).use { zip ->

            // Chỉ tìm files.txt ở thư mục gốc APK
            val entry = zip.getEntry("files.txt")
                ?: return CheckResult(
                    ConfigStatus.FILE_NOT_FOUND,
                    "ERROR: FILES.TXT NOT FOUND"
                )

            if (entry.isDirectory) {
                return CheckResult(
                    ConfigStatus.READ_ERROR,
                    "ERROR: FILES.TXT IS A DIRECTORY"
                )
            }

            val content = zip.getInputStream(entry)
                .bufferedReader()
                .use { it.readText().trim() }

            val validUrl = try {
                val uri = URI(content)
                val segments = uri.path
                    .split("/")
                    .filter { it.isNotBlank() }

                uri.scheme == "https" &&
                uri.host == "raw.githubusercontent.com" &&
                segments.size >= 4 &&
                segments.none { it == "." || it == ".." } &&
                uri.userInfo == null &&
                uri.port == -1 &&
                uri.query == null &&
                uri.fragment == null
            } catch (_: Exception) {
                false
            }

            if (!validUrl) {
                CheckResult(
                    ConfigStatus.INVALID_URL,
                    "ERROR: INVALID GITHUB RAW URL"
                )
            } else {
                CheckResult(
                    ConfigStatus.VALID,
                    "CONFIGURATION VALID"
                )
            }
        }
    } catch (e: Exception) {
        CheckResult(
            ConfigStatus.READ_ERROR,
            "ERROR: CANNOT READ APK"
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShieldApp()
        }
    }
}

@Composable
fun ShieldApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var fileName by remember {
        mutableStateOf("No APK selected")
    }

    var hash by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf("Waiting for APK")
    }

    var configResult by remember {
        mutableStateOf("Waiting for APK...")
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->

        if (uri != null) {
            try {
                val name = context.contentResolver
                    .query(uri, null, null, null, null)
                    ?.use { cursor ->
                        val index = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )
                        if (cursor.moveToFirst() && index >= 0)
                            cursor.getString(index)
                        else "Selected APK"
                    } ?: "Selected APK"

                val tempFile = File(
                    context.cacheDir,
                    "viu_check.apk"
                )

                context.contentResolver
                    .openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("Cannot open APK")

                val digest = MessageDigest.getInstance("SHA-256")

                tempFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)

                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }

                hash = digest.digest().joinToString("") {
                    "%02x".format(it)
                }

                fileName = name

                val result = checkFilesTxt(
                    tempFile.absolutePath
                )

                configResult = result.message

                status = when (result.status) {
                    ConfigStatus.VALID -> "Configuration valid"
                    ConfigStatus.FILE_NOT_FOUND -> "Missing configuration"
                    ConfigStatus.INVALID_URL -> "Invalid configuration"
                    ConfigStatus.READ_ERROR -> "APK read error"
                }

            } catch (e: Exception) {
                status = "ERROR"
                configResult = "ERROR: APK PROCESSING FAILED"
                hash = ""
            }
        }
    }

    val nativeStatus = remember {
        try {
            NativeGuard.status()
        } catch (e: Exception) {
            "Native library unavailable"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF111B35),
                        Bg,
                        Bg
                    )
                )
            )
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {

        Spacer(Modifier.height(12.dp))

        Text(
            "VIU SHIELD",
            color = Cyan,
            fontSize = 29.sp
        )

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
                    fontSize = 21.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "APK integrity workspace",
                    color = Color.Gray
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    "SELECTED FILE",
                    color = Cyan,
                    fontSize = 11.sp
                )

                Text(
                    fileName,
                    color = Color.White,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(16.dp))

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

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {

                Text(
                    "FILES.TXT CHECK",
                    color = Cyan
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    configResult,
                    color = Color.White,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "STATUS: $status",
                    color = Cyan
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {

                Text(
                    "SHA-256 FINGERPRINT",
                    color = Cyan,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    hash.ifEmpty { "Waiting for file..." },
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {

                Text(
                    "NATIVE ENGINE",
                    color = Cyan
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    nativeStatus,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            "ViU Shield • v0.2 • The Nhat (2016)",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.align(
                Alignment.CenterHorizontally
            )
        )
    }
}
