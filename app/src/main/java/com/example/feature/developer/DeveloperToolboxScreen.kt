package com.example.feature.developer

import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.common.SystemUtils
import com.example.core.ui.AquaGlassCard
import com.example.core.ui.AquaTopBar
import com.example.core.ui.HealthCondition
import com.example.core.ui.KeyValueRow
import com.example.core.ui.SectionHeader
import com.example.core.ui.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAquaColors
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

private enum class DevTab(val label: String) {
    JSON("JSON"),
    BASE64("Base64"),
    HASH("Hashes"),
    URL("URL"),
    TEXT("Text & Diff")
}

// MODULE 05 — DEVELOPER TOOLBOX
@Composable
fun DeveloperToolboxScreen(
    onBack: () -> Unit
) {
    val aqua = LocalAquaColors.current
    var activeTab by remember { mutableStateOf(DevTab.JSON) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(aqua.gradientStart, MaterialTheme.colorScheme.background)))
    ) {
        AquaTopBar(
            title = "Developer Toolbox",
            subtitle = "Module 05 • JSON, Base64, Hashes, URL & Text Suite",
            onBack = onBack
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DevTab.entries) { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("dev_tab_${tab.name.lowercase()}")
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                when (activeTab) {
                    DevTab.JSON -> JsonToolPanel()
                    DevTab.BASE64 -> Base64ToolPanel()
                    DevTab.HASH -> HashGeneratorPanel()
                    DevTab.URL -> UrlInspectorPanel()
                    DevTab.TEXT -> TextUtilitiesPanel()
                }
            }
        }
    }
}

