package com.winsoon.orderms.config;

import com.winsoon.orderms.security.OrderMsJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
public class CognitoConfig {

    @Bean
    CognitoIdentityProviderClient cognitoIdentityProviderClient(
            @org.springframework.beans.factory.annotation.Value("${AWS_REGION:ap-south-2}") String region) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }
}
