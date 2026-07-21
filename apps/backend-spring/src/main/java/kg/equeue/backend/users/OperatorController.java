package kg.equeue.backend.users;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.operatorshifts.OperatorShiftDtos.OpenShiftRequest;
import kg.equeue.backend.operatorshifts.OperatorShiftDtos.OperatorDashboardResponse;
import kg.equeue.backend.operatorshifts.OperatorShiftDtos.ShiftResponse;
import kg.equeue.backend.operatorshifts.OperatorShiftService;
import kg.equeue.backend.users.dto.UserResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operators")
@Tag(name = "Operators")
public class OperatorController {

    private final UserService userService;
    private final OperatorShiftService operatorShiftService;

    public OperatorController(UserService userService, OperatorShiftService operatorShiftService) {
        this.userService = userService;
        this.operatorShiftService = operatorShiftService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    List<UserResponse> list() {
        return userService.operators();
    }

    @PostMapping("/{operatorId}/shifts/open")
    @PreAuthorize("hasAuthority('WINDOW_OPEN')")
    ShiftResponse openShift(@PathVariable UUID operatorId,
                            @RequestBody(required = false) OpenShiftRequest request,
                            HttpServletRequest httpRequest) {
        return operatorShiftService.open(operatorId, request, httpRequest);
    }

    @PostMapping("/{operatorId}/shifts/current/close")
    @PreAuthorize("hasAuthority('WINDOW_CLOSE')")
    ShiftResponse closeCurrentShift(@PathVariable UUID operatorId, HttpServletRequest httpRequest) {
        return operatorShiftService.closeCurrent(operatorId, httpRequest);
    }

    @GetMapping("/{operatorId}/dashboard")
    @PreAuthorize("hasAuthority('TICKET_READ') or hasAuthority('USER_READ')")
    OperatorDashboardResponse dashboard(@PathVariable UUID operatorId) {
        return operatorShiftService.dashboard(operatorId);
    }
}
