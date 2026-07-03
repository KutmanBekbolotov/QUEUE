package kg.equeue.backend.permissions;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kg.equeue.backend.permissions.dto.PermissionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    List<PermissionResponse> list() {
        return permissionService.list();
    }
}
