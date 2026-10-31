package app.smartdisplay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import android.os.SystemClock
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Page { CLOCK, WEATHER, NEWS, DAYS }

/** 0 = hell, 1 = dunkel. Zwischen 3 und 30 Lux wird weich ueberblendet. */
private fun dimFromLux(lux: Float): Float {
    val x = ((lux - 3f) / 27f).coerceIn(0f, 1f)
    return 1f - x * x * (3f - 2f * x)
}

private fun brightnessFromLux(lux: Float?, dim: Float): Float {
    if (lux == null) return 1f - 0.93f * dim
    val r = (ln(1f + lux) / ln(801f)).coerceIn(0f, 1f)
    return (0.02f + 0.98f * r.pow(1.5f)).coerceIn(0.02f, 1f)
}

@Composable
fun SmartDisplayApp(
    lux: Float?,
    fontFamily: FontFamily,
    onBrightness: (Float) -> Unit,
    cameraGranted: Boolean,
    resumeTick: Int,
    proximity: Flow<Boolean>,
) {
    val context = LocalContext.current

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L + 5L)
        }
    }

    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var weatherError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val w = WeatherRepo.load()
            if (w != null) {
                weather = w
                weatherError = false
            } else {
                weatherError = true
            }
            delay(if (w != null) 15 * 60_000L else 60_000L)
        }
    }

    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            val n = NewsRepo.load()
            if (n.isNotEmpty()) news = n
            delay(if (n.isNotEmpty()) 15 * 60_000L else 60_000L)
        }
    }

    // --- Anwesenheit, Winken und Standby ----------------------------------------------------
    var lastActivity by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var manualOff by remember { mutableStateOf(false) }
    val offState = remember { mutableStateOf(false) }

    // Bewegung vor der Frontkamera haelt das Display an. Bei Fehlern wird nach 30 s neu versucht.
    LaunchedEffect(cameraGranted, resumeTick) {
        if (!cameraGranted) return@LaunchedEffect
        while (true) {
            motionEvents(context).collect { lastActivity = SystemClock.elapsedRealtime() }
            delay(30_000)
        }
    }

    // Winken: Die Hand ist kurz (unter 1,5 s) am Naehe-Sensor und geht wieder weg.
    LaunchedEffect(Unit) {
        var nearSince = 0L
        var lastWave = 0L
        proximity.collect { near ->
            val t = SystemClock.elapsedRealtime()
            if (near) {
                nearSince = t
            } else if (nearSince != 0L) {
                val duration = t - nearSince
                nearSince = 0L
                if (duration < 1_500L && t - lastWave > 1_200L) {
                    lastWave = t
                    manualOff = !offState.value
                    lastActivity = t
                }
            }
        }
    }

    val date = now.toLocalDate()
    val season = Days.season(date)
    val occasion = Days.occasion(date)
    val sky = weather?.let { skyOf(it.code) } ?: Sky.CLEAR
    val isDay = weather?.let { now.toLocalTime().isAfter(it.sunrise) && now.toLocalTime().isBefore(it.sunset) }
        ?: (now.hour in 7..19)

    val dimTarget = when {
        lux != null -> dimFromLux(lux)
        now.hour >= 22 || now.hour < 6 -> 1f
        else -> 0f
    }
    val dim by animateFloatAsState(dimTarget, tween(4_000), label = "dim")
    val brightness by animateFloatAsState(brightnessFromLux(lux, dimTarget), tween(2_500), label = "brightness")

    val palette = buildPalette(season, occasion, sky, dim)
    val sleeping = dim > 0.85f
    val pages = if (sleeping) listOf(Page.CLOCK) else Page.entries.toList()

    // Ohne Bewegung geht das Display aus: tagsueber nach 5, nachts nach 2 Minuten.
    val elapsed = remember(now) { SystemClock.elapsedRealtime() }
    val idleLimit = if (sleeping) 2 * 60_000L else 5 * 60_000L
    val off = manualOff || (cameraGranted && elapsed - lastActivity > idleLimit)
    offState.value = off
    val offAlpha by animateFloatAsState(if (off) 1f else 0f, tween(900), label = "off")
    val effectiveBrightness = if (off && offAlpha > 0.97f) 0.01f else brightness
    LaunchedEffect(effectiveBrightness) { onBrightness(effectiveBrightness) }

    var pageIndex by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(sleeping, taps) {
        while (true) {
            delay(20_000)
            pageIndex++
        }
    }
    val page = pages[pageIndex.mod(pages.size)]
    val events = remember(date) { Days.upcoming(date, 6) }

    // Einbrennschutz: Der Inhalt wandert alle paar Minuten um wenige Pixel (AMOLED).
    val shiftStep = (now.hour * 60 + now.minute) / 3
    val shiftX by animateDpAsState(((shiftStep % 7) - 3).times(2).dp, tween(4_000), label = "shiftX")
    val shiftY by animateDpAsState((((shiftStep / 7) % 5) - 2).times(2).dp, tween(4_000), label = "shiftY")

    MaterialTheme(
        colorScheme = colorSchemeOf(palette),
        typography = remember(fontFamily) { typographyOf(fontFamily) },
    ) {
        CompositionLocalProvider(LocalContentColor provides palette.onBg) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            lastActivity = SystemClock.elapsedRealtime()
                            if (offState.value) {
                                manualOff = false
                            } else {
                                pageIndex++
                                taps++
                            }
                        }
                    }
            ) {
                AmbientBackground(palette, season, occasion, sky, isDay, dim)

                AnimatedContent(
                    targetState = page,
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                        .offset(shiftX, shiftY),
                    transitionSpec = {
                        (fadeIn(spring<Float>(1f, Spring.StiffnessLow)) +
                            scaleIn(spring<Float>(0.7f, Spring.StiffnessLow), initialScale = 0.9f) +
                            slideInVertically(spring<IntOffset>(0.8f, Spring.StiffnessLow)) { it / 14 }) togetherWith
                            (fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.06f) +
                                slideOutVertically(tween(250)) { -it / 20 })
                    },
                    label = "pages",
                ) { p ->
                    when (p) {
                        Page.CLOCK -> ClockPage(now, weather, Days.greeting(date), Days.todayHoliday(date))
                        Page.WEATHER -> WeatherPage(weather, weatherError, isDay, sky)
                        Page.NEWS -> NewsPage(news, (pageIndex / pages.size) * 2)
                        Page.DAYS -> DaysPage(date, events)
                    }
                }

                if (pages.size > 1) {
                    PageIndicator(
                        count = pages.size,
                        selected = pageIndex.mod(pages.size),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(bottom = 14.dp),
                    )
                }

                // Standby: komplett schwarz (auf dem AMOLED-Display ist das praktisch aus).
                if (offAlpha > 0.001f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = offAlpha }
                            .background(Color.Black)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bausteine
