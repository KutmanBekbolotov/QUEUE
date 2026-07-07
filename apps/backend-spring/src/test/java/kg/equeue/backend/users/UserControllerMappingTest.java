package kg.equeue.backend.users;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import kg.equeue.backend.users.dto.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

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
}
