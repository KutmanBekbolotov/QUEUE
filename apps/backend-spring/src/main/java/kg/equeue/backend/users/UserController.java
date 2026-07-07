package kg.equeue.backend.users;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.users.dto.AssignUserRolesRequest;
import kg.equeue.backend.users.dto.CreateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserStatusRequest;
import kg.equeue.backend.users.dto.UserResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    UserResponse create(@Valid @RequestBody CreateUserRequest request, HttpServletRequest httpRequest) {
        return userService.create(request, httpRequest);
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request, HttpServletRequest httpRequest) {
        return userService.update(id, request, httpRequest);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_BLOCK') or hasAuthority('USER_UPDATE')")
    UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request, HttpServletRequest httpRequest) {
        return userService.updateStatus(id, request, httpRequest);
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    UserResponse assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignUserRolesRequest request, HttpServletRequest httpRequest) {
        return userService.assignRoles(id, request, httpRequest);
    }
}
