package kg.equeue.backend.ticketevents;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEventEntity, UUID> {
}

