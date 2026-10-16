package org.example.bwxw.repository;

import org.example.bwxw.entity.Leave;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    @Modifying
    @Query("UPDATE Leave l SET l.student = null WHERE l.student.id = :studentId")
    int detachStudent(Long studentId);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "approver"})
    List<Leave> findAll();
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "approver"})
    List<Leave> findByStudentId(Long studentId);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "approver"})
    List<Leave> findByStatus(Leave.LeaveStatus status);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "approver"})
    List<Leave> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "approver"})
    List<Leave> findByStatusOrderByCreatedAtDesc(Leave.LeaveStatus status);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "approver"})
    List<Leave> findByStudentIdAndStatus(Long studentId, Leave.LeaveStatus status);
    
    @EntityGraph(attributePaths = {"student", "student.room", "student.room.building", "approver"})
    @Query("SELECT l FROM Leave l WHERE l.status = 'PENDING' ORDER BY l.createdAt ASC")
    List<Leave> findPendingLeavesOrderedByDate();
    
    long countByStatus(Leave.LeaveStatus status);
    
    @Query("SELECT l FROM Leave l WHERE l.student.id = :studentId AND l.startDate <= :date AND l.endDate >= :date AND l.status = 'APPROVED'")
    List<Leave> findActiveLeavesByStudentAndDate(Long studentId, LocalDate date);
}
