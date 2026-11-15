
package com.viu.shield

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DexClass(
    val dexFile: String,
    val className: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ViUShieldUI()
        }
    }

    private fun readUleb(data: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = start

        while (pos < data.size) {
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)

            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 28) break
        }

        return Pair(result, pos)
    }

    private fun readDexClasses(data: ByteArray): List<String> {
        if (data.size < 112) return emptyList()

        if (data[0] != 'd'.code.toByte() ||
            data[1] != 'e'.code.toByte() ||
            data[2] != 'x'.code.toByte()
        ) return emptyList()

        val buffer = ByteBuffer.wrap(data)
            .order(ByteOrder.LITTLE_ENDIAN)

        val stringCount = buffer.getInt(56)
        val stringOffset = buffer.getInt(60)
        val typeCount = buffer.getInt(64)
        val typeOffset = buffer.getInt(68)

        if (stringCount < 0 || typeCount < 0 ||
            stringCount > 1000000 || typeCount > 1000000
        ) return emptyList()

        if (stringOffset < 0 || typeOffset < 0 ||
            stringOffset.toLong() + stringCount.toLong() * 4 > data.size ||
            typeOffset.toLong() + typeCount.toLong() * 4 > data.size
        ) return emptyList()

        val strings = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            val offset = buffer.getInt(stringOffset + i * 4)

            if (offset < 0 || offset >= data.size) {
                strings.add("")
                continue
            }

            val (_, textStart) = readUleb(data, offset)
            var end = textStart

            while (end < data.size && data[end] != 0.toByte()) {
                end++
            }

            strings.add(
                if (end <= data.size)
                    String(data, textStart, end - textStart, Charsets.UTF_8)
                else ""
            )
        }

        val classes = mutableListOf<String>()

        for (i in 0 until typeCount) {
            val index = buffer.getInt(typeOffset + i * 4)

            if (index !in strings.indices) continue

            val descriptor = strings[index]

            if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                classes.add(
                    descriptor
                        .removePrefix("L")
                        .removeSuffix(";")
                        .replace('/', '.')
                )
            }
        }

        return classes.distinct().sorted()
    }

    private suspend fun inspectApk(uri: Uri): List<DexClass> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<DexClass>()

            contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break

                        if (!entry.isDirectory &&
                            Regex("""classes(\d*)\.dex""")
                                .matches(entry.name)
                        ) {
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0

                            while (true) {
                                val count = zip.read(buffer)
                                if (count == -1) break

                                total += count
                                if (total > 100 * 1024 * 1024) {
                                    throw Exception("DEX file is too large")
                                }

                                output.write(buffer, 0, count)
                            }

                            val classes = readDexClasses(output.toByteArray())

                            classes.forEach { className ->
                                result.add(
                                    DexClass(entry.name, className)
                                )
                            }
                        }

                        zip.closeEntry()
                    }
                }
            }

            result.sortedWith(
                compareBy<DexClass> { it.dexFile }
                    .thenBy { it.className }
            )
        }

    @Composable
    private fun ViUShieldUI() {
        var selectedName by remember { mutableStateOf("No APK selected") }
        var search by remember { mutableStateOf("") }
        var classes by remember { mutableStateOf<List<DexClass>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        var nativeStatus by remember {
            mutableStateOf("Checking native engine...")
        }

        val scope = rememberCoroutineScope()

        val picker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                selectedName = uri.lastPathSegment ?: "Selected APK"
                loading = true
                error = ""
                classes = emptyList()

                scope.launch {
                    try {
                        classes = inspectApk(uri)
                        if (classes.isEmpty()) {
                            error = "No readable classes found."
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "APK inspection failed."
                    } finally {
                        loading = false
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            nativeStatus = try {
                NativeGuard.status()
            } catch (e: Throwable) {
                "Native library unavailable"
            }
        }

        val filtered = classes.filter {
            it.className.contains(search, ignoreCase = true) ||
            it.dexFile.contains(search, ignoreCase = true)
        }

        val groups = filtered.groupBy { it.dexFile }.toSortedMap()

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF8B80FF),
                background = Color(0xFF0B0D14),
                surface = Color(0xFF171A25)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0D14))
                    .padding(18.dp)
            ) {
                Text(
                    "ViU Shield",
                    color = Color.White,
                    fontSize = 28.sp
                )

                Text(
                    "APK Protection & Inspector",
                    color = Color(0xFF9298AC),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(20.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF171A25)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("NATIVE ENGINE", color = Color(0xFF9298AC))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            nativeStatus,
                            color = Color(0xFF8B80FF),
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        picker.launch(arrayOf("application/vnd.android.package-archive"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select APK")
                }

                Text(
                    selectedName,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search classes or DEX files") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("CLASS EXPLORER", color = Color.White)
                    Text(
                        "${filtered.size} classes",
                        color = Color(0xFF8B80FF)
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (loading) {
                    CircularProgressIndicator()
                }

                if (error.isNotEmpty()) {
                    Text(error, color = Color(0xFFFF7777))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEach { (dexName, entries) ->
                        item(key = "header_$dexName") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF24283A)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(dexName, color = Color(0xFFB7AEFF))
                                    Text(
                                        "${entries.size} classes",
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }

                        items(entries, key = { "${it.dexFile}:${it.className}" }) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF171A25)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    it.className,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    "ViU Shield • Native Engine",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}
