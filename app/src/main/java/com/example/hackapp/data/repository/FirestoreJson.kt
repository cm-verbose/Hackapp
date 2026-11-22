package com.example.hackapp.data.repository

import com.google.firebase.Timestamp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Bridges kotlinx.serialization and Firestore so [com.example.hackapp.data.Event]
 * can be stored without adding no-arg constructors / Firestore annotations.
 */
internal object FirestoreJson {

    /** JsonElement → Firestore-compatible value (Map / List / String / Long / Double / Boolean / null). */
    fun toFirestore(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> element.mapValues { (_, v) -> toFirestore(v) }
        is JsonArray -> element.map { toFirestore(it) }
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            else -> element.doubleOrNull
        }
    }

    /** Firestore document value → JsonElement. */
    fun fromFirestore(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to fromFirestore(v) })
        is List<*> -> JsonArray(value.map { fromFirestore(it) })
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Timestamp -> JsonPrimitive(value.toDate().time)
        else -> JsonPrimitive(value.toString())
    }
}
