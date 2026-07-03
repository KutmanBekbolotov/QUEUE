package kg.equeue.backend.terminals;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<TerminalEntity, UUID> {
}

