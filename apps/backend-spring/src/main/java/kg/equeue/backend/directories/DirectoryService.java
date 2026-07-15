package kg.equeue.backend.directories;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DepartmentScopeService;
import kg.equeue.backend.departmentservices.DepartmentServiceEntity;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.departments.DepartmentEntity;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.directories.DirectoryDtos.ActiveStatusRequest;
import kg.equeue.backend.directories.DirectoryDtos.AssignEmployeeServiceRequest;
import kg.equeue.backend.directories.DirectoryDtos.AssignEmployeeToWindowRequest;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentRequest;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentResponse;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentServiceRequest;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentServiceResponse;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentStatusRequest;
import kg.equeue.backend.directories.DirectoryDtos.HallRequest;
import kg.equeue.backend.directories.DirectoryDtos.HallResponse;
import kg.equeue.backend.directories.DirectoryDtos.OfficeRoomRequest;
import kg.equeue.backend.directories.DirectoryDtos.OfficeRoomResponse;
import kg.equeue.backend.directories.DirectoryDtos.RegionRequest;
import kg.equeue.backend.directories.DirectoryDtos.RegionResponse;
import kg.equeue.backend.directories.DirectoryDtos.ServiceCategoryRequest;
import kg.equeue.backend.directories.DirectoryDtos.ServiceCategoryResponse;
import kg.equeue.backend.directories.DirectoryDtos.ServiceRequest;
import kg.equeue.backend.directories.DirectoryDtos.ServiceResponse;
import kg.equeue.backend.directories.DirectoryDtos.WindowRequest;
import kg.equeue.backend.directories.DirectoryDtos.WindowResponse;
import kg.equeue.backend.directories.DirectoryDtos.WindowStatusRequest;
import kg.equeue.backend.employeeserviceassignments.EmployeeServiceAssignmentEntity;
import kg.equeue.backend.employeeserviceassignments.EmployeeServiceAssignmentRepository;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentEntity;
import kg.equeue.backend.employeewindowassignments.EmployeeWindowAssignmentRepository;
import kg.equeue.backend.halls.HallEntity;
import kg.equeue.backend.halls.HallRepository;
import kg.equeue.backend.officerooms.OfficeRoomEntity;
import kg.equeue.backend.officerooms.OfficeRoomRepository;
import kg.equeue.backend.regions.RegionEntity;
import kg.equeue.backend.regions.RegionRepository;
import kg.equeue.backend.servicecategories.ServiceCategoryEntity;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import kg.equeue.backend.servicewindows.WindowStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectoryService {

    private final RegionRepository regionRepository;
    private final DepartmentRepository departmentRepository;
    private final OfficeRoomRepository officeRoomRepository;
    private final HallRepository hallRepository;
    private final ServiceWindowRepository serviceWindowRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final QueueServiceRepository queueServiceRepository;
    private final DepartmentServiceRepository departmentServiceRepository;
    private final EmployeeServiceAssignmentRepository employeeServiceAssignmentRepository;
    private final EmployeeWindowAssignmentRepository employeeWindowAssignmentRepository;
    private final DepartmentScopeService departmentScopeService;
    private final AuditService auditService;

    public DirectoryService(RegionRepository regionRepository,
                            DepartmentRepository departmentRepository,
                            OfficeRoomRepository officeRoomRepository,
                            HallRepository hallRepository,
                            ServiceWindowRepository serviceWindowRepository,
                            ServiceCategoryRepository serviceCategoryRepository,
                            QueueServiceRepository queueServiceRepository,
                            DepartmentServiceRepository departmentServiceRepository,
                            EmployeeServiceAssignmentRepository employeeServiceAssignmentRepository,
                            EmployeeWindowAssignmentRepository employeeWindowAssignmentRepository,
                            DepartmentScopeService departmentScopeService,
                            AuditService auditService) {
        this.regionRepository = regionRepository;
        this.departmentRepository = departmentRepository;
        this.officeRoomRepository = officeRoomRepository;
        this.hallRepository = hallRepository;
        this.serviceWindowRepository = serviceWindowRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.queueServiceRepository = queueServiceRepository;
        this.departmentServiceRepository = departmentServiceRepository;
        this.employeeServiceAssignmentRepository = employeeServiceAssignmentRepository;
        this.employeeWindowAssignmentRepository = employeeWindowAssignmentRepository;
        this.departmentScopeService = departmentScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RegionResponse> regions() {
        return regionRepository.findAllByOrderByNameAsc().stream().map(this::regionResponse).toList();
    }

    @Transactional(readOnly = true)
    public RegionResponse region(UUID id) {
        return regionResponse(regionOrThrow(id));
    }

    @Transactional
    public RegionResponse createRegion(RegionRequest request, HttpServletRequest httpRequest) {
        if (regionRepository.existsByCode(request.code())) {
            throw conflict("REGION_EXISTS", "Region code already exists");
        }
        RegionEntity entity = new RegionEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        RegionEntity saved = regionRepository.save(entity);
        auditService.write("REGION_CREATE", "REGION", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return regionResponse(saved);
    }

    @Transactional
    public RegionResponse updateRegion(UUID id, RegionRequest request, HttpServletRequest httpRequest) {
        RegionEntity entity = regionOrThrow(id);
        entity.setCode(request.code());
        entity.setName(request.name());
        RegionEntity saved = regionRepository.save(entity);
        auditService.write("REGION_UPDATE", "REGION", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return regionResponse(saved);
    }

    @Transactional
    public RegionResponse updateRegionStatus(UUID id, ActiveStatusRequest request, HttpServletRequest httpRequest) {
        RegionEntity entity = regionOrThrow(id);
        entity.setActive(request.active());
        RegionEntity saved = regionRepository.save(entity);
        auditService.write("REGION_STATUS_UPDATE", "REGION", saved.getId(), simpleJson("active", String.valueOf(saved.isActive())), httpRequest);
        return regionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> departments() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .filter(department -> departmentScopeService.canAccessDepartment(department.getId()))
                .map(this::departmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse department(UUID id) {
        DepartmentEntity department = departmentOrThrow(id);
        departmentScopeService.requireDepartmentAccess(id);
        return departmentResponse(department);
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request, HttpServletRequest httpRequest) {
        if (!regionRepository.existsById(request.regionId())) {
            throw notFound("REGION_NOT_FOUND", "Region was not found");
        }
        if (departmentRepository.existsByCode(request.code())) {
            throw conflict("DEPARTMENT_EXISTS", "Department code already exists");
        }
        DepartmentEntity entity = new DepartmentEntity();
        applyDepartment(entity, request);
        DepartmentEntity saved = departmentRepository.save(entity);
        auditService.write("DEPARTMENT_CREATE", "DEPARTMENT", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return departmentResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(UUID id, DepartmentRequest request, HttpServletRequest httpRequest) {
        DepartmentEntity entity = departmentOrThrow(id);
        departmentScopeService.requireDepartmentAccess(id);
        if (!regionRepository.existsById(request.regionId())) {
            throw notFound("REGION_NOT_FOUND", "Region was not found");
        }
        applyDepartment(entity, request);
        DepartmentEntity saved = departmentRepository.save(entity);
        auditService.write("DEPARTMENT_UPDATE", "DEPARTMENT", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return departmentResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartmentStatus(UUID id, DepartmentStatusRequest request, HttpServletRequest httpRequest) {
        DepartmentEntity entity = departmentOrThrow(id);
        departmentScopeService.requireDepartmentAccess(id);
        entity.setActive(request.active());
        if (request.closed() != null) {
            entity.setClosed(request.closed());
        }
        DepartmentEntity saved = departmentRepository.save(entity);
        auditService.write("DEPARTMENT_STATUS_UPDATE", "DEPARTMENT", saved.getId(), simpleJson("active", String.valueOf(saved.isActive())), httpRequest);
        return departmentResponse(saved);
    }

    @Transactional
    public void deleteDepartment(UUID id, HttpServletRequest httpRequest) {
        DepartmentEntity entity = departmentOrThrow(id);
        departmentScopeService.requireDepartmentAccess(id);
        entity.setActive(false);
        entity.setClosed(true);
        departmentRepository.save(entity);

        List<OfficeRoomEntity> rooms = officeRoomRepository.findByDepartmentIdOrderByCodeAsc(id);
        rooms.forEach(room -> room.setActive(false));
        officeRoomRepository.saveAll(rooms);

        List<HallEntity> halls = hallRepository.findByDepartmentIdOrderByCodeAsc(id);
        halls.forEach(hall -> hall.setActive(false));
        hallRepository.saveAll(halls);

        List<ServiceWindowEntity> windows = serviceWindowRepository.findByDepartmentIdOrderByCodeAsc(id);
        windows.forEach(this::deactivateWindow);
        serviceWindowRepository.saveAll(windows);
        deactivateWindowAssignments(windows);

        List<DepartmentServiceEntity> departmentServices = departmentServiceRepository.findByDepartmentId(id);
        departmentServices.forEach(service -> service.setActive(false));
        departmentServiceRepository.saveAll(departmentServices);

        List<EmployeeServiceAssignmentEntity> employeeServices = employeeServiceAssignmentRepository.findByDepartmentId(id);
        employeeServices.forEach(assignment -> assignment.setActive(false));
        employeeServiceAssignmentRepository.saveAll(employeeServices);

        auditService.write("DEPARTMENT_DELETE", "DEPARTMENT", entity.getId(), simpleJson("active", "false"), httpRequest);
    }

    @Transactional(readOnly = true)
    public List<OfficeRoomResponse> rooms(UUID departmentId) {
        departmentScopeService.requireDepartmentAccess(departmentId);
        return officeRoomRepository.findByDepartmentIdOrderByCodeAsc(departmentId).stream().map(this::roomResponse).toList();
    }

    @Transactional
    public OfficeRoomResponse createRoom(UUID departmentId, OfficeRoomRequest request, HttpServletRequest httpRequest) {
        departmentOrThrow(departmentId);
        departmentScopeService.requireDepartmentAccess(departmentId);
        OfficeRoomEntity entity = new OfficeRoomEntity();
        entity.setDepartmentId(departmentId);
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setFloor(request.floor());
        OfficeRoomEntity saved = officeRoomRepository.save(entity);
        auditService.write("OFFICE_ROOM_CREATE", "OFFICE_ROOM", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return roomResponse(saved);
    }

    @Transactional
    public OfficeRoomResponse updateRoom(UUID id, OfficeRoomRequest request, HttpServletRequest httpRequest) {
        OfficeRoomEntity entity = roomOrThrow(id);
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setFloor(request.floor());
        OfficeRoomEntity saved = officeRoomRepository.save(entity);
        auditService.write("OFFICE_ROOM_UPDATE", "OFFICE_ROOM", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return roomResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<HallResponse> halls(UUID departmentId) {
        departmentScopeService.requireDepartmentAccess(departmentId);
        return hallRepository.findByDepartmentIdOrderByCodeAsc(departmentId).stream().map(this::hallResponse).toList();
    }

    @Transactional
    public HallResponse createHall(UUID departmentId, HallRequest request, HttpServletRequest httpRequest) {
        departmentOrThrow(departmentId);
        departmentScopeService.requireDepartmentAccess(departmentId);
        validateRoomBelongsToDepartment(request.officeRoomId(), departmentId);
        HallEntity entity = new HallEntity();
        entity.setDepartmentId(departmentId);
        entity.setOfficeRoomId(request.officeRoomId());
        entity.setCode(request.code());
        entity.setName(request.name());
        HallEntity saved = hallRepository.save(entity);
        auditService.write("HALL_CREATE", "HALL", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return hallResponse(saved);
    }

    @Transactional
    public HallResponse updateHall(UUID id, HallRequest request, HttpServletRequest httpRequest) {
        HallEntity entity = hallOrThrow(id);
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        validateRoomBelongsToDepartment(request.officeRoomId(), entity.getDepartmentId());
        entity.setOfficeRoomId(request.officeRoomId());
        entity.setCode(request.code());
        entity.setName(request.name());
        HallEntity saved = hallRepository.save(entity);
        auditService.write("HALL_UPDATE", "HALL", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return hallResponse(saved);
    }

    @Transactional
    public void deleteHall(UUID id, HttpServletRequest httpRequest) {
        HallEntity entity = hallOrThrow(id);
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        entity.setActive(false);
        hallRepository.save(entity);

        List<ServiceWindowEntity> windows = serviceWindowRepository.findByHallIdOrderByCodeAsc(id);
        windows.forEach(this::deactivateWindow);
        serviceWindowRepository.saveAll(windows);
        deactivateWindowAssignments(windows);

        auditService.write("HALL_DELETE", "HALL", entity.getId(), simpleJson("active", "false"), httpRequest);
    }

    @Transactional(readOnly = true)
    public List<WindowResponse> windows() {
        return serviceWindowRepository.findAllByOrderByDepartmentIdAscCodeAsc().stream()
                .filter(window -> departmentScopeService.canAccessDepartment(window.getDepartmentId()))
                .map(this::windowResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WindowResponse> windows(UUID departmentId) {
        departmentScopeService.requireDepartmentAccess(departmentId);
        return serviceWindowRepository.findByDepartmentIdOrderByCodeAsc(departmentId).stream().map(this::windowResponse).toList();
    }

    @Transactional(readOnly = true)
    public WindowResponse window(UUID id) {
        ServiceWindowEntity entity = windowOrThrow(id);
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        return windowResponse(entity);
    }

    @Transactional
    public WindowResponse createWindow(UUID departmentId, WindowRequest request, HttpServletRequest httpRequest) {
        departmentOrThrow(departmentId);
        departmentScopeService.requireDepartmentAccess(departmentId);
        validateHallBelongsToDepartment(request.hallId(), departmentId);
        ServiceWindowEntity entity = new ServiceWindowEntity();
        entity.setDepartmentId(departmentId);
        entity.setHallId(request.hallId());
        entity.setCode(request.code());
        entity.setDisplayName(request.displayName());
        ServiceWindowEntity saved = serviceWindowRepository.save(entity);
        auditService.write("WINDOW_CREATE", "WINDOW", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return windowResponse(saved);
    }

    @Transactional
    public WindowResponse updateWindow(UUID id, WindowRequest request, HttpServletRequest httpRequest) {
        ServiceWindowEntity entity = windowOrThrow(id);
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        validateHallBelongsToDepartment(request.hallId(), entity.getDepartmentId());
        entity.setHallId(request.hallId());
        entity.setCode(request.code());
        entity.setDisplayName(request.displayName());
        ServiceWindowEntity saved = serviceWindowRepository.save(entity);
        auditService.write("WINDOW_UPDATE", "WINDOW", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return windowResponse(saved);
    }

    @Transactional
    public WindowResponse updateWindowStatus(UUID id, WindowStatusRequest request, HttpServletRequest httpRequest) {
        ServiceWindowEntity entity = windowOrThrow(id);
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        entity.setStatus(request.status());
        ServiceWindowEntity saved = serviceWindowRepository.save(entity);
        auditService.write("WINDOW_STATUS_UPDATE", "WINDOW", saved.getId(), simpleJson("status", saved.getStatus().name()), httpRequest);
        return windowResponse(saved);
    }

    @Transactional
    public void deleteWindow(UUID id, HttpServletRequest httpRequest) {
        ServiceWindowEntity entity = windowOrThrow(id);
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        auditService.write("WINDOW_DELETE", "WINDOW", entity.getId(), simpleJson("deleted", "true"), httpRequest);
        serviceWindowRepository.delete(entity);
    }

    @Transactional
    public WindowResponse openWindow(UUID id, HttpServletRequest httpRequest) {
        ServiceWindowEntity entity = serviceWindowRepository.findWithLockById(id)
                .orElseThrow(() -> notFound("WINDOW_NOT_FOUND", "Window was not found"));
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        entity.setStatus(WindowStatus.OPEN);
        ServiceWindowEntity saved = serviceWindowRepository.save(entity);
        auditService.write("WINDOW_OPEN", "WINDOW", saved.getId(), simpleJson("status", "OPEN"), httpRequest);
        return windowResponse(saved);
    }

    @Transactional
    public WindowResponse closeWindow(UUID id, HttpServletRequest httpRequest) {
        ServiceWindowEntity entity = serviceWindowRepository.findWithLockById(id)
                .orElseThrow(() -> notFound("WINDOW_NOT_FOUND", "Window was not found"));
        departmentScopeService.requireDepartmentAccess(entity.getDepartmentId());
        entity.setStatus(WindowStatus.CLOSED);
        ServiceWindowEntity saved = serviceWindowRepository.save(entity);
        auditService.write("WINDOW_CLOSE", "WINDOW", saved.getId(), simpleJson("status", "CLOSED"), httpRequest);
        return windowResponse(saved);
    }

    @Transactional
    public WindowResponse assignEmployeeToWindow(UUID id, AssignEmployeeToWindowRequest request, HttpServletRequest httpRequest) {
        ServiceWindowEntity window = windowOrThrow(id);
        departmentScopeService.requireDepartmentAccess(window.getDepartmentId());
        EmployeeWindowAssignmentEntity assignment = employeeWindowAssignmentRepository
                .findByUserIdAndServiceWindowId(request.employeeId(), id)
                .orElseGet(EmployeeWindowAssignmentEntity::new);
        assignment.setUserId(request.employeeId());
        assignment.setServiceWindowId(id);
        assignment.setActive(true);
        employeeWindowAssignmentRepository.save(assignment);
        auditService.write("WINDOW_ASSIGN_EMPLOYEE", "WINDOW", id, simpleJson("employeeId", request.employeeId().toString()), httpRequest);
        return windowResponse(window);
    }

    @Transactional(readOnly = true)
    public List<ServiceCategoryResponse> serviceCategories() {
        return serviceCategoryRepository.findAllByOrderByNameAsc().stream().map(this::categoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceCategoryResponse serviceCategory(UUID id) {
        return categoryResponse(categoryOrThrow(id));
    }

    @Transactional
    public ServiceCategoryResponse createServiceCategory(ServiceCategoryRequest request, HttpServletRequest httpRequest) {
        if (serviceCategoryRepository.existsByCode(request.code())) {
            throw conflict("SERVICE_CATEGORY_EXISTS", "Service category code already exists");
        }
        ServiceCategoryEntity entity = new ServiceCategoryEntity();
        applyCategory(entity, request);
        ServiceCategoryEntity saved = serviceCategoryRepository.save(entity);
        auditService.write("SERVICE_CATEGORY_CREATE", "SERVICE_CATEGORY", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return categoryResponse(saved);
    }

    @Transactional
    public ServiceCategoryResponse updateServiceCategory(UUID id, ServiceCategoryRequest request, HttpServletRequest httpRequest) {
        ServiceCategoryEntity entity = categoryOrThrow(id);
        applyCategory(entity, request);
        ServiceCategoryEntity saved = serviceCategoryRepository.save(entity);
        auditService.write("SERVICE_CATEGORY_UPDATE", "SERVICE_CATEGORY", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return categoryResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> services() {
        return queueServiceRepository.findAllByOrderByNameAsc().stream().map(this::serviceResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse service(UUID id) {
        return serviceResponse(serviceOrThrow(id));
    }

    @Transactional
    public ServiceResponse createService(ServiceRequest request, HttpServletRequest httpRequest) {
        if (queueServiceRepository.existsByCode(request.code())) {
            throw conflict("SERVICE_EXISTS", "Service code already exists");
        }
        if (!serviceCategoryRepository.existsById(request.categoryId())) {
            throw notFound("SERVICE_CATEGORY_NOT_FOUND", "Service category was not found");
        }
        QueueServiceEntity entity = new QueueServiceEntity();
        applyService(entity, request);
        QueueServiceEntity saved = queueServiceRepository.save(entity);
        auditService.write("SERVICE_CREATE", "SERVICE", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return serviceResponse(saved);
    }

    @Transactional
    public ServiceResponse updateService(UUID id, ServiceRequest request, HttpServletRequest httpRequest) {
        QueueServiceEntity entity = serviceOrThrow(id);
        if (!serviceCategoryRepository.existsById(request.categoryId())) {
            throw notFound("SERVICE_CATEGORY_NOT_FOUND", "Service category was not found");
        }
        applyService(entity, request);
        QueueServiceEntity saved = queueServiceRepository.save(entity);
        auditService.write("SERVICE_UPDATE", "SERVICE", saved.getId(), simpleJson("code", saved.getCode()), httpRequest);
        return serviceResponse(saved);
    }

    @Transactional
    public ServiceResponse updateServiceStatus(UUID id, ActiveStatusRequest request, HttpServletRequest httpRequest) {
        QueueServiceEntity entity = serviceOrThrow(id);
        entity.setActive(request.active());
        QueueServiceEntity saved = queueServiceRepository.save(entity);
        auditService.write("SERVICE_STATUS_UPDATE", "SERVICE", saved.getId(), simpleJson("active", String.valueOf(saved.isActive())), httpRequest);
        return serviceResponse(saved);
    }

    @Transactional
    public void deleteService(UUID id, HttpServletRequest httpRequest) {
        QueueServiceEntity entity = serviceOrThrow(id);
        entity.setActive(false);
        queueServiceRepository.save(entity);

        List<DepartmentServiceEntity> departmentServices = departmentServiceRepository.findByServiceId(id);
        departmentServices.forEach(service -> service.setActive(false));
        departmentServiceRepository.saveAll(departmentServices);

        List<EmployeeServiceAssignmentEntity> employeeServices = employeeServiceAssignmentRepository.findByServiceId(id);
        employeeServices.forEach(assignment -> assignment.setActive(false));
        employeeServiceAssignmentRepository.saveAll(employeeServices);

        auditService.write("SERVICE_DELETE", "SERVICE", entity.getId(), simpleJson("active", "false"), httpRequest);
    }

    @Transactional(readOnly = true)
    public List<DepartmentServiceResponse> departmentServices(UUID departmentId) {
        departmentScopeService.requireDepartmentAccess(departmentId);
        return departmentServiceRepository.findByDepartmentIdAndActiveTrueOrderByServiceIdAsc(departmentId)
                .stream().map(this::departmentServiceResponse).toList();
    }

    @Transactional
    public DepartmentServiceResponse assignServiceToDepartment(UUID departmentId, UUID serviceId, DepartmentServiceRequest request, HttpServletRequest httpRequest) {
        departmentOrThrow(departmentId);
        serviceOrThrow(serviceId);
        departmentScopeService.requireDepartmentAccess(departmentId);
        DepartmentServiceEntity entity = departmentServiceRepository.findByDepartmentIdAndServiceId(departmentId, serviceId)
                .orElseGet(DepartmentServiceEntity::new);
        entity.setDepartmentId(departmentId);
        entity.setServiceId(serviceId);
        entity.setActive(true);
        applyDepartmentService(entity, request);
        DepartmentServiceEntity saved = departmentServiceRepository.save(entity);
        auditService.write("SERVICE_ASSIGN_TO_DEPARTMENT", "DEPARTMENT_SERVICE", saved.getId(), simpleJson("serviceId", serviceId.toString()), httpRequest);
        return departmentServiceResponse(saved);
    }

    @Transactional
    public void removeServiceFromDepartment(UUID departmentId, UUID serviceId, HttpServletRequest httpRequest) {
        departmentScopeService.requireDepartmentAccess(departmentId);
        DepartmentServiceEntity entity = departmentServiceRepository.findByDepartmentIdAndServiceId(departmentId, serviceId)
                .orElseThrow(() -> notFound("DEPARTMENT_SERVICE_NOT_FOUND", "Department service assignment was not found"));
        entity.setActive(false);
        departmentServiceRepository.save(entity);
        auditService.write("SERVICE_REMOVE_FROM_DEPARTMENT", "DEPARTMENT_SERVICE", entity.getId(), simpleJson("serviceId", serviceId.toString()), httpRequest);
    }

    @Transactional
    public void assignServiceToEmployee(UUID employeeId, UUID serviceId, AssignEmployeeServiceRequest request, HttpServletRequest httpRequest) {
        departmentOrThrow(request.departmentId());
        serviceOrThrow(serviceId);
        departmentScopeService.requireDepartmentAccess(request.departmentId());
        if (!departmentServiceRepository.existsByDepartmentIdAndServiceIdAndActiveTrue(request.departmentId(), serviceId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_NOT_AVAILABLE_IN_DEPARTMENT", "Service is not available in department");
        }
        EmployeeServiceAssignmentEntity entity = employeeServiceAssignmentRepository
                .findByUserIdAndDepartmentIdAndServiceId(employeeId, request.departmentId(), serviceId)
                .orElseGet(EmployeeServiceAssignmentEntity::new);
        entity.setUserId(employeeId);
        entity.setDepartmentId(request.departmentId());
        entity.setServiceId(serviceId);
        entity.setActive(true);
        EmployeeServiceAssignmentEntity saved = employeeServiceAssignmentRepository.save(entity);
        auditService.write("SERVICE_ASSIGN_TO_EMPLOYEE", "EMPLOYEE_SERVICE_ASSIGNMENT", saved.getId(), simpleJson("serviceId", serviceId.toString()), httpRequest);
    }

    @Transactional
    public void removeServiceFromEmployee(UUID employeeId, UUID serviceId, UUID departmentId, HttpServletRequest httpRequest) {
        List<EmployeeServiceAssignmentEntity> assignments = employeeServiceAssignmentRepository.findByUserIdAndServiceId(employeeId, serviceId);
        assignments.stream()
                .filter(assignment -> departmentId == null || assignment.getDepartmentId().equals(departmentId))
                .forEach(assignment -> {
                    departmentScopeService.requireDepartmentAccess(assignment.getDepartmentId());
                    assignment.setActive(false);
                    employeeServiceAssignmentRepository.save(assignment);
                    auditService.write("SERVICE_REMOVE_FROM_EMPLOYEE", "EMPLOYEE_SERVICE_ASSIGNMENT", assignment.getId(), simpleJson("serviceId", serviceId.toString()), httpRequest);
                });
    }

    private void applyDepartment(DepartmentEntity entity, DepartmentRequest request) {
        entity.setRegionId(request.regionId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setAddress(request.address());
        entity.setTimezone(request.timezone() == null || request.timezone().isBlank() ? "Asia/Bishkek" : request.timezone());
    }

    private void applyCategory(ServiceCategoryEntity entity, ServiceCategoryRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setTicketPrefix(request.ticketPrefix());
    }

    private void applyService(QueueServiceEntity entity, ServiceRequest request) {
        entity.setCategoryId(request.categoryId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setDefaultDurationMinutes(request.defaultDurationMinutes() == null ? 15 : request.defaultDurationMinutes());
        entity.setDailyLimit(request.dailyLimit());
    }

    private void applyDepartmentService(DepartmentServiceEntity entity, DepartmentServiceRequest request) {
        if (request == null) {
            return;
        }
        if (request.onlineBookingEnabled() != null) {
            entity.setOnlineBookingEnabled(request.onlineBookingEnabled());
        }
        if (request.terminalEnabled() != null) {
            entity.setTerminalEnabled(request.terminalEnabled());
        }
        if (request.qrEnabled() != null) {
            entity.setQrEnabled(request.qrEnabled());
        }
        entity.setDailyLimit(request.dailyLimit());
    }

    private void deactivateWindow(ServiceWindowEntity window) {
        window.setActive(false);
    }

    private void deactivateWindowAssignments(List<ServiceWindowEntity> windows) {
        windows.forEach(window -> {
            List<EmployeeWindowAssignmentEntity> assignments = employeeWindowAssignmentRepository.findByServiceWindowId(window.getId());
            assignments.forEach(assignment -> assignment.setActive(false));
            employeeWindowAssignmentRepository.saveAll(assignments);
        });
    }

    private void validateRoomBelongsToDepartment(UUID roomId, UUID departmentId) {
        if (roomId == null) {
            return;
        }
        OfficeRoomEntity room = roomOrThrow(roomId);
        if (!room.getDepartmentId().equals(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROOM_DEPARTMENT_MISMATCH", "Room does not belong to department");
        }
    }

    private void validateHallBelongsToDepartment(UUID hallId, UUID departmentId) {
        if (hallId == null) {
            return;
        }
        HallEntity hall = hallOrThrow(hallId);
        if (!hall.getDepartmentId().equals(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HALL_DEPARTMENT_MISMATCH", "Hall does not belong to department");
        }
    }

    private RegionResponse regionResponse(RegionEntity entity) {
        return new RegionResponse(entity.getId(), entity.getCode(), entity.getName(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private DepartmentResponse departmentResponse(DepartmentEntity entity) {
        return new DepartmentResponse(entity.getId(), entity.getRegionId(), entity.getCode(), entity.getName(), entity.getAddress(), entity.getTimezone(), entity.isActive(), entity.isClosed(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private OfficeRoomResponse roomResponse(OfficeRoomEntity entity) {
        return new OfficeRoomResponse(entity.getId(), entity.getDepartmentId(), entity.getCode(), entity.getName(), entity.getFloor(), entity.isActive());
    }

    private HallResponse hallResponse(HallEntity entity) {
        return new HallResponse(entity.getId(), entity.getDepartmentId(), entity.getOfficeRoomId(), entity.getCode(), entity.getName(), entity.isActive());
    }

    private WindowResponse windowResponse(ServiceWindowEntity entity) {
        return new WindowResponse(entity.getId(), entity.getDepartmentId(), entity.getHallId(), entity.getCode(), entity.getDisplayName(), entity.isActive(), entity.isOpen(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ServiceCategoryResponse categoryResponse(ServiceCategoryEntity entity) {
        return new ServiceCategoryResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getTicketPrefix(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ServiceResponse serviceResponse(QueueServiceEntity entity) {
        return new ServiceResponse(entity.getId(), entity.getCategoryId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getDefaultDurationMinutes(), entity.getDailyLimit(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private DepartmentServiceResponse departmentServiceResponse(DepartmentServiceEntity entity) {
        return new DepartmentServiceResponse(entity.getId(), entity.getDepartmentId(), entity.getServiceId(), entity.isActive(), entity.isOnlineBookingEnabled(), entity.isTerminalEnabled(), entity.isQrEnabled(), entity.getDailyLimit());
    }

    private RegionEntity regionOrThrow(UUID id) {
        return regionRepository.findById(id).orElseThrow(() -> notFound("REGION_NOT_FOUND", "Region was not found"));
    }

    private DepartmentEntity departmentOrThrow(UUID id) {
        return departmentRepository.findById(id).orElseThrow(() -> notFound("DEPARTMENT_NOT_FOUND", "Department was not found"));
    }

    private OfficeRoomEntity roomOrThrow(UUID id) {
        return officeRoomRepository.findById(id).orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Office room was not found"));
    }

    private HallEntity hallOrThrow(UUID id) {
        return hallRepository.findById(id).orElseThrow(() -> notFound("HALL_NOT_FOUND", "Hall was not found"));
    }

    private ServiceWindowEntity windowOrThrow(UUID id) {
        return serviceWindowRepository.findById(id).orElseThrow(() -> notFound("WINDOW_NOT_FOUND", "Window was not found"));
    }

    private ServiceCategoryEntity categoryOrThrow(UUID id) {
        return serviceCategoryRepository.findById(id).orElseThrow(() -> notFound("SERVICE_CATEGORY_NOT_FOUND", "Service category was not found"));
    }

    private QueueServiceEntity serviceOrThrow(UUID id) {
        return queueServiceRepository.findById(id).orElseThrow(() -> notFound("SERVICE_NOT_FOUND", "Service was not found"));
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private String simpleJson(String key, String value) {
        return "{\"" + key + "\":\"" + value + "\"}";
    }
}
