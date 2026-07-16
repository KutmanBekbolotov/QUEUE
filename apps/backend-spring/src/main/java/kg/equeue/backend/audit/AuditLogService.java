package kg.equeue.backend.audit;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kg.equeue.backend.audit.dto.AuditLogFilter;
import kg.equeue.backend.audit.dto.AuditLogPageResponse;
import kg.equeue.backend.audit.dto.AuditLogResponse;
import kg.equeue.backend.users.UserEntity;
import kg.equeue.backend.users.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Page<AuditLogEntity> page = auditLogRepository.findAll(
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return toResponses(page.getContent());
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse search(AuditLogFilter filter) {
        AuditLogFilter safeFilter = filter == null ? new AuditLogFilter() : filter;
        int pageNumber = Math.max(0, Objects.requireNonNullElse(safeFilter.getPage(), 0));
        int pageSize = Math.max(1, Math.min(Objects.requireNonNullElse(safeFilter.getSize(), 50), 200));
        Page<AuditLogEntity> page = auditLogRepository.findAll(
                specification(safeFilter),
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new AuditLogPageResponse(
                toResponses(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private Specification<AuditLogEntity> specification(AuditLogFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getCreatedFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedFrom()));
            }
            if (filter.getCreatedTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedTo()));
            }
            if (filter.getActorId() != null) {
                predicates.add(builder.equal(root.get("actorId"), filter.getActorId()));
            }
            addExactIgnoreCase(predicates, builder, root.get("actorType"), filter.getActorType());
            addExactIgnoreCase(predicates, builder, root.get("action"), filter.getAction());
            addExactIgnoreCase(predicates, builder, root.get("entityType"), filter.getEntityType());
            addExactIgnoreCase(predicates, builder, root.get("source"), filter.getSource());

            if (filter.getSearch() != null) {
                String pattern = "%" + filter.getSearch().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("action")), pattern),
                        builder.like(builder.lower(root.get("entityType")), pattern),
                        builder.like(builder.lower(root.get("source")), pattern),
                        builder.like(builder.lower(root.get("requestId")), pattern),
                        builder.like(builder.lower(root.get("ip")), pattern)
                ));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addExactIgnoreCase(List<Predicate> predicates,
                                    jakarta.persistence.criteria.CriteriaBuilder builder,
                                    jakarta.persistence.criteria.Path<String> path,
                                    String value) {
        if (value != null) {
            predicates.add(builder.equal(builder.lower(path), value.toLowerCase(Locale.ROOT)));
        }
    }

    private List<AuditLogResponse> toResponses(List<AuditLogEntity> entities) {
        Set<UUID> actorIds = entities.stream()
                .map(AuditLogEntity::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserEntity> users = new HashMap<>();
        if (!actorIds.isEmpty()) {
            userRepository.findAllByIdIn(actorIds).forEach(user -> users.put(user.getId(), user));
        }
        return entities.stream().map(entity -> toResponse(entity, users.get(entity.getActorId()))).toList();
    }

    private AuditLogResponse toResponse(AuditLogEntity entity, UserEntity actor) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getActorType(),
                entity.getActorId(),
                actorName(actor),
                actor == null ? List.of() : actor.getRoles().stream().map(role -> role.getCode()).sorted().toList(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getOldValue(),
                entity.getNewValue(),
                entity.getIp(),
                entity.getUserAgent(),
                entity.getSource(),
                entity.getRequestId(),
                entity.getCreatedAt()
        );
    }

    private String actorName(UserEntity actor) {
        if (actor == null) {
            return null;
        }
        return actor.getFullName() == null || actor.getFullName().isBlank()
                ? actor.getUsername()
                : actor.getFullName();
    }
}
