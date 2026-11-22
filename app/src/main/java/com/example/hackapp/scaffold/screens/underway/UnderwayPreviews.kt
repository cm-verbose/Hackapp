package com.example.hackapp.scaffold.screens.underway

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.hackapp.R
import com.example.hackapp.citypack.MontrealCityPack
import com.example.hackapp.ui.components.DemoControl
import com.example.hackapp.ui.components.StripStop
import com.example.hackapp.ui.components.TransportChoice
import com.example.hackapp.ui.theme.HackappTheme
import com.example.hackapp.ui.theme.StopType
import com.example.hackapp.underway.StopOutcome
import java.time.LocalDate

private val Jazz = UnderwayStopUi("1", "Jazz sur le toit", StopType.Music, false, "18:30", "Le Plateau-Mont-Royal")
private val Bistro = UnderwayStopUi("2", "Bistro du coin", StopType.Meal, true, "19:30", "Le Plateau-Mont-Royal")
private val Wine = UnderwayStopUi("3", "Bar à vin", StopType.Bar, false, "21:00", "Le Plateau-Mont-Royal")

/** The large-text previews use long names, where wrapping and clipping would show. */
private val LongStop = UnderwayStopUi(
    "5",
    "Théâtre du Nouveau Monde, grande salle",
    StopType.Theatre,
    false,
    "19:30",
    "Rosemont–La Petite-Patrie",
)
private val Strip = listOf(
    StripStop("18:30", StopType.Music),
    StripStop("19:30", StopType.Meal),
    StripStop("21:00", StopType.Bar),
    StripStop("23:30", StopType.Cinema),
)
private val FirstLeg = LegUi(Jazz, 1, 4, hero = null, leaveBy = null, strip = Strip, currentIndex = 0)
private val HeroLeg = FirstLeg.copy(stop = Bistro, stopIndex = 2, hero = LegTimesUi(null, 8, "19:23"), currentIndex = 1)
private val AtJazz = AtStopUi(Jazz, false, "19:15", "Bistro du coin", 10, lateStop = null)

private fun chrome(banner: Boolean = false) = UnderwayChrome(banner, {}, {}, {}, {})

/** Fare in the preview's language, from the City Pack. */
@Composable
private fun labels(): TransportLabels {
    val fare = MontrealCityPack.fareTable
    val language = LocalConfiguration.current.locales[0].language
    return TransportLabels("Chrono", "Uber", fare.singleRide.get(language).orEmpty(), fare.agencyName)
}

@Composable
private fun Leg(ui: LegUi, banner: Boolean = false, handoffFailed: Boolean = false) = HackappTheme {
    val error = if (handoffFailed) stringResource(R.string.handoff_unavailable, "Chrono") else null
    UnderwayLegPage("Samedi sur le Plateau", ui, TransportChoice.Transit, labels(), error, chrome(banner), {}, {}, {}, {}, {})
}

@Composable
private fun AtStop(ui: AtStopUi) = HackappTheme { UnderwayAtStopPage(ui, chrome(), {}, {}, {}, {}) }

@Preview(name = "Leg · first (30:751)", widthDp = 360, heightDp = 800)
@Composable
private fun LegFirstPreview() = Leg(FirstLeg)

@Preview(name = "Leg · Leave By (159:2539)", widthDp = 360, heightDp = 800)
@Composable
private fun LegLeaveByPreview() = Leg(FirstLeg.copy(leaveBy = "18:05"))

@Preview(name = "Leg · Leave By passed", widthDp = 360, heightDp = 800)
@Composable
private fun LegLeaveNowPreview() = Leg(FirstLeg.copy(leaveBy = "18:05", leaveNow = true))

@Preview(name = "Leg · hero (38:1132)", widthDp = 360, heightDp = 800)
@Composable
private fun LegHeroPreview() = Leg(HeroLeg)

@Preview(name = "Leg · English", locale = "en", widthDp = 360, heightDp = 800)
@Composable
private fun LegEnglishPreview() = Leg(HeroLeg)

@Preview(name = "Leg · banner (160:2662)", widthDp = 360, heightDp = 800)
@Composable
private fun LegBannerPreview() = Leg(FirstLeg, banner = true)

