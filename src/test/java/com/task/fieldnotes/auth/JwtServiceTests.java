package com.task.fieldnotes.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {
    private JwtService jwtService;
    private final ObjectMapper json = new ObjectMapper();
    private final String secret = "test-secret-key-at-least-256-bits-long-for-hmac-sha256-safety-123456";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, 900, json);
    }

    @Test
    void generatesAndValidatesValidJwt() {
        UUID userId = UUID.randomUUID();
        String email = "inspector@example.com";

        var generated = jwtService.generateAccessToken(userId, email);
        assertThat(generated.token()).isNotBlank();
        assertThat(generated.token().split("\\.")).hasSize(3);
        assertThat(generated.expiresInSeconds()).isEqualTo(900);
        assertThat(generated.expiresAt()).isAfter(Instant.now());

        var claims = jwtService.parseAndValidateToken(generated.token());
        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(userId);
        assertThat(claims.get().email()).isEqualTo(email);
        assertThat(claims.get().expiresAt().getEpochSecond()).isEqualTo(generated.expiresAt().getEpochSecond());
    }

    @Test
    void rejectsTamperedPayloadOrSignature() {
        UUID userId = UUID.randomUUID();
        String email = "inspector@example.com";
        var generated = jwtService.generateAccessToken(userId, email);
        String[] parts = generated.token().split("\\.");

        // Tamper signature
        String tamperedSigToken = parts[0] + "." + parts[1] + "." + parts[2] + "x";
        assertThat(jwtService.parseAndValidateToken(tamperedSigToken)).isEmpty();

        // Tamper payload
        String modifiedPayloadJson = "{\"sub\":\"" + UUID.randomUUID() + "\",\"email\":\"hacker@example.com\",\"exp\":" + (Instant.now().getEpochSecond() + 1000) + "}";
        String modifiedPayloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(modifiedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String tamperedPayloadToken = parts[0] + "." + modifiedPayloadB64 + "." + parts[2];
        assertThat(jwtService.parseAndValidateToken(tamperedPayloadToken)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "inspector@example.com";
        // Create expired payload directly
        Map<String, Object> payloadMap = Map.of(
                "sub", userId.toString(),
                "email", email,
                "iat", Instant.now().minusSeconds(1000).getEpochSecond(),
                "exp", Instant.now().minusSeconds(10).getEpochSecond()
        );
        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsString(payloadMap).getBytes(StandardCharsets.UTF_8));

        // Sign with same secret
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sigBytes = mac.doFinal((headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8));
        String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

        String expiredToken = headerB64 + "." + payloadB64 + "." + sigB64;
        assertThat(jwtService.parseAndValidateToken(expiredToken)).isEmpty();
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(jwtService.parseAndValidateToken(null)).isEmpty();
        assertThat(jwtService.parseAndValidateToken("")).isEmpty();
        assertThat(jwtService.parseAndValidateToken("invalid.token")).isEmpty();
        assertThat(jwtService.parseAndValidateToken("part1.part2.part3.part4")).isEmpty();
        assertThat(jwtService.parseAndValidateToken("notbase64.notbase64.notbase64")).isEmpty();
    }
}
