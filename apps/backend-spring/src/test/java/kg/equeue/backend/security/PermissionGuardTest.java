package kg.equeue.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import kg.equeue.backend.auth.AuthorityMapper;
import kg.equeue.backend.permissions.PermissionEntity;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.users.UserEntity;
import org.junit.jupiter.api.Test;

class PermissionGuardTest {

    @Test
    void authorityMapperExposesRoleAndDbPermissionCodes() {
        PermissionEntity permission = new PermissionEntity();
        permission.setCode("TICKET_CALL");

        RoleEntity role = new RoleEntity();
        role.setCode("OPERATOR");
        role.getPermissions().add(permission);

        UserEntity user = new UserEntity();
        user.setUsername("operator");
        user.getRoles().add(role);

        assertThat(AuthorityMapper.authorities(user))
                .extracting(Object::toString)
                .contains("ROLE_OPERATOR", "TICKET_CALL");
    }
}

