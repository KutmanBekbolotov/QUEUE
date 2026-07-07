package kg.equeue.backend.users;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserDepartmentScopeRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserDepartmentScopeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID primaryDepartmentId(UUID userId) {
        return jdbcTemplate.query("""
                SELECT department_id
                FROM user_department_scopes
                WHERE user_id = ?
                ORDER BY department_id
                LIMIT 1
                """, (rs, rowNum) -> rs.getObject("department_id", UUID.class), userId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public boolean departmentExists(UUID departmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM departments
                WHERE id = ?
                """, Integer.class, departmentId);
        return count != null && count > 0;
    }

    public void replacePrimaryDepartment(UUID userId, UUID departmentId) {
        jdbcTemplate.update("DELETE FROM user_department_scopes WHERE user_id = ?", userId);
        if (departmentId != null) {
            jdbcTemplate.update("""
                    INSERT INTO user_department_scopes (user_id, department_id)
                    VALUES (?, ?)
                    """, userId, departmentId);
        }
    }
}
