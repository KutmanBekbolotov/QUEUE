package kg.equeue.backend.devices;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.devices.DeviceDtos.CreateTerminalRequest;
import kg.equeue.backend.devices.DeviceDtos.CreateTvDisplayRequest;
import kg.equeue.backend.devices.DeviceDtos.DeviceResponse;
import kg.equeue.backend.devices.DeviceDtos.ProvisionedDeviceResponse;
import kg.equeue.backend.devices.DeviceDtos.UpdateTerminalRequest;
import kg.equeue.backend.devices.DeviceDtos.UpdateTvDisplayRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Devices")
public class DeviceController {

    private final DeviceManagementService deviceManagementService;

    public DeviceController(DeviceManagementService deviceManagementService) {
        this.deviceManagementService = deviceManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TERMINAL_READ') or hasAuthority('TV_READ')")
    List<DeviceResponse> devices() {
        return deviceManagementService.list();
    }

    @PostMapping("/terminals")
    @PreAuthorize("hasAuthority('TERMINAL_CREATE')")
    ResponseEntity<ProvisionedDeviceResponse> createTerminal(@Valid @RequestBody CreateTerminalRequest request,
                                                             HttpServletRequest httpRequest) {
        return oneTimeTokenResponse(HttpStatus.CREATED, deviceManagementService.createTerminal(request, httpRequest));
    }

    @PatchMapping("/terminals/{id}")
    @PreAuthorize("hasAuthority('TERMINAL_UPDATE')")
    DeviceResponse updateTerminal(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateTerminalRequest request,
                                  HttpServletRequest httpRequest) {
        return deviceManagementService.updateTerminal(id, request, httpRequest);
    }

    @DeleteMapping("/terminals/{id}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('TERMINAL_DELETE')")
    void deleteTerminal(@PathVariable UUID id, HttpServletRequest httpRequest) {
        deviceManagementService.deleteTerminal(id, httpRequest);
    }

    @PostMapping("/terminals/{id}/rotate-token")
    @PreAuthorize("hasAuthority('TERMINAL_CONFIGURE')")
    ResponseEntity<ProvisionedDeviceResponse> rotateTerminalToken(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return oneTimeTokenResponse(HttpStatus.OK, deviceManagementService.rotateTerminalToken(id, httpRequest));
    }

    @PostMapping("/tv-displays")
    @PreAuthorize("hasAuthority('TV_CREATE')")
    ResponseEntity<ProvisionedDeviceResponse> createTvDisplay(@Valid @RequestBody CreateTvDisplayRequest request,
                                                              HttpServletRequest httpRequest) {
        return oneTimeTokenResponse(HttpStatus.CREATED, deviceManagementService.createTvDisplay(request, httpRequest));
    }

    @PatchMapping("/tv-displays/{id}")
    @PreAuthorize("hasAuthority('TV_UPDATE')")
    DeviceResponse updateTvDisplay(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateTvDisplayRequest request,
                                   HttpServletRequest httpRequest) {
        return deviceManagementService.updateTvDisplay(id, request, httpRequest);
    }

    @DeleteMapping("/tv-displays/{id}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('TV_DELETE')")
    void deleteTvDisplay(@PathVariable UUID id, HttpServletRequest httpRequest) {
        deviceManagementService.deleteTvDisplay(id, httpRequest);
    }

    @PostMapping("/tv-displays/{id}/rotate-token")
    @PreAuthorize("hasAuthority('TV_CONFIGURE')")
    ResponseEntity<ProvisionedDeviceResponse> rotateTvDisplayToken(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return oneTimeTokenResponse(HttpStatus.OK, deviceManagementService.rotateTvDisplayToken(id, httpRequest));
    }

    private ResponseEntity<ProvisionedDeviceResponse> oneTimeTokenResponse(HttpStatus status,
                                                                            ProvisionedDeviceResponse response) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
