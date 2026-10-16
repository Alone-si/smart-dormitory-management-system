package org.example.bwxw.filter;

import jakarta.servlet.FilterChain;
import org.example.bwxw.entity.User;
import org.example.bwxw.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TokenAuthenticationFilter filter;

    @Test
    void permitsReadsInStudentVisitorMode() throws Exception {
        String token = "student-read-token";
        User user = forcedUser();
        TokenAuthenticationFilter.storeToken(token, user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus());
            verify(filterChain).doFilter(request, response);
        } finally {
            TokenAuthenticationFilter.removeToken(token);
        }
    }

    @Test
    void rejectsUnknownTokenAsUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        request.addHeader("Authorization", "Bearer unknown-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void permitsLoginRequestWithStaleToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void blocksWritesInStudentVisitorMode() throws Exception {
        String token = "student-write-token";
        User user = forcedUser();
        TokenAuthenticationFilter.storeToken(token, user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/repairs");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, filterChain);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("只读访客模式"));
            verify(filterChain, never()).doFilter(request, response);
        } finally {
            TokenAuthenticationFilter.removeToken(token);
        }
    }

    @Test
    void permitsPasswordChangeInStudentVisitorMode() throws Exception {
        String token = "student-password-token";
        User user = forcedUser();
        TokenAuthenticationFilter.storeToken(token, user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/users/password");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus());
            verify(filterChain).doFilter(request, response);
        } finally {
            TokenAuthenticationFilter.removeToken(token);
        }
    }

    @Test
    void permitsLoginInStudentVisitorMode() throws Exception {
        String token = "student-login-token";
        User user = forcedUser();
        TokenAuthenticationFilter.storeToken(token, user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus());
            verify(filterChain).doFilter(request, response);
        } finally {
            TokenAuthenticationFilter.removeToken(token);
        }
    }

    @Test
    void blocksAdminRequestsUntilInitialPasswordIsChanged() throws Exception {
        String token = "admin-password-change-token";
        User user = new User();
        user.setId(2L);
        user.setStatus("ACTIVE");
        user.setPassword("admin-hash");
        user.setUsername("admin");
        user.setRole(User.UserRole.ADMIN);
        user.setMustChangePassword(true);
        TokenAuthenticationFilter.storeToken(token, user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, filterChain);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("请先修改初始密码"));
            verify(filterChain, never()).doFilter(request, response);
        } finally {
            TokenAuthenticationFilter.removeToken(token);
        }
    }

    @Test
    void rejectsTokenAfterAccountIsDeactivated() throws Exception {
        User user = forcedUser();
        String token = "deactivated-user";
        TokenAuthenticationFilter.storeToken(token, user);
        user.setStatus("INACTIVE");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/current");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, filterChain);
            assertEquals(401, response.getStatus());
            verify(filterChain, never()).doFilter(request, response);
        } finally {
            TokenAuthenticationFilter.removeToken(token);
        }
    }

    @Test
    void passwordChangeInvalidatesEveryOldSessionButAllowsNewLogin() throws Exception {
        User user = forcedUser();
        String[] tokens = {"old-session-one", "old-session-two", "new-session"};
        TokenAuthenticationFilter.storeToken(tokens[0], user);
        TokenAuthenticationFilter.storeToken(tokens[1], user);
        user.setPassword("changed-password-hash");
        TokenAuthenticationFilter.storeToken(tokens[2], user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        try {
            for (int i = 0; i < tokens.length; i++) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/current");
                request.addHeader("Authorization", "Bearer " + tokens[i]);
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, filterChain);
                assertEquals(i < 2 ? 401 : 200, response.getStatus());
                if (i < 2) {
                    verify(filterChain, never()).doFilter(request, response);
                } else {
                    verify(filterChain).doFilter(request, response);
                }
            }
        } finally {
            for (String token : tokens) TokenAuthenticationFilter.removeToken(token);
        }
    }

    private User forcedUser() {
        User user = new User();
        user.setId(1L);
        user.setStatus("ACTIVE");
        user.setPassword("student-hash");
        user.setStudentId("20240001");
        user.setUsername("20240001");
        user.setRole(User.UserRole.STUDENT);
        user.setMustChangePassword(true);
        return user;
    }
}
