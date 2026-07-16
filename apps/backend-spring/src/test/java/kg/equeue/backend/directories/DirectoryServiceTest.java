package kg.equeue.backend.directories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.DepartmentScopeService;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.employeeserviceassignments.EmployeeServiceAssignmentRepository;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentEntity;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentRepository;
import kg.equeue.backend.halls.HallRepository;
import kg.equeue.backend.officerooms.OfficeRoomRepository;
import kg.equeue.backend.regions.RegionRepository;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import kg.equeue.backend.users.UserAssignmentService;
import kg.equeue.backend.users.UserDepartmentScopeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DirectoryServiceTest {

    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final OfficeRoomRepository officeRoomRepository = mock(OfficeRoomRepository.class);
    private final HallRepository hallRepository = mock(HallRepository.class);
    private final ServiceWindowRepository serviceWindowRepository = mock(ServiceWindowRepository.class);
    private final ServiceCategoryRepository serviceCategoryRepository = mock(ServiceCategoryRepository.class);
    private final QueueServiceRepository queueServiceRepository = mock(QueueServiceRepository.class);
    private final DepartmentServiceRepository departmentServiceRepository = mock(DepartmentServiceRepository.class);
    private final EmployeeServiceAssignmentRepository employeeServiceAssignmentRepository = mock(EmployeeServiceAssignmentRepository.class);
    private final EmployeeWindowAssignmentRepository employeeWindowAssignmentRepository = mock(EmployeeWindowAssignmentRepository.class);
    private final UserAssignmentService userAssignmentService = mock(UserAssignmentService.class);
    private final UserDepartmentScopeRepository userDepartmentScopeRepository = mock(UserDepartmentScopeRepository.class);
    private final FakeDepartmentScopeService departmentScopeService = new FakeDepartmentScopeService();
    private final CapturingAuditService auditService = new CapturingAuditService();
    private final DirectoryService directoryService = new DirectoryService(
            regionRepository,
            departmentRepository,
            officeRoomRepository,
            hallRepository,
            serviceWindowRepository,
            serviceCategoryRepository,
            queueServiceRepository,
            departmentServiceRepository,
            employeeServiceAssignmentRepository,
            employeeWindowAssignmentRepository,
            userAssignmentService,
            userDepartmentScopeRepository,
            departmentScopeService,
            auditService
    );

    @Test
    void deleteWindowHardDeletesWindowInsteadOfMarkingInactive() {
        UUID windowId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ServiceWindowEntity window = new ServiceWindowEntity();
        ReflectionTestUtils.setField(window, "id", windowId);
        window.setDepartmentId(departmentId);
        window.setCode("01");
        window.setDisplayName("Window 01");
        when(serviceWindowRepository.findById(windowId)).thenReturn(Optional.of(window));

        directoryService.deleteWindow(windowId, null);

        assertThat(departmentScopeService.requiredDepartmentId).isEqualTo(departmentId);
        verify(serviceWindowRepository).delete(window);
        verify(serviceWindowRepository, never()).save(any(ServiceWindowEntity.class));
        verify(employeeWindowAssignmentRepository, never()).findByServiceWindowId(any());
        assertThat(auditService.action).isEqualTo("WINDOW_DELETE");
        assertThat(auditService.entityType).isEqualTo("WINDOW");
        assertThat(auditService.entityId).isEqualTo(windowId);
        assertThat(auditService.newValue).isEqualTo("{\"deleted\":\"true\"}");
    }

    @Test
    void assignEmployeeToWindowUsesCanonicalAssignmentAndReturnsEmployee() {
        UUID windowId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        ServiceWindowEntity window = new ServiceWindowEntity();
        ReflectionTestUtils.setField(window, "id", windowId);
        window.setDepartmentId(departmentId);
        window.setCode("01");
        window.setDisplayName("Window 01");
        EmployeeWindowAssignmentEntity assignment = new EmployeeWindowAssignmentEntity();
        assignment.setUserId(employeeId);
        assignment.setServiceWindowId(windowId);
        when(serviceWindowRepository.findById(windowId)).thenReturn(Optional.of(window));
        when(userDepartmentScopeRepository.primaryDepartmentId(employeeId)).thenReturn(departmentId);
        when(employeeWindowAssignmentRepository.findFirstByServiceWindowIdAndActiveTrueOrderByAssignedAtDesc(windowId))
                .thenReturn(Optional.of(assignment));

        var response = directoryService.assignEmployeeToWindow(
                windowId,
                new DirectoryDtos.AssignEmployeeToWindowRequest(employeeId),
                null
        );

        verify(userAssignmentService).replaceWindow(employeeId, departmentId, windowId.toString());
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.id()).isEqualTo(windowId);
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

    private static class CapturingAuditService extends AuditService {
        String action;
        String entityType;
        UUID entityId;
        String newValue;

        CapturingAuditService() {
            super(null);
        }

        @Override
        public void write(String action, String entityType, UUID entityId, String newValue, HttpServletRequest request) {
            this.action = action;
            this.entityType = entityType;
            this.entityId = entityId;
            this.newValue = newValue;
        }
    }
}
