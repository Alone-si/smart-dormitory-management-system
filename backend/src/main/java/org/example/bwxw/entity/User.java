package org.example.bwxw.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类 - 支持管理员和学生双角色
 */
@Entity
@Table(name = "users")
@Data
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @JsonIgnore
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    
    @Column(nullable = false, unique = true, length = 50)
    private String username; // 登录用户名（学号）
    
    @Column(length = 50)
    private String displayName; // 显示用户名（可自定义，默认为学号）
    
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "must_change_password")
    private Boolean mustChangePassword = false;
    
    @Column(nullable = false, length = 50)
    private String name;
    
    @Column(length = 20)
    private String studentId; // 学号（学生专用）
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; // ADMIN 或 STUDENT
    
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;
    
    @Column(length = 20)
    private String phone;
    
    @Column(length = 100)
    private String email;
    
    @Column(length = 500)
    private String avatar; // 头像URL
    
    @Column(length = 50)
    private String className; // 班级（学生专用）
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room; // 所属宿舍（学生专用）
    
    @Column(length = 20)
    private String status; // ACTIVE, INACTIVE, GRADUATED
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AdminType adminType; // 管理员类型（仅管理员角色使用）
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum UserRole {
        ADMIN,
        STUDENT
    }
    
    public enum Gender {
        MALE,
        FEMALE
    }
    
    public enum AdminType {
        SUPER_ADMIN,    // 超级管理员（可以管理其他管理员）
        NORMAL_ADMIN    // 普通管理员（不能管理其他管理员）
    }
}
