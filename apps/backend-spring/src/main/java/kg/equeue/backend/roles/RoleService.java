package kg.equeue.backend.roles;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.permissions.PermissionEntity;
import kg.equeue.backend.permissions.PermissionRepository;
import kg.equeue.backend.roles.dto.AssignRolePermissionsRequest;
import kg.equeue.backend.roles.dto.CreateRoleRequest;
import kg.equeue.backend.roles.dto.RoleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request, HttpServletRequest httpRequest) {
        if (roleRepository.findByCode(request.code()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_EXISTS", "Role already exists");
        }
        RoleEntity role = new RoleEntity();
        role.setCode(request.code());
        role.setName(request.name());
        role.setSystemRole(false);
        role.setPermissions(loadPermissions(request.permissionCodes()));
        RoleEntity saved = roleRepository.save(role);
        auditService.write("ROLE_CREATE", "ROLE", saved.getId(), "{\"code\":\"" + saved.getCode() + "\"}", httpRequest);
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse assignPermissions(UUID id, AssignRolePermissionsRequest request, HttpServletRequest httpRequest) {
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Role was not found"));
        role.setPermissions(loadPermissions(request.permissionCodes()));
        RoleEntity saved = roleRepository.save(role);
        auditService.write("ROLE_ASSIGN_PERMISSION", "ROLE", saved.getId(), "{\"permissions\":\"updated\"}", httpRequest);
        return toResponse(saved);
    }

    private HashSet<PermissionEntity> loadPermissions(java.util.Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return new HashSet<>();
        }
        List<PermissionEntity> permissions = permissionRepository.findByCodeIn(permissionCodes);
        if (permissions.size() != permissionCodes.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERMISSION_NOT_FOUND", "One or more permissions were not found");
        }
        return new HashSet<>(permissions);
    }

    private RoleResponse toResponse(RoleEntity role) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.isSystemRole(),
                role.getPermissions().stream().map(PermissionEntity::getCode).sorted().toList(),
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}

