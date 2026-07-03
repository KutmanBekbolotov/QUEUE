package kg.equeue.backend.tickets;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketSequenceService {

    private final TicketSequenceRepository ticketSequenceRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketSequenceService(TicketSequenceRepository ticketSequenceRepository, NamedParameterJdbcTemplate jdbcTemplate) {
        this.ticketSequenceRepository = ticketSequenceRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int nextValue(UUID departmentId, UUID serviceCategoryId, LocalDate workDate) {
        TicketSequenceEntity sequence = ticketSequenceRepository
                .findForUpdate(departmentId, serviceCategoryId, workDate)
                .orElseGet(() -> createSequence(departmentId, serviceCategoryId, workDate));
        int next = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(next);
        ticketSequenceRepository.save(sequence);
        return next;
    }

    private TicketSequenceEntity createSequence(UUID departmentId, UUID serviceCategoryId, LocalDate workDate) {
        jdbcTemplate.update("""
                INSERT INTO ticket_sequences (department_id, service_category_id, work_date, current_value, last_number)
                VALUES (:departmentId, :serviceCategoryId, :workDate, 0, 0)
                ON CONFLICT (department_id, service_category_id, work_date) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("departmentId", departmentId)
                        .addValue("serviceCategoryId", serviceCategoryId)
                        .addValue("workDate", workDate));
        return ticketSequenceRepository.findForUpdate(departmentId, serviceCategoryId, workDate).orElseThrow();
    }
}
