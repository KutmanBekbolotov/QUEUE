package kg.equeue.backend.users;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.CurrentUser;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.roles.RoleRepository;
import kg.equeue.backend.users.dto.AssignUserRolesRequest;
import kg.equeue.backend.users.dto.CreateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserStatusRequest;
import kg.equeue.backend.users.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Set<String> DEVICE_ROLE_CODES = Set.of("TERMINAL_DEVICE", "TV_DEVICE");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserDepartmentScopeRepository departmentScopeRepository;
    private final UserAssignmentService userAssignmentService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       UserDepartmentScopeRepository departmentScopeRepository,
                       UserAssignmentService userAssignmentService,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentScopeRepository = departmentScopeRepository;
        this.userAssignmentService = userAssignmentService;
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
        requireDepartmentExists(request.departmentId());
        HashSet<RoleEntity> roles = loadRoles(request.roleCodes());
        requireOperatorDepartment(roles, request.departmentId());
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(roles);
        UserEntity saved = userRepository.saveAndFlush(user);
        departmentScopeRepository.replacePrimaryDepartment(saved.getId(), request.departmentId());
        if (request.windowId() != null) {
            userAssignmentService.replaceWindow(saved.getId(), request.departmentId(), request.windowId());
        }
        if (request.serviceIds() != null) {
            userAssignmentService.replaceServices(saved.getId(), request.departmentId(), request.serviceIds());
        }
        auditService.write("USER_CREATE", "USER", saved.getId(), "{\"username\":\"" + saved.getUsername() + "\"}", httpRequest);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, HttpServletRequest httpRequest) {
        UserEntity user = userRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));

        boolean invalidateTokens = false;
        UUID effectiveDepartmentId = departmentScopeRepository.primaryDepartmentId(id);
        boolean departmentChanged = false;
        if (request.username() != null) {
            if (request.username().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "USERNAME_REQUIRED", "Username is required");
            }
            if (!request.username().equalsIgnoreCase(user.getUsername())
                    && userRepository.existsByUsernameIgnoreCase(request.username())) {
                throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Username already exists");
            }
            if (!request.username().equals(user.getUsername())) {
                user.setUsername(request.username());
                invalidateTokens = true;
            }
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            invalidateTokens = true;
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.roleCodes() != null) {
            HashSet<RoleEntity> roles = loadRoles(request.roleCodes());
            boolean rolesChanged = !roleCodes(user.getRoles()).equals(roleCodes(roles));
            user.setRoles(roles);
            invalidateTokens = invalidateTokens || rolesChanged;
        }
        if (request.departmentId() != null) {
            requireDepartmentExists(request.departmentId());
            departmentChanged = !Objects.equals(effectiveDepartmentId, request.departmentId());
            effectiveDepartmentId = request.departmentId();
            invalidateTokens = invalidateTokens || departmentChanged;
        }
        requireOperatorDepartment(user.getRoles(), effectiveDepartmentId);
        if (departmentChanged) {
            departmentScopeRepository.replacePrimaryDepartment(id, request.departmentId());
        }
        if (request.windowId() != null || departmentChanged) {
            userAssignmentService.replaceWindow(id, effectiveDepartmentId, request.windowId());
        }
        if (request.serviceIds() != null || departmentChanged) {
            userAssignmentService.replaceServices(id, effectiveDepartmentId,
                    request.serviceIds() == null ? Set.of() : request.serviceIds());
        }
        if (invalidateTokens) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        UserEntity saved = userRepository.save(user);
        auditService.write("USER_UPDATE", "USER", saved.getId(), "{\"user\":\"updated\"}", httpRequest);
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
    public void delete(UUID id, HttpServletRequest httpRequest) {
        UserEntity user = userRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
        if (id.equals(CurrentUser.idOrNull())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DEACTIVATE_SELF", "Current user cannot deactivate own account");
        }
        user.setStatus(UserStatus.DISABLED);
        user.setTokenVersion(user.getTokenVersion() + 1);
        UserEntity saved = userRepository.save(user);
        auditService.write("USER_DELETE", "USER", saved.getId(), "{\"status\":\"DISABLED\"}", httpRequest);
    }

    @Transactional
    public UserResponse assignRoles(UUID id, AssignUserRolesRequest request, HttpServletRequest httpRequest) {
        UserEntity user = userRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
        HashSet<RoleEntity> roles = loadRoles(request.roleCodes());
        requireOperatorDepartment(roles, departmentScopeRepository.primaryDepartmentId(id));
        user.setRoles(roles);
        user.setTokenVersion(user.getTokenVersion() + 1);
        UserEntity saved = userRepository.save(user);
        auditService.write("USER_ROLE_ASSIGN", "USER", saved.getId(), "{\"roles\":\"updated\"}", httpRequest);
        return toResponse(saved);
    }

    private HashSet<RoleEntity> loadRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return new HashSet<>();
        }
        if (roleCodes.stream().anyMatch(DEVICE_ROLE_CODES::contains)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_ROLE_NOT_ASSIGNABLE",
                    "Device roles cannot be assigned to users; provision the device through /api/v1/devices");
        }
        List<RoleEntity> roles = roleRepository.findByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "One or more roles were not found");
        }
        return new HashSet<>(roles);
    }

    private void requireDepartmentExists(UUID departmentId) {
        if (departmentId != null && !departmentScopeRepository.departmentExists(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEPARTMENT_NOT_FOUND", "Department was not found");
        }
    }

    private void requireOperatorDepartment(Set<RoleEntity> roles, UUID departmentId) {
        boolean operator = roles.stream().anyMatch(role -> "OPERATOR".equals(role.getCode()));
        if (operator && departmentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATOR_DEPARTMENT_REQUIRED",
                    "departmentId is required for operator users");
        }
    }

    private Set<String> roleCodes(Set<RoleEntity> roles) {
        return roles.stream().map(RoleEntity::getCode).collect(java.util.stream.Collectors.toSet());
    }

    private UserResponse toResponse(UserEntity user) {
        UserEntity detailed = user.getRoles().isEmpty() ? userRepository.findDetailedById(user.getId()).orElse(user) : user;
        UUID departmentId = departmentScopeRepository.primaryDepartmentId(detailed.getId());
        UserAssignmentService.AssignmentSnapshot assignments = userAssignmentService.assignments(detailed.getId(), departmentId);
        return new UserResponse(
                detailed.getId(),
                detailed.getUsername(),
                detailed.getFullName(),
                detailed.getEmail(),
                detailed.getPhone(),
                departmentId,
                assignments.windowId(),
                assignments.serviceIds(),
                assignments.serviceCodes(),
                detailed.getStatus(),
                detailed.getTokenVersion(),
                detailed.getRoles().stream().map(RoleEntity::getCode).sorted().toList(),
                detailed.getCreatedAt(),
                detailed.getUpdatedAt()
        );
    }
}
