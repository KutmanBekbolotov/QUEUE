package kg.equeue.backend.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import kg.equeue.backend.common.ApiException;
import kg.equeue.backend.users.UserEntity;
import kg.equeue.backend.users.UserRepository;
import kg.equeue.backend.users.UserStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                authenticate(header.substring(7), request);
            } catch (ApiException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        Claims claims = jwtService.parse(token);
        UUID userId = UUID.fromString(claims.getSubject());
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);

        UserEntity user = userRepository.findDetailedById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || tokenVersion == null || user.getTokenVersion() != tokenVersion) {
            return;
        }

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                user.getId(),
                user.getUsername(),
                user.getTokenVersion(),
                user.getStatus(),
                AuthorityMapper.authorities(user)
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
