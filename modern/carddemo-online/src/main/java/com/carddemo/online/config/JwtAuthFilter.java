package com.carddemo.online.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.carddemo.online.repo.UserSecurityRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-token authentication; role ADMIN for user type 'A', USER otherwise. An admin token is re-checked
 * against USRSEC (primary-key read) on every request so a demoted admin is
 * treated as a regular user (COMEN01C menu) and a deleted admin's token is rejected at once.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserSecurityRepository users;

    public JwtAuthFilter(JwtService jwt, UserSecurityRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            jwt.verify(h.substring(7)).flatMap(this::current).ifPresent(u -> {
                var auth = new UsernamePasswordAuthenticationToken(u, null,
                        List.of(new SimpleGrantedAuthority(u.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }

    /** Admin tokens: deleted user -> unauthenticated; demoted user -> regular-user principal. */
    private Optional<SessionUser> current(SessionUser token) {
        if (!token.isAdmin()) {
            return Optional.of(token);
        }
        return users.findById(token.userId()).map(x -> "A".equals(x.getUserType())
                ? token
                : new SessionUser(token.userId(), "U", token.displayName()));
    }
}
