package com.medicineboxassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

data class PackageFields(val name: String = "", val strength: String = "", val form: String = "", val manufacturer: String = "") {
    fun json() = JSONObject().put("name", name).put("strength", strength).put("form", form).put("manufacturer", manufacturer)
}
data class ProductCandidate(val variantId: String, val label: String)
data class Identification(val fields: PackageFields, val candidates: List<ProductCandidate>, val clarify: Boolean)
data class Evidence(val section: String, val text: String, val sourceUrl: String)
data class MedicineAnswer(val status: String, val answer: String, val quotes: List<Evidence>)
data class ChatEntry(val question: String, val response: MedicineAnswer)

/** HTTP contract from PLAN.md. Credentials remain on the server. */
class MedboxApi(private val baseUrl: String) {
    init {
        val uri = URI(baseUrl)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
            && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Укажите адрес сервера, например http://10.0.2.2:8080"
        }
    }

    suspend fun identifyFields(fields: PackageFields): Identification = withContext(Dispatchers.IO) {
        parseIdentification(request("/api/identify/fields", fields.json().toString().toByteArray(), "application/json"))
    }

    suspend fun identifyPhoto(bytes: ByteArray, mime: String): Identification = withContext(Dispatchers.IO) {
        require(mime in listOf("image/jpeg", "image/png", "image/webp")) { "Выберите JPEG, PNG или WebP" }
        require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024) { "Фото должно быть не больше 10 МБ" }
        val boundary = "medbox-${UUID.randomUUID()}"
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"package\"\r\nContent-Type: $mime\r\n\r\n"
        val body = header.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        parseIdentification(request("/api/identify", body, "multipart/form-data; boundary=$boundary"))
    }

    suspend fun ask(id: String, question: String): MedicineAnswer = withContext(Dispatchers.IO) {
        val body = JSONObject().put("question", question).put("lang", "ru").toString().toByteArray()
        val result = request("/api/products/${pathId(id)}/ask", body, "application/json")
        val status = result.getString("status")
        require(status in listOf("ANSWERED", "CLARIFY", "INSUFFICIENT", "REFER_TO_PHARMACIST")) { "Неизвестный статус ответа" }
        val quotes = result.optJSONArray("quotes") ?: JSONArray()
        MedicineAnswer(status, result.getString("answer"), (0 until quotes.length()).map {
            val quote = quotes.getJSONObject(it)
            Evidence(quote.getString("section"), quote.getString("text"), quote.getString("sourceUrl"))
        })
    }

    suspend fun card(id: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val lines = request("/api/products/${pathId(id)}/card?lang=ru").getJSONArray("lines")
        (0 until lines.length()).map {
            val line = lines.getJSONObject(it)
            line.getString("field") to line.getString("text")
        }
    }

    private fun pathId(id: String): String {
        require(id.matches(Regex("[a-zA-Z0-9_-]+"))) { "Некорректный ID препарата" }
        return id
    }

    private fun request(path: String, body: ByteArray? = null, contentType: String = "application/json"): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException(when (status) {
                    404 -> "Операция API или препарат пока отсутствует (404)"
                    409 -> "Для образца пока нет проверенной инструкции (409)"
                    429 -> "Лимит запросов исчерпан. Попробуйте позже (429)"
                    503 -> "Сервер или модель пока недоступны (503)"
                    else -> "Ошибка сервера ($status)"
                })
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }

    private fun parseIdentification(result: JSONObject): Identification {
        val fields = result.getJSONObject("extracted")
        fun value(key: String) = if (fields.isNull(key)) "" else fields.optString(key)
        val candidates = result.getJSONArray("candidates")
        return Identification(PackageFields(value("name"), value("strength"), value("form"), value("manufacturer")),
            (0 until candidates.length()).map {
                val candidate = candidates.getJSONObject(it)
                ProductCandidate(candidate.getString("variantId"), candidate.getString("label"))
            }, result.getBoolean("needsClarification"))
    }
}
