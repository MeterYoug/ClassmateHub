package com.ruoyi.seating.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.seating.domain.SeatExam;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.domain.SeatStudentScore;
import com.ruoyi.seating.mapper.SeatStudentMapper;
import com.ruoyi.seating.mapper.SeatStudentScoreMapper;
import com.ruoyi.seating.service.ISeatClassService;
import com.ruoyi.seating.service.ISeatExamService;

class SeatStudentScoreServiceImplTest
{
    private static final List<String> SUBJECTS = List.of("语文", "数学", "英语", "科学");
    private static final String[] ALL_HEADERS = { "学号", "姓名", "语文", "数学", "英语", "科学" };

    private SeatStudentScoreServiceImpl service;
    private SeatStudentScoreMapper scoreMapper;

    @BeforeEach
    void setUp()
    {
        service = new SeatStudentScoreServiceImpl();
        scoreMapper = mock(SeatStudentScoreMapper.class);
        SeatStudentMapper studentMapper = mock(SeatStudentMapper.class);
        ISeatExamService examService = mock(ISeatExamService.class);
        ISeatClassService classService = mock(ISeatClassService.class);
        ReflectionTestUtils.setField(service, "seatStudentScoreMapper", scoreMapper);
        ReflectionTestUtils.setField(service, "seatStudentMapper", studentMapper);
        ReflectionTestUtils.setField(service, "seatExamService", examService);
        ReflectionTestUtils.setField(service, "seatClassService", classService);

        SeatExam exam = new SeatExam();
        exam.setExamId(1L);
        exam.setClassId(1L);
        exam.setSubjectSnapshot(JSON.toJSONString(SUBJECTS));
        when(examService.selectSeatExamByExamId(1L)).thenReturn(exam);

        SeatStudent student = new SeatStudent();
        student.setStudentId(10L);
        student.setStudentNo("202601001");
        student.setStudentName("测试学生");
        when(studentMapper.selectSeatStudentByClassIdAndStudentNo(any())).thenReturn(student);

        when(scoreMapper.selectSeatStudentScoreList(any())).thenReturn(Collections.emptyList());
        when(scoreMapper.insertSeatStudentScore(any())).thenReturn(1);
        when(scoreMapper.updateSeatStudentScore(any())).thenReturn(1);
    }

    @Test
    void partialImportShouldKeepExistingSubjectsAndRecalculateTotal() throws Exception
    {
        withExistingScore("{\"语文\":60,\"数学\":80,\"英语\":70,\"科学\":90}", "保留备注");
        MockMultipartFile file = workbook(
            new String[] { "学号", "姓名", "语文", "总分", "班级排名", "备注" },
            new String[] { "202601001", "测试学生", "95", "", "", "" });

        String result = service.importScores(1L, file, true, "tester");

        assertTrue(result.contains("本次导入科目：语文"), result);
        SeatStudentScore saved = capturedUpdatedScore();
        JSONObject scores = JSON.parseObject(saved.getSubjectScores());
        assertDecimal("95", scores.getBigDecimal("语文"));
        assertDecimal("80", scores.getBigDecimal("数学"));
        assertDecimal("70", scores.getBigDecimal("英语"));
        assertDecimal("90", scores.getBigDecimal("科学"));
        assertDecimal("335", saved.getTotalScore());
        assertEquals("保留备注", saved.getRemark());
    }

    @Test
    void blankSubjectCellShouldNotClearExistingScore() throws Exception
    {
        withExistingScore("{\"语文\":60,\"数学\":80,\"英语\":70,\"科学\":90}", null);
        MockMultipartFile file = workbook(
            new String[] { "学号", "姓名", "语文", "数学", "总分" },
            new String[] { "202601001", "测试学生", "95", "", "" });

        service.importScores(1L, file, true, "tester");

        JSONObject scores = JSON.parseObject(capturedUpdatedScore().getSubjectScores());
        assertDecimal("95", scores.getBigDecimal("语文"));
        assertDecimal("80", scores.getBigDecimal("数学"));
        assertDecimal("70", scores.getBigDecimal("英语"));
        assertDecimal("90", scores.getBigDecimal("科学"));
    }

