package kg.equeue.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kg.equeue.backend.audit.dto.AuditLogFilter;
import kg.equeue.backend.audit.dto.AuditLogPageResponse;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.users.UserEntity;
import kg.equeue.backend.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class AuditLogServiceTest {

    private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final AuditLogService service = new AuditLogService(auditLogRepository, userRepository);

    @Test
    void searchReturnsPagedDetailedRowsAndActorInformation() {
        UUID actorId = UUID.randomUUID();
        AuditLogEntity entity = auditEntity(actorId);
        UserEntity actor = user(actorId);
        when(auditLogRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AuditLogEntity>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(entity)));
        when(userRepository.findAllByIdIn(any())).thenReturn(List.of(actor));

        AuditLogFilter filter = new AuditLogFilter();
        filter.setPage(-2);
        filter.setSize(500);
        filter.setAction(" USER_UPDATE ");
        filter.setCreatedFrom(Instant.parse("2026-07-01T00:00:00Z"));
        AuditLogPageResponse response = service.search(filter);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().actorName()).isEqualTo("Test Operator");
        assertThat(response.content().getFirst().actorRoles()).containsExactly("OPERATOR");
        assertThat(response.content().getFirst().oldValue()).isEqualTo("{\"status\":\"ACTIVE\"}");
        assertThat(response.content().getFirst().newValue()).isEqualTo("{\"status\":\"BLOCKED\"}");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<AuditLogEntity>>any(),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }

    private AuditLogEntity auditEntity(UUID actorId) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setActorType("USER");
        entity.setActorId(actorId);
        entity.setAction("USER_UPDATE");
        entity.setEntityType("USER");
        entity.setEntityId(UUID.randomUUID());
        entity.setOldValue("{\"status\":\"ACTIVE\"}");
        entity.setNewValue("{\"status\":\"BLOCKED\"}");
        entity.setIp("127.0.0.1");
        entity.setSource("BACKEND");
        entity.setRequestId("request-1");
        entity.setCreatedAt(Instant.parse("2026-07-16T10:00:00Z"));
        return entity;
    }

    private UserEntity user(UUID actorId) {
        RoleEntity role = new RoleEntity();
        role.setCode("OPERATOR");
        UserEntity user = new UserEntity();
        user.setId(actorId);
        user.setUsername("operator-1");
        user.setFullName("Test Operator");
        user.setRoles(Set.of(role));
        return user;
    }
}
