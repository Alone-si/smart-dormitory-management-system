package org.example.bwxw.service;

import org.example.bwxw.dto.LoginRequest;
import org.example.bwxw.dto.LoginResponse;
import org.example.bwxw.entity.User;
import org.example.bwxw.filter.TokenAuthenticationFilter;
import org.example.bwxw.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 5 * 60 * 1000L;
    // ponytail: 单机项目用内存限制即可；部署多实例时再换共享存储。
    private final Map<String, LoginAttempt> failedLogins = new ConcurrentHashMap<>();
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null
                || request.getUsername().isBlank() || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        String username = request.getUsername().trim();
        if (username.length() > 64 || request.getPassword().length() > 128) {
            throw new IllegalArgumentException("用户名或密码格式不正确");
        }

        // 优先用学号登录（学生），如果找不到再用用户名登录（管理员）
        User user = userRepository.findByStudentId(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        LoginAttempt attempt = failedLogins.get(username);
        if (attempt != null && attempt.isLocked()) {
            throw new IllegalStateException("登录失败次数过多，请5分钟后再试");
        }
        if (attempt != null && attempt.isExpired()) {
            failedLogins.remove(username);
        }

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        
        if (!passwordMatches || !"ACTIVE".equals(user.getStatus())) {
            failedLogins.compute(username, (key, previous) -> LoginAttempt.next(previous));
            throw new IllegalArgumentException("用户名或密码错误");
        }

        failedLogins.remove(username);

        // 学生使用默认密码时进入只读访客模式；管理员的改密标记由创建/重置流程设置。
        if (user.getRole() == User.UserRole.STUDENT
                && "123456".equals(request.getPassword())
                && !Boolean.TRUE.equals(user.getMustChangePassword())) {
            user.setMustChangePassword(true);
            userRepository.save(user);
        }
        
        // 单机部署使用服务端内存 Token。
        String token = UUID.randomUUID().toString();
        
        TokenAuthenticationFilter.storeToken(token, user);
        
        
        return new LoginResponse(
                token,
                user.getRole(),
                user.getId(),
                user.getUsername(),
                user.getName(),
                user  // 返回完整的用户对象
        );
    }
    
    public User register(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        return userRepository.save(user);
    }

    private record LoginAttempt(int count, long lastFailedAt) {
        private static LoginAttempt next(LoginAttempt previous) {
            long now = System.currentTimeMillis();
            if (previous == null || now - previous.lastFailedAt >= LOCK_MILLIS) {
                return new LoginAttempt(1, now);
            }
            return new LoginAttempt(previous.count + 1, now);
        }

        private boolean isLocked() {
            return count >= MAX_FAILED_ATTEMPTS
                    && System.currentTimeMillis() - lastFailedAt < LOCK_MILLIS;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - lastFailedAt >= LOCK_MILLIS;
        }
    }
}
