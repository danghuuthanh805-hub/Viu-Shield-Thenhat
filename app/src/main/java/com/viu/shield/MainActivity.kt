
package com.viu.shield

import android.os.Bundle
import android.net.Uri
import android.os.Build
import android.content.ContentValues
import android.content.Context
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.zip.ZipFile

private val Bg = Color(0xFF090D18)
private val Panel = Color(0xFF151D30)
private val Cyan = Color(0xFF43D9FF)
private val Purple = Color(0xFF9274FF)
private val Green = Color(0xFF48E69A)
private val Red = Color(0xFFFF5D75)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShieldApp() }
    }
}

private fun getName(context: Context, uri: Uri): String {
    return context.contentResolver.query(
        uri, null, null, null, null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(
            OpenableColumns.DISPLAY_NAME
        )
        if (cursor.moveToFirst() && index >= 0) {
            cursor.getString(index)
        } else {
            "selected.apk"
        }
    } ?: "selected.apk"
}

private fun copySelected(
    context: Context,
    uri: Uri
): File {
    val output = File(context.cacheDir, "selected.apk")

    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) {
            "Không thể đọc APK đã chọn"
        }

        output.outputStream().use { out ->
            input.copyTo(out)
        }
    }

    require(output.length() > 0) {
        "APK được chọn đang trống"
    }

    return output
}

private fun readRawUrl(apk: File): String {
    ZipFile(apk).use { zip ->
        val entry = zip.getEntry("files.txt")
            ?: error("Không tìm thấy files.txt ở thư mục gốc APK")

        require(!entry.isDirectory) {
            "files.txt không phải file hợp lệ"
        }

        val content = zip.getInputStream(entry)
            .bufferedReader()
            .use { it.readText().trim() }

        val uri = URI(content)
        val parts = uri.path.split("/")
            .filter { it.isNotBlank() }

        require(
            uri.scheme == "https" &&
            uri.host == "raw.githubusercontent.com" &&
            parts.size >= 4 &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.query == null &&
            uri.fragment == null
        ) {
            "Link GitHub Raw không hợp lệ"
        }

        return content
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")

    file.inputStream().use { input ->
        val buffer = ByteArray(16384)

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }

    return digest.digest().joinToString("") {
        "%02x".format(it)
    }
}

private fun downloadApk(
    context: Context,
    rawUrl: String
): File {
    val connection = URL(rawUrl)
        .openConnection() as HttpURLConnection

    connection.connectTimeout = 20000
    connection.readTimeout = 30000
    connection.instanceFollowRedirects = true
    connection.requestMethod = "GET"

    try {
        connection.connect()

        require(connection.responseCode == 200) {
            "Tải thất bại: HTTP ${connection.responseCode}"
        }

        val output = File(
            context.cacheDir,
            "reference_${System.currentTimeMillis()}.apk"
        )

        var total = 0L
        val maxSize = 500L * 1024L * 1024L

        connection.inputStream.use { input ->
            output.outputStream().use { out ->
                val buffer = ByteArray(16384)

                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break

                    total += count

                    require(total <= maxSize) {
                        "APK vượt quá giới hạn 500 MB"
                    }

                    out.write(buffer, 0, count)
                }
            }
        }

        require(output.length() > 0) {
            "File tải về trống"
        }

        ZipFile(output).use { zip ->
            require(zip.getEntry("AndroidManifest.xml") != null) {
                "File tải về không phải APK hợp lệ"
            }
        }

        return output
    } finally {
        connection.disconnect()
    }
}

/*
 * Chỉ lấy các DEX ở thư mục gốc và thư viện native
 * trong lib/.
 */
private fun isProtectedEntry(name: String): Boolean {
    val isDex = Regex("""classes(\d*)\.dex""")
        .matches(name)

    val isNative = name.startsWith("lib/") &&
        name.endsWith(".so")

    return isDex || isNative
}

/*
 * Tính SHA-256 trực tiếp từ nội dung từng entry ZIP.
 */
private fun hashZipEntry(
    zip: ZipFile,
    name: String
): String {
    val entry = zip.getEntry(name)
        ?: error("Không tìm thấy $name")

    val digest = MessageDigest.getInstance("SHA-256")

    zip.getInputStream(entry).use { input ->
        val buffer = ByteArray(8192)

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }

    return digest.digest().joinToString("") {
        "%02x".format(it)
    }
}

private data class IntegrityResult(
    val status: String,
    val details: String,
    val checked: Int
)

/*
 * So sánh từng DEX và .so giữa APK đang kiểm tra
 * với APK tham chiếu tải từ GitHub Raw.
 */
