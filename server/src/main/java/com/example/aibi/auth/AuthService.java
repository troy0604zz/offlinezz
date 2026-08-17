package com.example.aibi.auth;

import com.example.aibi.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserRecord user = findUserByUsername(request.username()).orElseThrow(this::invalidCredentials);
        if (!user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }

        String token = createToken();
        Instant expiresAt = Instant.now().plus(properties.sessionHours(), ChronoUnit.HOURS);
        jdbc.sql("DELETE FROM auth_session WHERE expires_at < CURRENT_TIMESTAMP OR revoked=1").update();
        jdbc.sql("INSERT INTO auth_session(id,user_id,token_hash,expires_at,revoked) VALUES(?,?,?,?,0)")
                .params(UUID.randomUUID().toString(), user.id(), hashToken(token), Timestamp.from(expiresAt))
                .update();
        return new LoginResponse(token, "Bearer", expiresAt, profile(user));
    }

    public Optional<AuthenticatedUser> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT u.id,u.username,u.display_name,u.password_hash,u.enabled
                        FROM auth_session s JOIN app_user u ON u.id=s.user_id
                        WHERE s.token_hash=? AND s.revoked=0 AND s.expires_at>CURRENT_TIMESTAMP AND u.enabled=1
                        """)
                .param(hashToken(token))
                .query(this::mapUser)
                .optional()
                .map(user -> new AuthenticatedUser(profile(user)));
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            jdbc.sql("UPDATE auth_session SET revoked=1 WHERE token_hash=?").param(hashToken(token)).update();
        }
    }

    private Optional<UserRecord> findUserByUsername(String username) {
        return jdbc.sql("SELECT id,username,display_name,password_hash,enabled FROM app_user WHERE LOWER(username)=LOWER(?)")
                .param(username.trim()).query(this::mapUser).optional();
    }

    private UserRecord mapUser(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UserRecord(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getInt("enabled") == 1);
    }

    private UserProfile profile(UserRecord user) {
        Set<String> roles = new LinkedHashSet<>(jdbc.sql("""
                        SELECT r.code FROM app_role r JOIN app_user_role ur ON ur.role_id=r.id
                        WHERE ur.user_id=? ORDER BY r.code
                        """).param(user.id()).query(String.class).list());
        Set<String> permissions = new LinkedHashSet<>(jdbc.sql("""
                        SELECT DISTINCT p.code FROM app_permission p
                        JOIN app_role_permission rp ON rp.permission_id=p.id
                        JOIN app_user_role ur ON ur.role_id=rp.role_id
                        WHERE ur.user_id=? ORDER BY p.code
                        """).param(user.id()).query(String.class).list());
        return new UserProfile(user.id(), user.username(), user.displayName(), roles, permissions);
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 SHA-256", ex);
        }
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "用户名或密码不正确", HttpStatus.UNAUTHORIZED);
    }

    private record UserRecord(long id, String username, String displayName, String passwordHash, boolean enabled) {
    }
}
