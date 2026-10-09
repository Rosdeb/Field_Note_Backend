package com.task.fieldnotes.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    public record JwtClaims(UUID userId, String email, Instant issuedAt, Instant expiresAt) {}
    public record GeneratedToken(String token, Instant expiresAt, long expiresInSeconds) {}

    private static final String HEADER_B64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secretKeyBytes;
    private final Duration accessTokenDuration;
    private final ObjectMapper json;

    public JwtService(
            @Value("${fieldnotes.jwt.secret:fieldnotes-default-jwt-secret-key-must-be-at-least-256-bits-long-for-security}") String secret,
            @Value("${fieldnotes.jwt.access-token-expiration-seconds:900}") long accessExpirySeconds,
            ObjectMapper json
    ) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenDuration = Duration.ofSeconds(accessExpirySeconds);
        this.json = json;
    }

    public GeneratedToken generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenDuration);
        try {
            Map<String, Object> payloadMap = Map.of(
                    "sub", userId.toString(),
                    "email", email,
                    "iat", now.getEpochSecond(),
                    "exp", expiry.getEpochSecond()
            );
            String payloadJson = json.writeValueAsString(payloadMap);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String dataToSign = HEADER_B64 + "." + payloadB64;
            String signatureB64 = sign(dataToSign);
            String token = dataToSign + "." + signatureB64;
            return new GeneratedToken(token, expiry, accessTokenDuration.toSeconds());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate JWT", ex);
        }
    }

    public Optional<JwtClaims> parseAndValidateToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();

        try {
            String dataToSign = parts[0] + "." + parts[1];
            byte[] expectedSig = signBytes(dataToSign);
            byte[] actualSig = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return Optional.empty();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            var tree = json.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            if (tree == null) return Optional.empty();
            var subNode = tree.path("sub");
            var emailNode = tree.path("email");
            var expNode = tree.path("exp");
            if (subNode.isMissingNode() || subNode.isNull() || emailNode.isMissingNode() || emailNode.isNull() || expNode.isMissingNode() || expNode.isNull()) {
                return Optional.empty();
            }

            long exp = expNode.asLong();
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString(subNode.asText());
            String email = emailNode.asText();
            Instant expInstant = Instant.ofEpochSecond(exp);
            var iatNode = tree.path("iat");
            Instant iatInstant = (!iatNode.isMissingNode() && !iatNode.isNull()) ? Instant.ofEpochSecond(iatNode.asLong()) : Instant.now();
            return Optional.of(new JwtClaims(userId, email, iatInstant, expInstant));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String sign(String data) throws Exception {
        byte[] sigBytes = signBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
    }

    private byte[] signBytes(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
