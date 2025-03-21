package com.pfizer.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

public class PingFederateTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(PingFederateTokenProvider.class);
    
    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    
    private String accessToken;
    private Instant expiresAt;

    public PingFederateTokenProvider(RestTemplate restTemplate, String clientId, String clientSecret, String tokenUrl) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
    }

    public synchronized String getAccessToken() {
        if (accessToken == null || Instant.now().isAfter(expiresAt)) {
            fetchNewToken();
        }
        return accessToken;
    }

    private void fetchNewToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, requestEntity, Map.class);
            Map<String, Object> tokenResponse = response.getBody();

            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                this.accessToken = (String) tokenResponse.get("access_token");
                int expiresIn = tokenResponse.containsKey("expires_in") ? 
                    (Integer) tokenResponse.get("expires_in") : 3600;
                
                // Set expiration time slightly earlier to ensure we don't use an expired token
                this.expiresAt = Instant.now().plusSeconds(expiresIn - 60);
                
                logger.debug("Successfully obtained new access token, expires at: {}", expiresAt);
            } else {
                logger.error("Failed to obtain access token from PingFederate");
                throw new RuntimeException("Failed to obtain access token");
            }
        } catch (Exception e) {
            logger.error("Error fetching token from PingFederate", e);
            throw new RuntimeException("Error fetching token: " + e.getMessage(), e);
        }
    }
}