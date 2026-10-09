package com.task.fieldnotes.auth;

import com.task.fieldnotes.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.time.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {
    public record User(UUID id, String email) {}
    public record Session(
            UUID userId,
            String email,
            String token,
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Instant expiresAt,
            Instant refreshExpiresAt
    ) {}
    private record Account(UUID id, String email, String passwordHash) {}

    private final JdbcClient db;
    private final PasswordEncoder passwords;
    private final JwtService jwtService;
    private final int refreshDays;
    private final SecureRandom random = new SecureRandom();

    public AuthService(
            JdbcClient db,
            PasswordEncoder passwords,
            JwtService jwtService,
            @Value("${fieldnotes.jwt.refresh-token-expiration-days:${fieldnotes.session-days:30}}") int days
    ) {
        this.db = db;
        this.passwords = passwords;
        this.jwtService = jwtService;
        this.refreshDays = days;
    }

    @Transactional
    public Session register(String email, String password) {
        validatePassword(password);
        email = normalize(email);
        UUID id = UUID.randomUUID();
        db.sql("INSERT INTO users(id,email,password_hash) VALUES(:id,:email,:hash)")
                .param("id", id).param("email", email).param("hash", passwords.encode(password)).update();
        return issue(new User(id, email));
    }

    @Transactional
    public Session login(String email, String password) {
        validatePassword(password);
        var account = db.sql("SELECT id,email,password_hash FROM users WHERE email=:email")
                .param("email", normalize(email)).query(Account.class).optional();
        // A dummy hash equalizes BCrypt work for an unknown account.
        String hash = account.map(Account::passwordHash).orElse("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        boolean matches = passwords.matches(password, hash);
        if (account.isEmpty() || !matches) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        return issue(new User(account.get().id(), account.get().email()));
    }

    @Transactional
    public Session refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        String hash = Digests.sha256(refreshToken);
        var user = db.sql("SELECT u.id, u.email FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=:hash AND s.expires_at>CURRENT_TIMESTAMP")
                .param("hash", hash).query(User.class).optional();
        if (user.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        // Rotate refresh token: invalidate old refresh token and issue a fresh pair
        db.sql("DELETE FROM sessions WHERE token_hash=:hash").param("hash", hash).update();
        return issue(user.get());
    }

    public Optional<User> authenticate(String token) {
        var claims = jwtService.parseAndValidateToken(token);
        if (claims.isEmpty()) return Optional.empty();

        String hash = Digests.sha256(token);
        boolean revoked = db.sql("SELECT 1 FROM revoked_tokens WHERE token_hash=:hash AND expires_at>CURRENT_TIMESTAMP")
                .param("hash", hash).query(Integer.class).optional().isPresent();
        if (revoked) return Optional.empty();

        return Optional.of(new User(claims.get().userId(), claims.get().email()));
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        var claims = jwtService.parseAndValidateToken(token);
        if (claims.isPresent()) {
            UUID userId = claims.get().userId();
            Instant exp = claims.get().expiresAt();
            String hash = Digests.sha256(token);
            db.sql("INSERT INTO revoked_tokens(token_hash, user_id, expires_at) VALUES(:hash, :userId, :expires) ON CONFLICT (token_hash) DO NOTHING")
                    .param("hash", hash).param("userId", userId)
                    .param("expires", OffsetDateTime.ofInstant(exp, ZoneOffset.UTC)).update();
            db.sql("DELETE FROM sessions WHERE user_id=:userId").param("userId", userId).update();
        } else {
            String hash = Digests.sha256(token);
            db.sql("DELETE FROM sessions WHERE token_hash=:hash").param("hash", hash).update();
        }
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        String hash = Digests.sha256(refreshToken);
        db.sql("DELETE FROM sessions WHERE token_hash=:hash").param("hash", hash).update();
    }

    @Transactional
    public void logoutUser(UUID userId) {
        if (userId == null) return;
        db.sql("DELETE FROM sessions WHERE user_id=:userId").param("userId", userId).update();
    }

    private Session issue(User user) {
        var jwt = jwtService.generateAccessToken(user.id(), user.email());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant refreshExpiry = Instant.now().plus(Duration.ofDays(refreshDays));

        db.sql("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(:hash,:id,:expires)")
                .param("hash", Digests.sha256(refreshToken)).param("id", user.id())
                .param("expires", OffsetDateTime.ofInstant(refreshExpiry, ZoneOffset.UTC)).update();

        return new Session(
                user.id(),
                user.email(),
                jwt.token(),
                jwt.token(),
                refreshToken,
                "Bearer",
                jwt.expiresInSeconds(),
                jwt.expiresAt(),
                refreshExpiry
        );
    }

    private String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    private void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.bad("Password must be at least 10 characters and at most 72 UTF-8 bytes");
    }
}
