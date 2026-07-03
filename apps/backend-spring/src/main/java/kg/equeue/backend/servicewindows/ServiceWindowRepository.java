package kg.equeue.backend.servicewindows;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ServiceWindowRepository extends JpaRepository<ServiceWindowEntity, UUID> {
    List<ServiceWindowEntity> findAllByOrderByDepartmentIdAscCodeAsc();

    List<ServiceWindowEntity> findByDepartmentIdOrderByCodeAsc(UUID departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ServiceWindowEntity w where w.id = :id")
    java.util.Optional<ServiceWindowEntity> findWithLockById(UUID id);
}
