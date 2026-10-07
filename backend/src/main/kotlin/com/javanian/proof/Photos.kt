package com.javanian.proof

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.imageio.ImageIO
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class Photos(val jobs: Jobs) {
    @Transactional
    fun upload(
        id: UUID,
        unit: UUID,
        kind: String,
        version: Int,
        key: UUID,
        file: MultipartFile,
        a: Authentication,
    ): Map<String, Any> {
        val j = jobs.job(id, a, true)
        jobs.editable(j)
        if (kind !in listOf("before", "after"))
            fail(HttpStatus.BAD_REQUEST, "Choose before or after")
        if (file.size !in 1..5_242_880) fail(HttpStatus.BAD_REQUEST, "Photo must be at most 5 MB")
        val source = file.bytes
        val hash =
            MessageDigest.getInstance("SHA-256").digest(source).joinToString("") {
                "%02x".format(it)
            }
        val prior =
            jobs.db.queryForList("select * from photos where request_key=?", key).firstOrNull()
        if (prior != null) {
            if (prior["unit_id"] != unit || prior["kind"] != kind || prior["source_hash"] != hash)
                fail(HttpStatus.CONFLICT, "Upload retry key already used for different content")
            return jobs.detail(id, a)
        }
        jobs.version(j, version)
        if (
            jobs.db.queryForObject(
                "select count(*) from units where id=? and job_id=?",
                Long::class.java,
                unit,
                id,
            ) != 1L
        )
            fail(HttpStatus.NOT_FOUND, "Unit not found")
        val safe = normalize(source)
        val photo = UUID.randomUUID()
        jobs.db.update(
            "insert into photos(id,unit_id,kind,request_key,source_hash,bytes) values(?,?,?,?,?,?)",
            photo,
            unit,
            kind,
            key,
            hash,
            safe,
        )
        jobs.db.update("update units set ${kind}_id=? where id=?", photo, unit)
        jobs.bump(id)
        jobs.event(id, a, "PHOTO_SAVED", kind)
        return jobs.detail(id, a)
    }

    fun normalize(source: ByteArray): ByteArray {
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(source)).use { stream ->
                val readers = ImageIO.getImageReaders(stream)
                if (!readers.hasNext())
                    fail(HttpStatus.BAD_REQUEST, "Use a valid JPEG or PNG photo")
                val reader = readers.next()
                try {
                    if (reader.formatName.lowercase() !in listOf("jpeg", "png"))
                        fail(HttpStatus.BAD_REQUEST, "Use JPEG or PNG")
                    reader.input = stream
                    val w = reader.getWidth(0)
                    val h = reader.getHeight(0)
                    if (w < 1 || h < 1 || w.toLong() * h > 12_000_000)
                        fail(HttpStatus.BAD_REQUEST, "Photo must be at most 12 megapixels")
                    val raw = reader.read(0)
                    val clean = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                    val g = clean.createGraphics()
                    g.color = java.awt.Color.WHITE
                    g.fillRect(0, 0, w, h)
                    g.drawImage(raw, 0, 0, null)
                    g.dispose()
                    return ByteArrayOutputStream()
                        .also { ImageIO.write(clean, "jpg", it) }
                        .toByteArray()
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: org.springframework.web.server.ResponseStatusException) {
            throw e
        } catch (e: Exception) {
            fail(HttpStatus.BAD_REQUEST, "Photo could not be read. Choose another JPEG or PNG.")
        }
    }

    fun read(photo: UUID, a: Authentication): ByteArray {
        val row =
            jobs.db
                .queryForList(
                    "select p.bytes,u.job_id from photos p join units u on u.id=p.unit_id where p.id=?",
                    photo,
                )
                .firstOrNull() ?: fail(HttpStatus.NOT_FOUND, "Photo not found")
        jobs.job(row["job_id"] as UUID, a)
        return row["bytes"] as ByteArray
    }
}
