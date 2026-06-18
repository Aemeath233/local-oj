package com.coderushoj.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlMinutes;
    private final long refreshTtlMinutes;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.ttl-minutes}") long ttlMinutes,
            @Value("${app.jwt.refresh-ttl-minutes:10080}") long refreshTtlMinutes
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMinutes = ttlMinutes;
        this.refreshTtlMinutes = refreshTtlMinutes;
    }

    public String issue(User user) {
        return issueToken(user, "access", ttlMinutes);
    }

    public String issueRefreshToken(User user) {
        return issueToken(user, "refresh", refreshTtlMinutes);
    }

    private String issueToken(User user, String tokenType, long minutes) {
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.getId());
            payload.put("username", user.getUsername());
            payload.put("role", user.getRole().name());
            payload.put("ver", user.getAuthTokenVersion() == null ? 0 : user.getAuthTokenVersion());
            payload.put("type", tokenType);
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", Instant.now().plusSeconds(minutes * 60).getEpochSecond());
            String body = encodeJson(payload);
            return header + "." + body + "." + sign(header + "." + body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue token", ex);
        }
    }

    public Optional<CurrentUser> parse(String token) {
        return parseInternal(token, "access");
    }

    public Optional<CurrentUser> parseRefreshToken(String token) {
        return parseInternal(token, "refresh");
    }

    private Optional<CurrentUser> parseInternal(String token, String expectedType) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            long exp = payload.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            
            if (payload.has("type")) {
                if (!expectedType.equals(payload.path("type").asText())) {
                    return Optional.empty();
                }
            } else if ("refresh".equals(expectedType)) {
                return Optional.empty();
            }

            Long id = payload.path("sub").asLong();
            String username = payload.path("username").asText();
            Role role = Role.valueOf(payload.path("role").asText());
            if (!payload.has("ver")) {
                return Optional.empty();
            }
            int tokenVersion = payload.path("ver").asInt();
            return Optional.of(new CurrentUser(id, username, role, tokenVersion));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String encodeJson(Object value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
