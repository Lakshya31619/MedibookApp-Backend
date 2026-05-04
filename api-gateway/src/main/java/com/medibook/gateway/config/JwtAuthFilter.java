package com.medibook.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/oauth2",
            "/login/oauth2",        // OAuth2 callback from Google
            "/oauth2",              // OAuth2 redirect URIs
            "/actuator",            // health check endpoints
            "/eureka",              // Eureka dashboard
            "/swagger-ui",          // Swagger UI
            "/swagger-ui.html",     // Swagger UI entry point
            "/v3/api-docs",         // OpenAPI docs (all services)
            "/webjars",             // Swagger UI static assets

            "/api/providers",                   // GET /api/providers (list all) + /{id}
            "/api/providers/search",            // GET /api/providers/search
            "/api/providers/specialization",    // GET /api/providers/specialization/{spec}
            "/api/providers/location",          // GET /api/providers/location

            "/api/reviews/provider",            // GET /api/reviews/provider/{id}
            "/api/reviews/rating",              // GET /api/reviews/rating/{id}
            "/api/reviews/summary",             // GET /api/reviews/summary/{id}

            "/api/slots/available",              // GET /api/slots/available/{providerId}
            "/api/slots/provider"               // GET /api/slots/provider/{providerId}/available
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Allow public endpoints through without token check
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        if (isPublic) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            return unauthorizedResponse(exchange);
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            log.warn("Invalid JWT token for path: {}", path);
            return unauthorizedResponse(exchange);
        }

        String username = jwtUtil.extractUsername(token);
        String role     = jwtUtil.extractRole(token);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Auth-Username", username)
                .header("X-Auth-Role",     role != null ? role : "")
                .build();

        log.debug("Authenticated user '{}' with role '{}' -> {}", username, role, path);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}