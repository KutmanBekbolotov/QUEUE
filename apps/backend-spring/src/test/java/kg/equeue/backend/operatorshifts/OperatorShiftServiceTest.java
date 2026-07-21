package kg.equeue.backend.operatorshifts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.auth.AuthenticatedPrincipal;
import kg.equeue.backend.common.DepartmentScopeService;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.users.UserAssignmentService;
import kg.equeue.backend.users.UserDepartmentScopeRepository;
import kg.equeue.backend.users.UserEntity;
import kg.equeue.backend.users.UserRepository;
import kg.equeue.backend.users.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class OperatorShiftServiceTest {

    private final OperatorShiftRepository operatorShiftRepository = mock(OperatorShiftRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FakeUserDepartmentScopeRepository userDepartmentScopeRepository = new FakeUserDepartmentScopeRepository();
    private final FakeUserAssignmentService userAssignmentService = new FakeUserAssignmentService();
    private final ServiceWindowRepository serviceWindowRepository = mock(ServiceWindowRepository.class);
    private final FakeTicketService ticketService = new FakeTicketService();
    private final FakeDepartmentScopeService departmentScopeService = new FakeDepartmentScopeService();
    private final AuditService auditService = new AuditService(null);
    private final OperatorShiftService operatorShiftService = new OperatorShiftService(
            operatorShiftRepository,
            userRepository,
            userDepartmentScopeRepository,
            userAssignmentService,
            serviceWindowRepository,
            ticketService,
            departmentScopeService,
            auditService
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardForNewOperatorReturnsAssignedWindowWithoutOpenShift() {
        UUID adminId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        authenticate(adminId, "ROLE_ADMIN", "USER_READ", "TICKET_READ");

        when(userRepository.findDetailedById(operatorId)).thenReturn(Optional.of(operator(operatorId)));
        userDepartmentScopeRepository.primaryDepartmentId = departmentId;
        when(operatorShiftRepository.findFirstByOperatorIdAndStatusOrderByOpenedAtDesc(operatorId, OperatorShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        userAssignmentService.snapshot = new UserAssignmentService.AssignmentSnapshot(windowId, List.of(), List.of());
        when(serviceWindowRepository.findById(windowId)).thenReturn(Optional.of(window(windowId, departmentId)));

        var response = operatorShiftService.dashboard(operatorId);

        assertThat(response.operatorId()).isEqualTo(operatorId);
        assertThat(response.shift()).isNull();
        assertThat(response.window()).isNotNull();
        assertThat(response.window().id()).isEqualTo(windowId);
        assertThat(response.window().employeeId()).isEqualTo(operatorId);
        assertThat(response.activeTicket()).isNull();
        assertThat(response.generatedAt()).isNotNull();
        assertThat(departmentScopeService.requiredDepartmentId).isEqualTo(departmentId);
        assertThat(ticketService.operatorId).isEqualTo(operatorId);
        assertThat(ticketService.windowId).isEqualTo(windowId);
    }

    private UserEntity operator(UUID operatorId) {
        RoleEntity role = new RoleEntity();
        role.setCode("OPERATOR");
        UserEntity operator = new UserEntity();
        operator.setId(operatorId);
        operator.setUsername("operator");
        operator.setStatus(UserStatus.ACTIVE);
        operator.setRoles(Set.of(role));
        return operator;
    }

    private ServiceWindowEntity window(UUID windowId, UUID departmentId) {
        ServiceWindowEntity window = new ServiceWindowEntity();
        ReflectionTestUtils.setField(window, "id", windowId);
        ReflectionTestUtils.setField(window, "createdAt", Instant.now());
        ReflectionTestUtils.setField(window, "updatedAt", Instant.now());
        window.setDepartmentId(departmentId);
        window.setHallId(UUID.randomUUID());
        window.setCode("01");
        window.setDisplayName("Window 01");
        window.setActive(true);
        return window;
    }

    private void authenticate(UUID userId, String... authorities) {
        var granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var principal = new AuthenticatedPrincipal(userId, "admin", 1, UserStatus.ACTIVE, granted);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, granted));
    }

    private static class FakeDepartmentScopeService extends DepartmentScopeService {
        UUID requiredDepartmentId;

        FakeDepartmentScopeService() {
            super(null);
        }

        @Override
        public void requireDepartmentAccess(UUID departmentId) {
            requiredDepartmentId = departmentId;
        }
    }

    private static class FakeUserDepartmentScopeRepository extends UserDepartmentScopeRepository {
        UUID primaryDepartmentId;

        FakeUserDepartmentScopeRepository() {
            super(null);
        }

        @Override
        public UUID primaryDepartmentId(UUID userId) {
            return primaryDepartmentId;
        }
    }

    private static class FakeUserAssignmentService extends UserAssignmentService {
        AssignmentSnapshot snapshot = AssignmentSnapshot.empty();

        FakeUserAssignmentService() {
            super(null, null, null, null, null);
        }

        @Override
        public AssignmentSnapshot assignments(UUID userId, UUID departmentId) {
            return snapshot;
        }
    }

    private static class FakeTicketService extends TicketService {
        UUID operatorId;
        UUID windowId;

        FakeTicketService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public TicketResponse activeTicketForOperatorDashboard(UUID operatorId, UUID windowId) {
            this.operatorId = operatorId;
            this.windowId = windowId;
            return null;
        }
    }
}
