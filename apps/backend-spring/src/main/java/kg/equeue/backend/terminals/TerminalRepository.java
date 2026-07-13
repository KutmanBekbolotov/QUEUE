package kg.equeue.backend.terminals;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<TerminalEntity, UUID> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}

