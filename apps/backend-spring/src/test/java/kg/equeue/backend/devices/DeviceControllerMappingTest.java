package kg.equeue.backend.devices;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

class DeviceControllerMappingTest {

    @Test
    void deleteDeviceEndpointsReturnNoContentAndRequireDeletePermission() throws Exception {
        assertDelete("deleteTerminal", "/terminals/{id}", "hasAuthority('TERMINAL_DELETE')");
        assertDelete("deleteTvDisplay", "/tv-displays/{id}", "hasAuthority('TV_DELETE')");
    }

    private void assertDelete(String methodName, String path, String permission) throws Exception {
        Method method = DeviceController.class.getDeclaredMethod(
                methodName,
                UUID.class,
                HttpServletRequest.class
        );
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(permission);
    }
}
