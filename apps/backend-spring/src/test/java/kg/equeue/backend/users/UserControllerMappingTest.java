package kg.equeue.backend.users;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import kg.equeue.backend.users.dto.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

class UserControllerMappingTest {

    @Test
    void updateUserEndpointAcceptsPatchForAdminFrontend() throws Exception {
        Method method = UserController.class.getDeclaredMethod(
                "update",
                UUID.class,
                UpdateUserRequest.class,
                HttpServletRequest.class
        );
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/{id}");
        assertThat(mapping.method()).containsExactlyInAnyOrder(RequestMethod.PUT, RequestMethod.PATCH);
    }

    @Test
    void deleteUserEndpointReturnsNoContent() throws Exception {
        Method method = UserController.class.getDeclaredMethod(
                "delete",
                UUID.class,
                HttpServletRequest.class
        );
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/{id}");
        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAuthority('USER_DELETE')");
    }
}
