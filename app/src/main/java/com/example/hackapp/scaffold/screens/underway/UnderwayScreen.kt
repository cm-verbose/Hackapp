package com.example.hackapp.scaffold.screens.underway

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hackapp.HackappApplication
import com.example.hackapp.R
import com.example.hackapp.auth.LocalAuthService
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.data.model.LatLng
import com.example.hackapp.itinerary.Itinerary
import com.example.hackapp.itinerary.LocalItineraryRepository
import com.example.hackapp.itinerary.Stop
import com.example.hackapp.scaffold.screens.itinerary.itineraryTitle
import com.example.hackapp.scaffold.screens.itinerary.loadDistrictNames
import com.example.hackapp.scaffold.screens.itinerary.stopTypesFor
import com.example.hackapp.scaffold.screens.onboarding.LoadState
import com.example.hackapp.services.CityServices
import com.example.hackapp.services.HandoffDestination
import com.example.hackapp.services.HandoffResult
import com.example.hackapp.services.LocalCityServices
import com.example.hackapp.ui.components.TransportChoice
import com.example.hackapp.underway.EndOfDay
import com.example.hackapp.underway.Rejection
import com.example.hackapp.underway.UnderwayEvent
import com.example.hackapp.underway.UnderwayPhase
import com.example.hackapp.underway.UnderwayView
import kotlinx.coroutines.CancellationException

private class UnderwayItineraryInfo(val itinerary: Itinerary, val stops: UnderwayStopsInfo) {
    val districtName: String? =
        (itinerary.districtIds.firstOrNull() ?: itinerary.stops.firstOrNull()?.districtId)
            ?.let(stops.districtNames::get)
}

/**
 * Connected Underway flow. One route and one ViewModel: the page follows the engine's phase, so a
 * notification action taken outside the app can never leave a stale page on screen.
 */
