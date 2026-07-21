package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.departmentservices.DepartmentServiceEntity;
import kg.equeue.backend.departmentservices.DepartmentServiceRepository;
import kg.equeue.backend.departments.DepartmentEntity;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.servicecategories.ServiceCategoryEntity;
import kg.equeue.backend.servicecategories.ServiceCategoryRepository;
import kg.equeue.backend.services.QueueServiceEntity;
import kg.equeue.backend.services.QueueServiceRepository;
import kg.equeue.backend.ticketevents.TicketEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class TicketServiceQrSelfServiceGuardUnitTest {

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final TicketEventRepository ticketEventRepository = mock(TicketEventRepository.class);
    private final FakeTicketSequenceService ticketSequenceService = new FakeTicketSequenceService();
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final QueueServiceRepository queueServiceRepository = mock(QueueServiceRepository.class);
    private final ServiceCategoryRepository serviceCategoryRepository = mock(ServiceCategoryRepository.class);
    private final DepartmentServiceRepository departmentServiceRepository = mock(DepartmentServiceRepository.class);
    private final TicketService ticketService = new TicketService(
            ticketRepository,
            ticketEventRepository,
            ticketSequenceService,
            new NoopTicketDomainEventPublisher(),
            departmentRepository,
            queueServiceRepository,
            serviceCategoryRepository,
            departmentServiceRepository,
            null,
            null,
            null,
            null,
            new NoopAuditService(),
            null,
            new ObjectMapper(),
            null
    );

    @Test
    void createSelfServiceTicketNormalizesPhoneBeforeSavingQrTicket() {
        Core core = stubCore();
        when(ticketRepository.findFirstByDepartmentIdAndSourceAndCitizenPhoneAndStatusInOrderByCreatedAtAsc(
                any(), any(), any(), any()
        )).thenReturn(Optional.empty());
        when(ticketRepository.save(any(TicketEntity.class))).thenAnswer(invocation -> {
            TicketEntity ticket = invocation.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
            return ticket;
        });

        TicketDtos.TicketResponse response = ticketService.createSelfServiceTicket(qrRequest(core, "+996 (700) 000-000"), null);

        assertThat(response.citizenPhone()).isEqualTo("996700000000");
        ArgumentCaptor<TicketEntity> ticketCaptor = ArgumentCaptor.forClass(TicketEntity.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getCitizenPhone()).isEqualTo("996700000000");
        assertThat(ticketCaptor.getValue().getCitizenPin()).isNull();
        assertThat(ticketSequenceService.nextValueCalls).isEqualTo(1);
    }

    @Test
    void createSelfServiceTicketRejectsQrTicketWhenPhoneHasUnfinishedTicket() {
        Core core = stubCore();
        TicketEntity existing = existingQrTicket(core.departmentId(), "A-001", "996700000000", TicketStatus.WAITING);
        when(ticketRepository.findFirstByDepartmentIdAndSourceAndCitizenPhoneAndStatusInOrderByCreatedAtAsc(
                any(), any(), any(), any()
        )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> ticketService.createSelfServiceTicket(qrRequest(core, "+996 700 000 000"), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("QR_CITIZEN_HAS_UNFINISHED_TICKET");
                    assertThat(ex.getDetails()).containsEntry("ticketId", existing.getId());
                    assertThat(ex.getDetails()).containsEntry("ticketNumber", "A-001");
                });
        assertThat(ticketSequenceService.nextValueCalls).isZero();
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void createSelfServiceTicketRequiresQrPhone() {
        Core core = stubCore();

        assertThatThrownBy(() -> ticketService.createSelfServiceTicket(qrRequest(core, "  "), null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("QR_CITIZEN_PHONE_REQUIRED");
                });
        assertThat(ticketSequenceService.nextValueCalls).isZero();
        verify(ticketRepository, never()).save(any());
    }

    private Core stubCore() {
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        DepartmentEntity department = new DepartmentEntity();
        ReflectionTestUtils.setField(department, "id", departmentId);
        department.setCode("D-1");
        department.setName("Department");
        department.setActive(true);
        department.setClosed(false);
        QueueServiceEntity service = new QueueServiceEntity();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setCategoryId(categoryId);
        service.setCode("SERVICE");
        service.setName("Service");
        service.setActive(true);
        ServiceCategoryEntity category = new ServiceCategoryEntity();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setCode("CAT");
        category.setName("Category");
        category.setTicketPrefix("A");
        category.setActive(true);
        DepartmentServiceEntity departmentService = new DepartmentServiceEntity();
        departmentService.setDepartmentId(departmentId);
        departmentService.setServiceId(serviceId);
        departmentService.setActive(true);
        departmentService.setQrEnabled(true);

        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(queueServiceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(serviceCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(departmentServiceRepository.findByDepartmentIdAndServiceId(departmentId, serviceId))
                .thenReturn(Optional.of(departmentService));

        return new Core(departmentId, serviceId, categoryId);
    }

    private TicketDtos.CreateTicketRequest qrRequest(Core core, String citizenPhone) {
        return new TicketDtos.CreateTicketRequest(
                core.departmentId(),
                core.serviceId(),
                null,
                "Citizen",
                null,
                citizenPhone,
                TicketSource.QR_SELF_SERVICE,
                null,
                null,
                null
        );
    }

    private TicketEntity existingQrTicket(UUID departmentId, String ticketNumber, String citizenPhone, TicketStatus status) {
        TicketEntity ticket = new TicketEntity();
        ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
        ticket.setTicketNumber(ticketNumber);
        ticket.setTicketPrefix("A");
        ticket.setSequenceNumber(1);
        ticket.setWorkDate(LocalDate.now());
        ticket.setDepartmentId(departmentId);
        ticket.setCategoryId(UUID.randomUUID());
        ticket.setServiceId(UUID.randomUUID());
        ticket.setSource(TicketSource.QR_SELF_SERVICE);
        ticket.setCitizenPhone(citizenPhone);
        ticket.setStatus(status);
        return ticket;
    }

    private record Core(UUID departmentId, UUID serviceId, UUID categoryId) {
    }

    static class FakeTicketSequenceService extends TicketSequenceService {
        int nextValueCalls;

        FakeTicketSequenceService() {
            super(null, null);
        }

        @Override
        public int nextValue(UUID departmentId, UUID serviceCategoryId, LocalDate workDate) {
            nextValueCalls++;
            return nextValueCalls;
        }
    }

    static class NoopTicketDomainEventPublisher extends TicketDomainEventPublisher {
        NoopTicketDomainEventPublisher() {
            super(null, null);
        }

        @Override
        public TicketDomainEventPublisher.TicketDomainEvent publish(String eventType, TicketEntity ticket) {
            return null;
        }
    }

    static class NoopAuditService extends AuditService {
        NoopAuditService() {
            super(null);
        }

        @Override
        public void write(String action, String entityType, UUID entityId, String newValue, HttpServletRequest request) {
        }
    }
}