@Preview(name = "Leg · banner, 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LegBannerLargeTextPreview() = Leg(FirstLeg.copy(stop = LongStop), banner = true)

@Preview(name = "Leg · handoff failed, can confirm arrival", widthDp = 360, heightDp = 800)
@Composable
private fun LegHandoffErrorPreview() = Leg(FirstLeg, handoffFailed = true)

@Preview(name = "Leg · hero, handoff failed, 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LegHeroHandoffErrorLargeTextPreview() = Leg(HeroLeg.copy(stop = LongStop), handoffFailed = true)

@Preview(name = "Arrival (159:2624)", widthDp = 360, heightDp = 800)
@Composable
private fun ArrivalPreview() = HackappTheme {
    UnderwayArrivalPage(ArrivalUi(Jazz, "18:22"), chrome(), {}, {}, {})
}

@Preview(name = "Arrival · no expected time", widthDp = 360, heightDp = 800)
@Composable
private fun ArrivalNoEstimatePreview() = HackappTheme {
    UnderwayArrivalPage(ArrivalUi(Jazz, null), chrome(), {}, {}, {})
}

@Preview(name = "At the Stop (30:852)", widthDp = 360, heightDp = 800)
@Composable
private fun AtStopPreview() = AtStop(AtJazz)

@Preview(name = "At the Stop · Leave By passed", widthDp = 360, heightDp = 800)
@Composable
private fun AtStopLeaveNowPreview() = AtStop(AtJazz.copy(leaveNow = true))

@Preview(name = "At the Stop · last (159:2674)", widthDp = 360, heightDp = 800)
@Composable
private fun AtLastStopPreview() = AtStop(AtStopUi(Wine, true, null, null, null, null))

@Preview(name = "At the Stop · late (159:2712)", widthDp = 360, heightDp = 800)
@Composable
private fun AtStopLatePreview() = AtStop(AtJazz.copy(lateStop = Bistro, leaveNow = true))

@Preview(name = "At the Stop · late, Leave By passed, 360 dp at 1.3x", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun AtStopLateLargeTextPreview() = AtStop(
    AtJazz.copy(nextStopName = LongStop.name, lateStop = LongStop, leaveNow = true),
)

@Preview(name = "Skip dialog (160:2635)", widthDp = 360, heightDp = 400)
@Composable
private fun SkipDialogPreview() = HackappTheme { SkipStopDialog("Jazz sur le toit", {}, {}) }

@Preview(name = "Skip dialog · last Stop", widthDp = 360, heightDp = 400)
@Composable
private fun SkipLastDialogPreview() = HackappTheme { SkipStopDialog("Bar à vin", {}, {}, endsItinerary = true) }

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
private fun EndOfDayLargeTextPreview() = HackappTheme {
    UnderwayEndOfDayPage(
        Day.copy(results = Day.results.map { it.copy(name = LongStop.name) }),
        {},
    )
}

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

@Preview(name = "Error · back to Home", widthDp = 360, heightDp = 400)
@Composable
private fun ExitErrorPreview() = HackappTheme { UnderwayExitErrorPage({}) }

private const val DemoTime = "sam. 28 nov. · 18:42"

private fun demoChrome(canAdvance: Boolean = true, failed: Boolean = false) = UnderwayChrome(
    showNotificationBanner = false,
    onBack = {},
    onEndItinerary = {},
    onOpenNotificationSettings = {},
    onDismissBanner = {},
    demoControl = { DemoControl(DemoTime, canAdvance, {}, failed = failed) },
    onExitDemo = {},
)

@Composable
private fun DemoLeg(ui: LegUi, dark: Boolean = false) = HackappTheme(darkTheme = dark) {
    UnderwayLegPage("Samedi sur le Plateau", ui, TransportChoice.Transit, labels(), null, demoChrome(), {}, {}, {}, {}, {})
}

@Preview(name = "Demo · Leg (186:2902)", widthDp = 412, heightDp = 917)
@Composable
private fun DemoLegPreview() = DemoLeg(FirstLeg)

@Preview(name = "Demo · Leg, dark", widthDp = 412, heightDp = 917)
@Composable
private fun DemoLegDarkPreview() = DemoLeg(FirstLeg, dark = true)

@Preview(name = "Demo · Leg, 360 dp at 1.3x (186:3098)", widthDp = 360, heightDp = 917, fontScale = 1.3f)
@Composable
private fun DemoLegLargeTextPreview() = DemoLeg(FirstLeg)

@Preview(name = "Demo · hero Leg", widthDp = 412, heightDp = 917)
@Composable
private fun DemoHeroLegPreview() = DemoLeg(HeroLeg)

@Preview(name = "Demo · At the Stop (186:2941)", widthDp = 412, heightDp = 917)
@Composable
private fun DemoAtStopPreview() = HackappTheme { UnderwayAtStopPage(AtJazz, demoChrome(), {}, {}, {}, {}) }

@Preview(name = "Demo · exit failed", widthDp = 412, heightDp = 917)
@Composable
private fun DemoExitFailedPreview() = HackappTheme {
    UnderwayAtStopPage(AtJazz, demoChrome(failed = true), {}, {}, {}, {})
}

@Preview(name = "Demo · exit dialog", widthDp = 360, heightDp = 400)
@Composable
private fun DemoExitDialogPreview() = HackappTheme { ExitDemoDialog({}, {}) }
