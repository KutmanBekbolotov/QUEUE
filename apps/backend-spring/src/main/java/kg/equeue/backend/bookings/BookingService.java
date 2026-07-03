package kg.equeue.backend.bookings;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.bookings.BookingDtos.AvailableDatesResponse;
import kg.equeue.backend.bookings.BookingDtos.BookingResponse;
import kg.equeue.backend.bookings.BookingDtos.CancelBookingRequest;
import kg.equeue.backend.bookings.BookingDtos.CreateBookingRequest;
import kg.equeue.backend.bookings.BookingDtos.GenerateSlotsRequest;
import kg.equeue.backend.bookings.BookingDtos.GenerateSlotsResponse;
import kg.equeue.backend.bookings.BookingDtos.SlotResponse;
import kg.equeue.backend.bookingslots.BookingSlotEntity;
import kg.equeue.backend.bookingslots.BookingSlotRepository;
import kg.equeue.backend.bookingslots.BookingSlotStatus;
import kg.equeue.backend.cancellationreasons.CancellationReasonRepository;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.CurrentUser;
import kg.equeue.backend.common.DepartmentScopeService;
import kg.equeue.backend.departmentservices.DepartmentServiceEntity;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.departments.DepartmentEntity;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.integrationclients.IntegrationIdempotencyService;
import kg.equeue.backend.integrationclients.IntegrationRequestEntity;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.ticketevents.TicketActorType;
import kg.equeue.backend.tickets.TicketDtos.CreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import kg.equeue.backend.tickets.TicketRepository;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSource;
import kg.equeue.backend.tickets.TicketStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BookingEventRepository bookingEventRepository;
    private final BookingDomainEventPublisher bookingDomainEventPublisher;
    private final DepartmentRepository departmentRepository;
    private final QueueServiceRepository queueServiceRepository;
    private final DepartmentServiceRepository departmentServiceRepository;
    private final CancellationReasonRepository cancellationReasonRepository;
    private final DepartmentScopeService departmentScopeService;
    private final IntegrationIdempotencyService idempotencyService;
    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BookingService(BookingRepository bookingRepository,
                          BookingSlotRepository bookingSlotRepository,
                          BookingEventRepository bookingEventRepository,
                          BookingDomainEventPublisher bookingDomainEventPublisher,
                          DepartmentRepository departmentRepository,
                          QueueServiceRepository queueServiceRepository,
                          DepartmentServiceRepository departmentServiceRepository,
                          CancellationReasonRepository cancellationReasonRepository,
                          DepartmentScopeService departmentScopeService,
                          IntegrationIdempotencyService idempotencyService,
                          TicketService ticketService,
                          TicketRepository ticketRepository,
                          AuditService auditService,
                          JdbcTemplate jdbcTemplate,
                          ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingSlotRepository = bookingSlotRepository;
        this.bookingEventRepository = bookingEventRepository;
        this.bookingDomainEventPublisher = bookingDomainEventPublisher;
        this.departmentRepository = departmentRepository;
        this.queueServiceRepository = queueServiceRepository;
        this.departmentServiceRepository = departmentServiceRepository;
        this.cancellationReasonRepository = cancellationReasonRepository;
        this.departmentScopeService = departmentScopeService;
        this.idempotencyService = idempotencyService;
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AvailableDatesResponse availableDates(UUID departmentId, UUID serviceId, LocalDate fromDate, LocalDate toDate, BookingSource source) {
        validateAvailability(departmentId, serviceId, source == null ? BookingSource.WEBSITE_CABINET : source, true);
        LocalDate from = fromDate == null || fromDate.isBefore(LocalDate.now()) ? LocalDate.now() : fromDate;
        LocalDate to = toDate == null ? from.plusDays(30) : toDate;
        Set<LocalDate> holidays = holidays(departmentId, from, to);
        List<LocalDate> dates = bookingSlotRepository
                .findByDepartmentIdAndServiceIdAndSlotDateBetweenOrderBySlotDateAscSlotStartAsc(departmentId, serviceId, from, to)
                .stream()
                .filter(slot -> !holidays.contains(slot.getSlotDate()))
                .filter(slot -> slot.getStatus() == BookingSlotStatus.ACTIVE)
                .filter(BookingSlotEntity::hasCapacity)
                .filter(slot -> !isPastSlot(slot))
                .map(BookingSlotEntity::getSlotDate)
                .distinct()
                .toList();
        return new AvailableDatesResponse(departmentId, serviceId, dates);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> slots(UUID departmentId, UUID serviceId, LocalDate date, BookingSource source) {
        validateAvailability(departmentId, serviceId, source == null ? BookingSource.WEBSITE_CABINET : source, true);
        if (date.isBefore(LocalDate.now())) {
            return List.of();
        }
        if (isHoliday(departmentId, date)) {
            return List.of();
        }
        return bookingSlotRepository.findByDepartmentIdAndServiceIdAndSlotDateOrderBySlotStartAsc(departmentId, serviceId, date)
                .stream()
                .filter(slot -> slot.getStatus() == BookingSlotStatus.ACTIVE)
                .filter(BookingSlotEntity::hasCapacity)
                .filter(slot -> !isPastSlot(slot))
                .map(this::slotResponse)
                .toList();
    }

    @Transactional
    public GenerateSlotsResponse generateSlots(GenerateSlotsRequest request, HttpServletRequest httpRequest) {
        departmentScopeService.requireDepartmentAccess(request.departmentId());
        validateAvailability(request.departmentId(), request.serviceId(), BookingSource.ADMIN_CREATED, false);
        int created = 0;
        int skipped = 0;
        int disabled = 0;
        for (LocalDate date = request.fromDate(); !date.isAfter(request.toDate()); date = date.plusDays(1)) {
            if (date.isBefore(LocalDate.now()) || isHoliday(request.departmentId(), date)) {
                skipped++;
                continue;
            }
            WorkDay workDay = workDay(request.departmentId(), date.getDayOfWeek());
            if (workDay == null) {
                skipped++;
                continue;
            }
            LocalTime cursor = workDay.opensAt();
            while (!cursor.plusMinutes(request.intervalMinutes()).isAfter(workDay.closesAt())) {
                LocalTime end = cursor.plusMinutes(request.intervalMinutes());
                if (!insideBreak(cursor, end, workDay)) {
                    if (request.overwrite()) {
                        disabled += replaceFutureEmptySlot(request.departmentId(), request.serviceId(), date, cursor);
                    }
                    if (bookingSlotRepository.existsByDepartmentIdAndServiceIdAndSlotDateAndSlotStart(request.departmentId(), request.serviceId(), date, cursor)) {
                        skipped++;
                    } else {
                        BookingSlotEntity slot = new BookingSlotEntity();
                        slot.setDepartmentId(request.departmentId());
                        slot.setServiceId(request.serviceId());
                        slot.setSlotDate(date);
                        slot.setSlotStart(cursor);
                        slot.setSlotEnd(end);
                        slot.setCapacity(request.capacity());
                        bookingSlotRepository.save(slot);
                        created++;
                    }
                }
                cursor = end;
            }
        }
        auditService.write("BOOKING_SLOTS_GENERATE", "BOOKING_SLOT", null, "{\"created\":\"" + created + "\"}", httpRequest);
        return new GenerateSlotsResponse(created, skipped, disabled);
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request, HttpServletRequest httpRequest) {
        if (request.source() != BookingSource.ADMIN_CREATED) {
            String clientCode = idempotencyService.clientCode(httpRequest, request.source().name());
            String key = idempotencyService.idempotencyKey(httpRequest, request.idempotencyKey());
            String externalRequestId = idempotencyService.externalRequestId(httpRequest, request.externalId(), key);
            IntegrationIdempotencyService.BeginResult begin = idempotencyService.begin(clientCode, key, externalRequestId, request);
            if (begin.replay()) {
                return readReplay(begin.responseBody());
            }
            BookingResponse response = createInternal(request, key, TicketActorType.INTEGRATION, null, httpRequest);
            idempotencyService.complete(begin.entity(), response, 200);
            return response;
        }
        return createInternal(request, request.idempotencyKey(), TicketActorType.USER, CurrentUser.idOrNull(), httpRequest);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        BookingEntity booking = bookingRepository.findById(id).orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        departmentScopeService.requireDepartmentAccess(booking.getDepartmentId());
        return response(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse byToken(String qrToken) {
        BookingEntity booking = bookingRepository.findByQrToken(qrToken).orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        departmentScopeService.requireDepartmentAccess(booking.getDepartmentId());
        return response(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse byExternal(BookingSource source, String externalId) {
        BookingEntity booking = bookingRepository.findByExternalSourceAndExternalId(source, externalId)
                .orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        departmentScopeService.requireDepartmentAccess(booking.getDepartmentId());
        return response(booking);
    }

    @Transactional
    public BookingResponse cancel(UUID id, CancelBookingRequest request, HttpServletRequest httpRequest) {
        BookingEntity booking = bookingRepository.findWithLockById(id).orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        departmentScopeService.requireDepartmentAccess(booking.getDepartmentId());
        return cancelLocked(booking, request, TicketActorType.USER, CurrentUser.idOrNull(), httpRequest);
    }

    @Transactional
    public BookingResponse cancelExternal(BookingSource source, String externalId, CancelBookingRequest request, HttpServletRequest httpRequest) {
        BookingEntity booking = bookingRepository.findByExternalSourceAndExternalId(source, externalId)
                .orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        String clientCode = idempotencyService.clientCode(httpRequest, source.name());
        String key = idempotencyService.idempotencyKey(httpRequest, request == null ? null : request.idempotencyKey());
        String externalRequestId = idempotencyService.externalRequestId(httpRequest, null, key);
        IntegrationIdempotencyService.BeginResult begin = idempotencyService.begin(clientCode, key, externalRequestId, request == null ? Map.of("externalId", externalId) : request);
        if (begin.replay()) {
            return readReplay(begin.responseBody());
        }
        BookingEntity locked = bookingRepository.findWithLockById(booking.getId())
                .orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        BookingResponse response = cancelLocked(locked,
                request == null ? new CancelBookingRequest(null, "External cancellation", externalId, key) : request,
                TicketActorType.INTEGRATION,
                null,
                httpRequest);
        idempotencyService.complete(begin.entity(), response, 200);
        return response;
    }

    @Transactional
    public BookingResponse checkIn(UUID id, HttpServletRequest httpRequest) {
        BookingEntity booking = bookingRepository.findWithLockById(id).orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        departmentScopeService.requireDepartmentAccess(booking.getDepartmentId());
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_INVALID_STATUS", "Only CONFIRMED booking can be checked in");
        }
        LocalDateTime slotStart = LocalDateTime.of(booking.getBookingDate(), booking.getBookingStart());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(slotStart.minusMinutes(30))) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_CHECK_IN_TOO_EARLY", "Booking check-in window has not opened");
        }
        if (now.isAfter(slotStart.plusMinutes(15))) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_CHECK_IN_TOO_LATE", "Booking check-in window has closed");
        }
        TicketActorType ticketActorType = CurrentUser.hasAuthority("ROLE_INTEGRATION_SERVICE") ? TicketActorType.INTEGRATION : TicketActorType.USER;
        UUID ticketActorId = ticketActorType == TicketActorType.INTEGRATION ? null : CurrentUser.idOrNull();
        TicketResponse ticket = ticketService.create(new CreateTicketRequest(
                booking.getDepartmentId(),
                booking.getServiceId(),
                booking.getId(),
                booking.getCitizenFullName(),
                booking.getCitizenPin(),
                booking.getCitizenPhone(),
                ticketSource(booking.getExternalSource()),
                "Booking check-in " + booking.getBookingNumber(),
                null,
                null
        ), ticketActorType, ticketActorId, httpRequest);
        BookingStatus from = booking.getStatus();
        booking.setTicketId(ticket.id());
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(Instant.now());
        BookingEntity saved = bookingRepository.save(booking);
        writeEvent(saved, "booking.checked_in", from, BookingStatus.CHECKED_IN, TicketActorType.USER, CurrentUser.idOrNull(), Map.of("ticketId", ticket.id()));
        auditService.write("BOOKING_CHECK_IN", "BOOKING", saved.getId(), simpleJson("status", "CHECKED_IN"), httpRequest);
        bookingDomainEventPublisher.publish("booking.checked_in", saved);
        return response(saved);
    }

    @Transactional
    public BookingResponse expire(UUID id, HttpServletRequest httpRequest) {
        BookingEntity booking = bookingRepository.findWithLockById(id).orElseThrow(() -> notFound("BOOKING_NOT_FOUND", "Booking was not found"));
        departmentScopeService.requireDepartmentAccess(booking.getDepartmentId());
        return expireLocked(booking, TicketActorType.USER, CurrentUser.idOrNull(), httpRequest);
    }

    @Transactional
    public SlotResponse disableSlot(UUID id, HttpServletRequest httpRequest) {
        BookingSlotEntity slot = bookingSlotRepository.findWithLockById(id).orElseThrow(() -> notFound("BOOKING_SLOT_NOT_FOUND", "Booking slot was not found"));
        departmentScopeService.requireDepartmentAccess(slot.getDepartmentId());
        slot.setStatus(BookingSlotStatus.DISABLED);
        BookingSlotEntity saved = bookingSlotRepository.save(slot);
        auditService.write("BOOKING_SLOT_DISABLE", "BOOKING_SLOT", saved.getId(), simpleJson("status", "DISABLED"), httpRequest);
        return slotResponse(saved);
    }

    @Transactional
    public SlotResponse enableSlot(UUID id, HttpServletRequest httpRequest) {
        BookingSlotEntity slot = bookingSlotRepository.findWithLockById(id).orElseThrow(() -> notFound("BOOKING_SLOT_NOT_FOUND", "Booking slot was not found"));
        departmentScopeService.requireDepartmentAccess(slot.getDepartmentId());
        slot.setStatus(slot.getBookedCount() >= slot.getCapacity() ? BookingSlotStatus.FULL : BookingSlotStatus.ACTIVE);
        BookingSlotEntity saved = bookingSlotRepository.save(slot);
        auditService.write("BOOKING_SLOT_ENABLE", "BOOKING_SLOT", saved.getId(), simpleJson("status", saved.getStatus().name()), httpRequest);
        return slotResponse(saved);
    }

    @Scheduled(fixedDelayString = "${app.booking.expire-job-delay-ms:300000}")
    @Transactional
    public void expireOldConfirmedBookings() {
        LocalDate today = LocalDate.now();
        LocalTime threshold = LocalTime.now().minusMinutes(15);
        List<BookingEntity> candidates = new ArrayList<>(bookingRepository.findTop100ByStatusAndBookingDateLessThanEqualOrderByBookingDateAscBookingStartAsc(BookingStatus.CONFIRMED, today));
        for (BookingEntity booking : candidates) {
            if (booking.getBookingDate().isBefore(today) || !booking.getBookingStart().isAfter(threshold)) {
                expireLocked(booking, TicketActorType.SYSTEM, null, null);
            }
        }
    }

    private BookingResponse createInternal(CreateBookingRequest request, String idempotencyKey, TicketActorType actorType, UUID actorId, HttpServletRequest httpRequest) {
        validateAvailability(request.departmentId(), request.serviceId(), request.source(), true);
        if (request.externalId() != null && !request.externalId().isBlank()) {
            bookingRepository.findByExternalSourceAndExternalId(request.source(), request.externalId()).ifPresent(existing -> {
                throw new ApiException(HttpStatus.CONFLICT, "BOOKING_EXTERNAL_ID_DUPLICATE", "Booking external id already exists");
            });
        }
        BookingSlotEntity slot = bookingSlotRepository.findWithLockById(request.slotId())
                .orElseThrow(() -> notFound("BOOKING_SLOT_NOT_FOUND", "Booking slot was not found"));
        if (!slot.getDepartmentId().equals(request.departmentId()) || !slot.getServiceId().equals(request.serviceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKING_SLOT_NOT_FOUND", "Booking slot does not belong to requested department/service");
        }
        if (isPastSlot(slot)) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_SLOT_IN_PAST", "Booking slot is in the past");
        }
        if (!slot.hasCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_SLOT_NOT_AVAILABLE", "Booking slot is not available");
        }
        slot.incrementBookedCount();
        bookingSlotRepository.save(slot);

        BookingEntity booking = new BookingEntity();
        booking.setBookingNumber("B-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        booking.setDepartmentId(request.departmentId());
        booking.setServiceId(request.serviceId());
        booking.setSlotId(slot.getId());
        booking.setBookingDate(slot.getSlotDate());
        booking.setBookingStart(slot.getSlotStart());
        booking.setBookingEnd(slot.getSlotEnd());
        booking.setCitizenFullName(request.citizenFullName());
        booking.setCitizenPin(request.citizenPin());
        booking.setCitizenPhone(request.citizenPhone());
        booking.setVehicleNumber(request.vehicleNumber());
        booking.setExternalSource(request.source());
        booking.setExternalId(request.externalId());
        booking.setIdempotencyKey(idempotencyKey);
        booking.setQrToken(UUID.randomUUID().toString() + "-" + UUID.randomUUID());
        booking.setStatus(BookingStatus.CONFIRMED);
        BookingEntity saved = bookingRepository.save(booking);
        writeEvent(saved, "booking.created", null, BookingStatus.CONFIRMED, actorType, actorId, Map.of("slotId", slot.getId()));
        auditService.write("BOOKING_CREATE", "BOOKING", saved.getId(), simpleJson("bookingNumber", saved.getBookingNumber()), httpRequest);
        bookingDomainEventPublisher.publish("booking.created", saved);
        return response(saved);
    }

    private BookingResponse cancelLocked(BookingEntity booking, CancelBookingRequest request, TicketActorType actorType, UUID actorId, HttpServletRequest httpRequest) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_CANCELLED", "Booking is already cancelled");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.CREATED) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_INVALID_STATUS", "Only CREATED or CONFIRMED booking can be cancelled");
        }
        if (request != null && request.cancellationReasonId() != null && !cancellationReasonRepository.existsById(request.cancellationReasonId())) {
            throw notFound("CANCELLATION_REASON_NOT_FOUND", "Cancellation reason was not found");
        }
        BookingStatus from = booking.getStatus();
        BookingSlotEntity slot = booking.getSlotId() == null ? null : bookingSlotRepository.findWithLockById(booking.getSlotId()).orElse(null);
        if (slot != null) {
            slot.decrementBookedCount();
            bookingSlotRepository.save(slot);
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancellationReasonId(request == null ? null : request.cancellationReasonId());
        booking.setCancelComment(request == null ? null : request.comment());
        BookingEntity saved = bookingRepository.save(booking);
        writeEvent(saved, "booking.cancelled", from, BookingStatus.CANCELLED, actorType, actorId, Map.of());
        auditService.write("BOOKING_CANCEL", "BOOKING", saved.getId(), simpleJson("status", "CANCELLED"), httpRequest);
        bookingDomainEventPublisher.publish("booking.cancelled", saved);
        return response(saved);
    }

    private BookingResponse expireLocked(BookingEntity booking, TicketActorType actorType, UUID actorId, HttpServletRequest httpRequest) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_INVALID_STATUS", "Only CONFIRMED booking can expire");
        }
        BookingStatus from = booking.getStatus();
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setExpiredAt(Instant.now());
        BookingEntity saved = bookingRepository.save(booking);
        writeEvent(saved, "booking.expired", from, BookingStatus.EXPIRED, actorType, actorId, Map.of());
        auditService.write("BOOKING_EXPIRE", "BOOKING", saved.getId(), simpleJson("status", "EXPIRED"), httpRequest);
        bookingDomainEventPublisher.publish("booking.expired", saved);
        return response(saved);
    }

    private void validateAvailability(UUID departmentId, UUID serviceId, BookingSource source, boolean requireOnline) {
        DepartmentEntity department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> notFound("DEPARTMENT_NOT_FOUND", "Department was not found"));
        departmentScopeService.requireDepartmentAccess(departmentId);
        if (!department.isActive() || department.isClosed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEPARTMENT_NOT_AVAILABLE", "Department is not available");
        }
        QueueServiceEntity service = queueServiceRepository.findById(serviceId)
                .orElseThrow(() -> notFound("SERVICE_NOT_FOUND", "Service was not found"));
        if (!service.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_NOT_AVAILABLE", "Service is not available");
        }
        DepartmentServiceEntity departmentService = departmentServiceRepository.findByDepartmentIdAndServiceId(departmentId, serviceId)
                .filter(DepartmentServiceEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_NOT_AVAILABLE", "Service is not available in department"));
        if (requireOnline && source != BookingSource.ADMIN_CREATED && !departmentService.isOnlineBookingEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SERVICE_NOT_AVAILABLE_FOR_SOURCE", "Service is not available for requested source");
        }
    }

    private WorkDay workDay(UUID departmentId, DayOfWeek dayOfWeek) {
        List<WorkDay> rows = jdbcTemplate.query("""
                SELECT opens_at, closes_at, break_starts_at, break_ends_at
                FROM department_working_hours
                WHERE department_id = ? AND day_of_week = ? AND active = true
                ORDER BY opens_at
                """,
                (rs, rowNum) -> new WorkDay(rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class),
                        rs.getObject("break_starts_at", LocalTime.class), rs.getObject("break_ends_at", LocalTime.class)),
                departmentId, dayOfWeek.getValue());
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return null;
        }
        return new WorkDay(LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(13, 0), LocalTime.of(14, 0));
    }

    private boolean insideBreak(LocalTime start, LocalTime end, WorkDay workDay) {
        return workDay.breakStartsAt() != null && workDay.breakEndsAt() != null
                && start.isBefore(workDay.breakEndsAt()) && end.isAfter(workDay.breakStartsAt());
    }

    private int replaceFutureEmptySlot(UUID departmentId, UUID serviceId, LocalDate date, LocalTime start) {
        List<BookingSlotEntity> slots = bookingSlotRepository.findByDepartmentIdAndServiceIdAndSlotDateOrderBySlotStartAsc(departmentId, serviceId, date)
                .stream()
                .filter(slot -> slot.getSlotStart().equals(start))
                .filter(slot -> slot.getBookedCount() == 0)
                .filter(slot -> !isPastSlot(slot))
                .toList();
        for (BookingSlotEntity slot : slots) {
            bookingSlotRepository.delete(slot);
        }
        if (!slots.isEmpty()) {
            bookingSlotRepository.flush();
        }
        return slots.size();
    }

    private boolean isHoliday(UUID departmentId, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM department_holidays WHERE department_id = ? AND holiday_date = ?",
                Integer.class, departmentId, date);
        return count != null && count > 0;
    }

    private Set<LocalDate> holidays(UUID departmentId, LocalDate from, LocalDate to) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT holiday_date FROM department_holidays WHERE department_id = ? AND holiday_date BETWEEN ? AND ?",
                (rs, rowNum) -> rs.getObject("holiday_date", LocalDate.class),
                departmentId, from, to));
    }

    private boolean isPastSlot(BookingSlotEntity slot) {
        return LocalDateTime.of(slot.getSlotDate(), slot.getSlotStart()).isBefore(LocalDateTime.now());
    }

    private void writeEvent(BookingEntity booking, String eventType, BookingStatus from, BookingStatus to, TicketActorType actorType, UUID actorId, Map<String, ?> payload) {
        BookingEventEntity event = new BookingEventEntity();
        event.setBookingId(booking.getId());
        event.setEventType(eventType);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setDepartmentId(booking.getDepartmentId());
        event.setServiceId(booking.getServiceId());
        event.setPayload(toJson(payload));
        bookingEventRepository.save(event);
    }

    private SlotResponse slotResponse(BookingSlotEntity slot) {
        return new SlotResponse(slot.getId(), slot.getDepartmentId(), slot.getServiceId(), slot.getSlotDate(), slot.getSlotStart(), slot.getSlotEnd(),
                slot.getCapacity(), slot.getBookedCount(), Math.max(0, slot.getCapacity() - slot.getBookedCount()), slot.getStatus());
    }

    private BookingResponse response(BookingEntity booking) {
        TicketInfo ticketInfo = booking.getTicketId() == null
                ? new TicketInfo(null, null)
                : ticketRepository.findById(booking.getTicketId())
                        .map(ticket -> new TicketInfo(ticket.getTicketNumber(), ticket.getStatus()))
                        .orElse(new TicketInfo(null, null));
        return new BookingResponse(booking.getId(), booking.getBookingNumber(), booking.getStatus(), booking.getDepartmentId(), booking.getServiceId(),
                booking.getSlotId(), booking.getBookingDate(), booking.getBookingStart(), booking.getBookingEnd(), booking.getExternalSource(),
                booking.getExternalId(), booking.getQrToken(), booking.getTicketId(), ticketInfo.ticketNumber(), ticketInfo.ticketStatus(),
                booking.getCreatedAt(), booking.getUpdatedAt(), booking.getCancelledAt(), booking.getCheckedInAt(), booking.getExpiredAt());
    }

    private BookingResponse readReplay(String body) {
        try {
            return objectMapper.readValue(body, BookingResponse.class);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_PROCESSING", "Stored idempotent response is unavailable");
        }
    }

    private TicketSource ticketSource(BookingSource source) {
        if (source == null) {
            return TicketSource.ADMIN_CREATED;
        }
        return switch (source) {
            case WEBSITE_CABINET -> TicketSource.WEBSITE_CABINET;
            case TUNDUK -> TicketSource.TUNDUK;
            case CRM -> TicketSource.CRM;
            case CRM_ZENOSS -> TicketSource.CRM_ZENOSS;
            case ADMIN_CREATED -> TicketSource.ADMIN_CREATED;
        };
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String simpleJson(String key, String value) {
        return "{\"" + key + "\":\"" + value + "\"}";
    }

    private record WorkDay(LocalTime opensAt, LocalTime closesAt, LocalTime breakStartsAt, LocalTime breakEndsAt) {
    }

    private record TicketInfo(String ticketNumber, TicketStatus ticketStatus) {
    }
}
