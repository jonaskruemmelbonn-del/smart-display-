package app.smartdisplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

enum class Sky { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val tMax: Double,
    val tMin: Double,
    val rainProb: Int,
)

data class WeatherData(
    val temp: Double,
    val feelsLike: Double,
    val code: Int,
    val wind: Double,
    val humidity: Int,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val daily: List<DayForecast>,
)

fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.CLEAR
    2, 3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    in 51..67, in 80..82 -> Sky.RAIN
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.CLOUDY
}

fun describe(code: Int): String = when (code) {
    0 -> "Klar"
    1 -> "Überwiegend klar"
    2 -> "Teils bewölkt"
    3 -> "Bedeckt"
    45, 48 -> "Nebel"
    51, 53, 55 -> "Nieselregen"
    56, 57 -> "Gefrierender Nieselregen"
    61 -> "Leichter Regen"
    63 -> "Regen"
    65 -> "Starkregen"
    66, 67 -> "Gefrierender Regen"
    71 -> "Leichter Schneefall"
    73 -> "Schneefall"
    75 -> "Starker Schneefall"
    77 -> "Schneegriesel"
    80, 81, 82 -> "Regenschauer"
    85, 86 -> "Schneeschauer"
    95 -> "Gewitter"
    96, 99 -> "Gewitter mit Hagel"
    else -> "Wechselhaft"
}

/** Wetter ueber Open-Meteo (kostenlos, ohne Schluessel). Standort: Bonn-Duisdorf. */
object WeatherRepo {
    private const val ENDPOINT =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=50.7367&longitude=7.0553" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            "&timezone=Europe%2FBerlin&forecast_days=6&wind_speed_unit=kmh"

    suspend fun load(): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parse(text)
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(text: String): WeatherData {
        val root = JSONObject(text)
        val cur = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val tMax = daily.getJSONArray("temperature_2m_max")
        val tMin = daily.getJSONArray("temperature_2m_min")
        val rain = daily.getJSONArray("precipitation_probability_max")
        val sunrise = daily.getJSONArray("sunrise").getString(0)
        val sunset = daily.getJSONArray("sunset").getString(0)
        val days = (0 until times.length()).map { i ->
            DayForecast(
                date = LocalDate.parse(times.getString(i)),
                code = codes.getInt(i),
                tMax = tMax.getDouble(i),
                tMin = tMin.getDouble(i),
                rainProb = if (rain.isNull(i)) 0 else rain.getInt(i),
            )
        }
        return WeatherData(
            temp = cur.getDouble("temperature_2m"),
            feelsLike = cur.getDouble("apparent_temperature"),
            code = cur.getInt("weather_code"),
            wind = cur.getDouble("wind_speed_10m"),
            humidity = cur.getInt("relative_humidity_2m"),
            sunrise = LocalTime.parse(sunrise.substringAfter('T')),
            sunset = LocalTime.parse(sunset.substringAfter('T')),
            daily = days,
        )
    }
}
