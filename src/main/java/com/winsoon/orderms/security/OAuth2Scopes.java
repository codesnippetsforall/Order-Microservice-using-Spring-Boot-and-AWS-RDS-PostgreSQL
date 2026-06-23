package com.winsoon.orderms.security;

/**
 * Cognito resource-server scope names (Phase 3).
 * Token claim: {@code orderms/read} → Spring authority {@code SCOPE_orderms/read}.
 */
public final class OAuth2Scopes {

    public static final String READ = "orderms/read";
    public static final String WRITE = "orderms/write";
    public static final String ADMIN = "orderms/admin";

    public static final String AUTHORITY_READ = "SCOPE_" + READ;
    public static final String AUTHORITY_WRITE = "SCOPE_" + WRITE;
    public static final String AUTHORITY_ADMIN = "SCOPE_" + ADMIN;

    private OAuth2Scopes() {
    }
}
