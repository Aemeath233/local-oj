package com.localoj.backend.security;

import com.localoj.common.mapper.UserMapper;
import com.localoj.common.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserMapper userMapper,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = null;
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }

        if (token != null && !token.trim().isEmpty()) {
            jwtService.parse(token).ifPresent(user -> {
                User dbUser = userMapper.selectById(user.id());
                if (dbUser != null && Boolean.TRUE.equals(dbUser.getEnabled()) && dbUser.getRole() == user.role()) {
                    var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            });
        } else {
            String ticket = request.getParameter("ticket");
            if (ticket != null && !ticket.trim().isEmpty()) {
                String userJson = redisTemplate.opsForValue().get("sse:ticket:" + ticket);
                if (userJson != null) {
                    redisTemplate.delete("sse:ticket:" + ticket);
                    try {
                        CurrentUser user = objectMapper.readValue(userJson, CurrentUser.class);
                        User dbUser = userMapper.selectById(user.id());
                        if (dbUser != null && Boolean.TRUE.equals(dbUser.getEnabled()) && dbUser.getRole() == user.role()) {
                            var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
                            var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
