package kg.equeue.backend.auth;

import java.util.Collection;
import java.util.UUID;
import kg.equeue.backend.users.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final int tokenVersion;
    private final UserStatus status;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedPrincipal(UUID id, String username, int tokenVersion, UserStatus status,
                                  Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.tokenVersion = tokenVersion;
        this.status = status;
        this.authorities = authorities;
    }

    public UUID id() {
        return id;
    }

    public int tokenVersion() {
        return tokenVersion;
    }

    public UserStatus status() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}

