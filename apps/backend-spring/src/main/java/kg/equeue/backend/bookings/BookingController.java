package kg.equeue.backend.bookings;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.bookings.BookingDtos.AvailableDatesResponse;
import kg.equeue.backend.bookings.BookingDtos.BookingResponse;
import kg.equeue.backend.bookings.BookingDtos.CancelBookingRequest;
import kg.equeue.backend.bookings.BookingDtos.CreateBookingRequest;
import kg.equeue.backend.bookings.BookingDtos.GenerateSlotsRequest;
import kg.equeue.backend.bookings.BookingDtos.GenerateSlotsResponse;
import kg.equeue.backend.bookings.BookingDtos.SlotResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/booking")
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/available-dates")
    @PreAuthorize("hasAuthority('BOOKING_SLOT_READ') or hasAuthority('BOOKING_READ')")
    AvailableDatesResponse availableDates(@RequestParam UUID departmentId,
                                          @RequestParam UUID serviceId,
                                          @RequestParam(required = false) UUID regionId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                          @RequestParam(required = false) BookingSource source) {
        return bookingService.availableDates(departmentId, serviceId, fromDate, toDate, source);
    }

    @GetMapping("/slots")
    @PreAuthorize("hasAuthority('BOOKING_SLOT_READ') or hasAuthority('BOOKING_READ')")
    List<SlotResponse> slots(@RequestParam UUID departmentId,
                             @RequestParam UUID serviceId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(required = false) BookingSource source) {
        return bookingService.slots(departmentId, serviceId, date, source);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    BookingResponse create(@Valid @RequestBody CreateBookingRequest request, HttpServletRequest httpRequest) {
        return bookingService.create(request, httpRequest);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    BookingResponse get(@PathVariable UUID id) {
        return bookingService.get(id);
    }

    @GetMapping("/by-token/{qrToken}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    BookingResponse byToken(@PathVariable String qrToken) {
        return bookingService.byToken(qrToken);
    }

    @GetMapping("/external/{source}/{externalId}")
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    BookingResponse byExternal(@PathVariable BookingSource source, @PathVariable String externalId) {
        return bookingService.byExternal(source, externalId);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('BOOKING_CANCEL')")
    BookingResponse cancel(@PathVariable UUID id,
                           @RequestBody(required = false) CancelBookingRequest request,
                           HttpServletRequest httpRequest) {
        return bookingService.cancel(id, request == null ? new CancelBookingRequest(null, null, null, null) : request, httpRequest);
    }

    @PostMapping("/external/{source}/{externalId}/cancel")
    @PreAuthorize("hasAuthority('BOOKING_CANCEL')")
    BookingResponse cancelExternal(@PathVariable BookingSource source,
                                   @PathVariable String externalId,
                                   @RequestBody(required = false) CancelBookingRequest request,
                                   HttpServletRequest httpRequest) {
        return bookingService.cancelExternal(source, externalId, request, httpRequest);
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('BOOKING_CHECK_IN')")
    BookingResponse checkIn(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return bookingService.checkIn(id, httpRequest);
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasAuthority('BOOKING_CANCEL')")
    BookingResponse expire(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return bookingService.expire(id, httpRequest);
    }

    @PostMapping("/slots/generate")
    @PreAuthorize("hasAuthority('BOOKING_SLOT_MANAGE')")
    GenerateSlotsResponse generateSlots(@Valid @RequestBody GenerateSlotsRequest request, HttpServletRequest httpRequest) {
        return bookingService.generateSlots(request, httpRequest);
    }

    @PostMapping("/slots/{id}/disable")
    @PreAuthorize("hasAuthority('BOOKING_SLOT_MANAGE')")
    SlotResponse disableSlot(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return bookingService.disableSlot(id, httpRequest);
    }

    @PostMapping("/slots/{id}/enable")
    @PreAuthorize("hasAuthority('BOOKING_SLOT_MANAGE')")
    SlotResponse enableSlot(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return bookingService.enableSlot(id, httpRequest);
    }
}
