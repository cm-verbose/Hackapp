// Usage in ItineraryReviewScreen and PlanningDistrictPickScreen — no city is named anywhere:
val map by rememberDistrictMap()          // = LocalCityPack.current.districtMapAsset (Montréal / Toronto / …)

map?.let { m ->
    ItineraryRouteMap(
        map = m,
        stops = itinerary.stops.mapIndexed { i, s ->
            RouteStop(order = i + 1, districtId = s.districtId, type = s.stopType, location = s.location)
        },
    )
}

// Switching city: appViewModel.switchCity(CityId.Toronto)
//   → LocalCityPack changes → rememberDistrictMap() loads citypacks/tor/districts.json
//   → DistrictMap and ItineraryRouteMap redraw with Toronto shapes, colours and division tints.