private fun compareApks(
    selected: File,
    reference: File
): IntegrityResult {

    val originalHashes = mutableMapOf<String, String>()
    val selectedHashes = mutableMapOf<String, String>()

    ZipFile(reference).use { zip ->
        val entries = zip.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()

            if (!entry.isDirectory &&
                isProtectedEntry(entry.name)
            ) {
                originalHashes[entry.name] =
                    hashZipEntry(zip, entry.name)
            }
        }
    }

    ZipFile(selected).use { zip ->
        val entries = zip.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()

            if (!entry.isDirectory &&
                isProtectedEntry(entry.name)
            ) {
                selectedHashes[entry.name] =
                    hashZipEntry(zip, entry.name)
            }
        }
    }

    require(originalHashes.keys.any {
        it.endsWith(".dex")
    }) {
        "APK chuẩn không có DEX"
    }

    val missing = originalHashes.keys - selectedHashes.keys
    val added = selectedHashes.keys - originalHashes.keys

    val changed = originalHashes.keys
        .intersect(selectedHashes.keys)
        .filter { name ->
            originalHashes[name] != selectedHashes[name]
        }

    if (missing.isEmpty() &&
        added.isEmpty() &&
        changed.isEmpty()
    ) {
        return IntegrityResult(
            status = "ORIGINAL",
            details = "Không phát hiện thay đổi trong DEX và thư viện SO.",
            checked = originalHashes.size
        )
    }

    val details = buildString {
        if (changed.isNotEmpty()) {
            appendLine("FILE BỊ CHỈNH SỬA:")
            changed.forEach { appendLine("• $it") }
        }

        if (missing.isNotEmpty()) {
            appendLine("FILE BỊ THIẾU:")
            missing.forEach { appendLine("• $it") }
        }

        if (added.isNotEmpty()) {
            appendLine("FILE ĐƯỢC THÊM:")
            added.forEach { appendLine("• $it") }
        }
    }

    return IntegrityResult(
        status = "MODIFIED",
        details = details.trim(),
        checked = originalHashes.size
    )
}

private fun saveToDownloads(
    context: Context,
    source: File
): String {
    val name = "ViUShield_${System.currentTimeMillis()}.apk"

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(
            MediaStore.MediaColumns.MIME_TYPE,
            "application/vnd.android.package-archive"
        )
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/ViUShield"
        )
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val resolver = context.contentResolver

    val uri = resolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        values
    ) ?: error("Không tạo được file trong Downloads")

    try {
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output)

            source.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        val done = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }

        resolver.update(uri, done, null, null)

        return "Đã lưu: Download/ViUShield/$name"
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}

