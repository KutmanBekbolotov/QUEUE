package kg.equeue.backend.directories;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import kg.equeue.backend.directories.DirectoryDtos.DepartmentRequest;
import kg.equeue.backend.directories.DirectoryDtos.HallRequest;
import kg.equeue.backend.directories.DirectoryDtos.OfficeRoomRequest;
import kg.equeue.backend.directories.DirectoryDtos.RegionRequest;
import kg.equeue.backend.directories.DirectoryDtos.ServiceCategoryRequest;
import kg.equeue.backend.directories.DirectoryDtos.ServiceRequest;
import kg.equeue.backend.directories.DirectoryDtos.WindowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class DirectoryControllerMappingTest {

    @Test
    void fullUpdateDirectoryEndpointsAcceptPatchForFrontendCompatibility() throws Exception {
        assertPutAndPatch("updateRegion", "/regions/{id}", RegionRequest.class);
        assertPutAndPatch("updateDepartment", "/departments/{id}", DepartmentRequest.class);
        assertPutAndPatch("updateRoom", "/rooms/{id}", OfficeRoomRequest.class);
        assertPutAndPatch("updateHall", "/halls/{id}", HallRequest.class);
        assertPutAndPatch("updateWindow", "/windows/{id}", WindowRequest.class);
        assertPutAndPatch("updateServiceCategory", "/service-categories/{id}", ServiceCategoryRequest.class);
        assertPutAndPatch("updateService", "/services/{id}", ServiceRequest.class);
    }

    private void assertPutAndPatch(String methodName, String path, Class<?> requestType) throws Exception {
        Method method = DirectoryController.class.getDeclaredMethod(
                methodName,
                UUID.class,
                requestType,
                HttpServletRequest.class
        );
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
        assertThat(mapping.method()).containsExactlyInAnyOrder(RequestMethod.PUT, RequestMethod.PATCH);
    }
}
