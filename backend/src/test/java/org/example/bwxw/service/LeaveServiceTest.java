package org.example.bwxw.service;

import org.example.bwxw.entity.Leave;
import org.example.bwxw.entity.User;
import org.example.bwxw.repository.LeaveRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {
    @Mock private LeaveRepository leaveRepository;
    @InjectMocks private LeaveService leaveService;

    @Test
    void rejectsLongDateRangeEvenWhenClientClaimsOneDay() {
        Leave leave = leave(10);
        leave.setDays(1);
        assertThrows(RuntimeException.class, () -> leaveService.createLeave(leave));
        verifyNoInteractions(leaveRepository);
    }

    @Test
    void derivesInclusiveDaysFromDates() {
        Leave leave = leave(3);
        leave.setDays(1);
        leaveService.createLeave(leave);
        assertEquals(3, leave.getDays());
        verify(leaveRepository).save(leave);
    }

    @Test
    void rejectsReversedDates() {
        Leave leave = leave(0);
        assertThrows(IllegalArgumentException.class, () -> leaveService.createLeave(leave));
        verifyNoInteractions(leaveRepository);
    }

    private Leave leave(int days) {
        User student = new User();
        student.setId(1L);
        Leave leave = new Leave();
        leave.setStudent(student);
        leave.setStartDate(LocalDate.of(2026, 10, 16));
        leave.setEndDate(leave.getStartDate().plusDays(days - 1));
        return leave;
    }
}
