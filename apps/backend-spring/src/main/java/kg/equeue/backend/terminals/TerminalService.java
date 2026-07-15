package kg.equeue.backend.terminals;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.servicecategories.ServiceCategoryEntity;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.ticketevents.TicketActorType;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalService {

    private static final String TERMINAL_SERVICE_TYPE = "VS";

    private final TerminalRepository terminalRepository;
    private final DepartmentServiceRepository departmentServiceRepository;
    private final QueueServiceRepository queueServiceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final DeviceTokenService deviceTokenService;
    private final TicketService ticketService;

    public TerminalService(TerminalRepository terminalRepository,
                           DepartmentServiceRepository departmentServiceRepository,
                           QueueServiceRepository queueServiceRepository,
                           ServiceCategoryRepository serviceCategoryRepository,
                           DeviceTokenService deviceTokenService,
                           TicketService ticketService) {
        this.terminalRepository = terminalRepository;
        this.departmentServiceRepository = departmentServiceRepository;
        this.queueServiceRepository = queueServiceRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.deviceTokenService = deviceTokenService;
        this.ticketService = ticketService;
    }

    @Transactional
    public TerminalDtos.TerminalConfigResponse config(UUID terminalId, HttpServletRequest request) {
        TerminalEntity terminal = terminalOrThrow(terminalId);
        requireValidToken(terminal, request);
        terminal.setLastSeenAt(Instant.now());
        terminalRepository.save(terminal);
        List<UUID> serviceIds = departmentServiceRepository
                .findByDepartmentIdAndActiveTrueOrderByServiceIdAsc(terminal.getDepartmentId())
                .stream()
                .filter(service -> service.isTerminalEnabled())
                .map(service -> service.getServiceId())
                .toList();
        Map<UUID, QueueServiceEntity> servicesById = queueServiceRepository.findAllById(serviceIds)
                .stream()
                .collect(Collectors.toMap(QueueServiceEntity::getId, service -> service, (first, second) -> first));
        List<TerminalDtos.TerminalConfigServiceResponse> services = serviceIds.stream()
                .map(servicesById::get)
                .filter(Objects::nonNull)
                .map(this::terminalServiceResponse)
                .toList();
        List<UUID> categoryIds = services.stream()
                .map(TerminalDtos.TerminalConfigServiceResponse::categoryId)
                .distinct()
                .toList();
        Map<UUID, ServiceCategoryEntity> categoriesById = serviceCategoryRepository.findAllById(categoryIds)
                .stream()
                .collect(Collectors.toMap(ServiceCategoryEntity::getId, category -> category, (first, second) -> first));
        List<TerminalDtos.TerminalConfigCategoryResponse> categories = categoryIds.stream()
                .map(categoriesById::get)
                .filter(Objects::nonNull)
                .map(this::terminalCategoryResponse)
                .toList();
        return new TerminalDtos.TerminalConfigResponse(
                terminal.getId(),
                terminal.getDepartmentId(),
                terminal.getCode(),
                terminal.getName(),
                serviceIds,
                services,
                categories
        );
    }

    @Transactional
    public TicketResponse createTicket(UUID terminalId, TerminalDtos.TerminalCreateTicketRequest request, HttpServletRequest httpRequest) {
        TerminalEntity terminal = terminalOrThrow(terminalId);
        requireValidToken(terminal, httpRequest);
        if (!terminal.getDepartmentId().equals(request.departmentId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TERMINAL_DEPARTMENT_DENIED", "Terminal cannot create tickets for another department");
        }
        CreateTicketRequest normalized = new CreateTicketRequest(
                request.departmentId(),
                request.serviceId(),
                null,
                request.citizenFullName(),
                request.citizenPin(),
                request.citizenPhone(),
                TicketSource.TERMINAL,
                request.comment(),
                null,
                null
        );
        terminal.setLastSeenAt(Instant.now());
        terminalRepository.save(terminal);
        return ticketService.createDeviceTicket(normalized, TicketActorType.DEVICE, terminal.getId(), httpRequest);
    }

    private TerminalEntity terminalOrThrow(UUID terminalId) {
        return terminalRepository.findById(terminalId)
                .filter(TerminalEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND", "Terminal was not found or inactive"));
    }

    private void requireValidToken(TerminalEntity terminal, HttpServletRequest request) {
        String rawToken = deviceTokenService.requireRawToken(request);
        if (!deviceTokenService.matches(rawToken, terminal.getTokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_TOKEN", "Invalid terminal token");
        }
    }

    private TerminalDtos.TerminalConfigServiceResponse terminalServiceResponse(QueueServiceEntity service) {
        return new TerminalDtos.TerminalConfigServiceResponse(
                service.getId(),
                service.getCode(),
                localizedName(service.getName()),
                service.getCategoryId(),
                TERMINAL_SERVICE_TYPE
        );
    }

    private TerminalDtos.TerminalConfigCategoryResponse terminalCategoryResponse(ServiceCategoryEntity category) {
        return new TerminalDtos.TerminalConfigCategoryResponse(
                category.getId(),
                TERMINAL_SERVICE_TYPE,
                localizedName(category.getName())
        );
    }

    private TerminalDtos.LocalizedName localizedName(String name) {
        return new TerminalDtos.LocalizedName(name, name);
    }
}
