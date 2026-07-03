package kg.equeue.backend.roles;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.roles.dto.AssignRolePermissionsRequest;
import kg.equeue.backend.roles.dto.CreateRoleRequest;
import kg.equeue.backend.roles.dto.RoleResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    List<RoleResponse> list() {
        return roleService.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    RoleResponse create(@Valid @RequestBody CreateRoleRequest request, HttpServletRequest httpRequest) {
        return roleService.create(request, httpRequest);
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_PERMISSION')")
    RoleResponse assignPermissions(@PathVariable UUID id,
                                   @Valid @RequestBody AssignRolePermissionsRequest request,
                                   HttpServletRequest httpRequest) {
        return roleService.assignPermissions(id, request, httpRequest);
    }
}
