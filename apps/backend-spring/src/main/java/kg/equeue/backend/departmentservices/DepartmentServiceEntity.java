package kg.equeue.backend.departmentservices;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "department_services")
public class DepartmentServiceEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "online_booking_enabled", nullable = false)
    private boolean onlineBookingEnabled;

    @Column(name = "terminal_enabled", nullable = false)
    private boolean terminalEnabled = true;

    @Column(name = "qr_enabled", nullable = false)
    private boolean qrEnabled = true;

    @Column(name = "daily_limit")
    private Integer dailyLimit;

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isOnlineBookingEnabled() {
        return onlineBookingEnabled;
    }

    public void setOnlineBookingEnabled(boolean onlineBookingEnabled) {
        this.onlineBookingEnabled = onlineBookingEnabled;
    }

    public boolean isTerminalEnabled() {
        return terminalEnabled;
    }

    public void setTerminalEnabled(boolean terminalEnabled) {
        this.terminalEnabled = terminalEnabled;
    }

    public boolean isQrEnabled() {
        return qrEnabled;
    }

    public void setQrEnabled(boolean qrEnabled) {
        this.qrEnabled = qrEnabled;
    }

    public Integer getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(Integer dailyLimit) {
        this.dailyLimit = dailyLimit;
    }
}

