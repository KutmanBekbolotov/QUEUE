package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.servicewindows.ServiceWindowEntity;
import kg.equeue.backend.servicewindows.ServiceWindowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TicketServiceTvSnapshotTest {

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final QueueServiceRepository queueServiceRepository = mock(QueueServiceRepository.class);
    private final ServiceWindowRepository serviceWindowRepository = mock(ServiceWindowRepository.class);
    private final TicketService ticketService = new TicketService(
            ticketRepository,
            null,
            null,
            null,
            null,
            queueServiceRepository,
            null,
            null,
            serviceWindowRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void tvSnapshotIncludesWindowAndServiceDisplayFields() {
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        TicketEntity ticket = ticket(departmentId, serviceId, windowId, operatorId);
        QueueServiceEntity service = service(serviceId, "Vehicle registration");
        ServiceWindowEntity window = window(windowId, "5", "Window 5");
        when(ticketRepository.findTop20ByDepartmentIdAndStatusInOrderByCalledAtDescCreatedAtDesc(
                any(), any()
        )).thenReturn(List.of(ticket));
        when(queueServiceRepository.findAllById(List.of(serviceId))).thenReturn(List.of(service));
        when(serviceWindowRepository.findAllById(List.of(windowId))).thenReturn(List.of(window));

        TicketDtos.TvSnapshotResponse snapshot = ticketService.tvSnapshotForDevice(departmentId);

        assertThat(snapshot.tickets()).singleElement().satisfies(response -> {
            assertThat(response.windowId()).isEqualTo(windowId);
            assertThat(response.serviceWindowId()).isEqualTo(windowId);
            assertThat(response.operatorId()).isEqualTo(operatorId);
            assertThat(response.windowNumber()).isEqualTo("Window 5");
            assertThat(response.serviceName()).isNotNull();
            assertThat(response.serviceName().ru()).isEqualTo("Vehicle registration");
            assertThat(response.serviceName().ky()).isEqualTo("Vehicle registration");
        });
    }

    private TicketEntity ticket(UUID departmentId, UUID serviceId, UUID windowId, UUID operatorId) {
        TicketEntity ticket = new TicketEntity();
        ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
        ticket.setTicketNumber("A-001");
        ticket.setTicketPrefix("A");
        ticket.setSequenceNumber(1);
        ticket.setWorkDate(LocalDate.now());
        ticket.setDepartmentId(departmentId);
        ticket.setHallId(UUID.randomUUID());
        ticket.setCategoryId(UUID.randomUUID());
        ticket.setServiceId(serviceId);
        ticket.setWindowId(windowId);
        ticket.setServedByUserId(operatorId);
        ticket.setSource(TicketSource.TERMINAL);
        ticket.setStatus(TicketStatus.CALLED);
        return ticket;
    }

    private QueueServiceEntity service(UUID serviceId, String name) {
        QueueServiceEntity service = new QueueServiceEntity();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setCategoryId(UUID.randomUUID());
        service.setCode("SERVICE");
        service.setName(name);
        return service;
    }

    private ServiceWindowEntity window(UUID windowId, String code, String displayName) {
        ServiceWindowEntity window = new ServiceWindowEntity();
        ReflectionTestUtils.setField(window, "id", windowId);
        window.setDepartmentId(UUID.randomUUID());
        window.setHallId(UUID.randomUUID());
        window.setCode(code);
        window.setDisplayName(displayName);
        return window;
    }
}
