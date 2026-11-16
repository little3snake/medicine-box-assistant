package com.medicineboxassistant

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class Page { HOME, MANUAL, CANDIDATES, CONFIRM, CHAT, LEAFLET, CABINET, ERROR, SETTINGS }
data class SavedMedicine(val product: MedicineProduct, val server: String, val demo: Boolean)

/** Owns requests across rotation; no API key or medical answer is persisted on the device. */
class MedicineViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("medicine-box", 0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var retry: (() -> Unit)? = null
    var page by mutableStateOf(Page.HOME); private set
    var fields by mutableStateOf(PackageFields()); private set
    var identification by mutableStateOf<Identification?>(null); private set
    var product by mutableStateOf<MedicineProduct?>(null); private set
    var confirmed by mutableStateOf(false); private set
    var entries by mutableStateOf(emptyList<ChatEntry>()); private set
    var leaflet by mutableStateOf<MedicineLeaflet?>(null); private set
    var photo by mutableStateOf<Uri?>(null); private set
    var cameraUri: Uri? = null
    var question by mutableStateOf("")
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(""); private set
    var error by mutableStateOf(""); private set
    var errorTitle by mutableStateOf(""); private set
    var returnPage by mutableStateOf(Page.HOME); private set
    var demo by mutableStateOf(preferences.getBoolean("demo", true)); private set
    var server by mutableStateOf(preferences.getString("server", "http://10.0.2.2:8080")!!); private set
    var large by mutableStateOf(preferences.getBoolean("large", false)); private set
    var saved by mutableStateOf(readSaved()); private set
    var connectionStatus by mutableStateOf(""); private set
    var canRetry by mutableStateOf(false); private set

    fun toggleText() { large = !large; preferences.edit().putBoolean("large", large).apply() }
    fun navigate(next: Page) { if (!busy) page = next }
    fun back() {
        if (busy) { job?.cancel(); busy = false; page = returnPage; return }
        page = when (page) {
            Page.LEAFLET -> Page.CHAT
            Page.ERROR -> returnPage
            Page.CHAT -> Page.HOME
            Page.CONFIRM -> if ((identification?.candidates?.size ?: 0) > 1) Page.CANDIDATES else Page.HOME
            else -> Page.HOME
        }
    }
    fun editFields(value: PackageFields) { fields = value; invalidateConfirmation() }
    private fun invalidateConfirmation() { confirmed = false; product = null; entries = emptyList(); leaflet = null; question = "" }
    fun beginNew() { invalidateConfirmation(); identification = null; fields = PackageFields(); photo = null; page = Page.HOME }
    fun reportPhotoFailure() { retry = null; canRetry = false; fail("Не удалось открыть фото", "Выберите другое фото или введите название вручную.", Page.HOME) }

    private fun perform(label: String, retryAction: () -> Unit, requiresNetwork: Boolean = !demo, action: suspend () -> Unit) {
        if (busy) return
        if (requiresNetwork && LocalServerAccess.missing(getApplication(), server)) {
            retry = retryAction; canRetry = true
            fail("Нужен доступ к локальному серверу", "В настройках подключения разрешите доступ к локальной сети, затем повторите запрос.", page)
            return
        }
        returnPage = page
        retry = retryAction
        canRetry = true
        busy = true; progress = label; error = ""
        job = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: Exception) {
                when (exception) {
                    is SocketTimeoutException -> fail("Ответ занимает больше времени", "Попробуйте ещё раз. Данные упаковки сохранены.", returnPage)
                    is ConnectException, is UnknownHostException -> fail("Не удалось подключиться", "Проверьте интернет и адрес сервера в настройках. Начинать сначала не нужно.", returnPage)
                    is ApiFailure -> fail(if (exception.code == 503) "Сервис пока недоступен" else "Не удалось выполнить запрос", exception.message ?: "Попробуйте позже.", returnPage)
                    else -> fail("Не удалось выполнить запрос", if (exception is IllegalArgumentException) exception.message ?: "Проверьте данные." else "Попробуйте снова или выберите другой способ поиска.", returnPage)
                }
            } finally { busy = false }
        }
    }
    private fun fail(title: String, text: String, previous: Page) { errorTitle = title; error = text; returnPage = previous; page = Page.ERROR }
    fun retryRequest() { if (!busy) { page = returnPage; retry?.invoke() } }

    fun identifyPhoto(uri: Uri) {
        if (busy) return
        invalidateConfirmation(); identification = null; photo = uri
        perform("Читаем название и дозировку с фото…", { identifyPhoto(uri) }) {
            val result = if (demo) demoIdentification() else {
                val context = getApplication<Application>()
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        var count = input.read(buffer)
                        while (count != -1) {
                            require(output.size() + count <= 10 * 1024 * 1024) { "Фото должно быть не больше 10 МБ." }
                            output.write(buffer, 0, count); count = input.read(buffer)
                        }; output.toByteArray()
                    } ?: error("Фото недоступно")
                }
                MedboxApi(server).identifyPhoto(bytes, context.contentResolver.getType(uri) ?: "image/jpeg")
            }
            acceptIdentification(result)
        }
    }
    fun searchFields() {
        if (fields.name.isBlank() || busy) return
        invalidateConfirmation()
        perform("Ищем варианты в каталоге…", { searchFields() }) {
            acceptIdentification(if (demo) demoIdentification(fields) else MedboxApi(server).identifyFields(fields))
        }
    }
    private suspend fun acceptIdentification(result: Identification) {
        identification = result; fields = result.fields
        if (photo != null && fields.name.isBlank() && result.candidates.isEmpty()) {
            fail("Название не удалось прочитать", "Сфотографируйте название и дозировку крупнее, при хорошем освещении. Или введите данные вручную.", Page.HOME)
            return
        }
        if (result.candidates.size == 1) loadProduct(result.candidates.first()) else page = Page.CANDIDATES
    }
    fun choose(candidate: ProductCandidate) {
        if (busy) return
        perform("Загружаем данные упаковки…", { choose(candidate) }) { loadProduct(candidate) }
    }
    private suspend fun loadProduct(candidate: ProductCandidate) {
        invalidateConfirmation()
        product = if (demo) demoProduct else MedboxApi(server).product(candidate.variantId)
        page = Page.CONFIRM
    }
    fun confirm() { if (!busy && product != null) { confirmed = true; page = Page.CHAT } }
    fun ask(text: String = question) {
        val current = product ?: return
        val trimmed = text.trim()
        if (!confirmed || trimmed.isBlank() || trimmed.length > 2000 || busy) return
        question = trimmed
        perform("Ищем ответ в инструкции…", { ask(trimmed) }) {
            val response = if (demo) MedicineAnswer("INSUFFICIENT", "Это учебный пример интерфейса. Медицинские ответы доступны в режиме API по настоящей инструкции.", emptyList())
                else MedboxApi(server).ask(current.id, trimmed)
            entries = entries + ChatEntry(trimmed, response); question = ""; page = Page.CHAT
        }
    }
    fun openLeaflet() {
        val current = product ?: return
        if (!confirmed) return
        perform("Загружаем инструкцию…", { openLeaflet() }) {
            leaflet = if (demo) MedicineLeaflet("", listOf(LeafletSection("demo", "Учебный пример", "Здесь появится текст официальной инструкции. Демо не содержит медицинских сведений.")))
                else { require(!current.sample) { "Это учебный образец. Официальная инструкция пока не добавлена." }; MedboxApi(server).leaflet(current.id) }
            page = Page.LEAFLET
        }
    }
    fun saveProduct() {
        val current = product ?: return
        if (!confirmed) return
        val item = SavedMedicine(current, server, demo)
        saved = listOf(item) + saved.filterNot { it.product.id == current.id && it.server == server && it.demo == demo }
        persistSaved()
    }
    fun isSaved() = saved.any { it.product.id == product?.id && it.server == server && it.demo == demo }
    fun removeSaved(item: SavedMedicine) { saved = saved - item; persistSaved() }
    fun openSaved(item: SavedMedicine) {
        if (busy) return
        // Never silently switch servers or promote demo records to real data.
        if (item.server != server || item.demo != demo) {
            retry = null; canRetry = false
            fail("Другая среда подключения", "Эта запись сохранена ${if (item.demo) "в деморежиме" else "на другом сервере"}. Выберите соответствующее подключение в настройках.", Page.CABINET); return
        }
        perform("Проверяем данные сохранённого препарата…", { openSaved(item) }) {
            invalidateConfirmation(); photo = null
            product = if (demo) item.product else MedboxApi(server).product(item.product.id)
            fields = PackageFields(product!!.name, product!!.strength, product!!.form, product!!.manufacturer)
            identification = null; page = Page.CONFIRM
        }
    }
    fun configure(url: String, useDemo: Boolean) {
        MedboxApi(url.trim())
        server = url.trim().trimEnd('/'); demo = useDemo
        preferences.edit().putString("server", server).putBoolean("demo", demo).apply()
        connectionStatus = ""; beginNew()
    }
    fun checkConnection() {
        connectionStatus = ""
        perform("Проверяем сервер…", { checkConnection() }, requiresNetwork = true) {
            connectionStatus = if (MedboxApi(server).health()) "Сервер отвечает. Доступность модели проверяется при запросе." else "Сервер вернул неизвестный статус."
        }
    }
    private fun persistSaved() {
        val data = JSONArray()
        saved.forEach { item -> data.put(JSONObject().put("id", item.product.id).put("name", item.product.name)
            .put("strength", item.product.strength).put("form", item.product.form).put("manufacturer", item.product.manufacturer)
            .put("sample", item.product.sample).put("server", item.server).put("demo", item.demo)) }
        preferences.edit().putString("saved", data.toString()).apply()
    }
    private fun readSaved(): List<SavedMedicine> = runCatching {
        val data = JSONArray(preferences.getString("saved", "[]"))
        (0 until data.length()).map { val item = data.getJSONObject(it)
            SavedMedicine(MedicineProduct(item.getString("id"), item.getString("name"), item.optString("strength"),
                item.optString("form"), item.optString("manufacturer"), item.optBoolean("sample")), item.getString("server"), item.getBoolean("demo")) }
    }.getOrDefault(emptyList())
    private val demoProduct = MedicineProduct("demo-package", "Пример препарата", "20 мг", "Таблетки", "Пример Фарм", true)
    private fun demoIdentification(value: PackageFields = PackageFields("Пример препарата", "20 мг", "Таблетки", "Пример Фарм")) =
        Identification(value, listOf(ProductCandidate(demoProduct.id, demoProduct.label)), true)
    override fun onCleared() { scope.cancel() }
}
