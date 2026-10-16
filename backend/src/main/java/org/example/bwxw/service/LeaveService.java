package org.example.bwxw.service;

import org.example.bwxw.entity.Leave;
import org.example.bwxw.entity.User;
import org.example.bwxw.repository.LeaveRepository;
import org.example.bwxw.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaveService {
    
    @Autowired
    private LeaveRepository leaveRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    public List<Leave> getAllLeaves() {
        return leaveRepository.findAll();
    }
    
    public Leave getLeaveById(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("请假记录不存在"));
    }
    
    public List<Leave> getLeavesByStudent(Long studentId) {
        return leaveRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }
    
    public List<Leave> getPendingLeaves() {
        return leaveRepository.findPendingLeavesOrderedByDate();
    }
    
    public List<Leave> getLeavesByStatus(Leave.LeaveStatus status) {
        return leaveRepository.findByStatusOrderByCreatedAtDesc(status);
    }
    
    public Leave createLeave(Leave leave) {
        if (leave.getStartDate() == null || leave.getEndDate() == null
                || leave.getEndDate().isBefore(leave.getStartDate())) {
            throw new IllegalArgumentException("请假日期不正确");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;
        if (days > 3) {
            throw new RuntimeException("单次请假不能超过3天");
        }
        leave.setDays((int) days);
        
        Long studentId = leave.getStudent().getId();
        
        List<Leave> pendingLeaves = leaveRepository.findByStudentIdAndStatus(studentId, Leave.LeaveStatus.PENDING);
        if (!pendingLeaves.isEmpty()) {
            throw new RuntimeException("您有待审批的请假申请，请等待审批结果后再提交新的申请");
        }
        
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Leave> approvedLeaves = leaveRepository.findByStudentIdAndStatus(studentId, Leave.LeaveStatus.APPROVED);
        for (Leave approvedLeave : approvedLeaves) {
            if (!approvedLeave.getEndDate().isBefore(today)) {
                throw new RuntimeException("您有正在生效的请假，请假期间不能再次申请");
            }
        }
        
        return leaveRepository.save(leave);
    }
    
    @Transactional
    public Leave approveLeave(Long leaveId, Long approverId, String comment) {
        Leave leave = getLeaveById(leaveId);
        leave.setStatus(Leave.LeaveStatus.APPROVED);
        
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("审批人不存在"));
        leave.setApprover(approver);
        leave.setAdminComment(comment);
        leave.setApprovedAt(LocalDateTime.now());
        
        return leaveRepository.save(leave);
    }
    
    @Transactional
    public Leave rejectLeave(Long leaveId, Long approverId, String comment) {
        Leave leave = getLeaveById(leaveId);
        leave.setStatus(Leave.LeaveStatus.REJECTED);
        
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("审批人不存在"));
        leave.setApprover(approver);
        leave.setAdminComment(comment);
        leave.setApprovedAt(LocalDateTime.now());
        
        return leaveRepository.save(leave);
    }
    
    @Transactional
    public void deleteLeave(Long id) {
        Leave leave = getLeaveById(id);
        leaveRepository.delete(leave);
    }
}
