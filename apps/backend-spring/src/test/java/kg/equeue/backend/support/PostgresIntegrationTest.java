package kg.equeue.backend.support;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.auth.AuthenticatedPrincipal;
import kg.equeue.backend.bookings.BookingDomainEventPublisher;
import kg.equeue.backend.tickets.TicketDomainEventPublisher;
import kg.equeue.backend.users.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.reports.export.local-dir=target/test-exports",
        "app.reports.export.pdf-max-rows=1"
})
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    protected static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("equeue_test")
            .withUsername("equeue")
            .withPassword("equeue");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockBean
    protected AuditService auditService;

    @MockBean
    protected TicketDomainEventPublisher ticketDomainEventPublisher;

    @MockBean
    protected BookingDomainEventPublisher bookingDomainEventPublisher;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> "5672");
    }

    @BeforeEach
    void resetDatabaseAndSecurity() {
        cleanDatabase();
        authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected void cleanDatabase() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename <> 'flyway_schema_history'
                ORDER BY tablename
                """, String.class);
        if (!tables.isEmpty()) {
            String tableList = tables.stream()
                    .map(table -> "\"" + table.replace("\"", "\"\"") + "\"")
                    .reduce((left, right) -> left + ", " + right)
                    .orElseThrow();
            jdbcTemplate.execute("TRUNCATE TABLE " + tableList + " RESTART IDENTITY CASCADE");
        }
    }

    protected CoreData seedCoreData() {
        CoreData data = new CoreData(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_USER_ID
        );
        jdbcTemplate.update("""
                INSERT INTO users (id, username, password_hash, full_name, status)
                VALUES (?, 'test-user', '$2a$10$18zFYunaR5aSTQ5V5I1c9OjIZN0y/C5T1kk0CJ1bH5HAdxhNgkSCi', 'Operator User', 'ACTIVE')
                """, data.userId());
        jdbcTemplate.update("""
                INSERT INTO regions (id, code, name)
                VALUES (?, 'TEST_REGION', 'Test Region')
                """, data.regionId());
        jdbcTemplate.update("""
                INSERT INTO departments (id, region_id, code, name, active, closed)
                VALUES (?, ?, 'TEST_DEPARTMENT', 'Test Department', true, false)
                """, data.departmentId(), data.regionId());
        jdbcTemplate.update("""
                INSERT INTO office_rooms (id, department_id, code, name)
                VALUES (?, ?, 'ROOM_1', 'Room 1')
                """, data.roomId(), data.departmentId());
        jdbcTemplate.update("""
                INSERT INTO halls (id, department_id, office_room_id, code, name)
                VALUES (?, ?, ?, 'HALL_1', 'Hall 1')
                """, data.hallId(), data.departmentId(), data.roomId());
        jdbcTemplate.update("""
                INSERT INTO service_categories (id, code, name, ticket_prefix)
                VALUES (?, 'CAT', 'Category', 'A')
                """, data.categoryId());
        jdbcTemplate.update("""
                INSERT INTO services (id, category_id, code, name, default_duration_minutes, active)
                VALUES (?, ?, 'SERVICE_1', 'Service 1', 15, true)
                """, data.serviceId(), data.categoryId());
        jdbcTemplate.update("""
                INSERT INTO department_services (department_id, service_id, active, online_booking_enabled, terminal_enabled, qr_enabled)
                VALUES (?, ?, true, true, true, true)
                """, data.departmentId(), data.serviceId());
        jdbcTemplate.update("""
                INSERT INTO service_windows (id, department_id, hall_id, code, display_name, active, open, status)
                VALUES (?, ?, ?, 'WINDOW_1', 'Window 1', true, true, 'OPEN')
                """, data.windowId(), data.departmentId(), data.hallId());
        return data;
    }

    protected void authenticateAsSuperAdmin() {
        authenticate(TEST_USER_ID,
                "ROLE_SUPER_ADMIN",
                "REPORT_READ",
                "REPORT_EXPORT",
                "REPORT_VIEW_PERSONAL_DATA",
                "REPORT_EXPORT_PERSONAL_DATA");
    }

    protected void authenticate(UUID userId, String... authorities) {
        var granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var principal = new AuthenticatedPrincipal(userId, "test-user", 1, UserStatus.ACTIVE, granted);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, granted));
    }

    protected <T> List<ConcurrentResult<T>> runConcurrently(int attempts, Callable<T> callable) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ConcurrentResult<T>>> futures = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(index -> executor.submit(() -> {
                        authenticateAsSuperAdmin();
                        ready.countDown();
                        start.await();
                        try {
                            return new ConcurrentResult<>(callable.call(), null);
                        } catch (Throwable error) {
                            return new ConcurrentResult<T>(null, error);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    }))
                    .toList();
            ready.await();
            start.countDown();
            return futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception ex) {
                    return new ConcurrentResult<T>(null, ex);
                }
            }).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    protected void insertSlot(UUID slotId, CoreData data, LocalDate date, String start, String end, int capacity) {
        jdbcTemplate.update("""
                INSERT INTO booking_slots (id, department_id, service_id, slot_date, slot_start, slot_end, starts_at, ends_at, capacity, booked_count, reserved_count, status, active)
                VALUES (?, ?, ?, ?, ?::time, ?::time, ?::time, ?::time, ?, 0, 0, 'ACTIVE', true)
                """, slotId, data.departmentId(), data.serviceId(), date, start, end, start, end, capacity);
    }

    public record CoreData(
            UUID regionId,
            UUID departmentId,
            UUID categoryId,
            UUID serviceId,
            UUID roomId,
            UUID hallId,
            UUID windowId,
            UUID userId
    ) {
    }

    public record ConcurrentResult<T>(T value, Throwable error) {
        public boolean success() {
            return error == null;
        }
    }
}
