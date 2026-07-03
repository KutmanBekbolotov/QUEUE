package kg.equeue.backend.auth;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import kg.equeue.backend.roles.RoleEntity;
import kg.equeue.backend.users.UserEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class AuthorityMapper {

    private AuthorityMapper() {
    }

    public static Set<SimpleGrantedAuthority> authorities(UserEntity user) {
        Set<String> values = new LinkedHashSet<>();
        for (RoleEntity role : user.getRoles()) {
            values.add("ROLE_" + role.getCode());
            role.getPermissions().forEach(permission -> values.add(permission.getCode()));
        }
        return values.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

