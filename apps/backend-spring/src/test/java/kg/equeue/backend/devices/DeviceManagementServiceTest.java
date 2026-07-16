package kg.equeue.backend.devices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.Optional;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.devices.DeviceDtos.CreateTerminalRequest;
import kg.equeue.backend.devices.DeviceDtos.ProvisionedDeviceResponse;
import kg.equeue.backend.halls.HallRepository;
import kg.equeue.backend.terminals.TerminalEntity;
import kg.equeue.backend.terminals.TerminalRepository;
import kg.equeue.backend.tvdisplays.TvDisplayRepository;
import kg.equeue.backend.tvdisplays.TvDisplayEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class DeviceManagementServiceTest {

    private final TerminalRepository terminalRepository = org.mockito.Mockito.mock(TerminalRepository.class);
    private final TvDisplayRepository tvDisplayRepository = org.mockito.Mockito.mock(TvDisplayRepository.class);
    private final DepartmentRepository departmentRepository = org.mockito.Mockito.mock(DepartmentRepository.class);
    private final HallRepository hallRepository = org.mockito.Mockito.mock(HallRepository.class);
    private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
    private final DeviceTokenService deviceTokenService = new DeviceTokenService();
    private final DeviceManagementService service = new DeviceManagementService(
            terminalRepository,
            tvDisplayRepository,
            departmentRepository,
            hallRepository,
            deviceTokenService,
            auditService
    );

    @Test
    void createTerminalReturnsRawTokenOnceAndPersistsOnlyItsHash() {
        UUID departmentId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        when(departmentRepository.existsById(departmentId)).thenReturn(true);
        when(terminalRepository.existsByCode("TERM-1")).thenReturn(false);
        when(terminalRepository.saveAndFlush(any(TerminalEntity.class))).thenAnswer(invocation -> {
            TerminalEntity terminal = invocation.getArgument(0);
            ReflectionTestUtils.setField(terminal, "id", terminalId);
            return terminal;
        });

        ProvisionedDeviceResponse response = service.createTerminal(
                new CreateTerminalRequest(departmentId, " TERM-1 ", " Main terminal "),
                new MockHttpServletRequest()
        );

        assertThat(response.deviceToken()).isNotBlank();
        assertThat(response.device().id()).isEqualTo(terminalId);
        assertThat(response.device().code()).isEqualTo("TERM-1");
        assertThat(response.device().name()).isEqualTo("Main terminal");

        ArgumentCaptor<TerminalEntity> terminalCaptor = ArgumentCaptor.forClass(TerminalEntity.class);
        verify(terminalRepository).saveAndFlush(terminalCaptor.capture());
        assertThat(terminalCaptor.getValue().getTokenHash()).isNotEqualTo(response.deviceToken());
        assertThat(deviceTokenService.matches(response.deviceToken(), terminalCaptor.getValue().getTokenHash())).isTrue();

        ArgumentCaptor<String> auditValue = ArgumentCaptor.forClass(String.class);
        verify(auditService).write(eq("TERMINAL_CREATE"), eq("TERMINAL"), eq(terminalId), auditValue.capture(), any());
        assertThat(auditValue.getValue()).doesNotContain(response.deviceToken());
    }

    @Test
    void rotateTerminalTokenImmediatelyReplacesTheOldToken() {
        UUID terminalId = UUID.randomUUID();
        TerminalEntity terminal = new TerminalEntity();
        ReflectionTestUtils.setField(terminal, "id", terminalId);
        terminal.setDepartmentId(UUID.randomUUID());
        terminal.setCode("TERM-1");
        terminal.setName("Terminal 1");
        terminal.setTokenHash(deviceTokenService.hash("old-token"));
        terminal.setActive(true);
        when(terminalRepository.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(terminalRepository.save(terminal)).thenReturn(terminal);

        ProvisionedDeviceResponse response = service.rotateTerminalToken(terminalId, new MockHttpServletRequest());

        assertThat(response.deviceToken()).isNotEqualTo("old-token");
        assertThat(deviceTokenService.matches(response.deviceToken(), terminal.getTokenHash())).isTrue();
        assertThat(deviceTokenService.matches("old-token", terminal.getTokenHash())).isFalse();
    }

    @Test
    void deleteTerminalPermanentlyRemovesEntity() {
        UUID terminalId = UUID.randomUUID();
        TerminalEntity terminal = new TerminalEntity();
        ReflectionTestUtils.setField(terminal, "id", terminalId);
        when(terminalRepository.findById(terminalId)).thenReturn(Optional.of(terminal));

        service.deleteTerminal(terminalId, new MockHttpServletRequest());

        verify(terminalRepository).delete(terminal);
        verify(terminalRepository).flush();
        verify(auditService).write(
                eq("TERMINAL_DELETE"), eq("TERMINAL"), eq(terminalId), eq("{\"deleted\":true}"), any());
    }

    @Test
    void deleteTvDisplayPermanentlyRemovesEntity() {
        UUID displayId = UUID.randomUUID();
        TvDisplayEntity display = new TvDisplayEntity();
        ReflectionTestUtils.setField(display, "id", displayId);
        when(tvDisplayRepository.findById(displayId)).thenReturn(Optional.of(display));

        service.deleteTvDisplay(displayId, new MockHttpServletRequest());

        verify(tvDisplayRepository).delete(display);
        verify(tvDisplayRepository).flush();
        verify(auditService).write(
                eq("TV_DELETE"), eq("TV_DISPLAY"), eq(displayId), eq("{\"deleted\":true}"), any());
    }
}
