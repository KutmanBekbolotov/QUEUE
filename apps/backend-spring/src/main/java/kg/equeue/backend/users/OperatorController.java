package kg.equeue.backend.users;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kg.equeue.backend.users.dto.UserResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operators")
@Tag(name = "Operators")
public class OperatorController {

    private final UserService userService;

    public OperatorController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    List<UserResponse> list() {
        return userService.operators();
    }
}
