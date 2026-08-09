package com.sanproject.aso_service.security;

import com.sanproject.aso_service.repository.CustomerRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that runs on every HTTP request before controllers.
 * If the Authorization header carries a valid Bearer JWT, it places an AuthUser into
 * Spring Security's SecurityContext so controllers can call AuthSupport.requireClient() etc.
 * Client JWTs are rejected once the customer row is gone (account deletion).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomerRepository customerRepository;

    public JwtAuthenticationFilter(JwtService jwtService, CustomerRepository customerRepository) {
        this.jwtService = jwtService;
        this.customerRepository = customerRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    AuthUser user = jwtService.parseToken(token);
                    if (isDeletedClient(user)) {
                        SecurityContextHolder.clearContext();
                    } else {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (JwtException ignored) {
                    // Invalid token → leave context empty; SecurityFilterChain returns 401 if route needs auth.
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDeletedClient(AuthUser user) {
        if (user == null || !"CLIENT".equals(user.getRole()) || user.getId() == null) {
            return false;
        }
        return !customerRepository.existsById(user.getId());
    }
}
