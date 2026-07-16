package kg.equeue.backend.devices;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import kg.equeue.backend.audit.AuditService;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.common.DeviceTokenService;
import kg.equeue.backend.departments.DepartmentRepository;
import kg.equeue.backend.devices.DeviceDtos.CreateTerminalRequest;
import kg.equeue.backend.devices.DeviceDtos.CreateTvDisplayRequest;
import kg.equeue.backend.devices.DeviceDtos.DeviceResponse;
import kg.equeue.backend.devices.DeviceDtos.ProvisionedDeviceResponse;
import kg.equeue.backend.devices.DeviceDtos.UpdateTerminalRequest;
import kg.equeue.backend.devices.DeviceDtos.UpdateTvDisplayRequest;
import kg.equeue.backend.halls.HallEntity;
import kg.equeue.backend.halls.HallRepository;
import kg.equeue.backend.terminals.TerminalEntity;
import kg.equeue.backend.terminals.TerminalRepository;
import kg.equeue.backend.tvdisplays.TvDisplayEntity;
import kg.equeue.backend.tvdisplays.TvDisplayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceManagementService {

    private final TerminalRepository terminalRepository;
    private final TvDisplayRepository tvDisplayRepository;
    private final DepartmentRepository departmentRepository;
    private final HallRepository hallRepository;
    private final DeviceTokenService deviceTokenService;
    private final AuditService auditService;

    public DeviceManagementService(TerminalRepository terminalRepository,
                                   TvDisplayRepository tvDisplayRepository,
                                   DepartmentRepository departmentRepository,
                                   HallRepository hallRepository,
                                   DeviceTokenService deviceTokenService,
                                   AuditService auditService) {
        this.terminalRepository = terminalRepository;
        this.tvDisplayRepository = tvDisplayRepository;
        this.departmentRepository = departmentRepository;
        this.hallRepository = hallRepository;
        this.deviceTokenService = deviceTokenService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        Stream<DeviceResponse> terminals = terminalRepository.findAll().stream().map(this::terminalResponse);
        Stream<DeviceResponse> displays = tvDisplayRepository.findAll().stream().map(this::tvDisplayResponse);
        return Stream.concat(terminals, displays)
                .sorted(Comparator.comparing(DeviceResponse::type).thenComparing(DeviceResponse::code))
                .toList();
    }

    @Transactional
    public ProvisionedDeviceResponse createTerminal(CreateTerminalRequest request, HttpServletRequest httpRequest) {
        requireDepartment(request.departmentId());
        String code = request.code().trim();
        requireUniqueTerminalCode(code, null);

        String rawToken = deviceTokenService.generateRawToken();
        TerminalEntity terminal = new TerminalEntity();
        terminal.setDepartmentId(request.departmentId());
        terminal.setCode(code);
        terminal.setName(request.name().trim());
        terminal.setTokenHash(deviceTokenService.hash(rawToken));
        terminal.setActive(true);
        TerminalEntity saved = terminalRepository.saveAndFlush(terminal);

        auditService.write("TERMINAL_CREATE", "TERMINAL", saved.getId(), "{\"created\":true}", httpRequest);
        return new ProvisionedDeviceResponse(terminalResponse(saved), rawToken);
    }

    @Transactional
    public DeviceResponse updateTerminal(UUID id, UpdateTerminalRequest request, HttpServletRequest httpRequest) {
        TerminalEntity terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND", "Terminal was not found"));
        if (request.departmentId() != null) {
            requireDepartment(request.departmentId());
            terminal.setDepartmentId(request.departmentId());
        }
        if (request.code() != null) {
            String code = request.code().trim();
            requireUniqueTerminalCode(code, id);
            terminal.setCode(code);
        }
        if (request.name() != null) {
            terminal.setName(request.name().trim());
        }
        if (request.active() != null) {
            terminal.setActive(request.active());
        }
        TerminalEntity saved = terminalRepository.save(terminal);
        auditService.write("TERMINAL_UPDATE", "TERMINAL", saved.getId(), "{\"device\":\"updated\"}", httpRequest);
        return terminalResponse(saved);
    }

    @Transactional
    public ProvisionedDeviceResponse rotateTerminalToken(UUID id, HttpServletRequest httpRequest) {
        TerminalEntity terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND", "Terminal was not found"));
        String rawToken = deviceTokenService.generateRawToken();
        terminal.setTokenHash(deviceTokenService.hash(rawToken));
        TerminalEntity saved = terminalRepository.save(terminal);
        auditService.write("TERMINAL_TOKEN_ROTATE", "TERMINAL", saved.getId(), "{\"token\":\"rotated\"}", httpRequest);
        return new ProvisionedDeviceResponse(terminalResponse(saved), rawToken);
    }

    @Transactional
    public void deleteTerminal(UUID id, HttpServletRequest httpRequest) {
        TerminalEntity terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TERMINAL_NOT_FOUND", "Terminal was not found"));
        terminalRepository.delete(terminal);
        terminalRepository.flush();
        auditService.write("TERMINAL_DELETE", "TERMINAL", id, "{\"deleted\":true}", httpRequest);
    }

    @Transactional
    public ProvisionedDeviceResponse createTvDisplay(CreateTvDisplayRequest request, HttpServletRequest httpRequest) {
        requireDepartment(request.departmentId());
        requireHallInDepartment(request.hallId(), request.departmentId());
        String code = request.code().trim();
        requireUniqueTvCode(code, null);

        String rawToken = deviceTokenService.generateRawToken();
        TvDisplayEntity display = new TvDisplayEntity();
        display.setDepartmentId(request.departmentId());
        display.setHallId(request.hallId());
        display.setCode(code);
        display.setName(request.name().trim());
        display.setTokenHash(deviceTokenService.hash(rawToken));
        display.setActive(true);
        TvDisplayEntity saved = tvDisplayRepository.saveAndFlush(display);

        auditService.write("TV_CREATE", "TV_DISPLAY", saved.getId(), "{\"created\":true}", httpRequest);
        return new ProvisionedDeviceResponse(tvDisplayResponse(saved), rawToken);
    }

    @Transactional
    public DeviceResponse updateTvDisplay(UUID id, UpdateTvDisplayRequest request, HttpServletRequest httpRequest) {
        TvDisplayEntity display = tvDisplayRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TV_DISPLAY_NOT_FOUND", "TV display was not found"));
        UUID departmentId = request.departmentId() == null ? display.getDepartmentId() : request.departmentId();
        UUID hallId = request.hallId() == null ? display.getHallId() : request.hallId();
        requireDepartment(departmentId);
        requireHallInDepartment(hallId, departmentId);
        display.setDepartmentId(departmentId);
        display.setHallId(hallId);

        if (request.code() != null) {
            String code = request.code().trim();
            requireUniqueTvCode(code, id);
            display.setCode(code);
        }
        if (request.name() != null) {
            display.setName(request.name().trim());
        }
        if (request.active() != null) {
            display.setActive(request.active());
        }
        TvDisplayEntity saved = tvDisplayRepository.save(display);
        auditService.write("TV_UPDATE", "TV_DISPLAY", saved.getId(), "{\"device\":\"updated\"}", httpRequest);
        return tvDisplayResponse(saved);
    }

    @Transactional
    public ProvisionedDeviceResponse rotateTvDisplayToken(UUID id, HttpServletRequest httpRequest) {
        TvDisplayEntity display = tvDisplayRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TV_DISPLAY_NOT_FOUND", "TV display was not found"));
        String rawToken = deviceTokenService.generateRawToken();
        display.setTokenHash(deviceTokenService.hash(rawToken));
        TvDisplayEntity saved = tvDisplayRepository.save(display);
        auditService.write("TV_TOKEN_ROTATE", "TV_DISPLAY", saved.getId(), "{\"token\":\"rotated\"}", httpRequest);
        return new ProvisionedDeviceResponse(tvDisplayResponse(saved), rawToken);
    }

    @Transactional
    public void deleteTvDisplay(UUID id, HttpServletRequest httpRequest) {
        TvDisplayEntity display = tvDisplayRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TV_DISPLAY_NOT_FOUND", "TV display was not found"));
        tvDisplayRepository.delete(display);
        tvDisplayRepository.flush();
        auditService.write("TV_DELETE", "TV_DISPLAY", id, "{\"deleted\":true}", httpRequest);
    }

    private void requireDepartment(UUID departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEPARTMENT_NOT_FOUND", "Department was not found");
        }
    }

    private void requireHallInDepartment(UUID hallId, UUID departmentId) {
        if (hallId == null) {
            return;
        }
        HallEntity hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "HALL_NOT_FOUND", "Hall was not found"));
        if (!departmentId.equals(hall.getDepartmentId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HALL_DEPARTMENT_MISMATCH", "Hall belongs to another department");
        }
    }

    private void requireUniqueTerminalCode(String code, UUID currentId) {
        boolean exists = currentId == null
                ? terminalRepository.existsByCode(code)
                : terminalRepository.existsByCodeAndIdNot(code, currentId);
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "TERMINAL_CODE_EXISTS", "Terminal code already exists");
        }
    }

    private void requireUniqueTvCode(String code, UUID currentId) {
        boolean exists = currentId == null
                ? tvDisplayRepository.existsByCode(code)
                : tvDisplayRepository.existsByCodeAndIdNot(code, currentId);
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "TV_CODE_EXISTS", "TV display code already exists");
        }
    }

    private DeviceResponse terminalResponse(TerminalEntity entity) {
        return new DeviceResponse(entity.getId(), "TERMINAL", entity.getDepartmentId(), null,
                entity.getCode(), entity.getName(), entity.isActive(), entity.getLastSeenAt());
    }

    private DeviceResponse tvDisplayResponse(TvDisplayEntity entity) {
        return new DeviceResponse(entity.getId(), "TV_DISPLAY", entity.getDepartmentId(), entity.getHallId(),
                entity.getCode(), entity.getName(), entity.isActive(), entity.getLastSeenAt());
    }
}
