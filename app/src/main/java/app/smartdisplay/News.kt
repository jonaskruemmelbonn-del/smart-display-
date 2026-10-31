package app.smartdisplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

data class NewsItem(
    val title: String,
    val topline: String,
    val teaser: String,
    val time: OffsetDateTime?,
)

/** "vor 12 Min." usw. Leer, wenn keine Zeit bekannt ist. */
fun NewsItem.ago(): String {
    val t = time ?: return ""
    val minutes = Duration.between(t.toInstant(), Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "gerade eben"
        minutes < 60 -> "vor $minutes Min."
        minutes < 24 * 60 -> "vor ${minutes / 60} Std."
        minutes < 48 * 60 -> "gestern"
        else -> "vor ${minutes / (24 * 60)} Tagen"
    }
}

private fun JSONObject.str(name: String): String =
    if (isNull(name)) "" else optString(name, "").trim()

/** Schlagzeilen der Tagesschau (oeffentliche API, ohne Schluessel). */
object NewsRepo {
    private const val ENDPOINT = "https://www.tagesschau.de/api2u/homepage/"

    suspend fun load(): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "SmartDisplay/0.2")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parse(text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parse(text: String): List<NewsItem> {
        val array = JSONObject(text).optJSONArray("news") ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<NewsItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val title = o.str("title")
            if (title.isEmpty() || o.str("type") == "video") continue
            if (!seen.add(title)) continue
            val time = try {
                OffsetDateTime.parse(o.str("date"))
            } catch (e: Exception) {
                null
            }
            out += NewsItem(title, o.str("topline"), o.str("firstSentence"), time)
            if (out.size >= 12) break
        }
        return out
    }
}
