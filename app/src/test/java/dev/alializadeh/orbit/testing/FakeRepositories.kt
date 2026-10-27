package dev.alializadeh.orbit.testing

import androidx.compose.ui.graphics.ImageBitmap
import dev.alializadeh.orbit.person.photo.CropImage
import dev.alializadeh.orbit.person.photo.CropRect
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import dev.alializadeh.orbit.upcoming.UpcomingPerson
import dev.alializadeh.orbit.upcoming.UpcomingRepository
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRingRepository(rings: List<Ring> = TestRings.all) : RingRepository {
    val rings = MutableStateFlow(rings)

    override fun observeRings(): Flow<List<Ring>> = rings
}

/** A [PhotoStore] that only keeps file names: [files] are the photos that exist. */
class FakePhotoStore : PhotoStore {
    val files = mutableSetOf<String>()
    private var next = 1

    /** What [loadForCrop] returns; null makes the photo unreadable. */
    var image: CropImage? = FakeCropImage(1200, 800)

    override fun file(fileName: String): File = File("photos", fileName)

    override suspend fun loadForCrop(uri: String): CropImage? = image

    override suspend fun saveCrop(image: CropImage, crop: CropRect): String =
        "photo-${next++}.jpg".also { files += it }

    override fun delete(fileName: String) {
        files -= fileName
    }
}

class FakeCropImage(override val width: Int, override val height: Int) : CropImage {
    override fun imageBitmap(): ImageBitmap = error("Not drawn in JVM tests")
}

class FakeUpcomingRepository(people: List<UpcomingPerson> = emptyList()) : UpcomingRepository {
    val people = MutableStateFlow(people)

    override fun observePeople(): Flow<List<UpcomingPerson>> = this.people
}

/** A [Clock] whose instant tests move by hand. */
class MutableClock(var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    fun advance(duration: Duration) {
        instant += duration
    }
}
