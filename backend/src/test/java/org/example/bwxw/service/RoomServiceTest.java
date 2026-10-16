package org.example.bwxw.service;

import org.example.bwxw.entity.Room;
import org.example.bwxw.entity.User;
import org.example.bwxw.repository.RoomRepository;
import org.example.bwxw.repository.UserRepository;
import org.example.bwxw.repository.RepairRepository;
import org.example.bwxw.repository.LeaveRepository;
import org.example.bwxw.entity.Building;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock private RepairRepository repairRepository;
    @Mock private LeaveRepository leaveRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void checkoutKeepsStudentAndRoomOccupancyInSync() {
        Room room = new Room();
        room.setCapacity(4);
        room.setOccupied(4);
        room.setStatus(Room.RoomStatus.FULL);

        User student = new User();
        student.setId(1L);
        student.setRole(User.UserRole.STUDENT);
        student.setRoom(room);
        student.setStatus("ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));

        roomService.removeStudentFromRoom(1L);

        assertNull(student.getRoom());
        assertEquals("INACTIVE", student.getStatus());
        assertEquals(3, room.getOccupied());
        assertEquals(Room.RoomStatus.AVAILABLE, room.getStatus());
        verify(userRepository).save(student);
        verify(roomRepository).save(room);
    }

    @Test
    void rejectsAssignmentToFullRoomWithoutChangingStudent() {
        Building building = new Building();
        building.setType(Building.Gender.MALE);
        Room room = new Room();
        room.setBuilding(building);
        room.setCapacity(1);
        room.setOccupied(1);
        room.setStatus(Room.RoomStatus.FULL);
        User student = new User();
        student.setRole(User.UserRole.STUDENT);
        student.setGender(User.Gender.MALE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(roomRepository.findById(2L)).thenReturn(Optional.of(room));

        assertThrows(RuntimeException.class, () -> roomService.assignStudentToRoom(1L, 2L));
        assertNull(student.getRoom());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deletionDetachesHistoryBeforeDeletingStudentAndFlushesConstraints() {
        Room room = new Room();
        room.setCapacity(4);
        room.setOccupied(2);
        room.setStatus(Room.RoomStatus.MAINTENANCE);
        User student = new User();
        student.setRole(User.UserRole.STUDENT);
        student.setRoom(room);
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));

        roomService.deleteStudent(1L);

        assertNull(student.getRoom());
        assertEquals(1, room.getOccupied());
        assertEquals(Room.RoomStatus.MAINTENANCE, room.getStatus());
        var order = inOrder(repairRepository, leaveRepository, userRepository);
        order.verify(repairRepository).detachStudent(1L);
        order.verify(leaveRepository).detachStudent(1L);
        order.verify(userRepository).delete(student);
        order.verify(userRepository).flush();
    }

    @Test
    void deletionFailureRollsBackTheEntireServiceTransaction() {
        User student = new User();
        student.setRole(User.UserRole.STUDENT);
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        doThrow(new DataIntegrityViolationException("referenced student"))
                .when(userRepository).flush();
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(roomService);
        factory.addAdvice(interceptor);
        RoomService transactionalService = (RoomService) factory.getProxy();

        assertThrows(DataIntegrityViolationException.class, () -> transactionalService.deleteStudent(1L));

        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }
}
