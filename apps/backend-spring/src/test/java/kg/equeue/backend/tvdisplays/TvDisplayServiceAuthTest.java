package kg.equeue.backend.tvdisplays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.tickets.TicketService;
import kg.equeue.backend.tickets.TicketSseService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class TvDisplayServiceAuthTest {

    private final TvDisplayRepository repository = org.mockito.Mockito.mock(TvDisplayRepository.class);
    private final DeviceTokenService deviceTokenService = new DeviceTokenService();
    private final TicketService ticketService = org.mockito.Mockito.mock(TicketService.class);
    private final TicketSseService ticketSseService = org.mockito.Mockito.mock(TicketSseService.class);
    private final TvDisplayService service = new TvDisplayService(
            repository,
            deviceTokenService,
            ticketService,
            ticketSseService
    );

    @Test
    void displayEndpointAuthenticatesTheRequestedDisplayId() {
        UUID departmentId = UUID.randomUUID();
        TvDisplayEntity display = display(UUID.randomUUID(), departmentId, "TV-2", "token-2");
        when(repository.findById(display.getId())).thenReturn(Optional.of(display));
        MockHttpServletRequest request = requestWithToken("token-2");

        service.snapshot(display.getId(), request);

        assertThat(display.getLastSeenAt()).isNotNull();
        verify(repository).save(display);
        verify(ticketService).tvSnapshotForDevice(departmentId);
    }

    @Test
    void legacyDepartmentEndpointAcceptsTokenOfAnyActiveDisplay() {
        UUID departmentId = UUID.randomUUID();
        TvDisplayEntity first = display(UUID.randomUUID(), departmentId, "TV-1", "token-1");
        TvDisplayEntity second = display(UUID.randomUUID(), departmentId, "TV-2", "token-2");
        when(repository.findByDepartmentIdAndActiveTrueOrderByCodeAsc(departmentId))
                .thenReturn(List.of(first, second));

        service.legacySnapshot(departmentId, requestWithToken("token-2"));

        assertThat(first.getLastSeenAt()).isNull();
        assertThat(second.getLastSeenAt()).isNotNull();
        verify(repository).save(second);
        verify(ticketService).tvSnapshotForDevice(departmentId);
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Token", token);
        return request;
    }

    private TvDisplayEntity display(UUID id, UUID departmentId, String code, String token) {
        TvDisplayEntity display = new TvDisplayEntity();
        ReflectionTestUtils.setField(display, "id", id);
        display.setDepartmentId(departmentId);
        display.setCode(code);
        display.setName(code);
        display.setTokenHash(deviceTokenService.hash(token));
        display.setActive(true);
        return display;
    }
}
