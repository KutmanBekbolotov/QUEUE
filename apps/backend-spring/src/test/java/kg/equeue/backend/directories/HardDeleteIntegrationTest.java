package kg.equeue.backend.directories;

import static org.assertj.core.api.Assertions.assertThat;

import kg.equeue.backend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

class HardDeleteIntegrationTest extends PostgresIntegrationTest {

    @Test
    void deletingDepartmentCascadesOwnedDataButKeepsUsers() {
        CoreData data = seedCoreData();
        jdbcTemplate.update("""
                INSERT INTO user_department_scopes (user_id, department_id)
                VALUES (?, ?)
                """, data.userId(), data.departmentId());
        jdbcTemplate.update("""
                INSERT INTO terminals (department_id, code, name, token_hash)
                VALUES (?, 'DELETE_TERM', 'Delete terminal', 'hash')
                """, data.departmentId());
        jdbcTemplate.update("""
                INSERT INTO tv_displays (department_id, hall_id, code, name, token_hash)
                VALUES (?, ?, 'DELETE_TV', 'Delete TV', 'hash')
                """, data.departmentId(), data.hallId());

        assertThat(deleteRule("bookings", "department_id")).isEqualTo("CASCADE");
        assertThat(deleteRule("tickets", "department_id")).isEqualTo("CASCADE");

        jdbcTemplate.update("DELETE FROM departments WHERE id = ?", data.departmentId());

        assertThat(count("departments", "id", data.departmentId())).isZero();
        assertThat(count("office_rooms", "department_id", data.departmentId())).isZero();
        assertThat(count("halls", "department_id", data.departmentId())).isZero();
        assertThat(count("service_windows", "department_id", data.departmentId())).isZero();
        assertThat(count("department_services", "department_id", data.departmentId())).isZero();
        assertThat(count("terminals", "department_id", data.departmentId())).isZero();
        assertThat(count("tv_displays", "department_id", data.departmentId())).isZero();
        assertThat(count("users", "id", data.userId())).isOne();
        assertThat(count("user_department_scopes", "user_id", data.userId())).isZero();
    }

    private String deleteRule(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT rc.delete_rule
                FROM information_schema.referential_constraints rc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_catalog = rc.constraint_catalog
                 AND kcu.constraint_schema = rc.constraint_schema
                 AND kcu.constraint_name = rc.constraint_name
                WHERE kcu.table_schema = 'public'
                  AND kcu.table_name = ?
                  AND kcu.column_name = ?
                """, String.class, table, column);
    }

    private int count(String table, String column, Object value) {
        String allowedTable = switch (table) {
            case "departments", "office_rooms", "halls", "service_windows", "department_services",
                    "terminals", "tv_displays", "users", "user_department_scopes" -> table;
            default -> throw new IllegalArgumentException("Unsupported table: " + table);
        };
        String allowedColumn = switch (column) {
            case "id", "department_id", "user_id" -> column;
            default -> throw new IllegalArgumentException("Unsupported column: " + column);
        };
        Integer result = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + allowedTable + " WHERE " + allowedColumn + " = ?",
                Integer.class,
                value
        );
        return result == null ? 0 : result;
    }
}
