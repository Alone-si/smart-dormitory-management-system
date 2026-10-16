package org.example.bwxw.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import org.example.bwxw.dto.ApiResponse;
import org.example.bwxw.entity.Leave;
import org.example.bwxw.entity.User;
import org.example.bwxw.service.CurrentUserService;
import org.example.bwxw.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {
    
    @Autowired
    private LeaveService leaveService;
    
    @Autowired
    private CurrentUserService currentUserService;
    
        @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<List<Leave>> getAllLeaves() {
        try {
            List<Leave> leaves = leaveService.getAllLeaves();
            return ApiResponse.success(leaves);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
        @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<Leave> getLeaveById(@PathVariable Long id) {
        try {
            Leave leave = leaveService.getLeaveById(id);
            return ApiResponse.success(leave);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
        @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    @GetMapping("/student/{studentId}")
    public ApiResponse<List<Leave>> getLeavesByStudent(@PathVariable Long studentId) {
        try {
            User currentUser = currentUserService.getCurrentUser();
            if (currentUser.getRole() == User.UserRole.STUDENT && !currentUser.getId().equals(studentId)) {
                return ApiResponse.error("无权查看其他学生的请假记录");
            }
            List<Leave> leaves = leaveService.getLeavesByStudent(studentId);
            return ApiResponse.success(leaves);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
        @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending")
    public ApiResponse<List<Leave>> getPendingLeaves() {
        try {
            List<Leave> leaves = leaveService.getPendingLeaves();
            return ApiResponse.success(leaves);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
        @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ApiResponse<Leave> createLeave(@RequestBody Leave leave) {
        try {
            if (leave.getStartDate() == null || leave.getEndDate() == null) {
                return ApiResponse.error("请假日期不能为空");
            }
            if (leave.getEndDate().isBefore(leave.getStartDate())) {
                return ApiResponse.error("结束日期不能早于开始日期");
            }
            User currentUser = currentUserService.getCurrentUser();
            leave.setId(null);
            leave.setStudent(currentUser);
            leave.setStatus(Leave.LeaveStatus.PENDING);
            leave.setApprover(null);
            leave.setAdminComment(null);
            leave.setApprovedAt(null);

            Leave created = leaveService.createLeave(leave);
            return ApiResponse.success("请假申请提交成功", created);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
        @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/approve")
    public ApiResponse<Leave> approveLeave(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        try {
            String comment = (String) payload.get("comment");
            
            // 自动获取当前登录的管理员作为审批人
            User currentAdmin = currentUserService.getCurrentUser();
            
            Leave approved = leaveService.approveLeave(id, currentAdmin.getId(), comment);
            return ApiResponse.success("审批通过", approved);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
        @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reject")
    public ApiResponse<Leave> rejectLeave(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        try {
            String comment = (String) payload.get("comment");
            
            // 自动获取当前登录的管理员作为审批人
            User currentAdmin = currentUserService.getCurrentUser();
            
            Leave rejected = leaveService.rejectLeave(id, currentAdmin.getId(), comment);
            return ApiResponse.success("审批拒绝", rejected);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 删除请假记录
     */
        @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteLeave(@PathVariable Long id) {
        try {
            // 权限校验：只有管理员才能删除请假
            User currentUser = currentUserService.getCurrentUser();
            if (currentUser.getRole() != User.UserRole.ADMIN) {
                return ApiResponse.error("权限不足，只有管理员才能删除请假记录");
            }
            
            leaveService.deleteLeave(id);
            return ApiResponse.success("请假记录删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除请假记录失败: " + e.getMessage());
        }
    }
}
