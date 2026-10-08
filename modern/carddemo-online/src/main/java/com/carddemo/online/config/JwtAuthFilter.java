package com.carddemo.online.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.carddemo.online.repo.UserSecurityRepository;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-token authentication; role ADMIN for user type 'A', USER otherwise. An admin token is re-checked
 * against USRSEC (primary-key read) on every request so a demoted or deleted admin loses ADMIN at once and is
 * treated as a regular user (COMEN01C menu) for the rest of the token's life.
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
            jwt.verify(h.substring(7)).ifPresent(token -> {
                SessionUser u = token.isAdmin() && !stillAdmin(token.userId())
                        ? new SessionUser(token.userId(), "U", token.displayName())
                        : token;
                var auth = new UsernamePasswordAuthenticationToken(u, null,
                        List.of(new SimpleGrantedAuthority(u.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }

    private boolean stillAdmin(String userId) {
        return users.findById(userId).map(x -> "A".equals(x.getUserType())).orElse(false);
    }
}
