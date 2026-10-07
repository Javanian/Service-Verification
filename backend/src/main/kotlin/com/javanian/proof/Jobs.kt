package com.javanian.proof

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.*
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

fun fail(status: HttpStatus, message: String): Nothing =
    throw ResponseStatusException(status, message)

data class CreateJob(
    @field:NotBlank @field:Size(max = 160) val customer: String,
    @field:NotBlank @field:Size(max = 300) val location: String,
    @field:NotBlank @field:Size(max = 100) val invoice: String,
    val technician: String,
    @field:Size(min = 1, max = 30) val units: List<@NotBlank @Size(max = 100) String>,
)

data class SaveUnit(
    val version: Int,
    val cleaned: Boolean,
    val drainChecked: Boolean,
    val coolingChecked: Boolean,
    @field:Size(max = 2000) val actions: String,
)

data class Transition(val version: Int, @field:Size(max = 1000) val reason: String = "")

data class NewTech(
    @field:Pattern(regexp = "[a-zA-Z0-9._-]{3,60}") val username: String,
    @field:Size(min = 14, max = 72) val password: String,
)

@Service
class Jobs(val db: JdbcTemplate, val json: ObjectMapper) {
    fun owner(a: Authentication) = a.authorities.any { it.authority == "ROLE_OWNER" }

    fun requireOwner(a: Authentication) {
        if (!owner(a)) fail(HttpStatus.FORBIDDEN, "Owner access required")
    }

    fun job(id: UUID, a: Authentication, lock: Boolean = false): MutableMap<String, Any> {
        val row =
            db.queryForList("select * from jobs where id=?" + if (lock) " for update" else "", id)
                .firstOrNull() ?: fail(HttpStatus.NOT_FOUND, "Job not found")
        if (!owner(a) && row["technician"] != a.name) fail(HttpStatus.NOT_FOUND, "Job not found")
        return row
    }

    fun detail(id: UUID, a: Authentication): Map<String, Any> =
        job(id, a).apply {
            put(
                "units",
                db.queryForList("select * from units where job_id=? order by label,id", id),
            )
            put(
                "reports",
                db.queryForList(
                    "select revision, approved_by, approved_at from reports where job_id=? order by revision desc",
                    id,
                ),
            )
            put(
                "events",
                db.queryForList(
                    "select actor,action,detail,created_at from events where job_id=? order by id",
                    id,
                ),
            )
        }

    fun version(j: Map<String, Any>, v: Int) {
        if (j["version"] != v)
            fail(HttpStatus.CONFLICT, "This job changed. Reload before continuing.")
    }

    fun editable(j: Map<String, Any>) {
        if (j["status"] !in listOf("DRAFT", "CHANGES_REQUESTED"))
            fail(
                HttpStatus.CONFLICT,
                "This record is locked. Request an explicit revision after approval.",
            )
    }

    fun bump(id: UUID) {
        db.update("update jobs set version=version+1 where id=?", id)
    }

    fun event(id: UUID, a: Authentication, action: String, detail: String = "") {
        db.update(
            "insert into events(job_id,actor,action,detail) values(?,?,?,?)",
            id,
            a.name,
            action,
            detail,
        )
    }

    @Transactional
    fun create(input: CreateJob, a: Authentication): Map<String, Any> {
        requireOwner(a)
        if (
            db.queryForObject(
                "select count(*) from accounts where username=? and role='TECH'",
                Long::class.java,
                input.technician,
            ) != 1L
        )
            fail(HttpStatus.BAD_REQUEST, "Choose a technician")
        if (input.units.any { it.isBlank() || it.length > 100 })
            fail(HttpStatus.BAD_REQUEST, "Unit labels must be 1–100 characters")
        val id = UUID.randomUUID()
        db.update(
            "insert into jobs(id,customer,location,invoice,technician) values(?,?,?,?,?)",
            id,
            input.customer.trim(),
            input.location.trim(),
            input.invoice.trim(),
            input.technician,
        )
        input.units.forEach {
            db.update(
                "insert into units(id,job_id,label) values(?,?,?)",
                UUID.randomUUID(),
                id,
                it.trim(),
            )
        }
        event(id, a, "CREATED")
        return detail(id, a)
    }

    @Transactional
    fun save(id: UUID, unit: UUID, input: SaveUnit, a: Authentication): Map<String, Any> {
        val j = job(id, a, true)
        version(j, input.version)
        editable(j)
        if (
            db.update(
                "update units set cleaned=?,drain_checked=?,cooling_checked=?,actions=? where id=? and job_id=?",
                input.cleaned,
                input.drainChecked,
                input.coolingChecked,
                input.actions.trim(),
                unit,
                id,
            ) != 1
        )
            fail(HttpStatus.NOT_FOUND, "Unit not found")
        bump(id)
        event(id, a, "UNIT_SAVED")
        return detail(id, a)
    }

    fun complete(id: UUID) {
        if (
            db.queryForObject(
                "select count(*) from units where job_id=? and (not cleaned or not drain_checked or not cooling_checked or trim(actions)='' or before_id is null or after_id is null)",
                Long::class.java,
                id,
            ) != 0L
        )
            fail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Each unit needs all three checks, service actions, and before/after photos.",
            )
    }

    @Transactional
    fun transition(
        id: UUID,
        action: String,
        input: Transition,
        a: Authentication,
    ): Map<String, Any> {
        val j = job(id, a, true)
        version(j, input.version)
        when (action) {
            "submit" -> {
                editable(j)
                complete(id)
                db.update("update jobs set status='SUBMITTED' where id=?", id)
            }
            "request-changes" -> {
                requireOwner(a)
                if (j["status"] != "SUBMITTED")
                    fail(HttpStatus.CONFLICT, "Only submitted jobs can be reviewed")
                if (input.reason.isBlank()) fail(HttpStatus.BAD_REQUEST, "Give a correction reason")
                db.update(
                    "update jobs set status='CHANGES_REQUESTED',reason=? where id=?",
                    input.reason.trim(),
                    id,
                )
            }
            "approve" -> {
                requireOwner(a)
                if (j["status"] != "SUBMITTED")
                    fail(HttpStatus.CONFLICT, "Only submitted jobs can be approved")
                complete(id)
                db.update("update jobs set status='APPROVED',reason='' where id=?", id)
                db.update(
                    "insert into reports(job_id,revision,snapshot,approved_by) values(?,?,?::jsonb,?)",
                    id,
                    j["revision"],
                    json.writeValueAsString(
                        detail(id, a).filterKeys { it !in listOf("reports", "events") }
                    ),
                    a.name,
                )
            }
            "revise" -> {
                requireOwner(a)
                if (j["status"] != "APPROVED")
                    fail(HttpStatus.CONFLICT, "Only approved jobs can start a revision")
                if (input.reason.isBlank()) fail(HttpStatus.BAD_REQUEST, "Give a revision reason")
                db.update(
                    "update jobs set status='DRAFT',revision=revision+1,reason=? where id=?",
                    input.reason.trim(),
                    id,
                )
            }
            else -> fail(HttpStatus.NOT_FOUND, "Unknown action")
        }
        bump(id)
        event(id, a, action.uppercase(), input.reason)
        return detail(id, a)
    }
}
