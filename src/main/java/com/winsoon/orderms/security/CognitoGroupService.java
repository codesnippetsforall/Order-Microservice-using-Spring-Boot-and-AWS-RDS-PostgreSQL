package com.winsoon.orderms.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves Cognito group membership when {@code cognito:groups} is not present in the access token.
 * Results are cached to limit calls to Cognito per user.
 */
@Slf4j
@Service
public class CognitoGroupService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;
    private final boolean resolveViaApi;
    private final Map<String, CachedGroups> cache = new ConcurrentHashMap<>();
    private final Duration cacheTtl;

    public CognitoGroupService(
            CognitoIdentityProviderClient cognitoClient,
            @Value("${orderms.security.cognito.user-pool-id:}") String userPoolId,
            @Value("${orderms.security.cognito.resolve-groups-via-api:true}") boolean resolveViaApi,
            @Value("${orderms.security.cognito.group-cache-ttl-minutes:5}") long cacheTtlMinutes) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
        this.resolveViaApi = resolveViaApi;
        this.cacheTtl = Duration.ofMinutes(cacheTtlMinutes);
    }

    public List<String> resolveGroups(String username) {
        if (!resolveViaApi || username == null || username.isBlank() || userPoolId == null || userPoolId.isBlank()) {
            return List.of();
        }

        CachedGroups cached = cache.get(username);
        if (cached != null && cached.isValid(cacheTtl)) {
            return cached.groups();
        }

        try {
            List<String> groups = cognitoClient.adminListGroupsForUser(
                            AdminListGroupsForUserRequest.builder()
                                    .userPoolId(userPoolId)
                                    .username(username)
                                    .build())
                    .groups()
                    .stream()
                    .map(GroupType::groupName)
                    .toList();
            cache.put(username, new CachedGroups(groups, Instant.now()));
            log.debug("Resolved Cognito groups for {}: {}", username, groups);
            return groups;
        } catch (Exception ex) {
            log.warn("Failed to resolve Cognito groups for {}: {}", username, ex.getMessage());
            return List.of();
        }
    }

    private record CachedGroups(List<String> groups, Instant loadedAt) {
        boolean isValid(Duration ttl) {
            return loadedAt.plus(ttl).isAfter(Instant.now());
        }
    }
}
