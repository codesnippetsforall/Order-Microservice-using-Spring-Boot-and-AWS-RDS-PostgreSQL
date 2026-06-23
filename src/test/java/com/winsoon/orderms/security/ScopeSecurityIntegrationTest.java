package com.winsoon.orderms.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 — verifies scope-based authorization (403 vs 200).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScopeSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void orders_withValidJwtButNoScope_returns403() throws Exception {
        mockMvc.perform(get("/orders").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void orders_withReadScope_returns200() throws Exception {
        mockMvc.perform(get("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_READ))))
                .andExpect(status().isOk());
    }

    @Test
    void orders_withWriteScopeOnly_getReturns200() throws Exception {
        mockMvc.perform(get("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_WRITE))))
                .andExpect(status().isOk());
    }

    @Test
    void createOrder_withReadScopeOnly_returns403() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_READ)))
                        .contentType("application/json")
                        .content("""
                                {"customerId":1,"totalAmount":10.00,"shippingAddress":"test"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withWriteScope_returns201() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_WRITE)))
                        .contentType("application/json")
                        .content("""
                                {"customerId":1,"totalAmount":10.00,"shippingAddress":"test"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteOrder_withWriteScope_returns403() throws Exception {
        mockMvc.perform(delete("/orders/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_WRITE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOrder_withAdminScope_passesAuthorization() throws Exception {
        // Reaches controller (not 403); app returns 400 when order missing
        mockMvc.perform(delete("/orders/99999")
                        .with(jwt().authorities(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customers_withReadScope_returns200() throws Exception {
        mockMvc.perform(get("/customers")
                        .with(jwt().authorities(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_READ))))
                .andExpect(status().isOk());
    }
}
