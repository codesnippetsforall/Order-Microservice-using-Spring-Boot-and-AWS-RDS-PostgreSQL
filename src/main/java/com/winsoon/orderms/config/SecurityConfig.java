package com.winsoon.orderms.config;

import com.winsoon.orderms.security.OAuth2Scopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * OAuth2 Resource Server — Phase 2 (JWT) + Phase 3 (scopes + Cognito groups).
 * Public: health, Swagger. API: scope-based rules on orders/customers.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**"
    };

    private static final String[] API_READ_PATHS = {
            "/orders/**",
            "/customers/**"
    };

    private static final String[] API_WRITE_PATHS = {
            "/orders/**",
            "/customers/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, API_READ_PATHS)
                            .hasAnyAuthority(
                                    OAuth2Scopes.AUTHORITY_READ,
                                    OAuth2Scopes.AUTHORITY_WRITE,
                                    OAuth2Scopes.AUTHORITY_ADMIN)
                        .requestMatchers(HttpMethod.POST, API_WRITE_PATHS)
                            .hasAnyAuthority(
                                    OAuth2Scopes.AUTHORITY_WRITE,
                                    OAuth2Scopes.AUTHORITY_ADMIN)
                        .requestMatchers(HttpMethod.PUT, API_WRITE_PATHS)
                            .hasAnyAuthority(
                                    OAuth2Scopes.AUTHORITY_WRITE,
                                    OAuth2Scopes.AUTHORITY_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, API_WRITE_PATHS)
                            .hasAuthority(OAuth2Scopes.AUTHORITY_ADMIN)
                        .requestMatchers("/actuator/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter)));

        return http.build();
    }
}
