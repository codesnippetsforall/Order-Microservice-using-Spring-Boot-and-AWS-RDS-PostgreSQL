package com.winsoon.orderms.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 — Cognito group membership drives API access (no Lambda required).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CognitoGroupSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderMsJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void customerGroup_getOrders_returns200() throws Exception {
        mockMvc.perform(get("/orders")
                        .with(authentication(jwtAuthenticationConverter.convert(customerToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void customerGroup_putCustomer_returns403() throws Exception {
        mockMvc.perform(put("/customers/1")
                        .with(authentication(jwtAuthenticationConverter.convert(customerToken())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"User","email":"test@example.com"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGroup_putCustomer_allowedPastSecurity() throws Exception {
        mockMvc.perform(put("/customers/1")
                        .with(authentication(jwtAuthenticationConverter.convert(adminToken())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"User","email":"test@example.com"}
                                """))
                .andExpect(status().is2xxSuccessful());
    }

    private static Jwt customerToken() {
        return Jwt.withTokenValue("customer-token")
                .header("alg", "none")
                .subject("customer-sub")
                .claim("username", "customer@example.com")
                .claim("cognito:groups", List.of("CUSTOMER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static Jwt adminToken() {
        return Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject("admin-sub")
                .claim("username", "admin@example.com")
                .claim("cognito:groups", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
