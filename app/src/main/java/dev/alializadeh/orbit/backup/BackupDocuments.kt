package dev.alializadeh.orbit.backup

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * The documents the system pickers hand out (Storage Access Framework), by URI string. JVM tests use
 * plain files instead.
 */
interface BackupDocuments {
    /** Opens [uri] for writing from the start, truncating it. */
    fun openOutput(uri: String): OutputStream

    fun openInput(uri: String): InputStream

    /** Deletes [uri] if the provider allows it; a failure is ignored. */
    fun delete(uri: String)
}

class ContentResolverBackupDocuments @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupDocuments {

    // "wt", not "w": some providers don't truncate on "w".
    override fun openOutput(uri: String): OutputStream = try {
        context.contentResolver.openOutputStream(uri.toUri(), "wt")
    } catch (e: FileNotFoundException) {
        throw IOException("Can't write to $uri", e)
    } catch (e: SecurityException) {
        throw IOException("Can't write to $uri", e)
    } ?: throw IOException("Can't write to $uri")

    override fun openInput(uri: String): InputStream = try {
        context.contentResolver.openInputStream(uri.toUri())
    } catch (e: FileNotFoundException) {
        throw IOException("Can't read $uri", e)
    } catch (e: SecurityException) {
        throw IOException("Can't read $uri", e)
    } ?: throw IOException("Can't read $uri")

    override fun delete(uri: String) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri.toUri()) }
    }
}
