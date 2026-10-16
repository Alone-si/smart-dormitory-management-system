package org.example.bwxw.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 宿舍房间实体类
 */
@Entity
@Table(name = "rooms")
@Data
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;
    
    @ManyToOne
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;
    
    @Column(nullable = false, length = 20)
    private String roomNumber; // 房间号，如 "101", "201"
    
    @Column(nullable = false)
    private Integer floor; // 楼层
    
    @Column(nullable = false)
    private Integer capacity; // 床位数（通常4-6人）
    
    @Column(nullable = false)
    private Integer occupied; // 已入住人数
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status; // AVAILABLE, FULL, MAINTENANCE
    
    @OneToMany(mappedBy = "room")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<User> students;
    
    @Column(length = 500)
    private String dormContract; // 宿舍公约
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (occupied == null) {
            occupied = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum RoomStatus {
        AVAILABLE,  // 可入住
        FULL,       // 已满
        MAINTENANCE // 维护中
    }
}
