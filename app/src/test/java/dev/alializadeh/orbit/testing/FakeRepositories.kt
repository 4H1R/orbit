package dev.alializadeh.orbit.testing

import androidx.compose.ui.graphics.ImageBitmap
import dev.alializadeh.orbit.person.photo.CropImage
import dev.alializadeh.orbit.person.photo.CropRect
import dev.alializadeh.orbit.person.photo.PhotoStore
import dev.alializadeh.orbit.ring.Ring
import dev.alializadeh.orbit.ring.RingRepository
import java.io.File
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
