package com.javanian.proof

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.SecurityFilterChain

@Configuration
class Security {
    @Bean
    fun users(db: JdbcTemplate) = UserDetailsService { name ->
        db.query(
                "select * from accounts where username=?",
                { rs, _ ->
                    User.withUsername(rs.getString("username"))
                        .password(rs.getString("password"))
                        .roles(rs.getString("role"))
                        .build()
                },
                name,
            )
            .firstOrNull() ?: throw UsernameNotFoundException("Invalid credentials")
    }

    @Bean
    fun chain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers(
                        "/api/session",
                        "/api/login",
                        "/",
                        "/index.html",
                        "/*.js",
                        "/*.css",
                        "/favicon.ico",
                    )
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .formLogin {
                it.loginProcessingUrl("/api/login")
                    .successHandler { _, res, _ -> res.status = 204 }
                    .failureHandler { _, res, _ -> res.status = 401 }
            }
            .logout {
                it.logoutUrl("/api/logout").logoutSuccessHandler { _, res, _ -> res.status = 204 }
            }
            .exceptionHandling { it.authenticationEntryPoint { _, res, _ -> res.sendError(401) } }
            .headers {
                it.contentSecurityPolicy { c ->
                    c.policyDirectives(
                        "default-src 'self'; img-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
                    )
                }
            }
        return http.build()
    }
}