    @Test
    void templateWithoutAnySubjectColumnShouldBeRejected()
    {
        MockMultipartFile file = workbook(
            new String[] { "学号", "姓名", "总分", "班级排名", "备注" },
            new String[] { "202601001", "测试学生", "300", "", "" });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importScores(1L, file, true, "tester"));
        assertTrue(ex.getMessage().contains("至少需要包含一门考试科目成绩列"), ex.getMessage());
    }

    @Test
    void fullSubjectImportShouldKeepOriginalTotalBehavior() throws Exception
    {
        withExistingScore("{\"语文\":60,\"数学\":80,\"英语\":70,\"科学\":90}", null);
        MockMultipartFile file = workbook(
            new String[] { "学号", "姓名", "语文", "数学", "英语", "科学", "总分" },
            new String[] { "202601001", "测试学生", "90", "90", "90", "90", "360" });

        String result = service.importScores(1L, file, true, "tester");

        assertTrue(result.contains("本次导入科目：语文、数学、英语、科学"), result);
        assertDecimal("360", capturedUpdatedScore().getTotalScore());
    }

    @Test
    void inlineStringTemplateShouldBeImported() throws Exception
    {
        // openpyxl / pandas 导出的 xlsx 使用 inlineStr，POI 4.1.2 的 setCellType(STRING) 会清空其内容。
        MockMultipartFile file = inlineWorkbook(
            new String[] { "学号", "姓名", "语文", "数学", "英语", "科学", "总分" },
            new String[] { "202601001", "测试学生", "90", "90", "90", "90", "360" });

        String result = service.importScores(1L, file, true, "tester");

        assertTrue(result.contains("本次导入科目：语文、数学、英语、科学"), result);
        ArgumentCaptor<SeatStudentScore> captor = ArgumentCaptor.forClass(SeatStudentScore.class);
        verify(scoreMapper, times(1)).insertSeatStudentScore(captor.capture());
        assertDecimal("360", captor.getValue().getTotalScore());
    }

    @Test
    void mismatchedStudentNameShouldBeRejected()
    {
        MockMultipartFile file = workbook(ALL_HEADERS,
            new String[] { "202601001", "李四", "90", "90", "90", "90" });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importScores(1L, file, true, "tester"));
        assertTrue(ex.getMessage().contains("学号 202601001 对应 测试学生，表中姓名为 李四"), ex.getMessage());
        verify(scoreMapper, never()).insertSeatStudentScore(any());
    }

    @Test
    void blankStudentNameShouldBeAccepted() throws Exception
    {
        MockMultipartFile file = workbook(ALL_HEADERS,
            new String[] { "202601001", "", "90", "90", "90", "90" });

        String result = service.importScores(1L, file, true, "tester");

        assertTrue(result.contains("导入成功，共 1 条"), result);
        verify(scoreMapper, times(1)).insertSeatStudentScore(any());
    }

    @Test
    void duplicateStudentNoShouldBeRejected()
    {
        MockMultipartFile file = workbook(ALL_HEADERS, new String[][] {
            { "202601001", "测试学生", "90", "90", "90", "90" },
            { "202601001", "测试学生", "80", "80", "80", "80" }
        });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importScores(1L, file, true, "tester"));
        assertTrue(ex.getMessage().contains("第 3 行：学号 202601001 与第 2 行重复"), ex.getMessage());
        verify(scoreMapper, never()).insertSeatStudentScore(any());
    }

    @Test
    void anyRowErrorShouldPreventAllWrites()
    {
        MockMultipartFile file = workbook(ALL_HEADERS, new String[][] {
            { "202601001", "测试学生", "90", "90", "90", "90" },
            { "202601002", "李四", "90", "90", "90", "90" }
        });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importScores(1L, file, true, "tester"));
        assertTrue(ex.getMessage().contains("第 3 行"), ex.getMessage());
        verify(scoreMapper, never()).insertSeatStudentScore(any());
        verify(scoreMapper, never()).updateSeatStudentScore(any());
    }

    @Test
    void fileTotalScoreAndClassRankShouldBeIgnored() throws Exception
    {
        MockMultipartFile file = workbook(
            new String[] { "学号", "姓名", "语文", "数学", "英语", "科学", "总分", "班级排名", "备注" },
            new String[] { "202601001", "测试学生", "90", "90", "90", "90", "999", "第3名", "" });

        service.importScores(1L, file, true, "tester");

        ArgumentCaptor<SeatStudentScore> captor = ArgumentCaptor.forClass(SeatStudentScore.class);
        verify(scoreMapper, times(1)).insertSeatStudentScore(captor.capture());
        assertDecimal("360", captor.getValue().getTotalScore());
        assertNull(captor.getValue().getClassRank());
    }

    @Test
    void invalidScoreShouldReportColumnAndValue()
    {
        MockMultipartFile file = workbook(ALL_HEADERS,
            new String[] { "202601001", "测试学生", "缺考", "90", "90", "90" });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importScores(1L, file, true, "tester"));
        assertTrue(ex.getMessage().contains("语文 列：「缺考」不是有效分数"), ex.getMessage());
    }

    @Test
    void outOfRangeScoreShouldBeRejected()
    {
        MockMultipartFile file = workbook(ALL_HEADERS,
            new String[] { "202601001", "测试学生", "999", "90", "90", "90" });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importScores(1L, file, true, "tester"));
        assertTrue(ex.getMessage().contains("语文 列：分数必须在 0-150 之间"), ex.getMessage());
    }

    @Test
    void blankStudentNoShouldBeReportedInsteadOfSkipped()
    {
        MockMultipartFile file = workbook(ALL_HEADERS,
            new String[] { "", "测试学生", "90", "90", "90", "90" });

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.importScores(1L, file, true, "tester"));
        assertTrue(ex.getMessage().contains("第 2 行：学号列不能为空"), ex.getMessage());
    }

    private void withExistingScore(String subjectScores, String remark)
    {
        SeatStudentScore existing = new SeatStudentScore();
        existing.setScoreId(100L);
        existing.setExamId(1L);
        existing.setStudentId(10L);
        existing.setSubjectScores(subjectScores);
        existing.setTotalScore(new BigDecimal("300"));
        existing.setRemark(remark);
        when(scoreMapper.selectByExamIdAndStudentId(any())).thenReturn(existing);
    }

    private SeatStudentScore capturedUpdatedScore()
    {
        ArgumentCaptor<SeatStudentScore> captor = ArgumentCaptor.forClass(SeatStudentScore.class);
        verify(scoreMapper, times(1)).updateSeatStudentScore(captor.capture());
        return captor.getValue();
    }

    private void assertDecimal(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private MockMultipartFile inlineWorkbook(String[] headers, String[] values)
    {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream os = new ByteArrayOutputStream())
        {
            Sheet sheet = wb.createSheet("学生成绩");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++)
            {
                setInlineString(header, i, headers[i]);
            }
            Row row = sheet.createRow(1);
            for (int i = 0; i < values.length; i++)
            {
                setInlineString(row, i, values[i]);
            }
            wb.write(os);
            return new MockMultipartFile("file", "inline_scores.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", os.toByteArray());
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private void setInlineString(Row row, int column, String value)
    {
        XSSFCell cell = (XSSFCell) row.createCell(column);
        CTCell ctCell = cell.getCTCell();
        ctCell.setT(STCellType.INLINE_STR);
        ctCell.addNewIs().setT(value);
        assertEquals(STCellType.INLINE_STR, ctCell.getT());
    }

    private MockMultipartFile workbook(String[] headers, String[] values)
    {
        return workbook(headers, new String[][] { values });
    }

    private MockMultipartFile workbook(String[] headers, String[][] rows)
    {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream os = new ByteArrayOutputStream())
        {
            Sheet sheet = wb.createSheet("学生成绩");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++)
            {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++)
            {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < rows[r].length; i++)
                {
                    row.createCell(i).setCellValue(rows[r][i]);
                }
            }
            wb.write(os);
            return new MockMultipartFile("file", "scores.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", os.toByteArray());
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
