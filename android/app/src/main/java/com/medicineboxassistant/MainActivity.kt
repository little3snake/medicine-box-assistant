package com.medicineboxassistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF245E54), secondary = Color(0xFF6C5A86),
                background = Color(0xFFF6F8F5), surface = Color.White
            )) { MedicineScreen() }
        }
    }
}

@Composable
private fun MedicineScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("connection", 0) }
    var baseUrl by rememberSaveable { mutableStateOf(preferences.getString("url", "http://10.0.2.2:8080")!!) }
    var demo by rememberSaveable { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    var fields by remember { mutableStateOf(PackageFields()) }
    var identification by remember { mutableStateOf<Identification?>(null) }
    var selected by remember { mutableStateOf<ProductCandidate?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(listOf<ChatEntry>()) }
    var card by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var question by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun resetProduct() {
        confirmed = false
        selected = null
        identification = null
        entries = emptyList()
        card = emptyList()
        errorMessage = null
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        errorMessage = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: Exception) { errorMessage = exception.message ?: "Не удалось выполнить запрос" }
            finally { busy = false }
        }
    }
    fun loadPhoto(uri: Uri) {
        resetProduct()
        photo = uri.toString()
        perform {
            if (demo) {
                fields = PackageFields("Учебная упаковка", "Сверьте с фото", "Сверьте с фото", "Сверьте с фото")
                identification = Identification(fields, listOf(ProductCandidate("demo", "Учебная упаковка · демоданные")), true)
            } else {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count = it.read(buffer)
                        while (count != -1) {
                            require(output.size() + count <= 10 * 1024 * 1024) { "Фото должно быть не больше 10 МБ" }
                            output.write(buffer, 0, count)
                            count = it.read(buffer)
                        }
                        output.toByteArray()
                    } ?: error("Не удалось прочитать фото")
                }
                identification = MedboxApi(baseUrl).identifyPhoto(bytes, mime)
                fields = identification!!.fields
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPhoto(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { loadPhoto(Uri.parse(it)) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Medicine Box", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Инструкция к вашей упаковке", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { settings = true }, enabled = !busy) { Text(if (demo) "Демо ▾" else "API ▾") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFE3EFE8)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Начнём с упаковки", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text("Прикрепите фото. Проверьте название, дозировку и производителя, прежде чем задавать вопрос.")
                            Text("Справочная информация для взрослых. Решение о лечении обсудите с врачом или фармацевтом.", style = MaterialTheme.typography.bodySmall)
                            if (demo) Text("ДЕМОРЕЖИМ · распознавание и ответы учебные, API не вызывается.", fontWeight = FontWeight.Bold, color = Color(0xFF6C4D22))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { gallery.launch("image/*") }, enabled = !busy) { Text("Выбрать фото") }
                                OutlinedButton(onClick = {
                                    try {
                                        val file = File.createTempFile("package-", ".jpg", context.cacheDir)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
                                        cameraUri = uri.toString()
                                        camera.launch(uri)
                                    } catch (exception: Exception) { errorMessage = "Не удалось открыть камеру: ${exception.message}" }
                                }, enabled = !busy) { Text("Камера") }
                            }
                            if (photo != null) Text("Фото прикреплено", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Проверьте данные", fontWeight = FontWeight.Bold)
                            Text("Можно заполнить вручную, если фото не читается.", style = MaterialTheme.typography.bodySmall)
                            fun edit(next: PackageFields) { resetProduct(); fields = next }
                            OutlinedTextField(fields.name, { edit(fields.copy(name = it)) }, label = { Text("Название") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(fields.strength, { edit(fields.copy(strength = it)) }, label = { Text("Дозировка на упаковке") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(fields.form, { edit(fields.copy(form = it)) }, label = { Text("Форма выпуска") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(fields.manufacturer, { edit(fields.copy(manufacturer = it)) }, label = { Text("Производитель") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                resetProduct()
                                perform {
                                    identification = if (demo) Identification(fields, listOf(ProductCandidate("demo", "${fields.name} · учебный вариант")), true)
                                    else MedboxApi(baseUrl).identifyFields(fields)
                                }
                            }, enabled = !busy && fields.name.isNotBlank()) { Text("Найти в каталоге") }
                        }
                    }
                }
                identification?.let { result ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (result.clarify) "Уточните вариант по упаковке" else "Подтвердите найденный вариант", fontWeight = FontWeight.Bold)
                            if (result.candidates.isEmpty()) Text("Подходящего варианта нет. Уточните данные или сделайте другое фото.")
                            result.candidates.forEach { candidate ->
                                OutlinedButton(onClick = {
                                    selected = candidate; confirmed = false; entries = emptyList(); card = emptyList()
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text((if (selected == candidate) "✓ " else "") + candidate.label)
                                }
                            }
                            Button(onClick = { confirmed = true }, enabled = selected != null && !busy && !confirmed) {
                                Text(if (confirmed) "Упаковка подтверждена" else "Это моя упаковка")
                            }
                        }
                    }
                }
                if (confirmed) item {
                    Text("Вопросы по ${selected!!.label}", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { perform {
                        card = if (demo) listOf("Учебная карточка" to "Здесь появятся разделы официальной инструкции после подключения API.")
                        else MedboxApi(baseUrl).card(selected!!.variantId)
                    } }, enabled = !busy) { Text("Открыть карточку инструкции") }
                    card.forEach { (title, text) -> EvidencePanel(title, text) }
                }
                items(entries) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(Modifier.align(Alignment.End).fillMaxWidth(0.9f), shape = RoundedCornerShape(16.dp), color = Color(0xFFECE6F3)) {
                            Text(entry.question, Modifier.padding(16.dp))
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(when (entry.response.status) {
                                    "ANSWERED" -> "По инструкции"
                                    "CLARIFY" -> "Нужно уточнение"
                                    "REFER_TO_PHARMACIST" -> "Обсудите со специалистом"
                                    else -> "Недостаточно сведений"
                                }, fontWeight = FontWeight.Bold)
                                Text(entry.response.answer)
                                entry.response.quotes.forEach { quote -> EvidencePanel(quote.section, quote.text, quote.sourceUrl) }
                            }
                        }
                    }
                }
                errorMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) } }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(question, { question = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text(if (confirmed) "Вопрос об инструкции…" else "Сначала подтвердите упаковку") },
                    enabled = confirmed && !busy, maxLines = 4)
                TextButton(onClick = {
                    val text = question.trim()
                    val product = selected ?: return@TextButton
                    perform {
                        val response = if (demo) MedicineAnswer("INSUFFICIENT",
                            "Это демонстрация интерфейса. Для ответа по официальной инструкции подключите работающий сервер в настройках API.", emptyList())
                        else MedboxApi(baseUrl).ask(product.variantId, text)
                        entries = entries + ChatEntry(text, response)
                        question = ""
                    }
                }, enabled = confirmed && !busy && question.isNotBlank()) { Text("Отправить") }
            }
        }
    }
    if (settings) {
        var url by remember { mutableStateOf(baseUrl) }
        var useDemo by remember { mutableStateOf(demo) }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("Подключение") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useDemo, { useDemo = it }); Text("Учебный деморежим") }
                OutlinedTextField(url, { url = it; invalid = false }, label = { Text("Адрес бэкенда") }, isError = invalid)
                Text("Эмулятор: http://10.0.2.2:8080. Телефон: адрес компьютера в общей сети. API-ключ сюда не вводится.")
                if (invalid) Text("Проверьте адрес сервера", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(onClick = {
            try {
                MedboxApi(url.trim())
                baseUrl = url.trim(); demo = useDemo
                preferences.edit().putString("url", baseUrl).apply()
                resetProduct(); fields = PackageFields(); photo = null
                settings = false
            } catch (_: Exception) { invalid = true }
        }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = { settings = false }) { Text("Отмена") } })
    }
}

@Composable
private fun EvidencePanel(title: String, text: String, sourceUrl: String = "") {
    var expanded by remember(text) { mutableStateOf(false) }
    val context = LocalContext.current
    Column {
        TextButton(onClick = { expanded = !expanded }) { Text((if (expanded) "▾ " else "▸ ") + title) }
        if (expanded) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
            val source = runCatching { URI(sourceUrl) }.getOrNull()
            if (source?.scheme == "https" && !source.host.isNullOrBlank()) {
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) } }) { Text("Открыть источник ↗") }
            }
        }
    }
}
