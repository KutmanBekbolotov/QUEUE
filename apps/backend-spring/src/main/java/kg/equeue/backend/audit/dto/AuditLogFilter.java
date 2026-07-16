package kg.equeue.backend.audit.dto;

import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public class AuditLogFilter {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant createdTo;

    private UUID actorId;
    private String actorType;
    private String action;
    private String entityType;
    private String source;
    private String search;
    private Integer page = 0;
    private Integer size = 50;

    public Instant getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(Instant createdFrom) {
        this.createdFrom = createdFrom;
    }

    public Instant getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(Instant createdTo) {
        this.createdTo = createdTo;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = blankToNull(actorType);
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = blankToNull(action);
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = blankToNull(entityType);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = blankToNull(source);
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = blankToNull(search);
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
