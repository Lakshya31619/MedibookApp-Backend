package com.medibook.payment.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // ✅ Swagger / OpenAPI — allow without auth
                .requestMatchers(
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/actuator/**"
                ).permitAll()

                // Admin-only
                .requestMatchers(
                    "/payments/admin/**",
                    "/payments/all",
                    "/payments/revenue/**"
                ).hasRole("ADMIN")

                // Provider or Admin
                .requestMatchers(
                    "/payments/earnings/**",
                    "/payments/confirm-cash/**",
                    "/payments/provider/**"
                ).hasAnyRole("PROVIDER", "ADMIN")

                // FIX: Razorpay refund endpoint — provider or admin only
                // create-order and verify are open to any authenticated user (patient)
                .requestMatchers("/payments/razorpay/refund/**")
                    .hasAnyRole("PROVIDER", "ADMIN")

                // All other /payments/razorpay/** routes (create-order, verify)
                // require authentication but no specific role — any logged-in patient qualifies
                .requestMatchers("/payments/razorpay/**").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}