@Composable
fun UnderwayScreen(onExitToHome: () -> Unit) {
    BackHandler(onBack = onExitToHome)
    val uid = LocalAuthService.current.currentUser?.uid
    if (uid == null) {
        UnderwayErrorPage(onRetry = onExitToHome, onBack = onExitToHome)
        return
    }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val cityPack = LocalCityPack.current
    val clock = (appContext as HackappApplication).underwayClock
    val viewModel = UnderwayViewModel.create(
        uid = uid,
        coordinator = (appContext as HackappApplication).underwayCoordinator,
        clock = clock,
        notificationsAllowed = { NotificationManagerCompat.from(appContext).areNotificationsEnabled() },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nothingUnderway by viewModel.nothingUnderway.collectAsStateWithLifecycle()
    LaunchedEffect(nothingUnderway) { if (nothingUnderway) onExitToHome() }
    val endOfDay by viewModel.endOfDay.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
        // The Passport may have been unavailable: Stamps are awarded on a later try.
        if ((endOfDay as? LoadState.Ready)?.value?.stampsAvailable == false) viewModel.retry()
    }

    val data = (state as? LoadState.Ready)?.value
    val itineraryId = data?.view?.itineraryId
    var infoAttempt by remember { mutableIntStateOf(0) }
    val itineraries = LocalItineraryRepository.current
    val services = LocalCityServices.current
    val info by produceState<LoadState<UnderwayItineraryInfo>>(LoadState.Loading, uid, itineraryId, infoAttempt) {
        if (itineraryId == null) return@produceState
        value = LoadState.Loading
        value = try {
            val itinerary = itineraries.getItinerary(uid, itineraryId)
            if (itinerary == null) {
                LoadState.Failed
            } else {
                val stops = UnderwayStopsInfo(
                    stops = itinerary.stops,
                    stopTypes = services.events.stopTypesFor(itinerary.stops),
                    districtNames = loadDistrictNames(appContext, cityPack),
                    zone = cityPack.timeZone,
                )
                LoadState.Ready(UnderwayItineraryInfo(itinerary, stops))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LoadState.Failed
        }
    }

    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var skipStopId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.rejections.collect { if (it == Rejection.NeedsConfirmation) confirmEnd = true }
    }

    val ended = data?.view?.phase == UnderwayPhase.Ended || endOfDay !is LoadState.Loading
    when {
        ended -> EndOfDayContent(
            endOfDay = endOfDay,
            districtNames = (info as? LoadState.Ready)?.value?.stops?.districtNames.orEmpty(),
            onRetry = viewModel::retry,
            onDone = onExitToHome,
        )
        state is LoadState.Failed -> UnderwayErrorPage(onRetry = viewModel::onResume, onBack = onExitToHome)
        data == null -> UnderwayLoadingPage(onBack = onExitToHome)
        info is LoadState.Failed -> UnderwayErrorPage(onRetry = { infoAttempt++ }, onBack = onExitToHome)
        else -> {
            val loaded = (info as? LoadState.Ready)?.value
            if (loaded == null) {
                UnderwayLoadingPage(onBack = onExitToHome)
            } else {
                val view = data.view
                val chrome = UnderwayChrome(
                    showNotificationBanner = view.showNotificationBanner,
                    onBack = {
                        if (view.phase == UnderwayPhase.Arrival) {
                            viewModel.dispatch(UnderwayEvent.ReturnToLeg)
                        } else {
                            onExitToHome()
                        }
                    },
                    onEndItinerary = { viewModel.dispatch(UnderwayEvent.EndItinerary(confirmed = false)) },
                    onOpenNotificationSettings = {
                        viewModel.dispatch(UnderwayEvent.NotificationBannerHandled)
                        context.openNotificationSettings()
                    },
                    onDismissBanner = { viewModel.dispatch(UnderwayEvent.NotificationBannerHandled) },
                )
                ActivePhase(
                    view = view,
                    now = data.now,
                    info = loaded,
                    services = services,
                    chrome = chrome,
                    dispatch = viewModel::dispatch,
                    onAskSkip = { skipStopId = it },
                    onExitToHome = onExitToHome,
                )
                skipStopId?.let { id ->
                    val stop = loaded.itinerary.stops.firstOrNull { it.id == id }
                    if (stop == null) {
                        skipStopId = null
                    } else {
                        SkipStopDialog(
                            stopName = stop.name,
                            onConfirm = {
                                skipStopId = null
                                viewModel.dispatch(UnderwayEvent.SkipStop(id))
                            },
                            onDismiss = { skipStopId = null },
                        )
                    }
                }
                if (confirmEnd) {
                    EndEarlyDialog(
                        onConfirm = {
                            confirmEnd = false
                            viewModel.dispatch(UnderwayEvent.EndItinerary(confirmed = true))
                        },
                        onDismiss = { confirmEnd = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePhase(
    view: UnderwayView,
    now: java.time.Instant,
    info: UnderwayItineraryInfo,
    services: CityServices,
    chrome: UnderwayChrome,
    dispatch: (UnderwayEvent) -> Unit,
    onAskSkip: (String) -> Unit,
    onExitToHome: () -> Unit,
) {
    val stopId = view.currentStop?.id
    when (view.phase) {
        UnderwayPhase.Leg -> LegContent(view, now, info, services, chrome, dispatch, onAskSkip, onExitToHome)
        UnderwayPhase.Arrival -> {
            val ui = arrivalUi(view, info.stops)
            if (ui == null) {
                UnderwayErrorPage(onRetry = onExitToHome, onBack = onExitToHome)
            } else {
                UnderwayArrivalPage(
                    ui = ui,
                    chrome = chrome,
                    onArrived = { dispatch(UnderwayEvent.ConfirmArrival(stopId)) },
                    onChangeTransport = { dispatch(UnderwayEvent.ReturnToLeg) },
                    onSkip = { stopId?.let(onAskSkip) },
                )
            }
        }
        UnderwayPhase.AtStop -> {
            val ui = atStopUi(view, info.stops, services.legs)
            if (ui == null) {
                UnderwayErrorPage(onRetry = onExitToHome, onBack = onExitToHome)
            } else {
                UnderwayAtStopPage(
                    ui = ui,
                    chrome = chrome,
                    onLeaving = { dispatch(UnderwayEvent.Leaving(stopId)) },
                    onFinish = { dispatch(UnderwayEvent.EndItinerary(confirmed = false)) },
                    onGoAnyway = { dispatch(UnderwayEvent.GoAnyway(it)) },
                    onSkipNext = { onAskSkip(it.id) },
                )
            }
        }
        UnderwayPhase.Ended -> UnderwayLoadingPage(onBack = onExitToHome)
    }
}

@Composable
private fun LegContent(
    view: UnderwayView,
    now: java.time.Instant,
    info: UnderwayItineraryInfo,
    services: CityServices,
    chrome: UnderwayChrome,
    dispatch: (UnderwayEvent) -> Unit,
    onAskSkip: (String) -> Unit,
    onExitToHome: () -> Unit,
) {
    val stop = view.currentStop
    if (stop == null) {
        UnderwayErrorPage(onRetry = onExitToHome, onBack = onExitToHome)
        return
    }
    var transport by rememberSaveable(stop.id, view.transport) { mutableStateOf(view.transport.toChoice()) }
    var failedApp by rememberSaveable(stop.id) { mutableStateOf<String?>(null) }
    val ui = legUi(view, info.stops, now, services.legs, transport)
    if (ui == null) {
        UnderwayErrorPage(onRetry = onExitToHome, onBack = onExitToHome)
        return
    }
    val language = LocalConfiguration.current.locales[0].language
    val directionsAppName = stringResource(R.string.app_name_google_maps)
    val fare = services.fares.fareForLeg()
    val labels = TransportLabels(
        transitAppName = services.transit.appName,
        rideHailName = services.rideHail.appName,
        fareLabel = fare.label.get(language).orEmpty(),
        fareAgency = fare.agencyName,
    )
    val destination = stop.handoffDestination()
    fun finishHandoff(result: HandoffResult, appName: String, choice: TransportChoice) {
        if (result == HandoffResult.UNAVAILABLE) {
            failedApp = appName
        } else {
            failedApp = null
            dispatch(UnderwayEvent.OpenTransport(choice.toEngine()))
        }
    }
    UnderwayLegPage(
        title = itineraryTitle(info.itinerary.date, info.districtName),
        ui = ui,
        transport = transport,
        labels = labels,
        handoffError = failedApp?.let { stringResource(R.string.handoff_unavailable, it) },
        chrome = chrome,
        onTransportChange = {
            transport = it
            failedApp = null
        },
        onOpenTransport = {
            when (transport) {
                TransportChoice.Transit -> finishHandoff(
                    services.transit.openTransitApp(destination),
                    labels.transitAppName,
                    TransportChoice.Transit,
                )
                TransportChoice.RideHail -> finishHandoff(
                    services.rideHail.requestRide(destination),
                    labels.rideHailName,
                    TransportChoice.RideHail,
                )
            }
        },
        onOpenDirections = {
            finishHandoff(services.transit.openDirections(destination), directionsAppName, TransportChoice.Transit)
        },
        onSkip = { onAskSkip(stop.id) },
    )
}

@Composable
private fun EndOfDayContent(
    endOfDay: LoadState<EndOfDay>,
    districtNames: Map<String, String>,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    when (endOfDay) {
        LoadState.Loading -> UnderwayLoadingPage(onBack = onDone)
        LoadState.Failed -> UnderwayErrorPage(onRetry = onRetry, onBack = onDone)
        is LoadState.Ready -> UnderwayEndOfDayPage(endOfDayUi(endOfDay.value, districtNames), onDone)
    }
}

private fun Stop.handoffDestination() = HandoffDestination(name, LatLng(latitude, longitude))
