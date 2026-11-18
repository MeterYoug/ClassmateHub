package com.ruoyi.seating.service.impl;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.seating.domain.SeatClass;
import com.ruoyi.seating.domain.SeatExam;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.domain.SeatStudentScore;
import com.ruoyi.seating.mapper.SeatStudentMapper;
import com.ruoyi.seating.mapper.SeatStudentScoreMapper;
import com.ruoyi.seating.service.GradeSubjectHelper;
import com.ruoyi.seating.service.ISeatClassService;
import com.ruoyi.seating.service.ISeatExamService;
import com.ruoyi.seating.service.ISeatStudentScoreService;

@Service
public class SeatStudentScoreServiceImpl implements ISeatStudentScoreService
{
    private static final List<String> FIXED_HEADERS = List.of("学号", "姓名");
    private static final List<String> TAIL_HEADERS = List.of("总分", "班级排名", "备注");
    // DataFormatter 非线程安全，导入接口可能并发调用，按线程隔离。
    private static final ThreadLocal<DataFormatter> DATA_FORMATTER =
            ThreadLocal.withInitial(DataFormatter::new);
    // 考试暂无满分配置，先用固定上限兜底，避免负数/明显异常分数写入后污染总分与排名。
    private static final BigDecimal MAX_SUBJECT_SCORE = new BigDecimal("150");

    @Autowired
    private SeatStudentScoreMapper seatStudentScoreMapper;

    @Autowired
    private SeatStudentMapper seatStudentMapper;

    @Autowired
    private ISeatExamService seatExamService;

    @Autowired
    private ISeatClassService seatClassService;

    @Override
    public SeatStudentScore selectSeatStudentScoreByScoreId(Long scoreId)
    {
        return seatStudentScoreMapper.selectSeatStudentScoreByScoreId(scoreId);
    }

    @Override
    public List<SeatStudentScore> selectSeatStudentScoreList(SeatStudentScore seatStudentScore)
    {
        return seatStudentScoreMapper.selectSeatStudentScoreList(seatStudentScore);
    }

    @Override
    public int insertSeatStudentScore(SeatStudentScore seatStudentScore)
    {
        seatStudentScore.setCreateTime(DateUtils.getNowDate());
        seatStudentScore.setDelFlag("0");
        return seatStudentScoreMapper.insertSeatStudentScore(seatStudentScore);
    }

    @Override
    public int updateSeatStudentScore(SeatStudentScore seatStudentScore)
    {
        seatStudentScore.setUpdateTime(DateUtils.getNowDate());
        int rows = seatStudentScoreMapper.updateSeatStudentScore(seatStudentScore);
        if (seatStudentScore.getExamId() != null)
        {
            refreshRanksAndLevels(seatStudentScore.getExamId(), seatStudentScore.getUpdateBy());
        }
        return rows;
    }

    @Override
    public int deleteSeatStudentScoreByScoreIds(Long[] scoreIds)
    {
        return seatStudentScoreMapper.deleteSeatStudentScoreByScoreIds(scoreIds);
    }

    @Override
    public int deleteSeatStudentScoreByScoreId(Long scoreId)
    {
        return seatStudentScoreMapper.deleteSeatStudentScoreByScoreId(scoreId);
    }

    @Override
    public void exportImportTemplate(HttpServletResponse response, Long classId)
    {
        SeatClass seatClass = seatClassService.selectSeatClassByClassId(classId);
        if (seatClass == null)
        {
            throw new ServiceException("班级不存在");
        }
        List<String> subjects = GradeSubjectHelper.resolveClassSubjects(seatClass);
        writeTemplate(response, seatClass, subjects);
    }

