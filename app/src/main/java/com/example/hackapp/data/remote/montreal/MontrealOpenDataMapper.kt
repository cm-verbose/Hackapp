package com.example.hackapp.data.remote.montreal

import com.example.hackapp.data.Cost
import com.example.hackapp.data.CostStatus
import com.example.hackapp.data.Event
import com.example.hackapp.data.Location
import com.example.hackapp.data.Schedule
import com.example.hackapp.data.TargetAudience

/** Montréal shares Toronto's zone; "America/Montreal" is a deprecated alias. */
private const val MONTREAL_TIMEZONE = "America/Toronto"

/** The dataset writes missing values as "nan" (pandas export). */
private fun String?.clean(): String? =
    this?.trim()?.takeUnless { it.isEmpty() || it.equals("nan", ignoreCase = true) }

fun MontrealEventRecordDto.toEvent(): Event {
    val url = urlFiche.clean()
    // url_fiche ends with a stable numeric id, e.g. ".../conseil-...-101082"
    val stableId = url?.substringAfterLast('/')?.substringAfterLast('-')?.takeIf { it.all(Char::isDigit) }

    val address = listOfNotNull(adressePrincipale.clean(), adresseSecondaire.clean())
        .joinToString(", ")
        .ifEmpty { null }

    val costText = cout.clean()
    val registrationText = inscription.clean()

    return Event(
        id = "donneesmtl:${stableId ?: rowId}",
        title = titre.clean() ?: "(Sans titre)",
        description = description.clean(),
        url = url,
        type = typeEvenement.clean() ?: "other",
        categories = listOfNotNull(typeEvenement.clean(), emplacement.clean()).ifEmpty { null },
        targetAudience = publicCible.clean()?.let { TargetAudience(description = it) },
        location = Location(
            venueName = titreAdresse.clean(),
            address = address,
            city = "Montréal",
            borough = arrondissement.clean(),
            postalCode = codePostal.clean(),
            latitude = lat.clean()?.toDoubleOrNull(),
            longitude = long.clean()?.toDoubleOrNull(),
        ),
        schedule = Schedule(
            startDate = dateDebut.clean() ?: "",
            endDate = dateFin.clean(),
            timezone = MONTREAL_TIMEZONE,
        ),
        cost = Cost(
            status = costText.toCostStatus(),
            currency = if (costText.toCostStatus() == CostStatus.PAID) "CAD" else null,
            originalText = costText,
            registrationRequired = registrationText.toRegistrationRequired(),
        ),
    )
}

private fun String?.toCostStatus(): CostStatus {
    val text = this?.lowercase() ?: return CostStatus.UNKNOWN
    return when {
        "gratuit" in text -> CostStatus.FREE
        "payant" in text || "$" in text -> CostStatus.PAID
        else -> CostStatus.UNKNOWN
    }
}

private fun String?.toRegistrationRequired(): Boolean? {
    val text = this?.lowercase() ?: return null
    return when {
        "sur inscription" in text || "billet requis" in text -> true
        "entrée libre" in text || "sans billet" in text || "sans inscription" in text -> false
        else -> null
    }
}
