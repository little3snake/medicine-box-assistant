package com.medicineboxassistant

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[MedicineViewModel::class.java]
        setContent {
            val density = LocalDensity.current
            // Respect system font scaling; the optional app control adds another modest increase.
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * if (model.large) 1.15f else 1f)) {
                MaterialTheme(colorScheme = lightColorScheme(
                    primary = Color(0xFF2455A6), onPrimary = Color.White,
                    primaryContainer = Color(0xFFEAF0FC), onPrimaryContainer = Color(0xFF173B78),
                    background = Color(0xFFF5F7FB), onBackground = Color(0xFF18263D),
                    surface = Color.White, onSurface = Color(0xFF18263D),
                    surfaceVariant = Color(0xFFEDF1F8), onSurfaceVariant = Color(0xFF526079),
                    outline = Color(0xFF748197)
                )) { MedicineApp(model) }
            }
        }
    }
}

@Composable
private fun MedicineApp(model: MedicineViewModel) {
    val context = LocalContext.current
    var deleteItem by remember { mutableStateOf<SavedMedicine?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) model.identifyPhoto(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) model.cameraUri?.let(model::identifyPhoto)
    }
    fun takePhoto() {
        runCatching {
            val file = File.createTempFile("package-", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
            model.cameraUri = uri
            camera.launch(uri)
        }.onFailure { model.reportPhotoFailure() }
    }
    BackHandler(model.page != Page.HOME || model.busy) { model.back() }
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalPharmacy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Medicine Box", Modifier.weight(1f).padding(start = 10.dp), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = model::toggleText, enabled = !model.busy, contentPadding = PaddingValues(8.dp)) {
                        Text("Аа", modifier = Modifier.semantics { contentDescription = if (model.large) "Обычный размер текста" else "Увеличить текст" }, fontSize = 18.sp)
                    }
                    IconButton(onClick = { model.navigate(Page.SETTINGS) }, enabled = !model.busy) {
                        Icon(Icons.Outlined.Settings, "Настройки подключения")
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if (model.page == Page.CHAT && model.confirmed) QuestionComposer(model)
                if (model.page != Page.CHAT) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(selected = model.page != Page.CABINET, onClick = { model.navigate(Page.HOME) },
                            enabled = !model.busy, icon = { Icon(Icons.Outlined.ChatBubbleOutline, null) }, label = { Text("Помощник") })
                        NavigationBarItem(selected = model.page == Page.CABINET, onClick = { model.navigate(Page.CABINET) },
                            enabled = !model.busy, icon = { Icon(Icons.Outlined.Inventory2, null) }, label = { Text("Моя аптечка") })
                    }
                }
            }
        }
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(model.page) { listState.scrollToItem(0) }
        LaunchedEffect(model.entries.size) { if (model.entries.isNotEmpty()) listState.animateScrollToItem(0) }
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState,
            contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (model.busy) {
                item { Title("Подождите немного", model.progress) }
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { Text("Распознавание фото иногда занимает до нескольких минут. Можно отменить запрос.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { SecondaryAction("Отменить запрос", model::back) }
            } else {
                if (model.demo) item { Label("Демо · учебные данные, API не вызывается") }
                when (model.page) {
                    Page.HOME -> {
                        item { Title("Инструкция к вашему лекарству", "Добавьте фото упаковки, чтобы узнать о препарате.") }
                        item { PackageIllustration() }
                        item { ActionGroup {
                            PrimaryAction("Сфотографировать упаковку", ::takePhoto, Icons.Outlined.PhotoCamera)
                            SecondaryAction("Выбрать фото", { gallery.launch("image/*") }, Icons.Outlined.Image)
                            TextButton(onClick = { model.navigate(Page.MANUAL) }, modifier = Modifier.fillMaxWidth()) { Text("Найти по названию") }
                        } }
                        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Как это работает", fontWeight = FontWeight.SemiBold)
                            Step(1, "Добавьте фото упаковки")
                            Step(2, "Проверьте название и дозировку")
                            Step(3, "Задайте вопрос об инструкции")
                        } }
                    }
                    Page.MANUAL -> {
                        item { BackAction(model) }
                        item { Title("Данные с упаковки", "Перепишите название. Дозировка и производитель помогут различить похожие варианты.") }
                        item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Field("Название лекарства", model.fields.name) { model.editFields(model.fields.copy(name = it)) }
                            Field("Дозировка на упаковке", model.fields.strength) { model.editFields(model.fields.copy(strength = it)) }
                            Field("Форма выпуска", model.fields.form) { model.editFields(model.fields.copy(form = it)) }
                            Field("Производитель", model.fields.manufacturer) { model.editFields(model.fields.copy(manufacturer = it)) }
                        } }
                        item { PrimaryAction("Найти лекарство", model::searchFields, Icons.Outlined.Search, model.fields.name.isNotBlank()) }
                    }
                    Page.CANDIDATES -> {
                        val candidates = model.identification?.candidates.orEmpty()
                        item { BackAction(model) }
                        item { Title(if (candidates.isEmpty()) "Не нашли подходящий вариант" else "Какой вариант на вашей упаковке?",
                            if (candidates.isEmpty()) "Каталог пока небольшой. Проверьте данные или попробуйте другое фото."
                            else "Сверьте дозировку и производителя. Затем подтвердите упаковку.") }
                        candidates.forEach { candidate -> item(key = candidate.variantId) {
                            OutlinedCard(onClick = { model.choose(candidate) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(candidate.label, Modifier.weight(1f), fontSize = 18.sp)
                                    Icon(Icons.Outlined.ChevronRight, null)
                                }
                            }
                        } }
                        item { SecondaryAction("Исправить данные", { model.navigate(Page.MANUAL) }) }
                        item { SecondaryAction("Выбрать другое фото", { gallery.launch("image/*") }) }
                    }
                    Page.CONFIRM -> {
                        val product = model.product
                        item { BackAction(model) }
                        item { Title("Проверьте упаковку", "Название, дозировка и производитель должны совпадать.") }
                        item { PhotoPreview(model.photo) }
                        if (product != null) {
                            item { ProductDetails(product) }
                            if (product.sample) item { Label("Учебный образец. Медицинские ответы по нему недоступны.") }
                            item { ActionGroup {
                                PrimaryAction("Всё совпадает, продолжить", model::confirm, Icons.Outlined.Check)
                                SecondaryAction("Исправить данные", { model.navigate(Page.MANUAL) })
                                TextButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать другое фото") }
                            } }
                        }
                    }
                    Page.CHAT -> {
                        val product = model.product
                        item { BackAction(model, "Другое лекарство") }
                        if (product != null && model.confirmed) {
                            item { SelectedProduct(product) }
                            if (model.entries.isEmpty()) {
                                item { Title("Что хотите узнать?", "Выберите вопрос или напишите свой внизу.") }
                                item { ActionGroup {
                                    listOf("Для чего применяется?", "Какие противопоказания?", "Какие побочные действия?", "Как хранить?").forEach { text ->
                                        SecondaryAction(text, { model.ask(text) }, Icons.Outlined.ChevronRight)
                                    }
                                } }
                            }
                            // Latest exchange first, so the new answer remains visible without a nested scroll view.
                            model.entries.asReversed().forEachIndexed { index, entry -> item(key = "exchange-${model.entries.size - index}") {
                                Exchange(entry)
                            } }
                            item { ActionGroup {
                                SecondaryAction("Открыть инструкцию", model::openLeaflet, Icons.Outlined.MenuBook)
                                SecondaryAction(if (model.isSaved()) "Сохранено в аптечке" else "Сохранить в аптечку", model::saveProduct,
                                    if (model.isSaved()) Icons.Outlined.Check else Icons.Outlined.BookmarkBorder, !model.isSaved())
                            } }
                        }
                    }
                    Page.LEAFLET -> {
                        item { BackAction(model, "К вопросам") }
                        item { Title("Инструкция", model.product?.label.orEmpty()) }
                        model.leaflet?.let { leaflet ->
                            item { SourceLink(leaflet.sourceUrl) }
                            leaflet.sections.forEach { section -> item(key = section.id) { EvidencePanel(section.title, section.text) } }
                        }
                    }
                    Page.CABINET -> {
                        item { Title("Моя аптечка", "Сохранённые упаковки. Инструкции загружаются с сервера при открытии.") }
                        if (model.saved.isEmpty()) {
                            item { Text("Пока нет сохранённых лекарств", fontSize = 21.sp, fontWeight = FontWeight.SemiBold) }
                            item { Text("Добавьте упаковку. После подтверждения её можно сохранить здесь.") }
                            item { PrimaryAction("Добавить лекарство", model::beginNew) }
                        }
                        model.saved.forEach { item -> item(key = "${item.server}-${item.demo}-${item.product.id}") {
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(item.product.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                    Label(if (item.demo) "Демозапись" else "Сохранённая упаковка")
                                    SecondaryAction("Открыть упаковку", { model.openSaved(item) })
                                    TextButton(onClick = { deleteItem = item }) { Text("Удалить из аптечки") }
                                }
                            }
                        } }
                    }
                    Page.ERROR -> {
                        item { BackAction(model, "Вернуться") }
                        item { Title(model.errorTitle, model.error) }
                        model.product?.let { item { SelectedProduct(it) } }
                        item { ActionGroup {
                            if (model.canRetry) PrimaryAction("Повторить", model::retryRequest, Icons.Outlined.Refresh)
                            SecondaryAction("Ввести данные вручную", { model.navigate(Page.MANUAL) })
                            SecondaryAction("Выбрать другое фото", { gallery.launch("image/*") })
                            TextButton(onClick = { model.navigate(Page.SETTINGS) }) { Text("Настройки подключения") }
                        } }
                    }
                    Page.SETTINGS -> item { ConnectionSettings(model) }
                }
                item { Text("Помогает читать инструкцию. Не заменяет консультацию врача.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    deleteItem?.let { item -> AlertDialog(onDismissRequest = { deleteItem = null }, title = { Text("Удалить упаковку?") },
        text = { Text(item.product.label) }, confirmButton = { TextButton(onClick = { model.removeSaved(item); deleteItem = null }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Отмена") } }) }
}

@Composable private fun Title(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.semantics { heading() }, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
    }
}
@Composable private fun Label(text: String) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
@Composable private fun BackAction(model: MedicineViewModel, label: String = "На главную") {
    TextButton(onClick = model::back) { Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text(label) }
}
@Composable private fun ActionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable private fun PrimaryAction(text: String, onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, enabled: Boolean = true) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(16.dp)) {
        icon?.let { Icon(it, null); Spacer(Modifier.width(10.dp)) }; Text(text, fontSize = 17.sp)
    }
}
@Composable private fun SecondaryAction(text: String, onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(16.dp)) {
        Text(text, Modifier.weight(1f), fontSize = 17.sp); icon?.let { Spacer(Modifier.width(10.dp)); Icon(it, null) }
    }
}
@Composable private fun Field(label: String, text: String, onChange: (String) -> Unit) {
    OutlinedTextField(text, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 3)
}
@Composable private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) { Text(number.toString(), Modifier.padding(horizontal = 11.dp, vertical = 5.dp)) }
        Text(text, Modifier.padding(start = 12.dp), fontSize = 16.sp)
    }
}
@Composable private fun PackageIllustration() {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(24.dp).heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Medication, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Название и дозировка\nдолжны быть видны", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 16.sp)
        }
    }
}
@Composable private fun PhotoPreview(uri: Uri?) {
    if (uri == null) return
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options()
                while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1200) options.inSampleSize *= 2
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Surface(shape = RoundedCornerShape(16.dp)) { Image(it, "Ваше фото упаковки", Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit) } }
}
@Composable private fun ProductDetails(product: MedicineProduct) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            listOf("Дозировка" to product.strength, "Форма выпуска" to product.form, "Производитель" to product.manufacturer).forEach { (title, text) ->
                Column { Label(title); Text(text.ifBlank { "Не указан — сверьте с упаковкой" }, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}
@Composable private fun SelectedProduct(product: MedicineProduct) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircleOutline, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp)) { Text(product.name, fontWeight = FontWeight.SemiBold); Label("${product.strength} · ${product.form}") }
        }
    }
}
@Composable private fun QuestionComposer(model: MedicineViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(model.question, { if (it.length <= 2000) model.question = it },
                label = { Text("Ваш вопрос об инструкции") }, placeholder = { Text("Например, как хранить?") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 3, enabled = !model.busy)
            PrimaryAction("Отправить вопрос", { model.ask() }, Icons.Outlined.ArrowUpward, model.question.isNotBlank() && !model.busy)
        }
    }
}
@Composable private fun Exchange(entry: ChatEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(Modifier.align(Alignment.End).fillMaxWidth(.92f), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
            Text(entry.question, Modifier.padding(16.dp), fontSize = 18.sp)
        }
        val title = when (entry.response.status) {
            "ANSWERED" -> "По инструкции к этой упаковке"
            "CLARIFY" -> "Уточните вопрос"
            "REFER_TO_PHARMACIST" -> "Нужна консультация специалиста"
            else -> "Недостаточно сведений в инструкции"
        }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(entry.response.answer, fontSize = 18.sp, lineHeight = 27.sp)
        entry.response.quotes.forEach { EvidencePanel(it.section, it.text, it.sourceUrl) }
        HorizontalDivider()
    }
}
@Composable private fun EvidencePanel(title: String, text: String, sourceUrl: String = "") {
    var expanded by remember(text) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(title, Modifier.weight(1f), fontSize = 17.sp)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Свернуть" else "Развернуть")
            }
            if (expanded) { Text(text, Modifier.padding(8.dp), fontSize = 18.sp, lineHeight = 27.sp); SourceLink(sourceUrl) }
        }
    }
}
@Composable private fun SourceLink(url: String) {
    val context = LocalContext.current
    val source = runCatching { URI(url) }.getOrNull()
    var failed by remember { mutableStateOf(false) }
    if (source?.scheme == "https" && !source.host.isNullOrBlank() && source.userInfo == null) {
        TextButton(onClick = { failed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure }) {
            Text("Открыть источник инструкции"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.OpenInNew, null)
        }
    }
    if (failed) Text("Не удалось открыть браузер.", color = MaterialTheme.colorScheme.error)
}
@Composable private fun ConnectionSettings(model: MedicineViewModel) {
    var url by remember(model.server) { mutableStateOf(model.server) }
    var demo by remember(model.demo) { mutableStateOf(model.demo) }
    var invalid by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BackAction(model)
        Title("Подключение", "Настройки для разработки. Ключ модели хранится только на сервере.")
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(demo, { demo = it }); Text("Учебный деморежим") }
        Field("Адрес сервера", url) { url = it; invalid = false }
        Label("Эмулятор: http://10.0.2.2:8080. На телефоне нужен адрес компьютера в общей сети.")
        if (invalid) Text("Проверьте адрес: http:// или https:// и имя сервера.", color = MaterialTheme.colorScheme.error)
        PrimaryAction("Сохранить подключение", { runCatching { model.configure(url, demo) }.onFailure { invalid = true } })
        SecondaryAction("Проверить сохранённый сервер", model::checkConnection)
        if (model.connectionStatus.isNotBlank()) Text(model.connectionStatus)
    }
}