// ---------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
        content = content,
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    GlassCard(modifier, corner = 50.dp) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun PageIndicator(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val w by animateDpAsState(
                if (i == selected) 28.dp else 8.dp,
                spring<Dp>(0.6f, Spring.StiffnessMediumLow),
                label = "dotWidth",
            )
            val a by animateFloatAsState(if (i == selected) 0.95f else 0.35f, label = "dotAlpha")
            Box(
                Modifier
                    .height(8.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = a))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Seite 1: Uhr
// ---------------------------------------------------------------------------

@Composable
fun AnimatedDigit(ch: Char, style: androidx.compose.ui.text.TextStyle) {
    AnimatedContent(
        targetState = ch,
        modifier = Modifier.clip(RoundedCornerShape(0.dp)),
        transitionSpec = {
            (slideInVertically(spring<IntOffset>(0.65f, Spring.StiffnessMediumLow)) { it } +
                fadeIn(spring<Float>(1f, Spring.StiffnessMedium))) togetherWith
                (slideOutVertically(spring<IntOffset>(0.65f, Spring.StiffnessMediumLow)) { -it } +
                    fadeOut(spring<Float>(1f, Spring.StiffnessMedium)))
        },
        label = "digit",
    ) { c ->
        Text(c.toString(), style = style)
    }
}

@Composable
fun ClockPage(now: LocalDateTime, weather: WeatherData?, greeting: String?, holiday: String?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val fontSize = if (landscape) {
            min(maxHeight.value * 0.40f, maxWidth.value * 0.17f)
        } else {
            maxWidth.value * 0.28f
        }
        val style = MaterialTheme.typography.displayLarge.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.08f).sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val hh = now.hour.toString().padStart(2, '0')
        val mm = now.minute.toString().padStart(2, '0')
        val colonAlpha by animateFloatAsState(
            if (now.second % 2 == 0) 1f else 0.25f,
            tween(450),
            label = "colon",
        )

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (greeting != null) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedDigit(hh[0], style)
                AnimatedDigit(hh[1], style)
                Text(":", style = style, modifier = Modifier.graphicsLayer { alpha = colonAlpha })
                AnimatedDigit(mm[0], style)
                AnimatedDigit(mm[1], style)
            }
            Text(
                "${Days.weekday(now.toLocalDate())}, ${now.dayOfMonth}. ${Days.monthName(now.toLocalDate())}",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (weather != null) {
                    Pill("${weather.temp.roundToInt()}°  ${describe(weather.code)}")
                }
                if (holiday != null) Pill(holiday)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Seite 2: Wetter
// ---------------------------------------------------------------------------

@Composable
fun WeatherPage(weather: WeatherData?, error: Boolean, isDay: Boolean, sky: Sky) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (weather == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (error) "Keine Verbindung zum Wetterdienst" else "Wetter wird geladen",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val landscape = maxWidth > maxHeight
            val iconSize = if (landscape) maxHeight * 0.42f else maxWidth * 0.5f
            val tempSize = if (landscape) maxHeight.value * 0.28f else maxWidth.value * 0.26f

            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WeatherIcon(sky, isDay, Modifier.size(iconSize))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(
                            "${weather.temp.roundToInt()}°",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = tempSize.sp,
                                lineHeight = (tempSize * 1.05f).sp,
                                fontWeight = FontWeight.Light,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            describe(weather.code),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Bonn-Duisdorf",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Pill("Gefühlt ${weather.feelsLike.roundToInt()}°")
                    Pill("Wind ${weather.wind.roundToInt()} km/h")
                    Pill("Feuchte ${weather.humidity} %")
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    weather.daily.drop(1).take(5).forEachIndexed { i, d ->
                        ForecastCard(i, d, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(index: Int, d: DayForecast, modifier: Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 80L)
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, spring<Float>(0.8f, Spring.StiffnessLow), label = "forecast")
    GlassCard(
        modifier.graphicsLayer {
            alpha = a
            translationY = (1f - a) * 60f
        },
        corner = 28.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Days.weekdayShort(d.date),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
            WeatherIcon(skyOf(d.code), true, Modifier.size(44.dp))
            Text(
                "${d.tMax.roundToInt()}°",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${d.tMin.roundToInt()}°",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${d.rainProb} %",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Seite 3: Kommende Tage
// ---------------------------------------------------------------------------

@Composable
fun DaysPage(today: LocalDate, events: List<CalEvent>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(
                "Kommende Tage",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            if (landscape) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    events.take(6).chunked(3).forEachIndexed { col, part ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            part.forEachIndexed { i, e -> EventRow(col * 3 + i, e, today) }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    events.take(5).forEachIndexed { i, e -> EventRow(i, e, today) }
                }
            }
        }
    }
}

@Composable
private fun EventRow(index: Int, event: CalEvent, today: LocalDate) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 90L)
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, spring<Float>(0.8f, Spring.StiffnessLow), label = "eventIn")

    val color = when (event.kind) {
        EventKind.FEIERTAG -> MaterialTheme.colorScheme.primary
        EventKind.FERIEN -> MaterialTheme.colorScheme.secondary
        EventKind.BESONDERER_TAG -> MaterialTheme.colorScheme.tertiary
    }
    val days = ChronoUnit.DAYS.between(today, event.date)
    val relative = when (days) {
        0L -> "Heute"
        1L -> "Morgen"
        else -> "in $days Tagen"
    }

    GlassCard(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = a
                translationY = (1f - a) * 50f
            },
        corner = 28.dp,
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        event.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                    )
                    Text(
                        Days.monthShort(event.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "$relative  ·  ${Days.weekday(event.date)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Seite: Nachrichten (Tagesschau)
// ---------------------------------------------------------------------------

@Composable
private fun rememberAppear(index: Int): Float {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 90L)
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, spring<Float>(0.8f, Spring.StiffnessLow), label = "appear")
    return a
}

@Composable
fun NewsPage(news: List<NewsItem>, start: Int) {
    val items = remember(news, start) {
        if (news.isEmpty()) emptyList() else List(min(4, news.size)) { news[(start + it) % news.size] }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nachrichten werden geladen",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Nachrichten",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(14.dp))
                    Pill("Tagesschau")
                }
                Spacer(Modifier.height(14.dp))
                if (landscape) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        NewsFeature(0, items[0], Modifier.weight(1.25f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items.drop(1).forEachIndexed { i, item -> NewsRow(i + 1, item) }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NewsFeature(0, items[0], Modifier.fillMaxWidth())
                        items.drop(1).take(2).forEachIndexed { i, item -> NewsRow(i + 1, item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsFeature(index: Int, item: NewsItem, modifier: Modifier) {
    val a = rememberAppear(index)
    GlassCard(
        modifier.graphicsLayer {
            alpha = a
            translationY = (1f - a) * 50f
        },
        corner = 32.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            if (item.topline.isNotEmpty()) {
                Text(
                    item.topline,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.teaser.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    item.teaser,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val ago = item.ago()
            if (ago.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    ago,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun NewsRow(index: Int, item: NewsItem) {
    val a = rememberAppear(index)
    GlassCard(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = a
                translationY = (1f - a) * 50f
            },
        corner = 24.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val ago = item.ago()
                if (ago.isNotEmpty()) {
                    Text(
                        ago,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
