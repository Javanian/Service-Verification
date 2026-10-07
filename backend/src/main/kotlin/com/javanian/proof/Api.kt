package com.javanian.proof

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class Api(val jobs: Jobs, val photos: Photos, val passwords: BCryptPasswordEncoder) {
    @GetMapping("/session")
    fun session(request: HttpServletRequest, a: Authentication?): Map<String, Any?> {
        val csrf = request.getAttribute(CsrfToken::class.java.name) as CsrfToken
        return mapOf(
            "username" to a?.name,
            "owner" to (a?.let { jobs.owner(it) } ?: false),
            "csrf" to csrf.token,
        )
    }

    @GetMapping("/technicians")
    fun techs(a: Authentication): Any {
        jobs.requireOwner(a)
        return jobs.db.queryForList(
            "select username from accounts where role='TECH' order by username"
        )
    }

    @PostMapping("/technicians")
    fun tech(@Valid @RequestBody input: NewTech, a: Authentication): Any {
        jobs.requireOwner(a)
        if (input.password.toByteArray().size > 72)
            fail(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes")
        jobs.db.update(
            "insert into accounts values(?,?,'TECH')",
            input.username,
            passwords.encode(input.password),
        )
        return mapOf("username" to input.username)
    }

    @GetMapping("/jobs")
    fun list(a: Authentication): Any =
        if (jobs.owner(a)) jobs.db.queryForList("select * from jobs order by created_at desc")
        else
            jobs.db.queryForList(
                "select * from jobs where technician=? order by created_at desc",
                a.name,
            )

    @PostMapping("/jobs")
    fun create(@Valid @RequestBody input: CreateJob, a: Authentication) = jobs.create(input, a)

    @GetMapping("/jobs/{id}")
    fun detail(@PathVariable id: UUID, a: Authentication) = jobs.detail(id, a)

    @PutMapping("/jobs/{id}/units/{unit}")
    fun save(
        @PathVariable id: UUID,
        @PathVariable unit: UUID,
        @Valid @RequestBody input: SaveUnit,
        a: Authentication,
    ) = jobs.save(id, unit, input, a)

    @PostMapping("/jobs/{id}/{action:submit|approve|request-changes|revise}")
    fun transition(
        @PathVariable id: UUID,
        @PathVariable action: String,
        @Valid @RequestBody input: Transition,
        a: Authentication,
    ) = jobs.transition(id, action, input, a)

    @PostMapping("/jobs/{id}/units/{unit}/photos/{kind}")
    fun upload(
        @PathVariable id: UUID,
        @PathVariable unit: UUID,
        @PathVariable kind: String,
        @RequestParam version: Int,
        @RequestParam key: UUID,
        @RequestParam file: MultipartFile,
        a: Authentication,
    ) = photos.upload(id, unit, kind, version, key, file, a)

    @GetMapping("/photos/{photo}")
    fun photo(@PathVariable photo: UUID, a: Authentication): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore())
            .header("Content-Disposition", "inline; filename=service-evidence.jpg")
            .body(photos.read(photo, a))

    @GetMapping("/jobs/{id}/reports/{revision}")
    fun report(@PathVariable id: UUID, @PathVariable revision: Int, a: Authentication): Any {
        jobs.job(id, a)
        val row =
            jobs.db
                .queryForList(
                    "select snapshot::text,approved_by,approved_at from reports where job_id=? and revision=?",
                    id,
                    revision,
                )
                .firstOrNull() ?: fail(HttpStatus.NOT_FOUND, "Report not found")
        return mapOf(
            "job" to jobs.json.readTree(row["snapshot"] as String),
            "approvedBy" to row["approved_by"],
            "approvedAt" to row["approved_at"],
        )
    }
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(
        org.springframework.dao.DataAccessException::class,
        org.springframework.transaction.TransactionException::class,
    )
    fun storage(
        e: org.springframework.dao.DataAccessException
    ): ResponseEntity<Map<String, String>> {
        org.slf4j.LoggerFactory.getLogger(Errors::class.java)
            .warn("Database operation failed ({})", e.javaClass.simpleName)
        return ResponseEntity.status(503)
            .body(
                mapOf(
                    "message" to
                        "Storage is temporarily unavailable. Your changes were not confirmed. Retry when the service is restored."
                )
            )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun state(e: ResponseStatusException) =
        ResponseEntity.status(e.statusCode).body(mapOf("message" to e.reason))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: Exception) =
        ResponseEntity.badRequest()
            .body(mapOf("message" to "Check required fields and maximum lengths."))

    @ExceptionHandler(DuplicateKeyException::class)
    fun duplicate(e: Exception) =
        ResponseEntity.status(409)
            .body(mapOf("message" to "This username or request already exists."))

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException::class)
    fun size(e: Exception) =
        ResponseEntity.status(413).body(mapOf("message" to "Photo must be at most 5 MB."))
}
