package com.pfizer.ai.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

public class PingFederateAuthenticator {
    private static final Logger logger = LoggerFactory.getLogger(PingFederateAuthenticator.class);
    
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final RestTemplate restTemplate;
    
    private String accessToken;
    private Instant tokenExpiry;
    
    public PingFederateAuthenticator(String clientId, String clientSecret, String tokenUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.restTemplate = new RestTemplate();
        
        logger.info("PingFederate authenticator initialized with client ID: {}, token URL: {}", 
                    clientId, tokenUrl);
    }
    
    public synchronized String getAccessToken() {
        if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
            logger.debug("Token expired or not initialized, refreshing PingFederate access token");
            refreshToken();
        }
        return accessToken;
    }
    
    private void refreshToken() {
        logger.debug("Requesting new token from PingFederate");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);  // PingFederate typically uses Basic auth for the token endpoint
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TokenResponse token = response.getBody();
                this.accessToken = token.accessToken;
                // Token expires_in is typically in seconds, subtract 60s as a buffer
                this.tokenExpiry = Instant.now().plusSeconds(token.expiresIn - 60);
                logger.info("Successfully obtained new PingFederate access token, valid until: {}", tokenExpiry);
            } else {
                logger.error("Failed to obtain access token, status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to obtain access token from PingFederate");
            }
        } catch (Exception e) {
            logger.error("Error during token acquisition from PingFederate", e);
            throw new RuntimeException("Failed to obtain access token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Response structure for OAuth2 token endpoint
     */
    private static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        
        @JsonProperty("token_type")
        private String tokenType;
        
        @JsonProperty("expires_in")
        private int expiresIn;
    }
}