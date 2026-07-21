package kg.equeue.backend.operatorshifts;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.CurrentUser;
import kg.equeue.backend.common.DepartmentScopeService;
import kg.equeue.backend.directories.DirectoryDtos.WindowResponse;
import kg.equeue.backend.operatorshifts.OperatorShiftDtos.OpenShiftRequest;
import kg.equeue.backend.operatorshifts.OperatorShiftDtos.OperatorDashboardResponse;
import kg.equeue.backend.operatorshifts.OperatorShiftDtos.ShiftResponse;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.users.UserAssignmentService;
import kg.equeue.backend.users.UserDepartmentScopeRepository;
import kg.equeue.backend.users.UserEntity;
import kg.equeue.backend.users.UserRepository;
import kg.equeue.backend.users.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorShiftService {

    private final OperatorShiftRepository operatorShiftRepository;
    private final UserRepository userRepository;
    private final UserDepartmentScopeRepository userDepartmentScopeRepository;
    private final UserAssignmentService userAssignmentService;
    private final ServiceWindowRepository serviceWindowRepository;
    private final TicketService ticketService;
    private final DepartmentScopeService departmentScopeService;
    private final AuditService auditService;

    public OperatorShiftService(OperatorShiftRepository operatorShiftRepository,
                                UserRepository userRepository,
                                UserDepartmentScopeRepository userDepartmentScopeRepository,
                                UserAssignmentService userAssignmentService,
                                ServiceWindowRepository serviceWindowRepository,
                                TicketService ticketService,
                                DepartmentScopeService departmentScopeService,
                                AuditService auditService) {
        this.operatorShiftRepository = operatorShiftRepository;
        this.userRepository = userRepository;
        this.userDepartmentScopeRepository = userDepartmentScopeRepository;
        this.userAssignmentService = userAssignmentService;
        this.serviceWindowRepository = serviceWindowRepository;
        this.ticketService = ticketService;
        this.departmentScopeService = departmentScopeService;
        this.auditService = auditService;
    }

    @Transactional
    public ShiftResponse open(UUID operatorId, OpenShiftRequest request, HttpServletRequest httpRequest) {
        UserEntity operator = operatorOrThrow(operatorId);
        UUID primaryDepartmentId = primaryDepartmentOrThrow(operatorId);
        UUID departmentId = request != null && request.departmentId() != null
                ? request.departmentId()
                : primaryDepartmentId;
        if (!primaryDepartmentId.equals(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATOR_DEPARTMENT_MISMATCH",
                    "Shift department must match operator primary department");
        }
        requireCanManage(operatorId, departmentId);

        UUID windowId = request != null ? request.windowId() : null;
        if (windowId == null) {
            windowId = userAssignmentService.assignments(operatorId, departmentId).windowId();
        }
        ServiceWindowEntity window = windowId == null ? null : validateWindow(windowId, departmentId);

        OperatorShiftEntity existing = operatorShiftRepository
                .findOpenByOperatorIdForUpdate(operatorId, OperatorShiftStatus.OPEN)
                .orElse(null);
        if (existing != null) {
            if (sameShift(existing, departmentId, windowId)) {
                return response(existing);
            }
            throw conflict("OPERATOR_SHIFT_ALREADY_OPEN", "Operator already has an open shift");
        }
        if (window != null) {
            operatorShiftRepository.findOpenByWindowIdForUpdate(window.getId(), OperatorShiftStatus.OPEN)
                    .filter(shift -> !shift.getOperatorId().equals(operatorId))
                    .ifPresent(shift -> {
                        throw conflict("WINDOW_SHIFT_ALREADY_OPEN", "Window already has an open operator shift");
                    });
        }

        OperatorShiftEntity shift = new OperatorShiftEntity();
        shift.setOperatorId(operator.getId());
        shift.setDepartmentId(departmentId);
        shift.setWindowId(window == null ? null : window.getId());
        shift.setStatus(OperatorShiftStatus.OPEN);
        shift.setOpenedAt(Instant.now());
        try {
            OperatorShiftEntity saved = operatorShiftRepository.saveAndFlush(shift);
            auditService.write("OPERATOR_SHIFT_OPEN", "OPERATOR_SHIFT", saved.getId(), simpleJson("status", "OPEN"), httpRequest);
            return response(saved);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("OPERATOR_SHIFT_ALREADY_OPEN", "Operator or window already has an open shift");
        }
    }

    @Transactional
    public ShiftResponse closeCurrent(UUID operatorId, HttpServletRequest httpRequest) {
        operatorOrThrow(operatorId);
        OperatorShiftEntity shift = operatorShiftRepository
                .findOpenByOperatorIdForUpdate(operatorId, OperatorShiftStatus.OPEN)
                .orElseThrow(() -> conflict("OPERATOR_SHIFT_NOT_OPEN", "Operator does not have an open shift"));
        requireCanManage(operatorId, shift.getDepartmentId());
        shift.setStatus(OperatorShiftStatus.CLOSED);
        shift.setClosedAt(Instant.now());
        OperatorShiftEntity saved = operatorShiftRepository.saveAndFlush(shift);
        auditService.write("OPERATOR_SHIFT_CLOSE", "OPERATOR_SHIFT", saved.getId(), simpleJson("status", "CLOSED"), httpRequest);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public OperatorDashboardResponse dashboard(UUID operatorId) {
        operatorOrThrow(operatorId);
        UUID departmentId = primaryDepartmentOrThrow(operatorId);
        requireCanView(operatorId, departmentId);
        OperatorShiftEntity shift = operatorShiftRepository
                .findFirstByOperatorIdAndStatusOrderByOpenedAtDesc(operatorId, OperatorShiftStatus.OPEN)
                .orElse(null);

        UUID windowId = shift == null ? userAssignmentService.assignments(operatorId, departmentId).windowId() : shift.getWindowId();
        WindowResponse window = windowId == null ? null : windowResponse(validateWindow(windowId, departmentId), operatorId);
        TicketResponse activeTicket = ticketService.activeTicketForOperatorDashboard(operatorId, windowId);
        return new OperatorDashboardResponse(operatorId, shift == null ? null : response(shift), window, activeTicket, Instant.now());
    }

    private boolean sameShift(OperatorShiftEntity shift, UUID departmentId, UUID windowId) {
        return shift.getDepartmentId().equals(departmentId)
                && java.util.Objects.equals(shift.getWindowId(), windowId);
    }

    private UserEntity operatorOrThrow(UUID operatorId) {
        UserEntity operator = userRepository.findDetailedById(operatorId)
                .orElseThrow(() -> notFound("OPERATOR_NOT_FOUND", "Operator was not found"));
        if (operator.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATOR_INACTIVE", "Operator is inactive");
        }
        boolean operatorRole = operator.getRoles().stream().anyMatch(role -> "OPERATOR".equals(role.getCode()));
        if (!operatorRole) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_IS_NOT_OPERATOR", "User is not an operator");
        }
        return operator;
    }

    private UUID primaryDepartmentOrThrow(UUID operatorId) {
        UUID departmentId = userDepartmentScopeRepository.primaryDepartmentId(operatorId);
        if (departmentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATOR_DEPARTMENT_REQUIRED",
                    "Operator must be assigned to a department");
        }
        return departmentId;
    }

    private ServiceWindowEntity validateWindow(UUID windowId, UUID departmentId) {
        ServiceWindowEntity window = serviceWindowRepository.findById(windowId)
                .orElseThrow(() -> notFound("WINDOW_NOT_FOUND", "Window was not found"));
        if (!departmentId.equals(window.getDepartmentId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WINDOW_DEPARTMENT_MISMATCH",
                    "Window does not belong to operator department");
        }
        if (!window.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WINDOW_INACTIVE", "Window is inactive");
        }
        return window;
    }

    private void requireCanManage(UUID operatorId, UUID departmentId) {
        if (CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN")) {
            return;
        }
        departmentScopeService.requireDepartmentAccess(departmentId);
        if (!operatorId.equals(CurrentUser.idOrNull()) && !CurrentUser.hasAuthority("ROLE_DEPARTMENT_MANAGER")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OPERATOR_SCOPE_DENIED", "Operator is outside of current user scope");
        }
    }

    private void requireCanView(UUID operatorId, UUID departmentId) {
        if (operatorId.equals(CurrentUser.idOrNull())
                || CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN")
                || CurrentUser.hasAuthority("ROLE_DEPARTMENT_MANAGER")
                || CurrentUser.hasAuthority("USER_READ")) {
            departmentScopeService.requireDepartmentAccess(departmentId);
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "OPERATOR_SCOPE_DENIED", "Operator is outside of current user scope");
    }

    private WindowResponse windowResponse(ServiceWindowEntity window, UUID operatorId) {
        return new WindowResponse(
                window.getId(),
                window.getDepartmentId(),
                window.getHallId(),
                operatorId,
                window.getCode(),
                window.getDisplayName(),
                window.isActive(),
                window.isOpen(),
                window.getStatus(),
                window.getCreatedAt(),
                window.getUpdatedAt()
        );
    }

    private ShiftResponse response(OperatorShiftEntity entity) {
        return new ShiftResponse(
                entity.getId(),
                entity.getOperatorId(),
                entity.getDepartmentId(),
                entity.getWindowId(),
                entity.getStatus(),
                entity.getOpenedAt(),
                entity.getClosedAt()
        );
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of("status", OperatorShiftStatus.OPEN));
    }

    private String simpleJson(String key, String value) {
        return "{\"" + key + "\":\"" + value + "\"}";
    }
}