@Composable
private fun JsonToolPanel() {
    val context = LocalContext.current
    var input by remember {
        mutableStateOf("""{"app":"PandaNex","version":1,"modules":["CPU","Battery","SmartActions"],"active":true}""")
    }
    var output by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("Ready to validate or format JSON") }
    var isValid by remember { mutableStateOf<Boolean?>(null) }

    AquaGlassCard(glowAccent = true) {
        SectionHeader(title = "JSON Formatter, Validator & Minifier", icon = Icons.Default.Code)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            label = { Text("JSON Input") }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    try {
                        val trimmed = input.trim()
                        output = if (trimmed.startsWith("[")) {
                            JSONArray(trimmed).toString(2)
                        } else {
                            JSONObject(trimmed).toString(2)
                        }
                        isValid = true
                        statusMsg = "Valid JSON — Formatted (2-space indent)"
                    } catch (e: Exception) {
                        isValid = false
                        statusMsg = "JSON Syntax Error: ${e.message ?: "Invalid structure"}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Format")
            }
            OutlinedButton(
                onClick = {
                    try {
                        val trimmed = input.trim()
                        output = if (trimmed.startsWith("[")) {
                            JSONArray(trimmed).toString()
                        } else {
                            JSONObject(trimmed).toString()
                        }
                        isValid = true
                        statusMsg = "Valid JSON — Minified"
                    } catch (e: Exception) {
                        isValid = false
                        statusMsg = "JSON Syntax Error: ${e.message ?: "Invalid structure"}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Minify")
            }
            OutlinedButton(
                onClick = {
                    try {
                        val trimmed = input.trim()
                        if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                        isValid = true
                        statusMsg = "JSON Validation Passed: Well-formed payload"
                    } catch (e: Exception) {
                        isValid = false
                        statusMsg = "Invalid JSON: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Validate")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        StatusPill(
            label = statusMsg,
            condition = when (isValid) {
                true -> HealthCondition.NORMAL
                false -> HealthCondition.CRITICAL
                null -> HealthCondition.INFO
            }
        )
        if (output.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = output,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                label = { Text("JSON Output") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { SystemUtils.copyToClipboard(context, "JSON Output", output) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Result")
            }
        }
    }
}

@Composable
private fun Base64ToolPanel() {
    val context = LocalContext.current
    var input by remember { mutableStateOf("PandaNex Clean Aqua Dynamics") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AquaGlassCard(glowAccent = true) {
        SectionHeader(title = "Base64 Encoder & Decoder")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Source Text or Base64 String") }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    error = null
                    output = Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Encode Base64")
            }
            OutlinedButton(
                onClick = {
                    try {
                        error = null
                        val bytes = Base64.decode(input.trim(), Base64.DEFAULT)
                        output = String(bytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        error = "Invalid Base64 input: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Decode Base64")
            }
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (output.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            KeyValueRow("Output", output) {
                SystemUtils.copyToClipboard(context, "Base64 Result", output)
            }
        }
    }
}

@Composable
private fun HashGeneratorPanel() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("PandaNex") }

    fun digestHex(algo: String, raw: String): String {
        val md = MessageDigest.getInstance(algo)
        return md.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    val md5 = remember(text) { digestHex("MD5", text) }
    val sha1 = remember(text) { digestHex("SHA-1", text) }
    val sha256 = remember(text) { digestHex("SHA-256", text) }
    val sha512 = remember(text) { digestHex("SHA-512", text) }

    AquaGlassCard(glowAccent = true) {
        SectionHeader(title = "Cryptographic Hash Generator")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Input String to Hash") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        KeyValueRow("MD5", md5) { SystemUtils.copyToClipboard(context, "MD5", md5) }
        KeyValueRow("SHA-1", sha1) { SystemUtils.copyToClipboard(context, "SHA-1", sha1) }
        KeyValueRow("SHA-256", sha256) { SystemUtils.copyToClipboard(context, "SHA-256", sha256) }
        KeyValueRow("SHA-512", sha512) { SystemUtils.copyToClipboard(context, "SHA-512", sha512) }
    }
}

@Composable
private fun UrlInspectorPanel() {
    val context = LocalContext.current
    var urlInput by remember {
        mutableStateOf("https://pandanex.dev/diagnostics?module=cpu&refresh=1500&theme=dark_aqua")
    }
    var transformed by remember { mutableStateOf("") }

    val parsedParams = remember(urlInput) {
        runCatching {
            val uri = Uri.parse(urlInput.trim())
            uri.queryParameterNames.map { key -> key to (uri.getQueryParameter(key) ?: "") }
        }.getOrDefault(emptyList())
    }

    AquaGlassCard(glowAccent = true) {
        SectionHeader(title = "URL Encoder, Decoder & Query Inspector")
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("URL or Component String") }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { transformed = URLEncoder.encode(urlInput, "UTF-8") },
                modifier = Modifier.weight(1f)
            ) {
                Text("URL Encode")
            }
            OutlinedButton(
                onClick = {
                    transformed = runCatching { URLDecoder.decode(urlInput, "UTF-8") }
                        .getOrElse { "Decode error: ${it.message}" }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("URL Decode")
            }
        }
        if (transformed.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            KeyValueRow("Result", transformed) {
                SystemUtils.copyToClipboard(context, "URL Result", transformed)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(title = "Query Parameters (${parsedParams.size})")
        if (parsedParams.isEmpty()) {
            Text(
                "No query parameters detected in URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            parsedParams.forEach { (k, v) ->
                KeyValueRow(k, v) { SystemUtils.copyToClipboard(context, k, v) }
            }
        }
    }
}

@Composable
private fun TextUtilitiesPanel() {
    var textA by remember { mutableStateOf("PandaNex Smart Control Center\nClean Aqua Dynamics") }
    var textB by remember { mutableStateOf("PandaNex Smart Control Center\nFuturistic Toolbox") }

    val charCount = textA.length
    val wordCount = remember(textA) {
        textA.trim().takeIf { it.isNotEmpty() }?.split("\\s+".toRegex())?.size ?: 0
    }
    val lineCount = remember(textA) {
        if (textA.isEmpty()) 0 else textA.lines().size
    }

    val diffReport = remember(textA, textB) {
        val linesA = textA.lines()
        val linesB = textB.lines()
        val maxLines = maxOf(linesA.size, linesB.size)
        val diffs = mutableListOf<String>()
        for (i in 0 until maxLines) {
            val a = linesA.getOrNull(i)
            val b = linesB.getOrNull(i)
            if (a != b) {
                diffs.add("Line ${i + 1}: '${a ?: "∅"}' ≠ '${b ?: "∅"}'")
            }
        }
        if (diffs.isEmpty()) "Identical! Both text blocks match line-for-line." else diffs.joinToString("\n")
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AquaGlassCard(glowAccent = true) {
            SectionHeader(title = "Text Metrics & Case Converter")
            OutlinedTextField(
                value = textA,
                onValueChange = { textA = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Primary Text") }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusPill("Chars: $charCount", HealthCondition.INFO)
                StatusPill("Words: $wordCount", HealthCondition.INFO)
                StatusPill("Lines: $lineCount", HealthCondition.INFO)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { textA = textA.uppercase() }, modifier = Modifier.weight(1f)) {
                    Text("UPPER")
                }
                OutlinedButton(onClick = { textA = textA.lowercase() }, modifier = Modifier.weight(1f)) {
                    Text("lower")
                }
                OutlinedButton(
                    onClick = {
                        textA = textA.split(" ").joinToString(" ") { w ->
                            w.lowercase().replaceFirstChar { it.uppercase() }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Title Case")
                }
            }
        }

        AquaGlassCard {
            SectionHeader(title = "Line-by-Line Text Comparison")
            OutlinedTextField(
                value = textB,
                onValueChange = { textB = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Comparison Text (Compare against Primary)") }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = diffReport,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                color = if (textA == textB) LocalAquaColors.current.statusNormal else MaterialTheme.colorScheme.primary
            )
        }
    }
}
