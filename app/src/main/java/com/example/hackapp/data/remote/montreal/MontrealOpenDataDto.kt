package com.example.hackapp.data.remote.montreal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** CKAN `datastore_search` response from donnees.montreal.ca. */
@Serializable
data class DatastoreResponseDto(
    val success: Boolean = false,
    val result: DatastoreResultDto? = null,
)

@Serializable
data class DatastoreResultDto(
    val total: Int = 0,
    val records: List<MontrealEventRecordDto> = emptyList(),
)

/**
 * One row of the "Événements publics" dataset. Every field is text;
 * missing values come through as the literal string "nan".
 */
@Serializable
data class MontrealEventRecordDto(
    @SerialName("_id") val rowId: Int,
    val titre: String? = null,
    @SerialName("url_fiche") val urlFiche: String? = null,
    val description: String? = null,
    @SerialName("date_debut") val dateDebut: String? = null,
    @SerialName("date_fin") val dateFin: String? = null,
    @SerialName("type_evenement") val typeEvenement: String? = null,
    @SerialName("public_cible") val publicCible: String? = null,
    val emplacement: String? = null,
    val inscription: String? = null,
    val cout: String? = null,
    val arrondissement: String? = null,
    @SerialName("titre_adresse") val titreAdresse: String? = null,
    @SerialName("adresse_principale") val adressePrincipale: String? = null,
    @SerialName("adresse_secondaire") val adresseSecondaire: String? = null,
    @SerialName("code_postal") val codePostal: String? = null,
    val lat: String? = null,
    val long: String? = null,
)
