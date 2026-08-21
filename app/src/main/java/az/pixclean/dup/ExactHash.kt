package az.pixclean.dup

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest

/**
 * Byte-exact duplicate detection, same tiering as the reference script:
 * size bucket -> hash of the first 64 KB -> full hash. Only files that survive each
 * tier pay for the next one, so on a normal gallery almost nothing gets fully read.
 */
object ExactHash {

    const val HEAD_BYTES = 64 * 1024
    private const val CHUNK = 256 * 1024
    private val HEX = "0123456789abcdef".toCharArray()

    fun hash(resolver: ContentResolver, uri: Uri, limit: Long = -1L): String? = try {
        resolver.openInputStream(uri)?.use { digestOf(it, limit) }
    } catch (_: Exception) {
        null
    }

    private fun digestOf(input: InputStream, limit: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(CHUNK)
        var read = 0L
        while (true) {
            val want = if (limit < 0) CHUNK else minOf(CHUNK.toLong(), limit - read).toInt()
            if (want <= 0) break
            val n = input.read(buf, 0, want)
            if (n <= 0) break
            md.update(buf, 0, n)
            read += n
        }
        return toHex(md.digest())
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }
}
