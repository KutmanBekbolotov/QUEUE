package kg.equeue.backend.permissions;

import java.util.List;
import kg.equeue.backend.permissions.dto.PermissionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> list() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(),
                        permission.getCode(),
                        permission.getDescription(),
                        permission.getCreatedAt()))
                .toList();
    }
}

