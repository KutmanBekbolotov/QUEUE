package kg.equeue.backend.directories;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.directories.DirectoryDtos.ActiveStatusRequest;
import kg.equeue.backend.directories.DirectoryDtos.AssignEmployeeServiceRequest;
import kg.equeue.backend.directories.DirectoryDtos.AssignEmployeeToWindowRequest;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentRequest;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentResponse;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentServiceRequest;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentServiceResponse;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentStatusRequest;
import kg.equeue.backend.directories.DirectoryDtos.EmployeeServiceAssignmentResponse;
import kg.equeue.backend.directories.DirectoryDtos.HallRequest;
import kg.equeue.backend.directories.DirectoryDtos.HallResponse;
import kg.equeue.backend.directories.DirectoryDtos.OfficeRoomRequest;
import kg.equeue.backend.directories.DirectoryDtos.OfficeRoomResponse;
import kg.equeue.backend.directories.DirectoryDtos.RegionRequest;
import kg.equeue.backend.directories.DirectoryDtos.RegionResponse;
import kg.equeue.backend.directories.DirectoryDtos.ServiceCategoryRequest;
import kg.equeue.backend.directories.DirectoryDtos.ServiceCategoryResponse;
import kg.equeue.backend.directories.DirectoryDtos.ServiceRequest;
import kg.equeue.backend.directories.DirectoryDtos.ServiceResponse;
import kg.equeue.backend.directories.DirectoryDtos.WindowRequest;
import kg.equeue.backend.directories.DirectoryDtos.WindowResponse;
import kg.equeue.backend.directories.DirectoryDtos.WindowStatusRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Directories")
public class DirectoryController {

    private final DirectoryService directoryService;

    public DirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/regions")
    @PreAuthorize("hasAuthority('REGION_READ')")
    List<RegionResponse> regions() {
        return directoryService.regions();
    }

    @PostMapping("/regions")
    @PreAuthorize("hasAuthority('REGION_CREATE')")
    RegionResponse createRegion(@Valid @RequestBody RegionRequest request, HttpServletRequest httpRequest) {
        return directoryService.createRegion(request, httpRequest);
    }

    @GetMapping("/regions/{id}")
    @PreAuthorize("hasAuthority('REGION_READ')")
    RegionResponse region(@PathVariable UUID id) {
        return directoryService.region(id);
    }

    @RequestMapping(value = "/regions/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('REGION_UPDATE')")
    RegionResponse updateRegion(@PathVariable UUID id, @Valid @RequestBody RegionRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateRegion(id, request, httpRequest);
    }

