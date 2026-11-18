package com.ruoyi.seating.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.seating.domain.SeatPosition;
import com.ruoyi.seating.domain.SeatRule;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.engine.model.SeatingAssignmentResult;
import com.ruoyi.seating.engine.model.SeatingContext;
import com.ruoyi.seating.engine.model.SeatingResult;

class SeatingEngineTest
{
    private final SeatingEngine engine = new SeatingEngine();

    @Test
    void ruleConfiguredNotDeskmateShouldPreventSameDesk()
    {
        SeatStudent s1 = student(1L, "张三", "0");
        SeatStudent s2 = student(2L, "李四", "0");
        SeatStudent s3 = student(3L, "王五", "1");
        SeatPosition p1 = seat(10L, 1L, 1L);
        SeatPosition p2 = seat(11L, 1L, 2L);
        SeatPosition p3 = seat(12L, 1L, 3L);
        SeatPosition p4 = seat(13L, 1L, 4L);

        SeatRule notDeskmate = new SeatRule();
        notDeskmate.setRuleCode("NOT_DESKMATE");
        notDeskmate.setRuleCategory("HARD");
        notDeskmate.setRuleWeight(100L);
        notDeskmate.setRuleConfig("{\"studentIds\":[1,2]}");
        notDeskmate.setEnabled("1");
        notDeskmate.setStatus("0");

        SeatingResult result = engine.generate(
                new SeatingContext(List.of(s1, s2, s3), List.of(p1, p2, p3, p4),
                        List.of(notDeskmate), Collections.emptyList(), 42L, 200));

        assertTrue(result.getConflicts().isEmpty());
        SeatPosition seat1 = assignedSeat(result.getAssignments(), 1L);
        SeatPosition seat2 = assignedSeat(result.getAssignments(), 2L);
        // (1,1)-(1,2) is desk pair 1, (1,3)-(1,4) is desk pair 2
        boolean bothOnDesk1 = (seat1.getColIndex() <= 2 && seat2.getColIndex() <= 2);
        boolean bothOnDesk2 = (seat1.getColIndex() >= 3 && seat2.getColIndex() >= 3);
        assertFalse(bothOnDesk1 || bothOnDesk2, "张三和李四不应被安排在同一桌");
    }

    @Test
    void disabledFrontRowRuleShouldNotTriggerConflict()
    {
        SeatStudent s1 = student(1L, "张三", "0");
        SeatStudent s2 = student(2L, "李四", "0");
        SeatPosition p1 = seat(10L, 1L, 1L);
        SeatPosition p2 = seat(11L, 3L, 1L);

        SeatRule disabledRule = new SeatRule();
        disabledRule.setRuleCode("FRONT_ROW");
        disabledRule.setRuleCategory("HARD");
        disabledRule.setRuleConfig("{\"studentIds\":[1,2],\"frontRows\":1}");
        disabledRule.setEnabled("0");
        disabledRule.setStatus("0");

        SeatingResult result = engine.generate(
                new SeatingContext(List.of(s1, s2), List.of(p1, p2),
                        List.of(disabledRule), Collections.emptyList(), 1L, 100));

        assertTrue(result.getConflicts().isEmpty());
    }

    @Test
    void optimizeShouldUtilizeUnassignedAvailableSeats()
    {
        SeatStudent s1 = student(1L, "张三", "0");
        SeatStudent s2 = student(2L, "李四", "0");
        SeatPosition p1 = seat(10L, 1L, 1L);
        SeatPosition p2 = seat(11L, 1L, 2L);
        SeatPosition p3 = seat(12L, 2L, 1L);
        SeatPosition p4 = seat(13L, 2L, 2L);

        SeatRule genderBalance = new SeatRule();
        genderBalance.setRuleCode("GENDER_BALANCE");
        genderBalance.setRuleCategory("SOFT");
        genderBalance.setRuleWeight(100L);
        genderBalance.setEnabled("1");
        genderBalance.setStatus("0");

        SeatingResult result = engine.generate(
                new SeatingContext(List.of(s1, s2), List.of(p1, p2, p3, p4),
                        List.of(genderBalance), Collections.emptyList(), 7L, 200));

        assertEquals(2, result.getAssignments().size());
        SeatPosition seat1 = assignedSeat(result.getAssignments(), 1L);
        SeatPosition seat2 = assignedSeat(result.getAssignments(), 2L);
        assertNotEquals(seat1.getRowIndex(), seat2.getRowIndex(), "两名同性别学生在有空排可用座位时应分开坐");
    }

    private SeatStudent student(Long id, String name, String gender)
    {
        SeatStudent student = new SeatStudent();
        student.setStudentId(id);
        student.setStudentName(name);
        student.setGender(gender);
        student.setVisionLevel("0");
        student.setDisciplineLevel("0");
        student.setStatus("0");
        return student;
    }

    private SeatPosition seat(Long seatId, Long row, Long col)
    {
        SeatPosition seat = new SeatPosition();
        seat.setSeatId(seatId);
        seat.setRowIndex(row);
        seat.setColIndex(col);
        seat.setSeatType("0");
        seat.setIsAvailable("1");
        seat.setStatus("0");
        return seat;
    }

    private SeatPosition assignedSeat(List<SeatingAssignmentResult> assignments, Long studentId)
    {
        return assignments.stream()
                .filter(item -> studentId.equals(item.getStudent().getStudentId()))
                .findFirst()
                .orElseThrow()
                .getSeat();
    }
}
