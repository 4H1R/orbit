package dev.alializadeh.orbit.person.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picked photo, decoded for the crop screen. */
interface CropImage {
    val width: Int
    val height: Int

    fun imageBitmap(): ImageBitmap
}

/**
 * People's photos: 768 px square JPEGs (quality 85) in `filesDir/photos/<uuid>.jpg`. Room keeps
 * only the file name, Coil loads the [file], and a Backup carries the files that are referenced.
 */
interface PhotoStore {

    fun file(fileName: String): File

    /** Decodes the photo at [uri] (from the photo picker) for cropping, or null if it can't be read. */
    suspend fun loadForCrop(uri: String): CropImage?

    /** Saves the [crop] of [image] as a new photo and returns its file name. */
    suspend fun saveCrop(image: CropImage, crop: CropRect): String

    /** Deletes a photo file; a missing file is fine. Quick enough to call from `onCleared`. */
    fun delete(fileName: String)
}

class FilesPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoStore {

    private val directory: File get() = File(context.filesDir, DIRECTORY)

    /** A name that isn't one of Orbit's (a path, "..") never leaves the folder: it names no file. */
    override fun file(fileName: String): File =
        File(directory, if (isValidFileName(fileName)) fileName else NOT_A_PHOTO)

    override suspend fun loadForCrop(uri: String): CropImage? = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
            // ImageDecoder applies the EXIF orientation. Software memory, so it can be drawn
            // into the output bitmap; sampled down so a 50 MP photo doesn't fill the heap.
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                var sample = 1
                while (longest / (sample * 2) >= MAX_DECODED_SIZE) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
            BitmapCropImage(bitmap)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    override suspend fun saveCrop(image: CropImage, crop: CropRect): String = withContext(Dispatchers.IO) {
        val source = (image as BitmapCropImage).bitmap
        val output = createBitmap(SIZE_PX, SIZE_PX)
        android.graphics.Canvas(output).drawBitmap(
            source,
            Rect(crop.left.toInt(), crop.top.toInt(), (crop.left + crop.size).toInt(), (crop.top + crop.size).toInt()),
            RectF(0f, 0f, SIZE_PX.toFloat(), SIZE_PX.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        directory.mkdirs()
        val fileName = "${UUID.randomUUID()}.jpg"
        val target = file(fileName)
        val partial = File(directory, "$fileName.partial")
        partial.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        output.recycle()
        check(partial.renameTo(target)) { "Couldn't move the photo into place" }
        fileName
    }

    override fun delete(fileName: String) {
        if (isValidFileName(fileName)) file(fileName).delete()
    }

    private class BitmapCropImage(val bitmap: Bitmap) : CropImage {
        override val width: Int get() = bitmap.width
        override val height: Int get() = bitmap.height

        override fun imageBitmap(): ImageBitmap = bitmap.asImageBitmap()
    }

    companion object {
        const val DIRECTORY = "photos"
        const val SIZE_PX = 768
        const val JPEG_QUALITY = 85

        /** The decoded photo's longest side stays under twice this. */
        private const val MAX_DECODED_SIZE = 2048

        /** Every photo's name: a random UUID and ".jpg", never a path. */
        private val FILE_NAME = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg""")

        /** What [file] returns for a name that isn't a photo's: a file that never exists. */
        private const val NOT_A_PHOTO = "not-a-photo"

        /** Whether [fileName] is a name Orbit gives photos (Restore refuses a Backup with any other). */
        fun isValidFileName(fileName: String): Boolean = FILE_NAME.matches(fileName)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface PhotoModule {

    @Binds
    @Singleton
    fun bindPhotoStore(store: FilesPhotoStore): PhotoStore
}
