package app.smartdisplay

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class EventKind { FEIERTAG, FERIEN, BESONDERER_TAG }
enum class Season { WINTER, SPRING, SUMMER, AUTUMN }
enum class Occasion { NONE, HALLOWEEN, CHRISTMAS, NEWYEAR, CARNIVAL, EASTER, VALENTINE }

data class CalEvent(val date: LocalDate, val title: String, val kind: EventKind)

private data class FerienBlock(val name: String, val start: LocalDate, val end: LocalDate)

/**
 * Feiertage, Ferien und besondere Tage fuer Nordrhein-Westfalen (Bonn).
 * Die Schulferien sind fest hinterlegt (Quelle: kalenderpedia.de, Stand Oktober 2026)
 * und muessen ab 2028 ergaenzt werden.
 */
object Days {
    val LOCALE: Locale = Locale.GERMANY

    private fun d(y: Int, m: Int, day: Int): LocalDate = LocalDate.of(y, m, day)

    private val ferien = listOf(
        FerienBlock("Herbstferien", d(2026, 10, 17), d(2026, 10, 31)),
        FerienBlock("Weihnachtsferien", d(2026, 12, 23), d(2027, 1, 6)),
        FerienBlock("Osterferien", d(2027, 3, 22), d(2027, 4, 3)),
        FerienBlock("Pfingstferien", d(2027, 5, 18), d(2027, 5, 18)),
        FerienBlock("Sommerferien", d(2027, 7, 19), d(2027, 8, 31)),
        FerienBlock("Herbstferien", d(2027, 10, 23), d(2027, 11, 6)),
        FerienBlock("Weihnachtsferien", d(2027, 12, 24), d(2028, 1, 8)),
    )

    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val dd = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - dd - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    private fun eventsOfYear(year: Int): List<CalEvent> {
        val e = easter(year)
        val list = mutableListOf<CalEvent>()
        fun feiertag(date: LocalDate, t: String) { list.add(CalEvent(date, t, EventKind.FEIERTAG)) }
        fun besonders(date: LocalDate, t: String) { list.add(CalEvent(date, t, EventKind.BESONDERER_TAG)) }

        feiertag(d(year, 1, 1), "Neujahr")
        feiertag(e.minusDays(2), "Karfreitag")
        feiertag(e, "Ostersonntag")
        feiertag(e.plusDays(1), "Ostermontag")
        feiertag(d(year, 5, 1), "Tag der Arbeit")
        feiertag(e.plusDays(39), "Christi Himmelfahrt")
        feiertag(e.plusDays(49), "Pfingstsonntag")
        feiertag(e.plusDays(50), "Pfingstmontag")
        feiertag(e.plusDays(60), "Fronleichnam")
        feiertag(d(year, 10, 3), "Tag der Deutschen Einheit")
        feiertag(d(year, 11, 1), "Allerheiligen")
        feiertag(d(year, 12, 25), "1. Weihnachtstag")
        feiertag(d(year, 12, 26), "2. Weihnachtstag")

        besonders(d(year, 2, 14), "Valentinstag")
        besonders(e.minusDays(52), "Weiberfastnacht")
        besonders(e.minusDays(48), "Rosenmontag")
        besonders(e.minusDays(47), "Veilchendienstag")
        besonders(e.minusDays(46), "Aschermittwoch")
        besonders(d(year, 3, 20), "Frühlingsanfang")
        besonders(
            d(year, 3, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY)),
            "Beginn der Sommerzeit"
        )
        besonders(
            d(year, 5, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY)),
            "Muttertag"
        )
        besonders(d(year, 6, 21), "Sommeranfang")
        besonders(d(year, 9, 23), "Herbstanfang")
        besonders(d(year, 10, 31), "Halloween")
        besonders(
            d(year, 10, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY)),
            "Ende der Sommerzeit"
        )
        besonders(d(year, 11, 11), "Sankt Martin")
        besonders(d(year, 12, 6), "Nikolaus")
        besonders(
            d(year, 12, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).minusWeeks(3),
            "1. Advent"
        )
        besonders(d(year, 12, 21), "Winteranfang")
        besonders(d(year, 12, 24), "Heiligabend")
        besonders(d(year, 12, 31), "Silvester")
        return list
    }

    fun upcoming(from: LocalDate, limit: Int): List<CalEvent> {
        val all = mutableListOf<CalEvent>()
        for (y in from.year..from.year + 1) all += eventsOfYear(y)
        for (b in ferien) {
            if (b.start == b.end) {
                all += CalEvent(b.start, "${b.name} (ein Tag)", EventKind.FERIEN)
            } else {
                all += CalEvent(b.start, "Ferienbeginn: ${b.name}", EventKind.FERIEN)
                all += CalEvent(b.end, "Ferienende: ${b.name}", EventKind.FERIEN)
            }
        }
        return all.filter { !it.date.isBefore(from) }
            .sortedWith(compareBy({ it.date }, { it.kind.ordinal }))
            .take(limit)
    }

    fun season(date: LocalDate): Season = when (date.monthValue) {
        12, 1, 2 -> Season.WINTER
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        else -> Season.AUTUMN
    }

    fun occasion(date: LocalDate): Occasion {
        val e = easter(date.year)
        val md = date.monthValue * 100 + date.dayOfMonth
        return when {
            md in 1028..1031 -> Occasion.HALLOWEEN
            md >= 1230 || md <= 101 -> Occasion.NEWYEAR
            md in 1215..1226 -> Occasion.CHRISTMAS
            md in 213..214 -> Occasion.VALENTINE
            !date.isBefore(e.minusDays(52)) && !date.isAfter(e.minusDays(47)) -> Occasion.CARNIVAL
            !date.isBefore(e.minusDays(2)) && !date.isAfter(e.plusDays(1)) -> Occasion.EASTER
            else -> Occasion.NONE
        }
    }

    fun greeting(date: LocalDate): String? = when (occasion(date)) {
        Occasion.HALLOWEEN -> "Schaurig schönes Halloween"
        Occasion.CHRISTMAS -> if (date.dayOfMonth >= 24) "Frohe Weihnachten" else "Schöne Adventszeit"
        Occasion.NEWYEAR -> if (date.monthValue == 1) "Frohes neues Jahr" else "Guten Rutsch"
        Occasion.CARNIVAL -> "Bonn Alaaf"
        Occasion.EASTER -> "Frohe Ostern"
        Occasion.VALENTINE -> "Alles Liebe zum Valentinstag"
        Occasion.NONE -> null
    }

    /** Name eines Feiertags, falls heute einer ist. */
    fun todayHoliday(date: LocalDate): String? =
        eventsOfYear(date.year).firstOrNull { it.date == date && it.kind == EventKind.FEIERTAG }?.title

    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, LOCALE)
    fun weekdayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, LOCALE)
    fun monthName(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL, LOCALE)
    fun monthShort(date: LocalDate): String = date.month.getDisplayName(TextStyle.SHORT, LOCALE)
}
