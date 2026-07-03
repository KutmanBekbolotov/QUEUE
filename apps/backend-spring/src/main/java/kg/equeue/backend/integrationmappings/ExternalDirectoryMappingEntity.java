package kg.equeue.backend.integrationmappings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_directory_mappings")
public class ExternalDirectoryMappingEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "client_code", nullable = false)
    private String clientCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ExternalDirectoryMappingEntityType entityType;

    @Column(name = "external_code", nullable = false)
    private String externalCode;

    @Column(name = "internal_id", nullable = false)
    private UUID internalId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getClientCode() {
        return clientCode;
    }

    public void setClientCode(String clientCode) {
        this.clientCode = clientCode;
    }

    public ExternalDirectoryMappingEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(ExternalDirectoryMappingEntityType entityType) {
        this.entityType = entityType;
    }

    public String getExternalCode() {
        return externalCode;
    }

    public void setExternalCode(String externalCode) {
        this.externalCode = externalCode;
    }

    public UUID getInternalId() {
        return internalId;
    }

    public void setInternalId(UUID internalId) {
        this.internalId = internalId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
