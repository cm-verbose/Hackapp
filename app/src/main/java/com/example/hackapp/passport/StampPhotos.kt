package com.example.hackapp.passport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/** Saved photos are square, this many pixels a side. */
private const val PHOTO_SIDE = 512

/** A shared photo must fit one Nearby message with the rest of the Stamp. */
private const val SHARED_PHOTO_MAX_CHARS = 24_000

/**
 * The person's own photo behind each Stamp, kept on this device only. A photo is a background, so
 * it never changes the Stamp itself; Stamps stay in [StampRepository].
 */
class StampPhotoStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "stamp_photos")

    /** Bumped on every change, so screens reload the photos they show. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun fileFor(uid: String, key: String): File = File(dir, sha256("$uid|$key") + ".jpg")

    suspend fun load(uid: String, key: String): Bitmap? = withContext(Dispatchers.IO) {
        fileFor(uid, key).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    /** Saves a picture from the gallery. False when it cannot be read. */
    suspend fun setFromUri(uid: String, key: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val decoded = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > PHOTO_SIDE * 2) {
                    val factor = PHOTO_SIDE * 2f / longest
                    decoder.setTargetSize((info.size.width * factor).toInt(), (info.size.height * factor).toInt())
                }
            }
        }.getOrNull() ?: return@withContext false
        save(uid, key, decoded)
    }

    /** Saves a picture from the camera. */
    suspend fun setFromBitmap(uid: String, key: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        save(uid, key, bitmap)
    }

    /** Saves a photo received with a shared Stamp. */
    suspend fun setFromShared(uid: String, key: String, base64: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }.getOrNull() ?: return@withContext false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext false
        save(uid, key, bitmap)
    }

    suspend fun remove(uid: String, key: String) = withContext(Dispatchers.IO) {
        fileFor(uid, key).delete()
        _version.update { it + 1 }
    }

    /** A small copy for sharing, or null without a photo. */
    suspend fun sharedCopy(uid: String, key: String): String? {
        val photo = load(uid, key) ?: return null
        return withContext(Dispatchers.Default) {
            var side = 240
            var quality = 70
            while (side >= 96) {
                val scaled = Bitmap.createScaledBitmap(photo, side, side, true)
                val text = Base64.encodeToString(scaled.jpeg(quality), Base64.NO_WRAP)
                if (text.length <= SHARED_PHOTO_MAX_CHARS) return@withContext text
                if (quality > 45) quality -= 15 else side -= 48
            }
            null
        }
    }

    private fun save(uid: String, key: String, source: Bitmap): Boolean {
        val square = source.centerSquare(PHOTO_SIDE)
        dir.mkdirs()
        val target = fileFor(uid, key)
        val temp = File(dir, target.name + ".tmp")
        val ok = runCatching { temp.writeBytes(square.jpeg(85)); temp.renameTo(target) }.getOrDefault(false)
        if (ok) _version.update { it + 1 }
        return ok
    }
}

private fun Bitmap.centerSquare(side: Int): Bitmap {
    val edge = minOf(width, height)
    val cropped = Bitmap.createBitmap(this, (width - edge) / 2, (height - edge) / 2, edge, edge)
    return if (edge == side) cropped else Bitmap.createScaledBitmap(cropped, side, side, true)
}

private fun Bitmap.jpeg(quality: Int): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * A Stamp a friend sent. It is a keepsake, not a Stamp: it never counts toward the Passport, which
 * only holds places the person went to. [photo] is the sender's background, in Base64 JPEG.
 */
@Serializable
data class StampKeepsake(
    val id: String,
    val name: String,
    val kind: StampKind,
    val category: String? = null,
    val cityId: String,
    val districtId: String,
    val date: String,
    val shapeSeed: Int? = null,
    val fromName: String? = null,
    val receivedAtMillis: Long,
    val photo: String? = null,
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

/** What a thrown Stamp carries. */
@Serializable
data class SharedStamp(
    val version: Int = 1,
    val name: String,
    val kind: StampKind,
    val category: String? = null,
    val cityId: String,
    val districtId: String,
    val date: String,
    val shapeSeed: Int? = null,
    val photo: String? = null,
)

private val KeepsakeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun PassportStamp.toSharedPayload(photo: String?): String =
    KeepsakeJson.encodeToString(
        SharedStamp.serializer(),
        SharedStamp(
            name = name, kind = kind, category = category, cityId = cityId, districtId = districtId,
            date = date.toString(), shapeSeed = shapeSeed, photo = photo,
        ),
    )

fun decodeSharedStamp(payload: String): SharedStamp? =
    runCatching { KeepsakeJson.decodeFromString(SharedStamp.serializer(), payload) }.getOrNull()
        ?.takeIf { it.version == 1 && it.name.isNotBlank() && it.name.length <= 200 }

/** Received keepsakes, one JSON file per account on this device. */
class KeepsakeStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "keepsakes")
    private val serializer = ListSerializer(StampKeepsake.serializer())
    private val cache = MutableStateFlow<Map<String, List<StampKeepsake>>>(emptyMap())

    private fun fileFor(uid: String) = File(dir, sha256(uid) + ".json")

    suspend fun observe(uid: String): StateFlow<Map<String, List<StampKeepsake>>> {
        if (uid !in cache.value) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { KeepsakeJson.decodeFromString(serializer, fileFor(uid).readText()) }.getOrDefault(emptyList())
            }
            cache.update { it + (uid to loaded) }
        }
        return cache.asStateFlow()
    }

    suspend fun add(uid: String, keepsake: StampKeepsake) = withContext(Dispatchers.IO) {
        observe(uid)
        val next = listOf(keepsake) + cache.value[uid].orEmpty()
        dir.mkdirs()
        fileFor(uid).writeText(KeepsakeJson.encodeToString(serializer, next))
        cache.update { it + (uid to next) }
    }

    suspend fun remove(uid: String, id: String) = withContext(Dispatchers.IO) {
        observe(uid)
        val next = cache.value[uid].orEmpty().filterNot { it.id == id }
        dir.mkdirs()
        fileFor(uid).writeText(KeepsakeJson.encodeToString(serializer, next))
        cache.update { it + (uid to next) }
    }
}

val LocalStampPhotoStore = staticCompositionLocalOf<StampPhotoStore?> { null }
val LocalKeepsakeStore = staticCompositionLocalOf<KeepsakeStore?> { null }
