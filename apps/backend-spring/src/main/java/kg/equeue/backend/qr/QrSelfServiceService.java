package kg.equeue.backend.qr;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.departments.DepartmentEntity;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.qr.QrDtos.QrConfigCategoryResponse;
import kg.equeue.backend.qr.QrDtos.QrConfigResponse;
import kg.equeue.backend.qr.QrDtos.QrConfigServiceResponse;
import kg.equeue.backend.qr.QrDtos.QrCreateTicketRequest;
import kg.equeue.backend.servicecategories.ServiceCategoryEntity;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QrSelfServiceService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentServiceRepository departmentServiceRepository;
    private final QueueServiceRepository queueServiceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final TicketService ticketService;

    public QrSelfServiceService(DepartmentRepository departmentRepository,
                                DepartmentServiceRepository departmentServiceRepository,
                                QueueServiceRepository queueServiceRepository,
                                ServiceCategoryRepository serviceCategoryRepository,
                                TicketService ticketService) {
        this.departmentRepository = departmentRepository;
        this.departmentServiceRepository = departmentServiceRepository;
        this.queueServiceRepository = queueServiceRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.ticketService = ticketService;
    }

    @Transactional(readOnly = true)
    public QrConfigResponse config(UUID departmentId) {
        DepartmentEntity department = availableDepartment(departmentId);
        List<UUID> serviceIds = departmentServiceRepository
                .findByDepartmentIdAndActiveTrueOrderByServiceIdAsc(department.getId())
                .stream()
                .filter(service -> service.isQrEnabled())
                .map(service -> service.getServiceId())
                .toList();
        Map<UUID, QueueServiceEntity> servicesById = queueServiceRepository.findAllById(serviceIds)
                .stream()
                .collect(Collectors.toMap(QueueServiceEntity::getId, service -> service, (first, second) -> first));
        List<QueueServiceEntity> visibleServices = serviceIds.stream()
                .map(servicesById::get)
                .filter(Objects::nonNull)
                .filter(QueueServiceEntity::isActive)
                .toList();
        List<UUID> categoryIds = visibleServices.stream()
                .map(QueueServiceEntity::getCategoryId)
                .distinct()
                .toList();
        Map<UUID, ServiceCategoryEntity> categoriesById = serviceCategoryRepository.findAllById(categoryIds)
                .stream()
                .filter(ServiceCategoryEntity::isActive)
                .collect(Collectors.toMap(ServiceCategoryEntity::getId, category -> category, (first, second) -> first));
        visibleServices = visibleServices.stream()
                .filter(service -> categoriesById.containsKey(service.getCategoryId()))
                .toList();
        List<QrConfigServiceResponse> services = visibleServices.stream()
                .map(service -> serviceResponse(service, categoriesById.get(service.getCategoryId())))
                .toList();
        List<QrConfigCategoryResponse> categories = visibleServices.stream()
                .map(QueueServiceEntity::getCategoryId)
                .distinct()
                .map(categoriesById::get)
                .filter(Objects::nonNull)
                .map(this::categoryResponse)
                .toList();
        return new QrConfigResponse(department.getId(), department.getCode(), department.getName(), services, categories);
    }

    @Transactional
    public TicketResponse createTicket(QrCreateTicketRequest request, HttpServletRequest httpRequest) {
        CreateTicketRequest normalized = new CreateTicketRequest(
                request.departmentId(),
                request.serviceId(),
                null,
                request.citizenFullName(),
                null,
                request.citizenPhone(),
                TicketSource.QR_SELF_SERVICE,
                request.comment(),
                null,
                null
        );
        return ticketService.createSelfServiceTicket(normalized, httpRequest);
    }

    private DepartmentEntity availableDepartment(UUID departmentId) {
        DepartmentEntity department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", "Department was not found"));
        if (!department.isActive() || department.isClosed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEPARTMENT_NOT_AVAILABLE", "Department is not available");
        }
        return department;
    }

    private QrConfigServiceResponse serviceResponse(QueueServiceEntity service, ServiceCategoryEntity category) {
        return new QrConfigServiceResponse(
                service.getId(),
                service.getCode(),
                localizedName(service.getName()),
                service.getCategoryId(),
                category.getCode()
        );
    }

    private QrConfigCategoryResponse categoryResponse(ServiceCategoryEntity category) {
        return new QrConfigCategoryResponse(category.getId(), category.getCode(), localizedName(category.getName()));
    }

    private QrDtos.LocalizedName localizedName(String name) {
        return new QrDtos.LocalizedName(name, name);
    }
}
