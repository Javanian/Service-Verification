package com.javanian.proof

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@SpringBootApplication
class Application {
    @Bean fun passwords() = BCryptPasswordEncoder(12)

    @Bean
    fun bootstrap(db: JdbcTemplate, passwords: BCryptPasswordEncoder, env: Environment) =
        ApplicationRunner {
            if (
                db.queryForObject(
                    "select count(*) from accounts where role='OWNER'",
                    Long::class.java,
                ) == 0L
            ) {
                val name = env.getRequiredProperty("OWNER_USERNAME")
                val password = env.getRequiredProperty("OWNER_PASSWORD")
                require(
                    name.matches(Regex("[a-zA-Z0-9._-]{3,60}")) &&
                        password.length in 14..72 &&
                        password.toByteArray().size <= 72
                ) {
                    "Set a valid OWNER_USERNAME and OWNER_PASSWORD (14–72 characters)"
                }
                db.update(
                    "insert into accounts values (?,?, 'OWNER')",
                    name,
                    passwords.encode(password),
                )
            }
        }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
