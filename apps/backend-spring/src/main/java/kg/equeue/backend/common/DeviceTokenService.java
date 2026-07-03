package kg.equeue.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class DeviceTokenService {

    public String requireRawToken(HttpServletRequest request) {
        String token = request.getHeader("X-Device-Token");
        if (token == null || token.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }
        }
        if (token == null || token.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "DEVICE_TOKEN_REQUIRED", "Device token is required");
        }
        return token;
    }

    public boolean matches(String rawToken, String expectedHash) {
        return hash(rawToken).equals(expectedHash);
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash device token", ex);
        }
    }
}