    @Override
    @Transactional
    public String importScores(Long examId, MultipartFile file, boolean updateSupport, String operName) throws Exception
    {
        SeatExam exam = seatExamService.selectSeatExamByExamId(examId);
        if (exam == null)
        {
            throw new ServiceException("考试批次不存在");
        }
        List<String> subjects = parseSubjects(exam);
        List<ImportRow> pending = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> importSubjects;
        boolean partialImport;
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream()))
        {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1)
            {
                throw new ServiceException("导入成绩数据不能为空");
            }
            Map<String, Integer> headerIndex = readHeader(sheet.getRow(0));
            validateHeaders(headerIndex, subjects);
            importSubjects = resolveImportSubjects(headerIndex, subjects);
            partialImport = importSubjects.size() < subjects.size();
            // 阶段一：只做只读校验并组装待写数据，任一错误都不写库。
            Map<String, Integer> firstRowByStudentNo = new LinkedHashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++)
            {
                Row row = sheet.getRow(i);
                if (isBlankRow(row))
                {
                    continue;
                }
                int rowNumber = i + 1;
                String studentNo = cellString(row, headerIndex.get("学号"));
                if (StringUtils.isBlank(studentNo))
                {
                    errors.add("第 " + rowNumber + " 行：学号列不能为空");
                    continue;
                }
                Integer firstRow = firstRowByStudentNo.putIfAbsent(studentNo, rowNumber);
                if (firstRow != null)
                {
                    errors.add("第 " + rowNumber + " 行：学号 " + studentNo + " 与第 " + firstRow + " 行重复");
                    continue;
                }
                try
                {
                    SeatStudentScore score = buildScore(exam, subjects, importSubjects,
                        headerIndex, row, operName, studentNo);
                    SeatStudentScore existing = seatStudentScoreMapper.selectByExamIdAndStudentId(score);
                    if (existing != null && !updateSupport)
                    {
                        throw new ServiceException("该学生成绩已存在（如需覆盖请开启「按学号更新已有成绩」）");
                    }
                    if (partialImport && existing != null)
                    {
                        mergeExistingSubjects(score, existing, subjects, importSubjects, headerIndex, row);
                    }
                    pending.add(new ImportRow(score, existing));
                }
                catch (Exception e)
                {
                    errors.add("第 " + rowNumber + " 行：" + e.getMessage());
                }
            }
        }
        if (!errors.isEmpty())
        {
            throw new ServiceException("导入失败，数据未写入：" + System.lineSeparator()
                + String.join(System.lineSeparator(), errors));
        }
        // 阶段二：全部校验通过后才落库。
        for (ImportRow item : pending)
        {
            SeatStudentScore score = item.score;
            if (item.existing == null)
            {
                insertSeatStudentScore(score);
            }
            else
            {
                score.setScoreId(item.existing.getScoreId());
                score.setUpdateBy(operName);
                score.setUpdateTime(DateUtils.getNowDate());
                seatStudentScoreMapper.updateSeatStudentScore(score);
            }
        }
        refreshRanksAndLevels(examId, operName);
        return "导入成功，共 " + pending.size() + " 条，本次导入科目：" + String.join("、", importSubjects);
    }

    @Override
    @Transactional
    public int syncStudentScoreLevel(Long examId, String operName)
    {
        SeatStudentScore query = new SeatStudentScore();
        query.setExamId(examId);
        List<SeatStudentScore> scores = seatStudentScoreMapper.selectSeatStudentScoreList(query);
        int count = 0;
        for (SeatStudentScore score : scores)
        {
            if (score.getStudentId() != null && StringUtils.isNotBlank(score.getScoreLevel()))
            {
                count += seatStudentMapper.updateStudentScoreLevel(score.getStudentId(), score.getScoreLevel(), operName);
            }
        }
        return count;
    }

    private SeatStudentScore buildScore(SeatExam exam, List<String> subjects, List<String> importSubjects,
            Map<String, Integer> headerIndex, Row row, String operName, String studentNo)
    {
        SeatStudent lookup = new SeatStudent();
        lookup.setClassId(exam.getClassId());
        lookup.setStudentNo(studentNo);
        SeatStudent student = seatStudentMapper.selectSeatStudentByClassIdAndStudentNo(lookup);
        if (student == null)
        {
            throw new ServiceException("未找到学号 " + studentNo + " 对应的学生（可能不属于本班或已删除）");
        }
        String importName = cellString(row, headerIndex.get("姓名"));
        String expectedName = student.getStudentName() == null ? "" : student.getStudentName().trim();
        if (StringUtils.isNotBlank(importName) && !importName.equals(expectedName))
        {
            throw new ServiceException("学号 " + studentNo + " 对应 " + expectedName + "，表中姓名为 " + importName);
        }
        Map<String, BigDecimal> subjectMap = new LinkedHashMap<>();
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (String subject : subjects)
        {
            subjectMap.put(subject, BigDecimal.ZERO);
        }
        for (String subject : importSubjects)
        {
            BigDecimal value = subjectScore(row, headerIndex.get(subject), subject);
            if (value != null)
            {
                subjectMap.put(subject, value);
            }
        }
        for (BigDecimal value : subjectMap.values())
        {
            calculatedTotal = calculatedTotal.add(value);
        }
        SeatStudentScore score = new SeatStudentScore();
        score.setExamId(exam.getExamId());
        score.setClassId(exam.getClassId());
        score.setStudentId(student.getStudentId());
        score.setStudentNo(student.getStudentNo());
        score.setStudentNameSnapshot(student.getStudentName());
        score.setSubjectScores(JSON.toJSONString(subjectMap));
        // 总分始终按科目求和，不采纳文件里的总分；班级排名由 refreshRanksAndLevels 统一重算，均忽略文件值。
        score.setTotalScore(calculatedTotal.setScale(2, RoundingMode.HALF_UP));
        score.setRemark(cellString(row, headerIndex.get("备注")));
        score.setDelFlag("0");
        score.setCreateBy(operName);
        return score;
    }

    private void refreshRanksAndLevels(Long examId, String operName)
    {
        SeatStudentScore query = new SeatStudentScore();
        query.setExamId(examId);
        List<SeatStudentScore> scores = new ArrayList<>(seatStudentScoreMapper.selectSeatStudentScoreList(query));
        scores.sort(Comparator.comparing(SeatStudentScore::getTotalScore,
            Comparator.nullsLast(Comparator.reverseOrder())));
        int total = scores.size();
        for (int i = 0; i < scores.size(); i++)
        {
            SeatStudentScore score = scores.get(i);
            score.setClassRank((long) i + 1);
            score.setScoreLevel(levelOf(i + 1, total));
            score.setUpdateBy(operName);
            score.setUpdateTime(DateUtils.getNowDate());
            seatStudentScoreMapper.updateSeatStudentScore(score);
        }
    }

    private String levelOf(int rank, int total)
    {
        if (total <= 0)
        {
            return null;
        }
        if (rank <= Math.ceil(total * 0.25D))
        {
            return "A";
        }
        if (rank <= Math.ceil(total * 0.50D))
        {
            return "B";
        }
        if (rank <= Math.ceil(total * 0.75D))
        {
            return "C";
        }
        return "D";
    }

    private List<String> parseSubjects(SeatExam exam)
    {
        if (StringUtils.isNotBlank(exam.getSubjectSnapshot()))
        {
            return GradeSubjectHelper.normalizeSubjects(JSON.parseArray(exam.getSubjectSnapshot(), String.class));
        }
        SeatClass seatClass = seatClassService.selectSeatClassByClassId(exam.getClassId());
        return GradeSubjectHelper.resolveClassSubjects(seatClass);
    }

    private Map<String, Integer> readHeader(Row header)
    {
        if (header == null)
        {
            throw new ServiceException("模板表头不能为空");
        }
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++)
        {
            String name = cellString(header, i);
            if (StringUtils.isNotBlank(name))
            {
                headerIndex.put(name.trim(), i);
            }
        }
        return headerIndex;
    }

    private void validateHeaders(Map<String, Integer> headerIndex, List<String> subjects)
    {
        for (String header : FIXED_HEADERS)
        {
            if (!headerIndex.containsKey(header))
            {
                throw new ServiceException("导入模板缺少列：" + header);
            }
        }
        if (resolveImportSubjects(headerIndex, subjects).isEmpty())
        {
            throw new ServiceException("导入模板至少需要包含一门考试科目成绩列（当前考试科目：" + String.join("、", subjects) + "）");
        }
    }

    private List<String> resolveImportSubjects(Map<String, Integer> headerIndex, List<String> subjects)
    {
        List<String> importSubjects = new ArrayList<>();
        for (String subject : subjects)
        {
            if (headerIndex.containsKey(subject))
            {
                importSubjects.add(subject);
            }
        }
        return importSubjects;
    }

    private void mergeExistingSubjects(SeatStudentScore score, SeatStudentScore existing,
            List<String> subjects, List<String> importSubjects, Map<String, Integer> headerIndex, Row row)
    {
        JSONObject oldScores = StringUtils.isBlank(existing.getSubjectScores())
                ? null : JSON.parseObject(existing.getSubjectScores());
        Map<String, BigDecimal> merged = new LinkedHashMap<>();
        for (String subject : subjects)
        {
            BigDecimal oldValue = oldScores == null ? null : oldScores.getBigDecimal(subject);
            merged.put(subject, oldValue == null ? BigDecimal.ZERO : oldValue);
        }
        for (String subject : importSubjects)
        {
            BigDecimal newValue = subjectScore(row, headerIndex.get(subject), subject);
            if (newValue != null)
            {
                merged.put(subject, newValue);
            }
        }
        score.setSubjectScores(JSON.toJSONString(merged));
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : merged.values())
        {
            total = total.add(value);
        }
        score.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
        if (StringUtils.isBlank(score.getRemark()))
        {
            score.setRemark(existing.getRemark());
        }
    }

    private void writeTemplate(HttpServletResponse response, SeatClass seatClass, List<String> subjects)
    {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream os = response.getOutputStream())
        {
            Sheet sheet = workbook.createSheet("学生成绩");
            Row header = sheet.createRow(0);
            List<String> headers = new ArrayList<>();
            headers.addAll(FIXED_HEADERS);
            headers.addAll(subjects);
            headers.addAll(TAIL_HEADERS);
            for (int i = 0; i < headers.size(); i++)
            {
                header.createCell(i).setCellValue(headers.get(i));
                sheet.setColumnWidth(i, 16 * 256);
            }
            String fileName = URLEncoder.encode(seatClass.getClassName() + "_score_template.xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            workbook.write(os);
        }
        catch (Exception e)
        {
            throw new ServiceException("生成成绩导入模板失败：" + e.getMessage());
        }
    }

    private boolean isBlankRow(Row row)
    {
        if (row == null)
        {
            return true;
        }
        // 按整行判空，避免学号列留空但其他列有值的行被静默跳过。
        for (int i = 0; i < row.getLastCellNum(); i++)
        {
            if (StringUtils.isNotBlank(cellString(row, i)))
            {
                return false;
            }
        }
        return true;
    }

    private String cellString(Row row, Integer index)
    {
        if (row == null || index == null)
        {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null)
        {
            return null;
        }
        // POI 4.1.2 下 setCellType(STRING) 会清空 inlineStr 单元格（openpyxl/pandas 导出的常见格式），
        // 改用只读的 DataFormatter 按单元格原始类型取值，兼容 inlineStr、sharedStrings 与数值型学号。
        return StringUtils.trim(DATA_FORMATTER.get().formatCellValue(cell));
    }

    private BigDecimal subjectScore(Row row, Integer index, String subject)
    {
        String value = cellString(row, index);
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        BigDecimal score;
        try
        {
            score = new BigDecimal(value.trim());
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(subject + " 列：「" + value + "」不是有效分数");
        }
        if (score.signum() < 0 || score.compareTo(MAX_SUBJECT_SCORE) > 0)
        {
            throw new ServiceException(subject + " 列：分数必须在 0-" + MAX_SUBJECT_SCORE.toPlainString()
                + " 之间，当前为 " + value);
        }
        return score;
    }

    private static class ImportRow
    {
        private final SeatStudentScore score;
        private final SeatStudentScore existing;

        private ImportRow(SeatStudentScore score, SeatStudentScore existing)
        {
            this.score = score;
            this.existing = existing;
        }
    }
}
