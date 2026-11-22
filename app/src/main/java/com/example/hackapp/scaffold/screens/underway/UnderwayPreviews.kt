package com.example.hackapp.scaffold.screens.underway

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.hackapp.ui.components.StripStop
import com.example.hackapp.ui.components.TransportChoice
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import com.example.hackapp.underway.StopOutcome
import java.time.LocalDate

private val Jazz = UnderwayStopUi("1", "Jazz sur le toit", StopType.Music, false, "18:30", "Le Plateau-Mont-Royal")
private val Bistro = UnderwayStopUi("2", "Bistro du coin", StopType.Meal, true, "19:30", "Le Plateau-Mont-Royal")
private val Wine = UnderwayStopUi("3", "Bar à vin", StopType.Bar, false, "21:00", "Le Plateau-Mont-Royal")
private val Strip = listOf(
    StripStop("18:30", StopType.Music),
    StripStop("19:30", StopType.Meal),
    StripStop("21:00", StopType.Bar),
    StripStop("23:30", StopType.Cinema),
)
private val Labels = TransportLabels("Chrono", "Uber", "1 passage", "ARTM")
private val FirstLeg = LegUi(Jazz, 1, 4, hero = null, leaveBy = null, strip = Strip, currentIndex = 0)
private val AtJazz = AtStopUi(Jazz, false, "19:15", "Bistro du coin", 10, lateStop = null)

private fun chrome(banner: Boolean = false) = UnderwayChrome(banner, {}, {}, {}, {})

@Composable
private fun Leg(ui: LegUi, banner: Boolean = false, error: String? = null) = HackappTheme {
    UnderwayLegPage("Samedi sur le Plateau", ui, TransportChoice.Transit, Labels, error, chrome(banner), {}, {}, {}, {}, {})
}

@Composable
private fun AtStop(ui: AtStopUi) = HackappTheme { UnderwayAtStopPage(ui, chrome(), {}, {}, {}, {}) }

@Preview(name = "Leg · first (30:751)", widthDp = 360, heightDp = 800)
@Composable
private fun LegFirstPreview() = Leg(FirstLeg)

@Preview(name = "Leg · Leave By (159:2539)", widthDp = 360, heightDp = 800)
@Composable
private fun LegLeaveByPreview() = Leg(FirstLeg.copy(leaveBy = "18:05"))

@Preview(name = "Leg · hero (38:1132)", widthDp = 360, heightDp = 800)
@Composable
private fun LegHeroPreview() = Leg(
    FirstLeg.copy(stop = Bistro, stopIndex = 2, hero = LegTimesUi(null, 8, "19:23"), currentIndex = 1),
)

@Preview(name = "Leg · hero, 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LegHeroLargeTextPreview() = Leg(
    FirstLeg.copy(stop = Bistro, stopIndex = 2, hero = LegTimesUi(null, 8, "19:23"), currentIndex = 1),
)

@Preview(name = "Leg · banner (160:2662)", widthDp = 360, heightDp = 800)
@Composable
private fun LegBannerPreview() = Leg(FirstLeg, banner = true)

@Preview(name = "Leg · banner, 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LegBannerLargeTextPreview() = Leg(FirstLeg, banner = true)

private const val HandoffFailed = "Impossible d’ouvrir Chrono. Essayez une autre option."

@Preview(name = "Leg · handoff failed, can confirm arrival", widthDp = 360, heightDp = 800)
@Composable
private fun LegHandoffErrorPreview() = Leg(FirstLeg, error = HandoffFailed)

@Preview(name = "Leg · hero, handoff failed, 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LegHeroHandoffErrorLargeTextPreview() = Leg(
    FirstLeg.copy(stop = Bistro, stopIndex = 2, hero = LegTimesUi(null, 8, "19:23"), currentIndex = 1),
    error = HandoffFailed,
)

@Preview(name = "Arrival (159:2624)", widthDp = 360, heightDp = 800)
@Composable
private fun ArrivalPreview() = HackappTheme {
    UnderwayArrivalPage(ArrivalUi(Jazz, "18:22"), chrome(), {}, {}, {})
}

@Preview(name = "At the Stop (30:852)", widthDp = 360, heightDp = 800)
@Composable
private fun AtStopPreview() = AtStop(AtJazz)

@Preview(name = "At the Stop · last (159:2674)", widthDp = 360, heightDp = 800)
@Composable
private fun AtLastStopPreview() = AtStop(AtStopUi(Wine, true, null, null, null, null))

@Preview(name = "At the Stop · late (159:2712)", widthDp = 360, heightDp = 800)
@Composable
private fun AtStopLatePreview() = AtStop(AtJazz.copy(lateStop = Bistro))

@Preview(name = "At the Stop · late, 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun AtStopLateLargeTextPreview() = AtStop(AtJazz.copy(lateStop = Bistro))

@Preview(name = "Skip dialog (160:2635)", widthDp = 360, heightDp = 400)
@Composable
private fun SkipDialogPreview() = HackappTheme { SkipStopDialog("Jazz sur le toit", {}, {}) }

@Preview(name = "End early dialog (160:2649)", widthDp = 360, heightDp = 400)
@Composable
private fun EndEarlyDialogPreview() = HackappTheme { EndEarlyDialog({}, {}) }

private val Day = EndOfDayUi(
    results = listOf(
        DayResultUi("1", "Jazz sur le toit", StopOutcome.Visited, DayStampUi.New),
        DayResultUi("2", "Bistro du coin", StopOutcome.Visited, DayStampUi.AlreadyInPassport),
        DayResultUi("3", "Bar à vin", StopOutcome.TooBrief, DayStampUi.None),
        DayResultUi("4", "Cinéma du Parc", StopOutcome.Skipped, DayStampUi.None),
    ),
    newStamps = listOf(NewStampUi("k", "Jazz sur le toit", LocalDate.of(2026, 11, 28), "Le Plateau")),
)

@Preview(name = "End of day (160:2756)", widthDp = 360, heightDp = 800)
@Composable
private fun EndOfDayPreview() = HackappTheme { UnderwayEndOfDayPage(Day, {}) }

@Preview(name = "End of day · 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun EndOfDayLargeTextPreview() = HackappTheme { UnderwayEndOfDayPage(Day, {}) }

@Preview(name = "End of day · Passport unavailable", widthDp = 360, heightDp = 800)
@Composable
private fun EndOfDayNoPassportPreview() = HackappTheme {
    UnderwayEndOfDayPage(
        EndOfDayUi(Day.results.map { it.copy(stamp = DayStampUi.Unavailable) }, emptyList()),
        {},
    )
}

@Preview(name = "Loading", widthDp = 360, heightDp = 400)
@Composable
private fun LoadingPreview() = HackappTheme { UnderwayLoadingPage({}) }

@Preview(name = "Error", widthDp = 360, heightDp = 400)
@Composable
private fun ErrorPreview() = HackappTheme { UnderwayErrorPage({}, {}) }
