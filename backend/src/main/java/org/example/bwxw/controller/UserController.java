package org.example.bwxw.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import org.example.bwxw.dto.AdminRequest;
import org.example.bwxw.dto.AdminResponse;
import org.example.bwxw.dto.ApiResponse;
import org.example.bwxw.dto.UserInfoResponse;
import org.example.bwxw.entity.User;
import org.example.bwxw.filter.TokenAuthenticationFilter;
import org.example.bwxw.service.RoomService;
import org.example.bwxw.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoomService roomService;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/students")
    public ApiResponse<List<User>> getAllStudents() {
        try {
            List<User> students = userRepository.findByRoleOrderByStudentIdAsc(User.UserRole.STUDENT);
            return ApiResponse.success(students);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable Long id) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            return ApiResponse.success(user);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            // 更新用户信息
            if (userDetails.getName() != null) {
                user.setName(userDetails.getName());
            }
            if (userDetails.getPhone() != null) {
                user.setPhone(userDetails.getPhone());
            }
            if (userDetails.getEmail() != null) {
                user.setEmail(userDetails.getEmail());
            }
            
            User updatedUser = userRepository.save(user);
            return ApiResponse.success("更新成功", updatedUser);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    // 获取当前用户信息
    @GetMapping("/current")
    public ApiResponse<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        try {
            // 检查Authorization header
            String authHeader = request.getHeader("Authorization");
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            
            if ("anonymousUser".equals(username)) {
                return ApiResponse.error("用户未登录或token无效");
            }
            
            // 优先用学号查询，如果找不到再用用户名查询
            User user = userRepository.findByStudentId(username)
                    .or(() -> userRepository.findByUsername(username))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            // 直接从 User 实体获取 className（已从 Student 表迁移）
            String className = user.getClassName();
            
            UserInfoResponse response = new UserInfoResponse(user, className);
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    // 更新当前用户信息
    @PutMapping("/current")
    public ApiResponse<User> updateCurrentUser(@RequestBody User userDetails) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            // 优先用学号查询，如果找不到再用用户名查询
            User user = userRepository.findByStudentId(username)
                    .or(() -> userRepository.findByUsername(username))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            // 更新用户信息
            if (userDetails.getName() != null) {
                user.setName(userDetails.getName());
            }
            if (userDetails.getDisplayName() != null) {
                user.setDisplayName(userDetails.getDisplayName());
            }
            if (userDetails.getPhone() != null) {
                user.setPhone(userDetails.getPhone());
            }
            if (userDetails.getEmail() != null) {
                user.setEmail(userDetails.getEmail());
            }
            if (userDetails.getAvatar() != null) {
                user.setAvatar(userDetails.getAvatar());
            }
            
            User updatedUser = userRepository.save(user);
            return ApiResponse.success("更新成功", updatedUser);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    // 上传头像
    @PostMapping("/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ApiResponse.error("请选择文件");
            }
            
            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ApiResponse.error("只能上传图片文件");
            }
            
            // 验证文件大小 (2MB)
            if (file.getSize() > 2 * 1024 * 1024) {
                return ApiResponse.error("文件大小不能超过2MB");
            }
            
            // 创建上传目录
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) {
                uploadDirFile.mkdirs();
            }
            
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.lastIndexOf(".") < 0) {
                return ApiResponse.error("文件格式不正确");
            }
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String lowerExtension = fileExtension.toLowerCase();
            if (!lowerExtension.equals(".jpg")
                    && !lowerExtension.equals(".jpeg")
                    && !lowerExtension.equals(".png")
                    && !lowerExtension.equals(".webp")) {
                return ApiResponse.error("仅支持 JPG、JPEG、PNG、WEBP 图片");
            }
            String fileName = UUID.randomUUID().toString() + lowerExtension;
            
            // 保存文件
            Path filePath = Paths.get(uploadDir, fileName);
            Files.copy(file.getInputStream(), filePath);
            
            // 生成访问URL
            String avatarUrl = "/uploads/" + fileName;
            
            // 更新用户头像
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            // 优先用学号查询，如果找不到再用用户名查询
            User user = userRepository.findByStudentId(username)
                    .or(() -> userRepository.findByUsername(username))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            user.setAvatar(avatarUrl);
            userRepository.save(user);
            
            Map<String, String> result = new HashMap<>();
            result.put("avatarUrl", avatarUrl);
            return ApiResponse.success("头像上传成功", result);
        } catch (Exception e) {
            return ApiResponse.error("头像上传失败: " + e.getMessage());
        }
    }
    
    // 删除头像
    @DeleteMapping("/avatar")
    public ApiResponse<Void> removeAvatar() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            // 优先用学号查询，如果找不到再用用户名查询
            User user = userRepository.findByStudentId(username)
                    .or(() -> userRepository.findByUsername(username))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            // 删除头像文件（如果存在）
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String fileName = user.getAvatar().substring(user.getAvatar().lastIndexOf("/") + 1);
                Path filePath = Paths.get(uploadDir, fileName);
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    // 文件删除失败，记录日志但不影响数据库操作
                }
            }
            
            // 清空数据库中的头像字段
            user.setAvatar(null);
            userRepository.save(user);
            
            return ApiResponse.<Void>success("头像删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("头像删除失败: " + e.getMessage());
        }
    }
    
    // 修改密码
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> passwordData,
                                            HttpServletRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            // 优先用学号查询，如果找不到再用用户名查询
            User user = userRepository.findByStudentId(username)
                    .or(() -> userRepository.findByUsername(username))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            String currentPassword = passwordData.get("currentPassword");
            String newPassword = passwordData.get("newPassword");
            
            if (currentPassword == null || newPassword == null) {
                return ApiResponse.error("当前密码和新密码不能为空");
            }

            if (newPassword.length() < 8 || newPassword.length() > 128) {
                return ApiResponse.error("新密码长度必须为8至128位");
            }

            if (currentPassword.equals(newPassword)) {
                return ApiResponse.error("新密码不能与当前密码相同");
            }
            
            // 验证当前密码
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                return ApiResponse.error("当前密码不正确");
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            userRepository.save(user);

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                TokenAuthenticationFilter.removeToken(authHeader.substring(7));
            }
            
            return ApiResponse.<Void>success("密码已修改，请重新登录", null);
        } catch (Exception e) {
            return ApiResponse.error("密码修改失败: " + e.getMessage());
        }
    }
    
    // 修改显示用户名
    @PutMapping("/display-name")
    public ApiResponse<User> updateDisplayName(@RequestBody Map<String, String> data) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            // 优先用学号查询，如果找不到再用用户名查询
            User user = userRepository.findByStudentId(username)
                    .or(() -> userRepository.findByUsername(username))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            String newDisplayName = data.get("displayName");
            
            // 允许displayName为空，这样前端会显示默认的学号
            if (newDisplayName != null) {
                newDisplayName = newDisplayName.trim();
                // 限制长度
                if (newDisplayName.length() > 20) {
                    return ApiResponse.error("显示用户名不能超过20个字符");
                }
                // 如果输入为空字符串，则设为null，这样前端会显示学号
                user.setDisplayName(newDisplayName.isEmpty() ? null : newDisplayName);
            } else {
                user.setDisplayName(null);
            }
            User updatedUser = userRepository.save(user);
            
            return ApiResponse.success("用户名修改成功", updatedUser);
        } catch (Exception e) {
            return ApiResponse.error("用户名修改失败: " + e.getMessage());
        }
    }
    
    // 修改邮箱 - 仅学生可以修改自己的邮箱
    @PutMapping("/email")
    public ApiResponse<User> updateEmail(@RequestBody Map<String, String> data) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            
            // 优先用学号查询，如果找不到再用用户名查询
            User user = userRepository.findByStudentId(username)
                    .or(() -> userRepository.findByUsername(username))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            // 只有学生可以设置邮箱
            if (user.getRole() != User.UserRole.STUDENT) {
                return ApiResponse.error("只有学生可以设置邮箱");
            }
            
            String email = data.get("email");
            
            // 验证邮箱格式（如果不为空）
            if (email != null && !email.trim().isEmpty()) {
                email = email.trim();
                if (!isValidEmail(email)) {
                    return ApiResponse.error("邮箱格式不正确");
                }
                user.setEmail(email);
            } else {
                // 空值表示清除邮箱
                user.setEmail(null);
            }
            
            User updatedUser = userRepository.save(user);
            return ApiResponse.success("邮箱修改成功", updatedUser);
        } catch (Exception e) {
            return ApiResponse.error("邮箱修改失败: " + e.getMessage());
        }
    }
    
    // 邮箱格式验证
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return true; // 空邮箱是有效的（表示清除）
        }
        // 邮箱正则表达式
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
    
    // ==================== 管理员管理接口 ====================
    
    /**
     * 获取所有管理员列表
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admins")
    public ApiResponse<List<AdminResponse>> getAllAdmins() {
        try {
            // 权限校验：只有超级管理员才能查看管理员列表
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getAdminType() != User.AdminType.SUPER_ADMIN) {
                return ApiResponse.error("权限不足，只有超级管理员才能管理其他管理员");
            }
            
            List<User> admins = userRepository.findByRole(User.UserRole.ADMIN);
            List<AdminResponse> responses = admins.stream()
                .map(this::convertToAdminResponse)
                .toList();
            return ApiResponse.success(responses);
        } catch (Exception e) {
            return ApiResponse.error("获取管理员列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建新管理员
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/admins")
    public ApiResponse<AdminResponse> createAdmin(@RequestBody AdminRequest request) {
        try {
            // 权限校验：只有超级管理员才能创建管理员
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getAdminType() != User.AdminType.SUPER_ADMIN) {
                return ApiResponse.error("权限不足，只有超级管理员才能创建管理员");
            }
            
            // 验证必填字段
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ApiResponse.error("姓名不能为空");
            }
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ApiResponse.error("用户名不能为空");
            }
            if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
                return ApiResponse.error("电话号码不能为空");
            }
            if (request.getAdminType() == null) {
                return ApiResponse.error("请选择管理员类型");
            }
            if (request.getPassword() == null
                    || request.getPassword().length() < 8
                    || request.getPassword().length() > 128) {
                return ApiResponse.error("初始密码长度必须为8至128位");
            }
            
            // 检查用户名是否已存在
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                return ApiResponse.error("用户名已存在");
            }
            
            // 创建管理员用户
            User admin = new User();
            admin.setName(request.getName().trim());
            admin.setUsername(request.getUsername().trim());
            admin.setPhone(request.getPhone().trim());
            admin.setPassword(passwordEncoder.encode(request.getPassword()));
            admin.setMustChangePassword(true);
            admin.setRole(User.UserRole.ADMIN);
            admin.setAdminType(request.getAdminType());
            admin.setStatus("ACTIVE");
            
            User savedAdmin = userRepository.save(admin);
            return ApiResponse.success("管理员创建成功", convertToAdminResponse(savedAdmin));
        } catch (Exception e) {
            return ApiResponse.error("创建管理员失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新管理员信息
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admins/{id}")
    public ApiResponse<AdminResponse> updateAdmin(@PathVariable Long id, @RequestBody AdminRequest request) {
        try {
            // 权限校验：只有超级管理员才能编辑管理员
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getAdminType() != User.AdminType.SUPER_ADMIN) {
                return ApiResponse.error("权限不足，只有超级管理员才能编辑管理员");
            }
            
            User admin = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("管理员不存在"));
            
            // 验证是否是管理员
            if (admin.getRole() != User.UserRole.ADMIN) {
                return ApiResponse.error("该用户不是管理员");
            }
            
            // 验证必填字段
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ApiResponse.error("姓名不能为空");
            }
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ApiResponse.error("用户名不能为空");
            }
            if (request.getPhone() == null || request.getPhone().trim().isEmpty()) {
                return ApiResponse.error("电话号码不能为空");
            }
            if (request.getAdminType() == null) {
                return ApiResponse.error("请选择管理员类型");
            }
            
            // 检查用户名是否被其他用户占用
            userRepository.findByUsername(request.getUsername())
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(id)) {
                        throw new RuntimeException("用户名已被其他用户使用");
                    }
                });
            
            // 更新信息
            admin.setName(request.getName().trim());
            admin.setUsername(request.getUsername().trim());
            admin.setPhone(request.getPhone().trim());
            admin.setAdminType(request.getAdminType());
            
            User updatedAdmin = userRepository.save(admin);
            return ApiResponse.success("管理员信息更新成功", convertToAdminResponse(updatedAdmin));
        } catch (Exception e) {
            return ApiResponse.error("更新管理员失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除管理员
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/admins/{id}")
    public ApiResponse<Void> deleteAdmin(@PathVariable Long id) {
        try {
            // 权限校验：只有超级管理员才能删除管理员
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getAdminType() != User.AdminType.SUPER_ADMIN) {
                return ApiResponse.error("权限不足，只有超级管理员才能删除管理员");
            }
            
            // 防止删除自己
            if (currentUser.getId().equals(id)) {
                return ApiResponse.error("不能删除自己");
            }
            
            User admin = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("管理员不存在"));
            
            // 验证是否是管理员
            if (admin.getRole() != User.UserRole.ADMIN) {
                return ApiResponse.error("该用户不是管理员");
            }
            
            // 防止删除最后一个超级管理员
            if (admin.getAdminType() == User.AdminType.SUPER_ADMIN) {
                long superAdminCount = userRepository.findByRole(User.UserRole.ADMIN).stream()
                    .filter(u -> u.getAdminType() == User.AdminType.SUPER_ADMIN)
                    .count();
                if (superAdminCount <= 1) {
                    return ApiResponse.error("不能删除最后一个超级管理员");
                }
            }
            
            userRepository.delete(admin);
            return ApiResponse.success("管理员删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除管理员失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置管理员临时密码，并要求下次登录后修改
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admins/{id}/reset-password")
    public ApiResponse<Void> resetAdminPassword(@PathVariable Long id,
                                                @RequestBody Map<String, String> passwordData) {
        try {
            // 权限校验：只有超级管理员才能重置密码
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getAdminType() != User.AdminType.SUPER_ADMIN) {
                return ApiResponse.error("权限不足，只有超级管理员才能重置密码");
            }
            
            User admin = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("管理员不存在"));
            
            // 验证是否是管理员
            if (admin.getRole() != User.UserRole.ADMIN) {
                return ApiResponse.error("该用户不是管理员");
            }

            String password = passwordData.get("password");
            if (password == null || password.length() < 8 || password.length() > 128) {
                return ApiResponse.error("临时密码长度必须为8至128位");
            }
            
            admin.setPassword(passwordEncoder.encode(password));
            admin.setMustChangePassword(true);
            userRepository.save(admin);
            
            return ApiResponse.success("密码已重置，该管理员下次登录后必须修改密码", null);
        } catch (Exception e) {
            return ApiResponse.error("重置密码失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换User实体为AdminResponse
     */
    private AdminResponse convertToAdminResponse(User user) {
        AdminResponse response = new AdminResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setUsername(user.getUsername());
        response.setPhone(user.getPhone());
        response.setStatus(user.getStatus());
        response.setAdminType(user.getAdminType());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
    
    /**
     * 获取当前登录的用户
     */
    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByStudentId(username)
            .or(() -> userRepository.findByUsername(username))
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    // ==================== 学生管理接口 ====================
    
    /**
     * 更新学生信息
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/students/{id}")
    public ApiResponse<User> updateStudent(@PathVariable Long id, @RequestBody User studentDetails) {
        try {
            // 权限校验：只有管理员才能更新学生信息
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getRole() != User.UserRole.ADMIN) {
                return ApiResponse.error("权限不足，只有管理员才能更新学生信息");
            }
            
            User student = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
            
            // 验证是否是学生
            if (student.getRole() != User.UserRole.STUDENT) {
                return ApiResponse.error("该用户不是学生");
            }
            
            // 更新学生信息
            if (studentDetails.getName() != null) {
                student.setName(studentDetails.getName());
            }
            if (studentDetails.getStudentId() != null && !studentDetails.getStudentId().equals(student.getStudentId())) {
                // 检查新学号是否已存在
                if (userRepository.findByStudentId(studentDetails.getStudentId()).isPresent()) {
                    return ApiResponse.error("学号已存在");
                }
                student.setStudentId(studentDetails.getStudentId());
            }
            if (studentDetails.getGender() != null) {
                student.setGender(studentDetails.getGender());
            }
            if (studentDetails.getPhone() != null) {
                student.setPhone(studentDetails.getPhone());
            }
            if (studentDetails.getClassName() != null) {
                student.setClassName(studentDetails.getClassName());
            }
            // 允许清空班级
            if (studentDetails.getClassName() == null && studentDetails.getPhone() != null) {
                // 如果传了phone但className为null，说明是要清空班级
                student.setClassName(null);
            }
            
            User updatedStudent = userRepository.save(student);
            return ApiResponse.success("更新成功", updatedStudent);
        } catch (Exception e) {
            return ApiResponse.error("更新学生失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除学生
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/students/{id}")
    public ApiResponse<Void> deleteStudent(@PathVariable Long id) {
        try {
            // 权限校验：只有管理员才能删除学生
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getRole() != User.UserRole.ADMIN) {
                return ApiResponse.error("权限不足，只有管理员才能删除学生");
            }
            
            roomService.deleteStudent(id);
            return ApiResponse.success("学生删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除学生失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置学生密码为默认密码123456
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/students/{id}/reset-password")
    public ApiResponse<Void> resetStudentPassword(@PathVariable Long id) {
        try {
            // 权限校验：只有管理员才能重置密码
            User currentUser = getCurrentAuthenticatedUser();
            if (currentUser.getRole() != User.UserRole.ADMIN) {
                return ApiResponse.error("权限不足，只有管理员才能重置密码");
            }
            
            User student = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
            
            // 验证是否是学生
            if (student.getRole() != User.UserRole.STUDENT) {
                return ApiResponse.error("该用户不是学生");
            }
            
            student.setPassword(passwordEncoder.encode("123456"));
            student.setMustChangePassword(true);
            userRepository.save(student);
            
            return ApiResponse.success("密码重置成功，新密码为：123456", null);
        } catch (Exception e) {
            return ApiResponse.error("重置密码失败: " + e.getMessage());
        }
    }
}