@Composable
fun ShieldApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember {
        mutableStateOf("Chưa chọn APK")
    }

    var hash by remember {
        mutableStateOf("")
    }

    var rawUrl by remember {
        mutableStateOf("")
    }

    var result by remember {
        mutableStateOf("Đang chờ APK...")
    }

    var downloadResult by remember {
        mutableStateOf("Chưa tải APK chuẩn")
    }

    var integrity by remember {
        mutableStateOf("Chưa kiểm tra")
    }

    var integrityDetails by remember {
        mutableStateOf("Chưa có dữ liệu")
    }

    var nativeStatus by remember {
        mutableStateOf("Chưa kiểm tra")
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var selectedFile by remember {
        mutableStateOf<File?>(null)
    }

    var pendingSave by remember {
        mutableStateOf<File?>(null)
    }

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.android.package-archive"
        )
    ) { uri ->
        val file = pendingSave

        if (uri != null && file != null) {
            scope.launch {
                busy = true

                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver
                            .openOutputStream(uri).use { output ->
                                requireNotNull(output)

                                file.inputStream().use {
                                    it.copyTo(output)
                                }
                            }
                    }

                    downloadResult = "Đã lưu APK thành công"
                } catch (e: Exception) {
                    downloadResult = "Lỗi lưu: ${e.message}"
                } finally {
                    busy = false
                    pendingSave = null
                }
            }
        } else {
            pendingSave = null
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true

                try {
                    val name = getName(context, uri)

                    val file = withContext(Dispatchers.IO) {
                        copySelected(context, uri)
                    }

                    val url = withContext(Dispatchers.IO) {
                        readRawUrl(file)
                    }

                    val digest = withContext(Dispatchers.IO) {
                        sha256(file)
                    }

                    selectedFile = file
                    fileName = name
                    rawUrl = url
                    hash = digest

                    result = "FILES.TXT HỢP LỆ"
                    integrity = "Sẵn sàng kiểm tra"
                    integrityDetails =
                        "Chọn Download Reference APK để bắt đầu."

                    downloadResult = "Sẵn sàng tải APK chuẩn"

                } catch (e: Exception) {
                    selectedFile = null
                    rawUrl = ""
                    hash = ""

                    result = "LỖI: ${e.message}"
                    integrity = "Không thể kiểm tra"
                    integrityDetails = "APK chưa hợp lệ"
                } finally {
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        nativeStatus = try {
            NativeGuard.status()
        } catch (e: Throwable) {
            "NATIVE ERROR: ${e.javaClass.simpleName}"
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
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {

        Text(
            "VIU SHIELD",
            color = Cyan,
            fontSize = 30.sp
        )

        Text(
            "ANDROID APK INTEGRITY SYSTEM",
            color = Color.LightGray,
            fontSize = 11.sp
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(18.dp)) {

                Text(
                    "Protection Center",
                    color = Color.White,
                    fontSize = 22.sp
                )

                Spacer(Modifier.height(14.dp))

                Text("SELECTED APK", color = Cyan)
                Text(fileName, color = Color.White)

                Spacer(Modifier.height(15.dp))

                Button(
                    enabled = !busy,
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/octet-stream"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Purple
                    )
                ) {
                    Text("＋ Select APK")
                }
            }
        }

        InfoCard("FILES.TXT CHECK", result)

        InfoCard(
            "NATIVE ENGINE",
            nativeStatus
        )

        InfoCard(
            "SELECTED APK SHA-256",
            hash.ifEmpty { "Chưa có dữ liệu" }
        )

        InfoCard(
            "GITHUB RAW",
            rawUrl.ifEmpty { "Chưa có link" }
        )

        Button(
            enabled = selectedFile != null &&
                rawUrl.isNotEmpty() &&
                !busy,

            onClick = {
                val selected = selectedFile ?: return@Button

                scope.launch {
                    busy = true

                    downloadResult = "Đang tải APK chuẩn..."
                    integrity = "ĐANG KIỂM TRA"
                    integrityDetails = "Đang so sánh DEX và thư viện SO..."

                    try {
                        val downloaded = withContext(Dispatchers.IO) {
                            downloadApk(context, rawUrl)
                        }

                        val downloadedHash =
                            withContext(Dispatchers.IO) {
                                sha256(downloaded)
                            }

                        val comparison =
                            withContext(Dispatchers.IO) {
                                compareApks(selected, downloaded)
                            }

                        integrity = comparison.status

                        integrityDetails =
                            comparison.details +
                            "\n\nSố file chuẩn đã kiểm tra: ${comparison.checked}"

                        if (comparison.status == "ORIGINAL") {
                            integrity = "ORIGINAL"
                        } else {
                            integrity = "MODIFIED"
                        }

                        val message = if (
                            Build.VERSION.SDK_INT >= 29
                        ) {
                            withContext(Dispatchers.IO) {
                                saveToDownloads(context, downloaded)
                            }
                        } else {
                            pendingSave = downloaded
                            savePicker.launch(downloaded.name)
                            "Chọn thư mục Download để lưu APK"
                        }

                        downloadResult =
                            "$message\nSHA-256 APK chuẩn: $downloadedHash"

                    } catch (e: Exception) {
                        integrity = "CHECK FAILED"
                        integrityDetails = e.message
                            ?: "Lỗi không xác định"

                        downloadResult =
                            "THẤT BẠI: ${e.message}"

                    } finally {
                        busy = false
                    }
                }
            },

            modifier = Modifier.fillMaxWidth(),

            colors = ButtonDefaults.buttonColors(
                containerColor = Cyan,
                contentColor = Color.Black
            )
        ) {
            Text("↓  DOWNLOAD & VERIFY APK")
        }

        InfoCard(
            "INTEGRITY STATUS",
            integrity
        )

        InfoCard(
            "INTEGRITY DETAILS",
            integrityDetails
        )

        InfoCard(
            "DOWNLOAD RESULT",
            downloadResult
        )

        if (busy) {
            CircularProgressIndicator(
                color = Cyan,
                modifier = Modifier.align(
                    Alignment.CenterHorizontally
                )
            )

            Text(
                "Đang xử lý...",
                color = Color.White,
                modifier = Modifier.align(
                    Alignment.CenterHorizontally
                )
            )
        }

        Text(
            "ViU Shield 1.0 • The Nhat",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.align(
                Alignment.CenterHorizontally
            )
        )
    }
}

@Composable
fun InfoCard(
    title: String,
    value: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {

            Text(
                title,
                color = Cyan,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                value,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}
