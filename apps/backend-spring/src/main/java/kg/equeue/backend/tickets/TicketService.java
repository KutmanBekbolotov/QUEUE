package kg.equeue.backend.tickets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.cancellationreasons.CancellationReasonRepository;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.CurrentUser;
import kg.equeue.backend.common.DepartmentScopeService;
import kg.equeue.backend.departmentservices.DepartmentServiceEntity;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.departments.DepartmentEntity;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.integrationclients.IntegrationIdempotencyService;
import kg.equeue.backend.pausereasons.PauseReasonRepository;
import kg.equeue.backend.servicecategories.ServiceCategoryEntity;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import kg.equeue.backend.servicewindows.WindowStatus;
import kg.equeue.backend.ticketevents.TicketActorType;
import kg.equeue.backend.ticketevents.TicketEventEntity;
import kg.equeue.backend.ticketevents.TicketEventRepository;
import kg.equeue.backend.tickets.TicketDtos.CallNextTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.CallTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.CancelTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.PauseTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketDtos.TransferTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TvSnapshotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final EnumSet<TicketStatus> TV_STATUSES = EnumSet.of(TicketStatus.CALLED, TicketStatus.IN_SERVICE);

    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;
    private final TicketSequenceService ticketSequenceService;
    private final TicketDomainEventPublisher ticketDomainEventPublisher;
    private final DepartmentRepository departmentRepository;
    private final QueueServiceRepository queueServiceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final DepartmentServiceRepository departmentServiceRepository;
    private final ServiceWindowRepository serviceWindowRepository;
    private final CancellationReasonRepository cancellationReasonRepository;
    private final PauseReasonRepository pauseReasonRepository;
    private final DepartmentScopeService departmentScopeService;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final IntegrationIdempotencyService idempotencyService;

    public TicketService(TicketRepository ticketRepository,
                         TicketEventRepository ticketEventRepository,
                         TicketSequenceService ticketSequenceService,
                         TicketDomainEventPublisher ticketDomainEventPublisher,
                         DepartmentRepository departmentRepository,
                         QueueServiceRepository queueServiceRepository,
                         ServiceCategoryRepository serviceCategoryRepository,
                         DepartmentServiceRepository departmentServiceRepository,
                         ServiceWindowRepository serviceWindowRepository,
                         CancellationReasonRepository cancellationReasonRepository,
                         PauseReasonRepository pauseReasonRepository,
                         DepartmentScopeService departmentScopeService,
                         AuditService auditService,
                         NamedParameterJdbcTemplate jdbcTemplate,
                         ObjectMapper objectMapper,
                         IntegrationIdempotencyService idempotencyService) {
        this.ticketRepository = ticketRepository;
        this.ticketEventRepository = ticketEventRepository;
        this.ticketSequenceService = ticketSequenceService;
        this.ticketDomainEventPublisher = ticketDomainEventPublisher;
        this.departmentRepository = departmentRepository;
        this.queueServiceRepository = queueServiceRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.departmentServiceRepository = departmentServiceRepository;
        this.serviceWindowRepository = serviceWindowRepository;
        this.cancellationReasonRepository = cancellationReasonRepository;
        this.pauseReasonRepository = pauseReasonRepository;
        this.departmentScopeService = departmentScopeService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, HttpServletRequest httpRequest) {
        if (CurrentUser.hasAuthority("ROLE_INTEGRATION_SERVICE")) {
            String clientCode = idempotencyService.clientCode(httpRequest, request.source().name());
            String key = idempotencyService.idempotencyKey(httpRequest, request.idempotencyKey());
            String externalRequestId = idempotencyService.externalRequestId(httpRequest, request.externalId(), key);
            IntegrationIdempotencyService.BeginResult begin = idempotencyService.begin(clientCode, key, externalRequestId, request);
            if (begin.replay()) {
                return readReplay(begin.responseBody());
            }
            TicketResponse response = create(request, TicketActorType.INTEGRATION, null, true, httpRequest);
            idempotencyService.complete(begin.entity(), response, 200);
            return response;
        }
        return create(request, TicketActorType.USER, CurrentUser.idOrNull(), true, httpRequest);
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, TicketActorType actorType, UUID actorId, HttpServletRequest httpRequest) {
        return create(request, actorType, actorId, true, httpRequest);
    }

    @Transactional
    public TicketResponse createDeviceTicket(CreateTicketRequest request, TicketActorType actorType, UUID actorId, HttpServletRequest httpRequest) {
        return create(request, actorType, actorId, false, httpRequest);
    }

    private TicketResponse create(CreateTicketRequest request, TicketActorType actorType, UUID actorId, boolean enforceScope, HttpServletRequest httpRequest) {
        DepartmentEntity department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> notFound("DEPARTMENT_NOT_FOUND", "Department was not found"));
        if (enforceScope) {
            departmentScopeService.requireDepartmentAccess(department.getId());
        }
        if (!department.isActive()) {
            throw badRequest("DEPARTMENT_INACTIVE", "Department is inactive");
        }
        if (department.isClosed()) {
            throw badRequest("DEPARTMENT_CLOSED", "Department is closed");
        }

        QueueServiceEntity service = queueServiceRepository.findById(request.serviceId())
                .orElseThrow(() -> notFound("SERVICE_NOT_FOUND", "Service was not found"));
        if (!service.isActive()) {
            throw badRequest("SERVICE_INACTIVE", "Service is inactive");
        }

        ServiceCategoryEntity category = serviceCategoryRepository.findById(service.getCategoryId())
                .orElseThrow(() -> notFound("SERVICE_CATEGORY_NOT_FOUND", "Service category was not found"));
        if (!category.isActive()) {
            throw badRequest("SERVICE_CATEGORY_INACTIVE", "Service category is inactive");
        }

        DepartmentServiceEntity departmentService = departmentServiceRepository
                .findByDepartmentIdAndServiceId(department.getId(), service.getId())
                .filter(DepartmentServiceEntity::isActive)
                .orElseThrow(() -> badRequest("SERVICE_NOT_AVAILABLE_IN_DEPARTMENT", "Service is not available in department"));
        validateSourceAllowed(request.source(), departmentService);

        LocalDate workDate = LocalDate.now();
        int sequence = ticketSequenceService.nextValue(department.getId(), category.getId(), workDate);
        String ticketNumber = category.getTicketPrefix() + "-" + "%03d".formatted(sequence);

        TicketEntity ticket = new TicketEntity();
        ticket.setRegionId(department.getRegionId());
        ticket.setDepartmentId(department.getId());
        ticket.setCategoryId(category.getId());
        ticket.setServiceId(service.getId());
        ticket.setBookingId(request.bookingId());
        ticket.setTicketPrefix(category.getTicketPrefix());
        ticket.setSequenceNumber(sequence);
        ticket.setTicketNumber(ticketNumber);
        ticket.setWorkDate(workDate);
        ticket.setCitizenFullName(request.citizenFullName());
        ticket.setCitizenPin(request.citizenPin());
        ticket.setCitizenPhone(request.citizenPhone());
        ticket.setSource(request.source());
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setComment(request.comment());
        TicketEntity saved = ticketRepository.save(ticket);
        writeEvent(saved, "ticket.created", null, TicketStatus.WAITING, actorType, actorId, Map.of("source", request.source()));
        auditService.write("TICKET_CREATE", "TICKET", saved.getId(), simpleJson("ticketNumber", saved.getTicketNumber()), httpRequest);
        ticketDomainEventPublisher.publish("ticket.created", saved);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(UUID departmentId) {
        if (departmentId != null) {
            departmentScopeService.requireDepartmentAccess(departmentId);
            return ticketRepository.findByDepartmentIdOrderByCreatedAtDesc(departmentId).stream().map(this::response).toList();
        }
        if (!CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEPARTMENT_REQUIRED", "departmentId is required for scoped users");
        }
        return ticketRepository.findAll().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID id) {
        TicketEntity ticket = ticketOrThrow(id);
        departmentScopeService.requireDepartmentAccess(ticket.getDepartmentId());
        return response(ticket);
    }

    @Transactional
    public TicketResponse call(UUID id, CallTicketRequest request, HttpServletRequest httpRequest) {
        TicketEntity ticket = ticketRepository.findWithLockById(id)
                .orElseThrow(() -> notFound("TICKET_NOT_FOUND", "Ticket was not found"));
        ServiceWindowEntity window = validOpenWindow(request.windowId(), ticket.getDepartmentId());
        requireCanUseWindow(window);
        return callLocked(ticket, window, httpRequest, "ticket.called");
    }

    @Transactional
    public TicketResponse callNext(CallNextTicketRequest request, HttpServletRequest httpRequest) {
        departmentScopeService.requireDepartmentAccess(request.departmentId());
        ServiceWindowEntity window = validOpenWindow(request.windowId(), request.departmentId());
        requireCanUseWindow(window);

        List<UUID> ids = jdbcTemplate.queryForList("""
                SELECT id
                FROM tickets
                WHERE department_id = :departmentId
                  AND service_id IN (:serviceIds)
                  AND status = 'WAITING'
                ORDER BY priority DESC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("departmentId", request.departmentId())
                        .addValue("serviceIds", request.serviceIds()),
                UUID.class);
        if (ids.isEmpty()) {
            throw notFound("NO_WAITING_TICKETS", "No waiting tickets are available");
        }
        TicketEntity ticket = ticketRepository.findById(ids.get(0))
                .orElseThrow(() -> notFound("TICKET_NOT_FOUND", "Ticket was not found"));
        return callLocked(ticket, window, httpRequest, "ticket.called");
    }

    @Transactional
    public TicketResponse start(UUID id, HttpServletRequest httpRequest) {
        return transition(id, TicketStatus.CALLED, TicketStatus.IN_SERVICE, "ticket.started", ticket -> ticket.setServiceStartedAt(Instant.now()), httpRequest);
    }

    @Transactional
    public TicketResponse pause(UUID id, PauseTicketRequest request, HttpServletRequest httpRequest) {
        if (request.pauseReasonId() != null && !pauseReasonRepository.existsById(request.pauseReasonId())) {
            throw notFound("PAUSE_REASON_NOT_FOUND", "Pause reason was not found");
        }
        return transition(id, TicketStatus.IN_SERVICE, TicketStatus.PAUSED, "ticket.paused", ticket -> {
            ticket.setPauseReasonId(request.pauseReasonId());
            ticket.setComment(request.comment());
            ticket.setServicePausedAt(Instant.now());
        }, httpRequest);
    }

    @Transactional
    public TicketResponse resume(UUID id, HttpServletRequest httpRequest) {
        return transition(id, TicketStatus.PAUSED, TicketStatus.IN_SERVICE, "ticket.resumed", ticket -> {
            ticket.setPauseReasonId(null);
            ticket.setServicePausedAt(null);
        }, httpRequest);
    }

    @Transactional
    public TicketResponse complete(UUID id, HttpServletRequest httpRequest) {
        return transition(id, TicketStatus.IN_SERVICE, TicketStatus.COMPLETED, "ticket.completed", ticket -> ticket.setServiceCompletedAt(Instant.now()), httpRequest);
    }

    @Transactional
    public TicketResponse cancel(UUID id, CancelTicketRequest request, HttpServletRequest httpRequest) {
        if (request.cancellationReasonId() == null && (request.comment() == null || request.comment().isBlank())) {
            throw badRequest("CANCELLATION_REASON_REQUIRED", "cancellationReasonId or comment is required");
        }
        if (request.cancellationReasonId() != null && !cancellationReasonRepository.existsById(request.cancellationReasonId())) {
            throw notFound("CANCELLATION_REASON_NOT_FOUND", "Cancellation reason was not found");
        }
        TicketEntity ticket = ticketRepository.findWithLockById(id)
                .orElseThrow(() -> notFound("TICKET_NOT_FOUND", "Ticket was not found"));
        if (!TicketTransitionPolicy.canTransition(ticket.getStatus(), TicketStatus.CANCELLED)) {
            throw invalidTransition(ticket.getStatus(), TicketStatus.CANCELLED);
        }
        TicketStatus from = ticket.getStatus();
        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setCancellationReasonId(request.cancellationReasonId());
        ticket.setComment(request.comment());
        ticket.setCancelledAt(Instant.now());
        return saveTransition(ticket, from, TicketStatus.CANCELLED, "ticket.cancelled", httpRequest);
    }

    @Transactional
    public TicketResponse noShow(UUID id, HttpServletRequest httpRequest) {
        return transition(id, TicketStatus.CALLED, TicketStatus.NO_SHOW, "ticket.no_show", ignored -> {
        }, httpRequest);
    }

    @Transactional
    public TicketResponse transfer(UUID id, TransferTicketRequest request, HttpServletRequest httpRequest) {
        TicketEntity ticket = ticketRepository.findWithLockById(id)
                .orElseThrow(() -> notFound("TICKET_NOT_FOUND", "Ticket was not found"));
        if (!TicketTransitionPolicy.canTransition(ticket.getStatus(), TicketStatus.TRANSFERRED)) {
            throw invalidTransition(ticket.getStatus(), TicketStatus.TRANSFERRED);
        }
        DepartmentEntity targetDepartment = departmentRepository.findById(request.targetDepartmentId())
                .orElseThrow(() -> notFound("TARGET_DEPARTMENT_NOT_FOUND", "Target department was not found"));
        QueueServiceEntity targetService = queueServiceRepository.findById(request.targetServiceId())
                .orElseThrow(() -> notFound("TARGET_SERVICE_NOT_FOUND", "Target service was not found"));
        departmentScopeService.requireDepartmentAccess(ticket.getDepartmentId());
        departmentScopeService.requireDepartmentAccess(targetDepartment.getId());
        if (!departmentServiceRepository.existsByDepartmentIdAndServiceIdAndActiveTrue(targetDepartment.getId(), targetService.getId())) {
            throw badRequest("TARGET_SERVICE_NOT_AVAILABLE", "Target service is not available in target department");
        }
        if (request.targetWindowId() != null) {
            validOpenWindow(request.targetWindowId(), targetDepartment.getId());
        }
        TicketStatus from = ticket.getStatus();
        ticket.setStatus(TicketStatus.TRANSFERRED);
        ticket.setComment(request.comment());
        writeEvent(ticket, "ticket.transferred", from, TicketStatus.TRANSFERRED, TicketActorType.USER, CurrentUser.idOrNull(), Map.of(
                "targetDepartmentId", request.targetDepartmentId(),
                "targetServiceId", request.targetServiceId(),
                "targetWindowId", request.targetWindowId() == null ? "" : request.targetWindowId().toString()
        ));
        TicketEntity saved = ticketRepository.save(ticket);
        auditService.write("TICKET_TRANSFER", "TICKET", saved.getId(), simpleJson("status", "TRANSFERRED"), httpRequest);
        ticketDomainEventPublisher.publish("ticket.transferred", saved);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public TvSnapshotResponse tvSnapshot(UUID departmentId) {
        departmentScopeService.requireDepartmentAccess(departmentId);
        return tvSnapshotUnchecked(departmentId);
    }

    @Transactional(readOnly = true)
    public TvSnapshotResponse tvSnapshotForDevice(UUID departmentId) {
        return tvSnapshotUnchecked(departmentId);
    }

    private TvSnapshotResponse tvSnapshotUnchecked(UUID departmentId) {
        List<TicketResponse> tickets = ticketRepository
                .findTop20ByDepartmentIdAndStatusInOrderByCalledAtDescCreatedAtDesc(departmentId, TV_STATUSES)
                .stream()
                .map(this::response)
                .toList();
        return new TvSnapshotResponse(departmentId, tickets, Instant.now());
    }

    private TicketResponse callLocked(TicketEntity ticket, ServiceWindowEntity window, HttpServletRequest httpRequest, String eventType) {
        if (ticket.getStatus() != TicketStatus.WAITING) {
            throw invalidTransition(ticket.getStatus(), TicketStatus.CALLED);
        }
        if (!TicketTransitionPolicy.canTransition(ticket.getStatus(), TicketStatus.CALLED)) {
            throw invalidTransition(ticket.getStatus(), TicketStatus.CALLED);
        }
        ticket.setStatus(TicketStatus.CALLED);
        ticket.setWindowId(window.getId());
        ticket.setHallId(window.getHallId());
        ticket.setServedByUserId(CurrentUser.idOrNull());
        ticket.setCalledAt(Instant.now());
        return saveTransition(ticket, TicketStatus.WAITING, TicketStatus.CALLED, eventType, httpRequest);
    }

    private TicketResponse transition(UUID id, TicketStatus expected, TicketStatus target, String eventType, TicketMutator mutator, HttpServletRequest httpRequest) {
        TicketEntity ticket = ticketRepository.findWithLockById(id)
                .orElseThrow(() -> notFound("TICKET_NOT_FOUND", "Ticket was not found"));
        departmentScopeService.requireDepartmentAccess(ticket.getDepartmentId());
        if (ticket.getStatus() != expected) {
            throw invalidTransition(ticket.getStatus(), target);
        }
        if (!TicketTransitionPolicy.canTransition(ticket.getStatus(), target)) {
            throw invalidTransition(ticket.getStatus(), target);
        }
        ticket.setStatus(target);
        mutator.apply(ticket);
        return saveTransition(ticket, expected, target, eventType, httpRequest);
    }

    private TicketResponse saveTransition(TicketEntity ticket, TicketStatus from, TicketStatus to, String eventType, HttpServletRequest httpRequest) {
        writeEvent(ticket, eventType, from, to, TicketActorType.USER, CurrentUser.idOrNull(), Map.of());
        TicketEntity saved = ticketRepository.save(ticket);
        auditService.write(eventType.toUpperCase().replace('.', '_'), "TICKET", saved.getId(), simpleJson("status", to.name()), httpRequest);
        ticketDomainEventPublisher.publish(eventType, saved);
        return response(saved);
    }

    private void writeEvent(TicketEntity ticket, String eventType, TicketStatus from, TicketStatus to, TicketActorType actorType, UUID actorId, Map<String, ?> payload) {
        TicketEventEntity event = new TicketEventEntity();
        event.setTicketId(ticket.getId());
        event.setEventType(eventType);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setDepartmentId(ticket.getDepartmentId());
        event.setWindowId(ticket.getWindowId());
        event.setPayload(toJson(payload));
        ticketEventRepository.save(event);
    }

    private void validateSourceAllowed(TicketSource source, DepartmentServiceEntity departmentService) {
        boolean allowed = switch (source) {
            case TERMINAL -> departmentService.isTerminalEnabled();
            case QR_SELF_SERVICE -> departmentService.isQrEnabled();
            case WEBSITE_CABINET, TUNDUK -> departmentService.isOnlineBookingEnabled();
            case CRM, CRM_ZENOSS, ADMIN_CREATED -> departmentService.isActive();
        };
        if (!allowed) {
            throw badRequest("TICKET_SOURCE_NOT_ALLOWED", "Ticket source is not allowed for this service and department");
        }
    }

    private TicketResponse readReplay(String body) {
        try {
            return objectMapper.readValue(body, TicketResponse.class);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_PROCESSING", "Stored idempotent response is unavailable");
        }
    }

    private ServiceWindowEntity validOpenWindow(UUID windowId, UUID departmentId) {
        ServiceWindowEntity window = serviceWindowRepository.findById(windowId)
                .orElseThrow(() -> notFound("WINDOW_NOT_FOUND", "Window was not found"));
        if (!window.getDepartmentId().equals(departmentId)) {
            throw badRequest("WINDOW_DEPARTMENT_MISMATCH", "Window does not belong to ticket department");
        }
        if (!window.isActive() || window.getStatus() != WindowStatus.OPEN) {
            throw badRequest("WINDOW_NOT_OPEN", "Window is not active/open");
        }
        return window;
    }

    private void requireCanUseWindow(ServiceWindowEntity window) {
        if (CurrentUser.hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_ADMIN")) {
            return;
        }
        if (CurrentUser.hasAuthority("ROLE_DEPARTMENT_MANAGER")) {
            departmentScopeService.requireDepartmentAccess(window.getDepartmentId());
            return;
        }
        departmentScopeService.requireWindowAccess(window.getId());
    }

    private TicketEntity ticketOrThrow(UUID id) {
        return ticketRepository.findById(id).orElseThrow(() -> notFound("TICKET_NOT_FOUND", "Ticket was not found"));
    }

    private TicketResponse response(TicketEntity ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTicketPrefix(),
                ticket.getSequenceNumber(),
                ticket.getWorkDate(),
                ticket.getRegionId(),
                ticket.getDepartmentId(),
                ticket.getOfficeRoomId(),
                ticket.getHallId(),
                ticket.getWindowId(),
                ticket.getCategoryId(),
                ticket.getServiceId(),
                ticket.getCitizenFullName(),
                ticket.getCitizenPin(),
                ticket.getCitizenPhone(),
                ticket.getSource(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getCalledAt(),
                ticket.getServiceStartedAt(),
                ticket.getServicePausedAt(),
                ticket.getServiceCompletedAt(),
                ticket.getCancelledAt(),
                ticket.getCancellationReasonId(),
                ticket.getPauseReasonId(),
                ticket.getServedByUserId(),
                ticket.getComment(),
                ticket.getVersion()
        );
    }

    private ApiException invalidTransition(TicketStatus from, TicketStatus to) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_TICKET_STATUS_TRANSITION",
                "Invalid ticket status transition", Map.of("from", from, "to", to));
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String simpleJson(String key, String value) {
        return "{\"" + key + "\":\"" + value + "\"}";
    }

    private interface TicketMutator {
        void apply(TicketEntity ticket);
    }
}
