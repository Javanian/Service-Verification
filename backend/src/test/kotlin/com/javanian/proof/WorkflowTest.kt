package com.javanian.proof

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties =
        ["spring.datasource.url=\${TEST_DB_URL:jdbc:postgresql://localhost:5432/serviceproof_test}"]
)
@AutoConfigureMockMvc
class WorkflowTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var db: JdbcTemplate
    @Autowired lateinit var json: ObjectMapper

    fun actor(name: String) = user(name).roles(if (name == "owner") "OWNER" else "TECH")

    @BeforeEach
    fun reset() {
        db.dataSource!!.connection.use {
            require(it.metaData.url.substringBefore("?").endsWith("_test")) {
                "Integration tests require a database name ending in _test"
            }
        }
        db.execute("truncate events,reports,photos,units,jobs,accounts cascade")
        listOf("owner", "alice", "bob").forEach {
            db.update(
                "insert into accounts values(?,? ,?)",
                it,
                "unused",
                if (it == "owner") "OWNER" else "TECH",
            )
        }
    }

    fun request(path: String, body: Any, name: String = "owner", code: Int = 200): JsonNode {
        val r =
            mvc.perform(
                    post("/api$path")
                        .with(actor(name))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))
                )
                .andExpect(status().`is`(code))
                .andReturn()
        return if (r.response.contentAsString.isBlank()) json.createObjectNode()
        else json.readTree(r.response.contentAsString)
    }

    fun create(): JsonNode =
        request(
            "/jobs",
            mapOf(
                "customer" to "North Studio",
                "location" to "Level 2",
                "invoice" to "INV-008",
                "technician" to "alice",
                "units" to listOf("Lobby AC-01"),
            ),
        )

    fun current(id: String): JsonNode =
        json.readTree(
            mvc.perform(get("/api/jobs/$id").with(actor("alice")))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        )

    fun save(j: JsonNode, code: Int = 200): JsonNode {
        val r =
            mvc.perform(
                    put("/api/jobs/${j["id"].asText()}/units/${j["units"][0]["id"].asText()}")
                        .with(actor("alice"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                mapOf(
                                    "version" to j["version"].asInt(),
                                    "cleaned" to true,
                                    "drainChecked" to true,
                                    "coolingChecked" to true,
                                    "actions" to
                                        "Cleaned coil; drain flows freely. Cooling restored.",
                                )
                            )
                        )
                )
                .andExpect(status().`is`(code))
                .andReturn()
        return json.readTree(r.response.contentAsString)
    }

    fun upload(
        j: JsonNode,
        kind: String,
        key: String = UUID.randomUUID().toString(),
        bytes: ByteArray = png(),
        name: String = "alice",
        code: Int = 200,
    ): JsonNode {
        val r =
            mvc.perform(
                    multipart(
                            "/api/jobs/${j["id"].asText()}/units/${j["units"][0]["id"].asText()}/photos/$kind"
                        )
                        .file(MockMultipartFile("file", "camera.png", "image/png", bytes))
                        .param("version", j["version"].asText())
                        .param("key", key)
                        .with(actor(name))
                        .with(csrf())
                )
                .andExpect(status().`is`(code))
                .andReturn()
        return if (r.response.contentAsString.isBlank()) json.createObjectNode()
        else json.readTree(r.response.contentAsString)
    }

    fun png(): ByteArray =
        ByteArrayOutputStream()
            .also { ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()

    fun act(
        j: JsonNode,
        action: String,
        name: String = "owner",
        reason: String = "",
        code: Int = 200,
    ) =
        request(
            "/jobs/${j["id"].asText()}/$action",
            mapOf("version" to j["version"].asInt(), "reason" to reason),
            name,
            code,
        )

    @Test
    fun `complete correction approval and explicit revision preserve frozen report`() {
        var j = create()
        act(j, "submit", "alice", code = 422)
        j = save(j)
        j = upload(j, "before")
        j = upload(j, "after")
        j = act(j, "submit", "alice")
        save(j, 409)
        act(j, "approve", "alice", code = 403)
        act(j, "request-changes", reason = "", code = 400)
        j = act(j, "request-changes", reason = "Clarify cooling result")
        assertEquals("CHANGES_REQUESTED", j["status"].asText())
        j = save(j)
        j = act(j, "submit", "alice")
        j = act(j, "approve")
        val id = j["id"].asText()
        val snapshot =
            db.queryForObject(
                "select snapshot::text from reports where job_id=? and revision=1",
                String::class.java,
                UUID.fromString(id),
            )
        save(j, 409)
        upload(j, "after", code = 409)
        j = act(j, "revise", reason = "Follow-up inspection")
        assertEquals(2, j["revision"].asInt())
        j = save(j)
        j = upload(j, "after")
        assertEquals(
            snapshot,
            db.queryForObject(
                "select snapshot::text from reports where job_id=? and revision=1",
                String::class.java,
                UUID.fromString(id),
            ),
        )
        j = act(j, "submit", "alice")
        j = act(j, "approve")
        mvc.perform(get("/api/jobs/$id/reports/1").with(actor("alice"))).andExpect(status().isOk)
        assertEquals(2, db.queryForObject("select count(*) from reports", Int::class.java))
        assertThrows(Exception::class.java) { db.update("update reports set approved_by='alice'") }
    }

    @Test
    fun `technician isolation private photos and csrf`() {
        var j = create()
        val id = j["id"].asText()
        j = upload(j, "before")
        val photo = j["units"][0]["before_id"].asText()
        mvc.perform(get("/api/jobs/$id").with(actor("bob"))).andExpect(status().isNotFound)
        mvc.perform(get("/api/photos/$photo").with(actor("bob"))).andExpect(status().isNotFound)
        mvc.perform(get("/api/photos/$photo")).andExpect(status().isUnauthorized)
        val image =
            mvc.perform(get("/api/photos/$photo").with(actor("alice")))
                .andExpect(status().isOk)
                .andReturn()
                .response
        assertEquals("no-store", image.getHeader("Cache-Control"))
        assertEquals("image/jpeg", image.contentType)
        assertNotNull(ImageIO.read(image.contentAsByteArray.inputStream()))
        upload(j, "after", name = "bob", code = 404)
        mvc.perform(
                post("/api/jobs/$id/submit")
                    .with(actor("alice"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":1}")
            )
            .andExpect(status().isForbidden)
        val list =
            mvc.perform(get("/api/jobs").with(actor("bob"))).andReturn().response.contentAsString
        assertEquals("[]", list)
        request(
            "/technicians",
            mapOf("username" to "unauthorized", "password" to "long-password-value"),
            "alice",
            403,
        )
    }

    @Test
    fun `retry is idempotent invalid images fail and stale edits conflict`() {
        val original = create()
        val key = UUID.randomUUID().toString()
        var j = upload(original, "before", key)
        val retry = upload(original, "before", key)
        assertEquals(j["version"], retry["version"])
        assertEquals(1, db.queryForObject("select count(*) from photos", Int::class.java))
        upload(j, "after", key, code = 409)
        upload(j, "after", bytes = "<svg onload='alert(1)'/>".toByteArray(), code = 400)
        save(original, 409)
        j = save(j)
        act(j, "approve", code = 409)
        act(j, "submit", "alice", code = 422)
    }

    @Test
    fun `required fields and excessive units rejected`() {
        request(
            "/jobs",
            mapOf(
                "customer" to " ",
                "location" to "x",
                "invoice" to "x",
                "technician" to "alice",
                "units" to listOf("u"),
            ),
            code = 400,
        )
        request(
            "/jobs",
            mapOf(
                "customer" to "x",
                "location" to "x",
                "invoice" to "x",
                "technician" to "alice",
                "units" to (1..31).map { "u$it" },
            ),
            code = 400,
        )
    }

    @Test
    fun `two simultaneous writes yield one success and one conflict`() {
        val j = create()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val tasks =
                (1..2).map {
                    pool.submit<Int> {
                        start.await()
                        mvc.perform(
                                put(
                                        "/api/jobs/${j["id"].asText()}/units/${j["units"][0]["id"].asText()}"
                                    )
                                    .with(actor("alice"))
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                        """{"version":0,"cleaned":true,"drainChecked":true,"coolingChecked":true,"actions":"Concurrent edit"}"""
                                    )
                            )
                            .andReturn()
                            .response
                            .status
                    }
                }
            start.countDown()
            assertEquals(
                listOf(200, 409),
                tasks.map { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }.sorted(),
            )
        } finally {
            pool.shutdownNow()
        }
    }

    @Autowired lateinit var jobs: Jobs
    @Autowired lateinit var transactions: org.springframework.transaction.PlatformTransactionManager

    @Test
    fun `job and deployment photo quotas reject atomically`() {
        val j = create()
        val auth =
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "alice",
                "unused",
                listOf(
                    org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TECH")
                ),
            )
        for ((jobLimit, totalLimit) in listOf(1L to 100000L, 100000L to 1L)) {
            val error =
                assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
                    org.springframework.transaction.support
                        .TransactionTemplate(transactions)
                        .execute {
                            Photos(jobs, jobLimit, totalLimit)
                                .upload(
                                    UUID.fromString(j["id"].asText()),
                                    UUID.fromString(j["units"][0]["id"].asText()),
                                    "before",
                                    0,
                                    UUID.randomUUID(),
                                    MockMultipartFile("file", "photo.png", "image/png", png()),
                                    auth,
                                )
                        }
                }
            assertEquals(507, error.statusCode.value())
            assertEquals(0, db.queryForObject("select count(*) from photos", Int::class.java))
            assertEquals(0, current(j["id"].asText())["version"].asInt())
        }
    }

    @Autowired lateinit var context: org.springframework.context.ApplicationContext

    @Test
    fun `no XSLT view rendering path is configured`() {
        assertTrue(
            context
                .getBeansOfType(org.springframework.web.servlet.view.xslt.XsltView::class.java)
                .isEmpty()
        )
        assertTrue(
            context
                .getBeansOfType(
                    org.springframework.web.servlet.view.xslt.XsltViewResolver::class.java
                )
                .isEmpty()
        )
    }
}
