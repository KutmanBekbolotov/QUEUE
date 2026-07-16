package kg.equeue.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kg.equeue.backend.audit.LoginAuditLogEntity;
import kg.equeue.backend.audit.LoginAuditLogRepository;
import kg.equeue.backend.auth.dto.AuthResponse;
import kg.equeue.backend.auth.dto.LoginRequest;
import kg.equeue.backend.auth.dto.MeResponse;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.config.SecurityProperties;
import kg.equeue.backend.permissions.PermissionEntity;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.users.UserDepartmentScopeRepository;
import kg.equeue.backend.users.UserAssignmentService;
import kg.equeue.backend.users.UserEntity;
import kg.equeue.backend.users.UserRepository;
import kg.equeue.backend.users.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAuditLogRepository loginAuditLogRepository;
    private final UserDepartmentScopeRepository departmentScopeRepository;
    private final UserAssignmentService userAssignmentService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAuditLogRepository loginAuditLogRepository,
                       UserDepartmentScopeRepository departmentScopeRepository,
                       UserAssignmentService userAssignmentService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAuditLogRepository = loginAuditLogRepository;
        this.departmentScopeRepository = departmentScopeRepository;
        this.userAssignmentService = userAssignmentService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        UserEntity user = userRepository.findByUsernameIgnoreCase(request.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLogin(request.username(), user, false, "BAD_CREDENTIALS", httpRequest);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid username or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditLogin(request.username(), user, false, "USER_NOT_ACTIVE", httpRequest);
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_NOT_ACTIVE", "User is not active");
        }

        RefreshTokenPair refresh = createRefreshToken(user, clientIp(httpRequest));
        auditLogin(user.getUsername(), user, true, null, httpRequest);
        return response(user, refresh.rawToken());
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, HttpServletRequest httpRequest) {
        String hash = hash(rawRefreshToken);
        RefreshTokenEntity existing = refreshTokenRepository.findWithLockByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token"));
        Instant now = Instant.now();
        if (!existing.isActive(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is expired or revoked");
        }

        UserEntity user = userRepository.findDetailedById(existing.getUser().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token user no longer exists"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_NOT_ACTIVE", "User is not active");
        }

        RefreshTokenPair replacement = createRefreshToken(user, clientIp(httpRequest));
        existing.setRevokedAt(now);
        existing.setReplacedByHash(replacement.hash());
        refreshTokenRepository.save(existing);
        return response(user, replacement.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);
        refreshTokenRepository.findWithLockByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedPrincipal principal) {
        UserEntity user = userRepository.findDetailedById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Authenticated user was not found"));
        UUID departmentId = departmentScopeRepository.primaryDepartmentId(user.getId());
        UserAssignmentService.AssignmentSnapshot assignments = userAssignmentService.assignments(user.getId(), departmentId);
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                departmentId,
                assignments.windowId(),
                assignments.serviceIds(),
                assignments.serviceCodes(),
                user.getStatus(),
                roleCodes(user),
                permissionCodes(user)
        );
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash token", ex);
        }
    }

    private AuthResponse response(UserEntity user, String rawRefreshToken) {
        String accessToken = jwtService.createAccessToken(user);
        Instant expiresAt = Instant.now().plus(securityProperties.getJwt().getAccessTokenTtl());
        UUID departmentId = departmentScopeRepository.primaryDepartmentId(user.getId());
        UserAssignmentService.AssignmentSnapshot assignments = userAssignmentService.assignments(user.getId(), departmentId);
        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                expiresAt,
                roleCodes(user),
                permissionCodes(user),
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                departmentId,
                assignments.windowId(),
                assignments.serviceIds(),
                assignments.serviceCodes(),
                user.getStatus()
        );
    }

    private RefreshTokenPair createRefreshToken(UserEntity user, String ip) {
        String rawToken = randomToken();
        String hash = hash(rawToken);
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUser(user);
        entity.setTokenHash(hash);
        entity.setExpiresAt(Instant.now().plus(securityProperties.getJwt().getRefreshTokenTtl()));
        entity.setCreatedByIp(ip);
        refreshTokenRepository.save(entity);
        return new RefreshTokenPair(rawToken, hash);
    }

    private String randomToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<String> roleCodes(UserEntity user) {
        return user.getRoles().stream()
                .map(RoleEntity::getCode)
                .sorted()
                .toList();
    }

    private List<String> permissionCodes(UserEntity user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet())
                .stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private void auditLogin(String username, UserEntity user, boolean success, String reason, HttpServletRequest request) {
        LoginAuditLogEntity audit = new LoginAuditLogEntity();
        audit.setUsername(username);
        audit.setUserId(user == null ? null : user.getId());
        audit.setSuccess(success);
        audit.setReason(reason);
        audit.setIp(clientIp(request));
        audit.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        loginAuditLogRepository.save(audit);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record RefreshTokenPair(String rawToken, String hash) {
    }
}
