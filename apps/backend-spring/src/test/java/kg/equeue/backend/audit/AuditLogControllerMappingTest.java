package kg.equeue.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import kg.equeue.backend.audit.dto.AuditLogFilter;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

class AuditLogControllerMappingTest {

    @Test
    void searchEndpointUsesPagedFilterAndAuditPermission() throws Exception {
        Method method = AuditLogController.class.getDeclaredMethod("search", AuditLogFilter.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        ModelAttribute modelAttribute = method.getParameters()[0].getAnnotation(ModelAttribute.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/search");
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAuthority('AUDIT_READ')");
        assertThat(modelAttribute).isNotNull();
    }
}