    @PatchMapping("/regions/{id}/status")
    @PreAuthorize("hasAuthority('REGION_UPDATE')")
    RegionResponse updateRegionStatus(@PathVariable UUID id, @Valid @RequestBody ActiveStatusRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateRegionStatus(id, request, httpRequest);
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    List<DepartmentResponse> departments() {
        return directoryService.departments();
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('DEPARTMENT_CREATE')")
    DepartmentResponse createDepartment(@Valid @RequestBody DepartmentRequest request, HttpServletRequest httpRequest) {
        return directoryService.createDepartment(request, httpRequest);
    }

    @GetMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    DepartmentResponse department(@PathVariable UUID id) {
        return directoryService.department(id);
    }

    @RequestMapping(value = "/departments/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    DepartmentResponse updateDepartment(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateDepartment(id, request, httpRequest);
    }

    @PatchMapping("/departments/{id}/status")
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE') or hasAuthority('DEPARTMENT_CLOSE')")
    DepartmentResponse updateDepartmentStatus(@PathVariable UUID id, @Valid @RequestBody DepartmentStatusRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateDepartmentStatus(id, request, httpRequest);
    }

    @DeleteMapping("/departments/{id}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE') or hasAuthority('DEPARTMENT_CLOSE')")
    void deleteDepartment(@PathVariable UUID id, HttpServletRequest httpRequest) {
        directoryService.deleteDepartment(id, httpRequest);
    }

    @GetMapping("/departments/{departmentId}/rooms")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    List<OfficeRoomResponse> rooms(@PathVariable UUID departmentId) {
        return directoryService.rooms(departmentId);
    }

    @PostMapping("/departments/{departmentId}/rooms")
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    OfficeRoomResponse createRoom(@PathVariable UUID departmentId, @Valid @RequestBody OfficeRoomRequest request, HttpServletRequest httpRequest) {
        return directoryService.createRoom(departmentId, request, httpRequest);
    }

    @RequestMapping(value = "/rooms/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    OfficeRoomResponse updateRoom(@PathVariable UUID id, @Valid @RequestBody OfficeRoomRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateRoom(id, request, httpRequest);
    }

    @GetMapping("/departments/{departmentId}/halls")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    List<HallResponse> halls(@PathVariable UUID departmentId) {
        return directoryService.halls(departmentId);
    }

    @PostMapping("/departments/{departmentId}/halls")
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    HallResponse createHall(@PathVariable UUID departmentId, @Valid @RequestBody HallRequest request, HttpServletRequest httpRequest) {
        return directoryService.createHall(departmentId, request, httpRequest);
    }

    @RequestMapping(value = "/halls/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    HallResponse updateHall(@PathVariable UUID id, @Valid @RequestBody HallRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateHall(id, request, httpRequest);
    }

    @DeleteMapping("/halls/{id}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    void deleteHall(@PathVariable UUID id, HttpServletRequest httpRequest) {
        directoryService.deleteHall(id, httpRequest);
    }

    @GetMapping("/departments/{departmentId}/windows")
    @PreAuthorize("hasAuthority('WINDOW_READ')")
    List<WindowResponse> windows(@PathVariable UUID departmentId) {
        return directoryService.windows(departmentId);
    }

    @GetMapping("/service-windows")
    @PreAuthorize("hasAuthority('WINDOW_READ')")
    List<WindowResponse> serviceWindows() {
        return directoryService.windows();
    }

    @PostMapping("/departments/{departmentId}/windows")
    @PreAuthorize("hasAuthority('WINDOW_CREATE')")
    WindowResponse createWindow(@PathVariable UUID departmentId, @Valid @RequestBody WindowRequest request, HttpServletRequest httpRequest) {
        return directoryService.createWindow(departmentId, request, httpRequest);
    }

    @GetMapping("/windows/{id}")
    @PreAuthorize("hasAuthority('WINDOW_READ')")
    WindowResponse window(@PathVariable UUID id) {
        return directoryService.window(id);
    }

    @RequestMapping(value = "/windows/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('WINDOW_UPDATE')")
    WindowResponse updateWindow(@PathVariable UUID id, @Valid @RequestBody WindowRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateWindow(id, request, httpRequest);
    }

    @PatchMapping("/windows/{id}/status")
    @PreAuthorize("hasAuthority('WINDOW_UPDATE')")
    WindowResponse updateWindowStatus(@PathVariable UUID id, @Valid @RequestBody WindowStatusRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateWindowStatus(id, request, httpRequest);
    }

    @DeleteMapping("/windows/{id}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('WINDOW_UPDATE')")
    void deleteWindow(@PathVariable UUID id, HttpServletRequest httpRequest) {
        directoryService.deleteWindow(id, httpRequest);
    }

    @PostMapping("/windows/{id}/assign-employee")
    @PreAuthorize("hasAuthority('WINDOW_ASSIGN_EMPLOYEE')")
    WindowResponse assignEmployeeToWindow(@PathVariable UUID id,
                                          @Valid @RequestBody AssignEmployeeToWindowRequest request,
                                          HttpServletRequest httpRequest) {
        return directoryService.assignEmployeeToWindow(id, request, httpRequest);
    }

    @PostMapping("/windows/{id}/open")
    @PreAuthorize("hasAuthority('WINDOW_OPEN')")
    WindowResponse openWindow(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return directoryService.openWindow(id, httpRequest);
    }

    @PostMapping("/windows/{id}/close")
    @PreAuthorize("hasAuthority('WINDOW_CLOSE')")
    WindowResponse closeWindow(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return directoryService.closeWindow(id, httpRequest);
    }

    @GetMapping("/service-categories")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    List<ServiceCategoryResponse> serviceCategories() {
        return directoryService.serviceCategories();
    }

    @PostMapping("/service-categories")
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    ServiceCategoryResponse createServiceCategory(@Valid @RequestBody ServiceCategoryRequest request, HttpServletRequest httpRequest) {
        return directoryService.createServiceCategory(request, httpRequest);
    }

    @GetMapping("/service-categories/{id}")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    ServiceCategoryResponse serviceCategory(@PathVariable UUID id) {
        return directoryService.serviceCategory(id);
    }

    @RequestMapping(value = "/service-categories/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    ServiceCategoryResponse updateServiceCategory(@PathVariable UUID id,
                                                  @Valid @RequestBody ServiceCategoryRequest request,
                                                  HttpServletRequest httpRequest) {
        return directoryService.updateServiceCategory(id, request, httpRequest);
    }

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    List<ServiceResponse> services() {
        return directoryService.services();
    }

    @PostMapping("/services")
    @PreAuthorize("hasAuthority('SERVICE_CREATE')")
    ServiceResponse createService(@Valid @RequestBody ServiceRequest request, HttpServletRequest httpRequest) {
        return directoryService.createService(request, httpRequest);
    }

    @GetMapping("/services/{id}")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    ServiceResponse service(@PathVariable UUID id) {
        return directoryService.service(id);
    }

    @RequestMapping(value = "/services/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    ServiceResponse updateService(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateService(id, request, httpRequest);
    }

    @PatchMapping("/services/{id}/status")
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    ServiceResponse updateServiceStatus(@PathVariable UUID id, @Valid @RequestBody ActiveStatusRequest request, HttpServletRequest httpRequest) {
        return directoryService.updateServiceStatus(id, request, httpRequest);
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    void deleteService(@PathVariable UUID id, HttpServletRequest httpRequest) {
        directoryService.deleteService(id, httpRequest);
    }

    @GetMapping("/departments/{departmentId}/services")
    @PreAuthorize("hasAuthority('SERVICE_READ')")
    List<DepartmentServiceResponse> departmentServices(@PathVariable UUID departmentId) {
        return directoryService.departmentServices(departmentId);
    }

    @PostMapping("/departments/{departmentId}/services/{serviceId}")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN_TO_DEPARTMENT')")
    DepartmentServiceResponse assignServiceToDepartment(@PathVariable UUID departmentId,
                                                        @PathVariable UUID serviceId,
                                                        @RequestBody(required = false) DepartmentServiceRequest request,
                                                        HttpServletRequest httpRequest) {
        return directoryService.assignServiceToDepartment(departmentId, serviceId, request, httpRequest);
    }

    @DeleteMapping("/departments/{departmentId}/services/{serviceId}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN_TO_DEPARTMENT')")
    void removeServiceFromDepartment(@PathVariable UUID departmentId, @PathVariable UUID serviceId, HttpServletRequest httpRequest) {
        directoryService.removeServiceFromDepartment(departmentId, serviceId, httpRequest);
    }

    @PostMapping("/employees/{employeeId}/services/{serviceId}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN_TO_EMPLOYEE')")
    void assignServiceToEmployee(@PathVariable UUID employeeId,
                                 @PathVariable UUID serviceId,
                                 @Valid @RequestBody AssignEmployeeServiceRequest request,
                                 HttpServletRequest httpRequest) {
        directoryService.assignServiceToEmployee(employeeId, serviceId, request, httpRequest);
    }

    @GetMapping("/employees/{employeeId}/services")
    @PreAuthorize("hasAuthority('SERVICE_READ') or hasAuthority('SERVICE_ASSIGN_TO_EMPLOYEE')")
    List<EmployeeServiceAssignmentResponse> employeeServices(@PathVariable UUID employeeId,
                                                             @RequestParam(required = false) UUID departmentId) {
        return directoryService.employeeServices(employeeId, departmentId);
    }

    @DeleteMapping("/employees/{employeeId}/services/{serviceId}")
    @ResponseStatus(NO_CONTENT)
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN_TO_EMPLOYEE')")
    void removeServiceFromEmployee(@PathVariable UUID employeeId,
                                   @PathVariable UUID serviceId,
                                   @RequestParam(required = false) UUID departmentId,
                                   HttpServletRequest httpRequest) {
        directoryService.removeServiceFromEmployee(employeeId, serviceId, departmentId, httpRequest);
    }
}
