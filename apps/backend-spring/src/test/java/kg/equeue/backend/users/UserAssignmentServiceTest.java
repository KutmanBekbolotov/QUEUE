package kg.equeue.backend.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.employeeserviceassignments.EmployeeServiceAssignmentEntity;
import kg.equeue.backend.employeeserviceassignments.EmployeeServiceAssignmentRepository;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentEntity;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserAssignmentServiceTest {

    private final EmployeeWindowAssignmentRepository windowAssignmentRepository = mock(EmployeeWindowAssignmentRepository.class);
    private final EmployeeServiceAssignmentRepository serviceAssignmentRepository = mock(EmployeeServiceAssignmentRepository.class);
    private final ServiceWindowRepository serviceWindowRepository = mock(ServiceWindowRepository.class);
    private final QueueServiceRepository queueServiceRepository = mock(QueueServiceRepository.class);
    private final DepartmentServiceRepository departmentServiceRepository = mock(DepartmentServiceRepository.class);
    private final UserAssignmentService userAssignmentService = new UserAssignmentService(
            windowAssignmentRepository,
            serviceAssignmentRepository,
            serviceWindowRepository,
            queueServiceRepository,
            departmentServiceRepository
    );

    @Test
    void replaceWindowCreatesActiveAssignment() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        ServiceWindowEntity window = mock(ServiceWindowEntity.class);
        when(window.getId()).thenReturn(windowId);
        when(window.getDepartmentId()).thenReturn(departmentId);
        when(window.isActive()).thenReturn(true);
        when(serviceWindowRepository.findWithLockById(windowId)).thenReturn(Optional.of(window));
        when(windowAssignmentRepository.findByUserIdAndServiceWindowId(userId, windowId)).thenReturn(Optional.empty());

        userAssignmentService.replaceWindow(userId, departmentId, windowId.toString());

        ArgumentCaptor<EmployeeWindowAssignmentEntity> captor = ArgumentCaptor.forClass(EmployeeWindowAssignmentEntity.class);
        verify(windowAssignmentRepository).deactivateActiveByUserId(userId);
        verify(windowAssignmentRepository).deactivateActiveByServiceWindowId(windowId);
        verify(windowAssignmentRepository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(assignment -> {
            assertThat(assignment.getUserId()).isEqualTo(userId);
            assertThat(assignment.getServiceWindowId()).isEqualTo(windowId);
            assertThat(assignment.isActive()).isTrue();
        });
    }

    @Test
    void replaceWindowWithBlankIdentifierClearsCurrentAssignment() {
        UUID userId = UUID.randomUUID();

        userAssignmentService.replaceWindow(userId, UUID.randomUUID(), " ");

        verify(windowAssignmentRepository).deactivateActiveByUserId(userId);
        verify(windowAssignmentRepository, never()).save(any(EmployeeWindowAssignmentEntity.class));
    }

    @Test
    void replaceServicesAcceptsCodesAndUuids() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID firstServiceId = UUID.randomUUID();
        UUID secondServiceId = UUID.randomUUID();
        QueueServiceEntity firstService = service(firstServiceId, "VS");
        QueueServiceEntity secondService = service(secondServiceId, "TS");
        when(serviceAssignmentRepository.findByUserId(userId)).thenReturn(List.of());
        when(queueServiceRepository.findByCodeIgnoreCase("VS")).thenReturn(Optional.of(firstService));
        when(queueServiceRepository.findById(secondServiceId)).thenReturn(Optional.of(secondService));
        when(departmentServiceRepository.existsByDepartmentIdAndServiceIdAndActiveTrue(departmentId, firstServiceId)).thenReturn(true);
        when(departmentServiceRepository.existsByDepartmentIdAndServiceIdAndActiveTrue(departmentId, secondServiceId)).thenReturn(true);

        userAssignmentService.replaceServices(userId, departmentId, Set.of("VS", secondServiceId.toString()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<EmployeeServiceAssignmentEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(serviceAssignmentRepository).saveAll(captor.capture());
        List<EmployeeServiceAssignmentEntity> saved = toList(captor.getValue());
        assertThat(saved).hasSize(2).allSatisfy(assignment -> {
            assertThat(assignment.getUserId()).isEqualTo(userId);
            assertThat(assignment.getDepartmentId()).isEqualTo(departmentId);
            assertThat(assignment.isActive()).isTrue();
        });
        assertThat(saved).extracting(EmployeeServiceAssignmentEntity::getServiceId)
                .containsExactlyInAnyOrder(firstServiceId, secondServiceId);
    }

    @Test
    void replaceServicesAcceptsDepartmentServiceRowKeys() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        QueueServiceEntity service = service(serviceId, "VS");
        when(serviceAssignmentRepository.findByUserId(userId)).thenReturn(List.of());
        when(queueServiceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(departmentServiceRepository.existsByDepartmentIdAndServiceIdAndActiveTrue(departmentId, serviceId)).thenReturn(true);

        userAssignmentService.replaceServices(userId, departmentId, Set.of(departmentId + ":" + serviceId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<EmployeeServiceAssignmentEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(serviceAssignmentRepository).saveAll(captor.capture());
        assertThat(toList(captor.getValue()))
                .singleElement()
                .satisfies(assignment -> assertThat(assignment.getServiceId()).isEqualTo(serviceId));
    }

    @Test
    void assignmentsReturnsWindowAndServiceDetails() {
        UUID userId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        EmployeeWindowAssignmentEntity windowAssignment = new EmployeeWindowAssignmentEntity();
        windowAssignment.setServiceWindowId(windowId);
        EmployeeServiceAssignmentEntity serviceAssignment = new EmployeeServiceAssignmentEntity();
        serviceAssignment.setServiceId(serviceId);
        QueueServiceEntity service = service(serviceId, "VS");
        ServiceWindowEntity window = mock(ServiceWindowEntity.class);
        when(window.getId()).thenReturn(windowId);
        when(window.isActive()).thenReturn(true);
        when(windowAssignmentRepository.findByUserIdAndActiveTrueOrderByAssignedAtDesc(userId))
                .thenReturn(List.of(windowAssignment));
        when(serviceWindowRepository.findById(windowId)).thenReturn(Optional.of(window));
        when(serviceAssignmentRepository.findByUserIdAndActiveTrueOrderByServiceIdAsc(userId))
                .thenReturn(List.of(serviceAssignment));
        when(queueServiceRepository.findAllById(any())).thenReturn(List.of(service));

        UserAssignmentService.AssignmentSnapshot result = userAssignmentService.assignments(userId);

        assertThat(result.windowId()).isEqualTo(windowId);
        assertThat(result.serviceIds()).containsExactly(serviceId);
        assertThat(result.serviceCodes()).containsExactly("VS");
    }

    private QueueServiceEntity service(UUID id, String code) {
        QueueServiceEntity service = mock(QueueServiceEntity.class);
        when(service.getId()).thenReturn(id);
        when(service.getCode()).thenReturn(code);
        when(service.isActive()).thenReturn(true);
        return service;
    }

    private <T> List<T> toList(Iterable<T> values) {
        java.util.ArrayList<T> result = new java.util.ArrayList<>();
        values.forEach(result::add);
        return result;
    }
}
