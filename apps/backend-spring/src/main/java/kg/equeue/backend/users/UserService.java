package kg.equeue.backend.users;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.CurrentUser;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.roles.RoleRepository;
import kg.equeue.backend.users.dto.AssignUserRolesRequest;
import kg.equeue.backend.users.dto.CreateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserStatusRequest;
import kg.equeue.backend.users.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> operators() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream().anyMatch(role -> "OPERATOR".equals(role.getCode())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userRepository.findDetailedById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, HttpServletRequest httpRequest) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Username already exists");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(loadRoles(request.roleCodes()));
        UserEntity saved = userRepository.save(user);
        auditService.write("USER_CREATE", "USER", saved.getId(), "{\"username\":\"" + saved.getUsername() + "\"}", httpRequest);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UpdateUserStatusRequest request, HttpServletRequest httpRequest) {
        UserEntity user = userRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
        if (id.equals(CurrentUser.idOrNull()) && request.status() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DEACTIVATE_SELF", "Current user cannot deactivate own account");
        }
        user.setStatus(request.status());
        user.setTokenVersion(user.getTokenVersion() + 1);
        UserEntity saved = userRepository.save(user);
        auditService.write("USER_STATUS_UPDATE", "USER", saved.getId(), "{\"status\":\"" + saved.getStatus() + "\"}", httpRequest);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse assignRoles(UUID id, AssignUserRolesRequest request, HttpServletRequest httpRequest) {
        UserEntity user = userRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
        user.setRoles(loadRoles(request.roleCodes()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        UserEntity saved = userRepository.save(user);
        auditService.write("USER_ROLE_ASSIGN", "USER", saved.getId(), "{\"roles\":\"updated\"}", httpRequest);
        return toResponse(saved);
    }

    private HashSet<RoleEntity> loadRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return new HashSet<>();
        }
        List<RoleEntity> roles = roleRepository.findByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "One or more roles were not found");
        }
        return new HashSet<>(roles);
    }

    private UserResponse toResponse(UserEntity user) {
        UserEntity detailed = user.getRoles().isEmpty() ? userRepository.findDetailedById(user.getId()).orElse(user) : user;
        return new UserResponse(
                detailed.getId(),
                detailed.getUsername(),
                detailed.getFullName(),
                detailed.getEmail(),
                detailed.getPhone(),
                detailed.getStatus(),
                detailed.getTokenVersion(),
                detailed.getRoles().stream().map(RoleEntity::getCode).sorted().toList(),
                detailed.getCreatedAt(),
                detailed.getUpdatedAt()
        );
    }
}
