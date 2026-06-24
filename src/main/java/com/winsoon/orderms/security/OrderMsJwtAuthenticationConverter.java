package com.winsoon.orderms.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Builds Spring authorities from JWT {@code scope} claim and Cognito {@code cognito:groups}.
 * Groups/scopes are injected at login by the Cognito Pre Token Lambda (see deploy/setup-pretoken-lambda.sh).
 * Falls back to {@link CognitoGroupService} only when {@code resolve-groups-via-api} is enabled.
 */
@Component
public class OrderMsJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
    private final CognitoGroupService cognitoGroupService;

    public OrderMsJwtAuthenticationConverter(CognitoGroupService cognitoGroupService) {
        this.cognitoGroupService = cognitoGroupService;
        scopeConverter.setAuthorityPrefix("SCOPE_");
        delegate.setJwtGrantedAuthoritiesConverter(this::resolveAuthorities);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }

    private Collection<GrantedAuthority> resolveAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new LinkedHashSet<>(scopeConverter.convert(jwt));
        authorities.addAll(CognitoGroupAuthorities.fromGroups(resolveGroups(jwt)));
        return authorities;
    }

    private List<String> resolveGroups(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null && !groups.isEmpty()) {
            return groups;
        }

        String username = jwt.getClaimAsString("username");
        if (username == null || username.isBlank()) {
            username = jwt.getClaimAsString("cognito:username");
        }
        if (username == null || username.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(cognitoGroupService.resolveGroups(username));
    }
}
