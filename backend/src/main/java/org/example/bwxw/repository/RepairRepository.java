package org.example.bwxw.repository;

import org.example.bwxw.entity.Repair;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RepairRepository extends JpaRepository<Repair, Long> {

    @Modifying
    @Query("UPDATE Repair r SET r.student = null WHERE r.student.id = :studentId")
    int detachStudent(Long studentId);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    List<Repair> findAll();
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    List<Repair> findByStudentId(Long studentId);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    List<Repair> findByRoomId(Long roomId);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    List<Repair> findByStatus(Repair.RepairStatus status);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    List<Repair> findByType(Repair.RepairType type);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    List<Repair> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    List<Repair> findByStatusOrderByCreatedAtDesc(Repair.RepairStatus status);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "room", "room.building", "handler"})
    @Query("SELECT r FROM Repair r WHERE r.status = 'PENDING' ORDER BY r.urgency DESC, r.createdAt ASC")
    List<Repair> findPendingRepairsOrderedByUrgency();
    
    long countByStatus(Repair.RepairStatus status);
    
    @Query("SELECT r.type, COUNT(r) FROM Repair r GROUP BY r.type")
    List<Object[]> countByType();
}
