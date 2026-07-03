package kg.equeue.backend.cancellationreasons;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CancellationReasonRepository extends JpaRepository<CancellationReasonEntity, UUID> {
}

