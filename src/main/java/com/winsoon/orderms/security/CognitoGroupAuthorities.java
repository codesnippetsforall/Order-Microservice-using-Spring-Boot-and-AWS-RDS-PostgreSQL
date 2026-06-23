package com.winsoon.orderms.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps Cognito user pool groups to the same authorities used by scope-based rules.
 */
public final class CognitoGroupAuthorities {

    public static final String GROUP_ADMIN = "ADMIN";
    public static final String GROUP_SALES = "SALES";
    public static final String GROUP_CUSTOMER = "CUSTOMER";

    private CognitoGroupAuthorities() {
    }

    public static Collection<GrantedAuthority> fromGroups(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (groups.contains(GROUP_ADMIN)) {
            authorities.add(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_READ));
            authorities.add(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_WRITE));
            authorities.add(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_ADMIN));
        } else if (groups.contains(GROUP_SALES)) {
            authorities.add(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_READ));
            authorities.add(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_WRITE));
        } else if (groups.contains(GROUP_CUSTOMER)) {
            authorities.add(new SimpleGrantedAuthority(OAuth2Scopes.AUTHORITY_READ));
        }
        return new ArrayList<>(authorities);
    }
}
