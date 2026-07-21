package kg.equeue.backend.users;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.employeeserviceassignments.EmployeeServiceAssignmentEntity;
import kg.equeue.backend.employeeserviceassignments.EmployeeServiceAssignmentRepository;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentEntity;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAssignmentService {

    private final EmployeeWindowAssignmentRepository windowAssignmentRepository;
    private final EmployeeServiceAssignmentRepository serviceAssignmentRepository;
    private final ServiceWindowRepository serviceWindowRepository;
    private final QueueServiceRepository queueServiceRepository;
    private final DepartmentServiceRepository departmentServiceRepository;

    public UserAssignmentService(EmployeeWindowAssignmentRepository windowAssignmentRepository,
                                 EmployeeServiceAssignmentRepository serviceAssignmentRepository,
                                 ServiceWindowRepository serviceWindowRepository,
                                 QueueServiceRepository queueServiceRepository,
                                 DepartmentServiceRepository departmentServiceRepository) {
        this.windowAssignmentRepository = windowAssignmentRepository;
        this.serviceAssignmentRepository = serviceAssignmentRepository;
        this.serviceWindowRepository = serviceWindowRepository;
        this.queueServiceRepository = queueServiceRepository;
        this.departmentServiceRepository = departmentServiceRepository;
    }

    @Transactional(readOnly = true)
    public AssignmentSnapshot assignments(UUID userId) {
        return assignments(userId, null);
    }

    @Transactional(readOnly = true)
    public AssignmentSnapshot assignments(UUID userId, UUID departmentId) {
        UUID windowId = windowAssignmentRepository
                .findByUserIdAndActiveTrueOrderByAssignedAtDesc(userId)
                .stream()
                .map(EmployeeWindowAssignmentEntity::getServiceWindowId)
                .map(serviceWindowRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(ServiceWindowEntity::isActive)
                .filter(window -> departmentId == null || departmentId.equals(window.getDepartmentId()))
                .map(ServiceWindowEntity::getId)
                .findFirst()
                .orElse(null);

        List<EmployeeServiceAssignmentEntity> serviceAssignments = departmentId == null
                ? serviceAssignmentRepository.findByUserIdAndActiveTrueOrderByServiceIdAsc(userId)
                : serviceAssignmentRepository.findByUserIdAndDepartmentIdAndActiveTrueOrderByServiceIdAsc(userId, departmentId);
        List<UUID> assignedServiceIds = serviceAssignments.stream()
                .map(EmployeeServiceAssignmentEntity::getServiceId)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        Map<UUID, QueueServiceEntity> activeServicesById = new LinkedHashMap<>();
        queueServiceRepository.findAllById(assignedServiceIds).stream()
                .filter(QueueServiceEntity::isActive)
                .filter(service -> departmentId == null
                        || departmentServiceRepository.existsByDepartmentIdAndServiceIdAndActiveTrue(departmentId, service.getId()))
                .forEach(service -> activeServicesById.put(service.getId(), service));
        List<UUID> serviceIds = assignedServiceIds.stream()
                .filter(activeServicesById::containsKey)
                .toList();
        List<String> serviceCodes = serviceIds.stream()
                .map(activeServicesById::get)
                .map(QueueServiceEntity::getCode)
                .filter(code -> code != null)
                .toList();
        return new AssignmentSnapshot(windowId, serviceIds, serviceCodes);
    }

    @Transactional
    public void replaceWindow(UUID userId, UUID departmentId, String windowIdentifier) {
        String normalized = normalize(windowIdentifier);
        ServiceWindowEntity window = null;
        if (normalized != null) {
            requireDepartment(departmentId);
            window = resolveWindow(normalized, departmentId);
        }

        windowAssignmentRepository.deactivateActiveByUserId(userId);
        if (window == null) {
            return;
        }

        windowAssignmentRepository.deactivateActiveByServiceWindowId(window.getId());
        EmployeeWindowAssignmentEntity selected = windowAssignmentRepository
                .findByUserIdAndServiceWindowId(userId, window.getId())
                .orElseGet(EmployeeWindowAssignmentEntity::new);
        selected.setUserId(userId);
        selected.setServiceWindowId(window.getId());
        selected.setActive(true);
        selected.setAssignedAt(Instant.now());
        windowAssignmentRepository.save(selected);
    }

    @Transactional
    public void replaceServices(UUID userId, UUID departmentId, Set<String> serviceIdentifiers) {
        List<EmployeeServiceAssignmentEntity> assignments = new ArrayList<>(serviceAssignmentRepository.findByUserId(userId));
        assignments.forEach(assignment -> assignment.setActive(false));

        Map<UUID, QueueServiceEntity> requestedServices = resolveServices(serviceIdentifiers, departmentId);
        for (QueueServiceEntity service : requestedServices.values()) {
            EmployeeServiceAssignmentEntity selected = assignments.stream()
                    .filter(assignment -> assignment.getDepartmentId().equals(departmentId)
                            && assignment.getServiceId().equals(service.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        EmployeeServiceAssignmentEntity assignment = new EmployeeServiceAssignmentEntity();
                        assignment.setUserId(userId);
                        assignment.setDepartmentId(departmentId);
                        assignment.setServiceId(service.getId());
                        assignments.add(assignment);
                        return assignment;
                    });
            selected.setActive(true);
        }
        try {
            serviceAssignmentRepository.saveAll(assignments);
            serviceAssignmentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_SERVICE_ASSIGNMENT_CONFLICT",
                    "Employee service assignment conflicts with existing data");
        }
    }

    private ServiceWindowEntity resolveWindow(String identifier, UUID departmentId) {
        UUID windowId = parseUuid(identifier).orElseGet(() -> serviceWindowRepository
                .findByDepartmentIdOrderByCodeAsc(departmentId)
                .stream()
                .filter(item -> item.getCode().equalsIgnoreCase(identifier))
                .map(ServiceWindowEntity::getId)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "WINDOW_NOT_FOUND", "Assigned window was not found")));
        ServiceWindowEntity window = serviceWindowRepository.findWithLockById(windowId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "WINDOW_NOT_FOUND", "Assigned window was not found"));
        if (!departmentId.equals(window.getDepartmentId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WINDOW_DEPARTMENT_MISMATCH",
                    "Assigned window does not belong to the user department");
        }
        if (!window.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WINDOW_INACTIVE", "Assigned window is inactive");
        }
        return window;
    }

    private Map<UUID, QueueServiceEntity> resolveServices(Set<String> identifiers, UUID departmentId) {
        Map<UUID, QueueServiceEntity> services = new LinkedHashMap<>();
        if (identifiers == null) {
            return services;
        }
        for (String rawIdentifier : identifiers) {
            String identifier = normalize(rawIdentifier);
            if (identifier == null) {
                continue;
            }
            requireDepartment(departmentId);
            QueueServiceEntity service = parseUuid(identifier)
                    .flatMap(queueServiceRepository::findById)
                    .orElseGet(() -> queueServiceRepository.findByCodeIgnoreCase(identifier)
                            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_NOT_FOUND",
                                    "Assigned service was not found: " + identifier)));
            if (!service.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_INACTIVE",
                        "Assigned service is inactive: " + identifier);
            }
            if (!departmentServiceRepository.existsByDepartmentIdAndServiceIdAndActiveTrue(departmentId, service.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_NOT_AVAILABLE_IN_DEPARTMENT",
                        "Assigned service is not available in the user department: " + identifier);
            }
            services.put(service.getId(), service);
        }
        return services;
    }

    private java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        int rowKeySeparator = normalized.lastIndexOf(':');
        if (rowKeySeparator >= 0 && rowKeySeparator < normalized.length() - 1) {
            normalized = normalized.substring(rowKeySeparator + 1).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private void requireDepartment(UUID departmentId) {
        if (departmentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNMENT_DEPARTMENT_REQUIRED",
                    "departmentId is required for window and service assignments");
        }
    }

    public record AssignmentSnapshot(UUID windowId, List<UUID> serviceIds, List<String> serviceCodes) {
        public static AssignmentSnapshot empty() {
            return new AssignmentSnapshot(null, List.of(), List.of());
        }
    }
}
