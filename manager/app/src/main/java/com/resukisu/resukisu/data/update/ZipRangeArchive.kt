package com.resukisu.resukisu.data.update

import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ZipEntryMetadata(
    val name: String,
    val flags: Int,
    val compressionMethod: Int,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long,
    val canUseRange: Boolean = true,
)

class ZipRangeArchive(
    private val client: OkHttpClient,
) {

    private val archiveClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val rangeClient = archiveClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun listEntries(url: String): List<ZipEntryMetadata> {
        return try {
            listRangedEntries(url)
        } catch (_: IOException) {
            listFullArchiveEntries(url)
        }
    }

    private fun listRangedEntries(url: String): List<ZipEntryMetadata> {
        val tail = getRangeBytes(url, "bytes=-65557")
        return ZipCentralDirectory.parse(tail.bytes, tail.startOffset) { offset, length ->
            getRangeBytes(url, "bytes=$offset-${offset + length - 1}").bytes
        }
    }

    fun extractEntry(
        url: String,
        entryName: String,
        target: File,
        onProgress: (Int) -> Unit,
    ) {
        val entry = listEntries(url).firstOrNull { it.name == entryName }
            ?: throw IOException("ZIP entry not found")
        extractEntry(url, entry, target, onProgress)
    }

    fun extractEntry(
        url: String,
        entry: ZipEntryMetadata,
        target: File,
        onProgress: (Int) -> Unit,
    ) {
        try {
            if (entry.canUseRange) {
                extractRangedEntry(url, entry, target, onProgress)
            } else {
                extractFullArchive(url, entry.name, target, onProgress)
            }
        } catch (error: ZipRangeUnsupportedException) {
            extractFullArchive(url, entry.name, target, onProgress)
        }
    }

    private fun extractRangedEntry(
        url: String,
        entry: ZipEntryMetadata,
        target: File,
        onProgress: (Int) -> Unit,
    ) {
        if (entry.flags and FLAG_ENCRYPTED != 0) {
            throw IOException("Encrypted ZIP entries are unsupported")
        }

        val localHeader = getRangeBytes(
            url,
            "bytes=${entry.localHeaderOffset}-${entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE - 1}"
        ).bytes
        if (localHeader.readUIntAt(0) != LOCAL_FILE_HEADER_SIGNATURE) {
            throw IOException("Invalid ZIP local file header")
        }

        val nameLength = localHeader.readUShortAt(26)
        val extraLength = localHeader.readUShortAt(28)
        val dataOffset = entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE + nameLength + extraLength

        if (entry.compressedSize == 0L) {
            writeVerified(ByteArrayInputStream(ByteArray(0)), target, entry, onProgress)
            return
        }

        withRangeStream(
            url,
            "bytes=$dataOffset-${dataOffset + entry.compressedSize - 1}"
        ) { compressedStream ->
            val decodedStream = when (entry.compressionMethod) {
                METHOD_STORED -> compressedStream
                METHOD_DEFLATED -> InflaterInputStream(compressedStream, Inflater(true))
                else -> throw IOException("Unsupported ZIP compression method")
            }
            decodedStream.use {
                writeVerified(it, target, entry, onProgress)
            }
        }
    }

    private fun extractFullArchive(
        url: String,
        entryName: String,
        target: File,
        onProgress: (Int) -> Unit,
    ) {
        val parent = target.parentFile ?: throw IOException("Missing target directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Unable to create target directory")
        }

        val archive = File.createTempFile("manager-update-", ".zip", parent)
        try {
            archiveClient.newCall(newRequest(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0L) {
                                onProgress(((copied * FULL_ARCHIVE_DOWNLOAD_PROGRESS) / total).toInt())
                            }
                        }
                    }
                }
            }

            ZipFile(archive).use { zipFile ->
                val zipEntry =
                    zipFile.getEntry(entryName) ?: throw IOException("ZIP entry not found")
                val entry = ZipEntryMetadata(
                    name = zipEntry.name,
                    flags = 0,
                    compressionMethod = zipEntry.method,
                    crc32 = zipEntry.crc,
                    compressedSize = zipEntry.compressedSize,
                    uncompressedSize = zipEntry.size,
                    localHeaderOffset = 0,
                )
                zipFile.getInputStream(zipEntry).use { input ->
                    writeVerified(input, target, entry) { progress ->
                        FULL_ARCHIVE_DOWNLOAD_PROGRESS +
                                ((progress * (100 - FULL_ARCHIVE_DOWNLOAD_PROGRESS)) / 100)
                    }
                }
            }
        } finally {
            archive.delete()
        }
    }

    private fun listFullArchiveEntries(url: String): List<ZipEntryMetadata> {
        return archiveClient.newCall(newRequest(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            body.byteStream().use { input ->
                ZipInputStream(input).use { zip ->
                    buildList {
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            zip.closeEntry()
                            add(
                                ZipEntryMetadata(
                                    name = entry.name,
                                    flags = 0,
                                    compressionMethod = entry.method,
                                    crc32 = entry.crc,
                                    compressedSize = entry.compressedSize,
                                    uncompressedSize = entry.size,
                                    localHeaderOffset = 0,
                                    canUseRange = false,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun writeVerified(
        input: InputStream,
        target: File,
        entry: ZipEntryMetadata,
        onProgress: (Int) -> Unit,
    ) {
        val parent = target.parentFile ?: throw IOException("Missing target directory")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Unable to create target directory")
        }

        val partial = File(parent, ".${target.name}.part")
        if (partial.exists()) partial.delete()

        var completed = false
        try {
            val crc = CRC32()
            var copied = 0L
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    crc.update(buffer, 0, read)
                    copied += read
                    if (entry.uncompressedSize > 0L) {
                        onProgress(
                            ((copied * 100L) / entry.uncompressedSize).toInt().coerceIn(0, 100)
                        )
                    }
                }
            }

            if (copied != entry.uncompressedSize || crc.value != entry.crc32) {
                throw IOException("Downloaded APK validation failed")
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Unable to replace target APK")
            }
            if (!partial.renameTo(target)) {
                throw IOException("Unable to finalize APK")
            }
            completed = true
            onProgress(100)
        } finally {
            if (!completed) partial.delete()
        }
    }

    private fun getRangeBytes(url: String, range: String): RangeBytes {
        return withRangeResponse(url, range) { response ->
            val contentRange = response.header("Content-Range")
                ?: throw IOException("Missing Content-Range")
            val match = CONTENT_RANGE_PATTERN.matchEntire(contentRange)
                ?: throw IOException("Invalid Content-Range")
            val startOffset = match.groupValues[1].toLongOrNull()
                ?: throw IOException("Invalid Content-Range")
            val body = response.body ?: throw IOException("Empty body")
            RangeBytes(body.bytes(), startOffset)
        }
    }

    private fun withRangeStream(
        url: String,
        range: String,
        block: (InputStream) -> Unit,
    ) {
        withRangeResponse(url, range) { response ->
            val body = response.body ?: throw IOException("Empty body")
            body.byteStream().use(block)
        }
    }

    private fun <T> withRangeResponse(
        url: String,
        range: String,
        block: (Response) -> T,
    ): T {
        var currentUrl = url
        repeat(MAX_RANGE_REDIRECTS) {
            rangeClient.newCall(
                newRequest(currentUrl).header("Range", range).build()
            ).execute().use { response ->
                if (response.code in HTTP_REDIRECT_START..HTTP_REDIRECT_END) {
                    val location = response.header("Location")
                        ?: throw IOException("Missing ZIP redirect location")
                    currentUrl = URI(response.request.url.toString()).resolve(location).toString()
                    return@use
                }

                if (response.code == HTTP_OK) throw ZipRangeUnsupportedException()
                if (response.code != HTTP_PARTIAL) throw IOException("HTTP ${response.code}")
                return block(response)
            }
        }
        throw IOException("Too many ZIP redirects")
    }

    private data class RangeBytes(
        val bytes: ByteArray,
        val startOffset: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RangeBytes

            if (startOffset != other.startOffset) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = startOffset.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    private fun newRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)

    private class ZipRangeUnsupportedException : IOException()

    private fun ByteArray.readUShortAt(offset: Int): Int {
        if (offset < 0 || offset + 2 > size) throw IOException("Truncated ZIP data")
        return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.readUIntAt(offset: Int): Long {
        if (offset < 0 || offset + 4 > size) throw IOException("Truncated ZIP data")
        return (this[offset].toLong() and 0xff) or
                ((this[offset + 1].toLong() and 0xff) shl 8) or
                ((this[offset + 2].toLong() and 0xff) shl 16) or
                ((this[offset + 3].toLong() and 0xff) shl 24)
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val HTTP_REDIRECT_START = 300
        const val HTTP_REDIRECT_END = 399
        const val MAX_RANGE_REDIRECTS = 5
        const val LOCAL_FILE_HEADER_SIZE = 30L
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
        const val METHOD_STORED = 0
        const val METHOD_DEFLATED = 8
        const val FLAG_ENCRYPTED = 1
        const val FULL_ARCHIVE_DOWNLOAD_PROGRESS = 70
        val CONTENT_RANGE_PATTERN = Regex("bytes (\\d+)-\\d+/\\d+")
    }
}

object ZipCentralDirectory {

    fun parse(
        tail: ByteArray,
        tailOffset: Long,
        readRange: (offset: Long, length: Int) -> ByteArray,
    ): List<ZipEntryMetadata> {
        val eocdOffset = findEndOfCentralDirectory(tail)
        val entryCount = tail.readUShort(eocdOffset + 10)
        val centralDirectorySize = tail.readUInt(eocdOffset + 12)
        val centralDirectoryOffset = tail.readUInt(eocdOffset + 16)

        if (entryCount == ZIP64_U16 ||
            centralDirectorySize == ZIP64_U32 ||
            centralDirectoryOffset == ZIP64_U32
        ) {
            throw IOException("ZIP64 archives are unsupported")
        }
        if (centralDirectorySize > Int.MAX_VALUE) {
            throw IOException("ZIP central directory is too large")
        }

        val centralDirectoryEnd = centralDirectoryOffset + centralDirectorySize
        val tailEnd = tailOffset + tail.size
        val directory =
            if (centralDirectoryOffset >= tailOffset && centralDirectoryEnd <= tailEnd) {
                val start = (centralDirectoryOffset - tailOffset).toInt()
                tail.copyOfRange(start, start + centralDirectorySize.toInt())
            } else {
                readRange(centralDirectoryOffset, centralDirectorySize.toInt())
            }
        if (directory.size != centralDirectorySize.toInt()) {
            throw IOException("Incomplete ZIP central directory")
        }

        return parseEntries(directory, entryCount)
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
        for (index in bytes.size - END_OF_CENTRAL_DIRECTORY_SIZE downTo 0) {
            if (bytes.readUInt(index) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) continue
            val commentLength = bytes.readUShort(index + 20)
            if (index + END_OF_CENTRAL_DIRECTORY_SIZE + commentLength <= bytes.size) {
                return index
            }
        }
        throw IOException("ZIP end of central directory not found")
    }

    private fun parseEntries(
        directory: ByteArray,
        entryCount: Int,
    ): List<ZipEntryMetadata> {
        val entries = ArrayList<ZipEntryMetadata>(entryCount)
        var offset = 0
        repeat(entryCount) {
            if (offset + CENTRAL_DIRECTORY_HEADER_SIZE > directory.size ||
                directory.readUInt(offset) != CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE
            ) {
                throw IOException("Invalid ZIP central directory entry")
            }

            val flags = directory.readUShort(offset + 8)
            val compressionMethod = directory.readUShort(offset + 10)
            val crc32 = directory.readUInt(offset + 16)
            val compressedSize = directory.readUInt(offset + 20)
            val uncompressedSize = directory.readUInt(offset + 24)
            val nameLength = directory.readUShort(offset + 28)
            val extraLength = directory.readUShort(offset + 30)
            val commentLength = directory.readUShort(offset + 32)
            val localHeaderOffset = directory.readUInt(offset + 42)

            if (compressedSize == ZIP64_U32 ||
                uncompressedSize == ZIP64_U32 ||
                localHeaderOffset == ZIP64_U32
            ) {
                throw IOException("ZIP64 entries are unsupported")
            }

            val nameOffset = offset + CENTRAL_DIRECTORY_HEADER_SIZE
            val nextOffset = nameOffset + nameLength + extraLength + commentLength
            if (nextOffset > directory.size) throw IOException("Truncated ZIP central directory entry")

            entries += ZipEntryMetadata(
                name = String(directory, nameOffset, nameLength, Charsets.UTF_8),
                flags = flags,
                compressionMethod = compressionMethod,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset,
            )
            offset = nextOffset
        }
        return entries
    }

    private fun ByteArray.readUShort(offset: Int): Int {
        requireBounds(offset, 2)
        return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.readUInt(offset: Int): Long {
        requireBounds(offset, 4)
        return (this[offset].toLong() and 0xff) or
                ((this[offset + 1].toLong() and 0xff) shl 8) or
                ((this[offset + 2].toLong() and 0xff) shl 16) or
                ((this[offset + 3].toLong() and 0xff) shl 24)
    }

    private fun ByteArray.requireBounds(offset: Int, length: Int) {
        if (offset < 0 || offset + length > size) {
            throw IOException("Truncated ZIP data")
        }
    }

    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
    private const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
    private const val CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50L
    private const val CENTRAL_DIRECTORY_HEADER_SIZE = 46
    private const val ZIP64_U16 = 0xffff
    private const val ZIP64_U32 = 0xffffffffL
}
