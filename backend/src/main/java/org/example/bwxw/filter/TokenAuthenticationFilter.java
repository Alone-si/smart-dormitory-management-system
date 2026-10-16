package org.example.bwxw.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.bwxw.entity.User;
import org.example.bwxw.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private UserRepository userRepository;
    
    private static final long TOKEN_TTL_MILLIS = 2 * 60 * 60 * 1000L;
    private static final Map<String, TokenEntry> tokenStore = new ConcurrentHashMap<>();

    public static void storeToken(String token, User user) {
        tokenStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        tokenStore.put(token, new TokenEntry(user.getId(), user.getPassword(), System.currentTimeMillis() + TOKEN_TTL_MILLIS));
    }

    public static void removeToken(String token) {
        tokenStore.remove(token);
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            TokenEntry entry = tokenStore.get(token);
            if (entry == null || entry.isExpired()) {
                tokenStore.remove(token);
                if (!request.getRequestURI().startsWith("/api/auth/")) {
                    rejectExpiredLogin(response);
                    return;
                }
            } else {
                User user = userRepository.findById(entry.userId).orElse(null);

                // 密码变更使所有旧会话失效；使用不可变 ID 避免用户名变更导致身份混淆。
                if (user == null || !"ACTIVE".equals(user.getStatus())
                        || !Objects.equals(entry.passwordHash, user.getPassword())) {
                    tokenStore.remove(token);
                    rejectExpiredLogin(response);
                    return;
                }

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                if (user.getAdminType() == User.AdminType.SUPER_ADMIN) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                }

                String principal = user.getStudentId() != null && !user.getStudentId().isEmpty()
                        ? user.getStudentId()
                        : user.getUsername();

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (Boolean.TRUE.equals(user.getMustChangePassword())
                        && !isAllowedBeforePasswordChange(request, user)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/json");
                    String message = user.getRole() == User.UserRole.STUDENT
                            ? "当前为只读访客模式，修改密码后即可操作"
                            : "请先修改初始密码";
                    response.getWriter().write(
                            "{\"code\":403,\"message\":\"" + message + "\",\"data\":null}"
                    );
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void rejectExpiredLogin(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":401,\"message\":\"登录状态已失效，请重新登录\",\"data\":null}");
    }

    private boolean isAllowedBeforePasswordChange(HttpServletRequest request, User user) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || "/api/users/password".equals(path)) {
            return true;
        }
        if (user.getRole() != User.UserRole.STUDENT) {
            return false;
        }
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private static class TokenEntry {
        private final Long userId;
        private final String passwordHash;
        private final long expiresAt;

        TokenEntry(Long userId, String passwordHash, long expiresAt) {
            this.userId = userId;
            this.passwordHash = passwordHash;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
