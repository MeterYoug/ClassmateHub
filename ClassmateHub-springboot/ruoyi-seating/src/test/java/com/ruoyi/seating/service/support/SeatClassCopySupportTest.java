package com.ruoyi.seating.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.seating.domain.SeatClassroom;

class SeatClassCopySupportTest
{
    @Test
    void nextSemesterShouldFlipBetweenTerms()
    {
        assertEquals("2", SeatClassCopySupport.nextSemester("1"));
        assertEquals("1", SeatClassCopySupport.nextSemester("2"));
        assertEquals("2", SeatClassCopySupport.nextSemester("上学期"));
        assertEquals("1", SeatClassCopySupport.nextSemester("下学期"));
    }

    @Test
    void nextSchoolYearShouldAdvanceOnlyForSecondSemester()
    {
        assertEquals("2025-2026", SeatClassCopySupport.nextSchoolYear("2025-2026", "1"));
        assertEquals("2026-2027", SeatClassCopySupport.nextSchoolYear("2025-2026", "2"));
        assertEquals("2026-2027", SeatClassCopySupport.nextSchoolYear("2025-2026", "下学期"));
    }

    @Test
    void selectPrimaryClassroomShouldPickDefaultActiveLayout()
    {
        SeatClassroom inactive = new SeatClassroom();
        inactive.setClassroomId(1L);
        inactive.setIsDefault("0");
        inactive.setStatus("0");

        SeatClassroom primary = new SeatClassroom();
        primary.setClassroomId(2L);
        primary.setIsDefault("1");
        primary.setStatus("0");

        List<SeatClassroom> classroomList = Arrays.asList(inactive, primary);
        assertSame(primary, SeatClassCopySupport.selectPrimaryClassroom(classroomList));
    }

    @Test
    void selectPrimaryClassroomShouldReturnNullWhenNoDefaultActiveLayoutExists()
    {
        SeatClassroom classroom = new SeatClassroom();
        classroom.setClassroomId(1L);
        classroom.setIsDefault("1");
        classroom.setStatus("1");

        assertNull(SeatClassCopySupport.selectPrimaryClassroom(Arrays.asList(classroom)));
        assertNull(SeatClassCopySupport.selectPrimaryClassroom(null));
    }

    @Test
    void remapRuleConfigStudentIdsShouldMapToCopiedStudentIds()
    {
        String configJson = "{\"studentIds\":[101,102,999],\"frontRows\":2}";
        Map<Long, Long> studentIdMap = Map.of(101L, 201L, 102L, 202L);

        String remapped = SeatClassCopySupport.remapRuleConfigStudentIds(configJson, studentIdMap);
        JSONObject parsed = JSON.parseObject(remapped);

        assertEquals(2, parsed.getIntValue("frontRows"));
        assertEquals(List.of(201L, 202L), parsed.getList("studentIds", Long.class));
    }

    @Test
    void remapRuleConfigStudentIdsShouldClearStudentIdsWhenStudentsNotCopied()
    {
        String configJson = "{\"studentIds\":[101,102],\"frontRows\":3}";

        String remapped = SeatClassCopySupport.remapRuleConfigStudentIds(configJson, Collections.emptyMap());
        JSONObject parsed = JSON.parseObject(remapped);

        assertEquals(3, parsed.getIntValue("frontRows"));
        assertEquals(Collections.emptyList(), parsed.getList("studentIds", Long.class));
    }
}
