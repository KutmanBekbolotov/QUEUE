package kg.equeue.backend.pausereasons;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PauseReasonRepository extends JpaRepository<PauseReasonEntity, UUID> {
}

