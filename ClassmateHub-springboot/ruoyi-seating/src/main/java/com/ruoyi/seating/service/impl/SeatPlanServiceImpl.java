package com.ruoyi.seating.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.seating.domain.SeatAiConfig;
import com.ruoyi.seating.domain.SeatAssignment;
import com.ruoyi.seating.domain.SeatClassroom;
import com.ruoyi.seating.domain.SeatExam;
import com.ruoyi.seating.domain.SeatPlanScore;
import com.ruoyi.seating.domain.SeatPosition;
import com.ruoyi.seating.domain.SeatRule;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.domain.SeatStudentRelation;
import com.ruoyi.seating.domain.SeatStudentScore;
import com.ruoyi.seating.engine.SeatingEngine;
import com.ruoyi.seating.engine.ai.AiSeatingCoordinator;
import com.ruoyi.seating.engine.model.AiSeatingRequest;
import com.ruoyi.seating.engine.model.AiSeatingResponse;
import com.ruoyi.seating.engine.model.SeatingAssignmentResult;
import com.ruoyi.seating.engine.model.SeatingContext;
import com.ruoyi.seating.engine.model.SeatingResult;
import com.ruoyi.seating.engine.model.SeatingScoreItem;
import com.ruoyi.seating.mapper.SeatAssignmentMapper;
import com.ruoyi.seating.mapper.SeatClassroomMapper;
import com.ruoyi.seating.mapper.SeatExamMapper;
import com.ruoyi.seating.mapper.SeatPlanMapper;
import com.ruoyi.seating.mapper.SeatPlanScoreMapper;
import com.ruoyi.seating.mapper.SeatPositionMapper;
import com.ruoyi.seating.mapper.SeatRuleMapper;
import com.ruoyi.seating.mapper.SeatStudentMapper;
import com.ruoyi.seating.mapper.SeatStudentRelationMapper;
import com.ruoyi.seating.mapper.SeatStudentScoreMapper;
import com.ruoyi.seating.domain.SeatPlan;
import com.ruoyi.seating.service.ISeatAiConfigService;
import com.ruoyi.seating.service.ISeatPlanService;
import com.ruoyi.seating.service.ISeatUserAiPermissionService;

/**
 * 排座方案Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-06-15
 */
@Service
public class SeatPlanServiceImpl implements ISeatPlanService 
{
    @Autowired
    private SeatPlanMapper seatPlanMapper;

    @Autowired
    private SeatStudentMapper seatStudentMapper;

    @Autowired
    private SeatPositionMapper seatPositionMapper;

    @Autowired
    private SeatAssignmentMapper seatAssignmentMapper;

    @Autowired
    private SeatPlanScoreMapper seatPlanScoreMapper;

    @Autowired
    private SeatRuleMapper seatRuleMapper;

    @Autowired
    private SeatStudentRelationMapper seatStudentRelationMapper;

    @Autowired
    private SeatingEngine seatingEngine;

    @Autowired
    private AiSeatingCoordinator aiSeatingCoordinator;

    @Autowired
    private ISeatAiConfigService seatAiConfigService;

    @Autowired
    private ISeatUserAiPermissionService seatUserAiPermissionService;

    @Autowired
    private SeatClassroomMapper seatClassroomMapper;

    @Autowired
    private SeatExamMapper seatExamMapper;

    @Autowired
    private SeatStudentScoreMapper seatStudentScoreMapper;

    /**
     * 查询排座方案
     * 
     * @param planId 排座方案主键
     * @return 排座方案
     */
    @Override
    public SeatPlan selectSeatPlanByPlanId(Long planId)
    {
        return seatPlanMapper.selectSeatPlanByPlanId(planId);
    }

    /**
     * 查询排座方案列表
     * 
     * @param seatPlan 排座方案
     * @return 排座方案
     */
    @Override
    public List<SeatPlan> selectSeatPlanList(SeatPlan seatPlan)
    {
        return seatPlanMapper.selectSeatPlanList(seatPlan);
    }

    /**
     * 新增排座方案
     * 
     * @param seatPlan 排座方案
     * @return 结果
     */
    @Override
    public int insertSeatPlan(SeatPlan seatPlan)
    {
        seatPlan.setPlanName(normalizePlanName(seatPlan.getPlanName(), null));
        seatPlan.setCreateTime(DateUtils.getNowDate());
        return seatPlanMapper.insertSeatPlan(seatPlan);
    }

    /**
     * 修改排座方案
     * 
     * @param seatPlan 排座方案
     * @return 结果
     */
    @Override
    public int updateSeatPlan(SeatPlan seatPlan)
    {
        if (seatPlan.getPlanName() != null)
        {
            seatPlan.setPlanName(normalizePlanName(seatPlan.getPlanName(), null));
        }
        seatPlan.setUpdateTime(DateUtils.getNowDate());
        return seatPlanMapper.updateSeatPlan(seatPlan);
    }

    /**
     * 批量删除排座方案
     * 
     * @param planIds 需要删除的排座方案主键
     * @return 结果
     */
    @Override
    public int deleteSeatPlanByPlanIds(Long[] planIds)
    {
        return seatPlanMapper.deleteSeatPlanByPlanIds(planIds);
    }

    /**
     * 删除排座方案信息
     * 
     * @param planId 排座方案主键
     * @return 结果
     */
    @Override
    public int deleteSeatPlanByPlanId(Long planId)
    {
        return seatPlanMapper.deleteSeatPlanByPlanId(planId);
    }

    @Override
    @Transactional
    public SeatPlan generateSeatPlan(SeatPlan seatPlan, String operName)
    {
        SeatStudent studentQuery = new SeatStudent();
        studentQuery.setClassId(seatPlan.getClassId());
        studentQuery.setStatus("0");
        List<SeatStudent> students = seatStudentMapper.selectSeatStudentList(studentQuery);
        if (students.isEmpty())
        {
            throw new ServiceException("班级没有可排座学生");
        }

        SeatPosition seatQuery = new SeatPosition();
        seatQuery.setClassroomId(seatPlan.getClassroomId());
        List<SeatPosition> allSeats = seatPositionMapper.selectSeatPositionList(seatQuery);
        List<SeatPosition> usableSeats = allSeats.stream()
                .filter(item -> "0".equals(item.getSeatType())
                        && "1".equals(item.getIsAvailable())
                        && !"1".equals(item.getStatus()))
                .toList();
        if (usableSeats.isEmpty())
        {
            throw new ServiceException("教室没有可用座位");
        }
        if (usableSeats.size() < students.size())
        {
            throw new ServiceException("可用座位数不足，学生 " + students.size() + " 人，可用座位 " + usableSeats.size() + " 个");
        }

        SeatRule ruleQuery = new SeatRule();
        ruleQuery.setClassId(seatPlan.getClassId());
        ruleQuery.setEnabled("1");
        ruleQuery.setStatus("0");
        List<SeatRule> rules = seatRuleMapper.selectSeatRuleList(ruleQuery);

        SeatStudentRelation relationQuery = new SeatStudentRelation();
        relationQuery.setClassId(seatPlan.getClassId());
        relationQuery.setEnabled("1");
        List<SeatStudentRelation> relations = seatStudentRelationMapper.selectSeatStudentRelationList(relationQuery);

        boolean keepLocked = paramBoolean(seatPlan, "keepLockedSeats", false);
        Map<Long, Long> lockedSeatByStudentId = keepLocked
                ? loadActivePlanLockedSeats(seatPlan.getClassId(), seatPlan.getClassroomId())
                : Collections.emptyMap();

        boolean aiMode = "AI".equalsIgnoreCase(seatPlan.getPlanType())
                || "AI".equalsIgnoreCase(paramString(seatPlan, "generateMode"));

        SeatingResult result;
        String finalPlanType = "AUTO";
        Long usedAiConfigId = null;
        String teacherPrompt = StringUtils.trim(seatPlan.getAiPrompt());
        String aiReasoning = null;

        if (aiMode)
        {
            checkCurrentUserAiPermission();
            SeatAiConfig aiConfig = resolveAiConfig(seatPlan.getAiConfigId());
            usedAiConfigId = aiConfig.getConfigId();
            boolean autoFallback = paramBoolean(seatPlan, "autoFallback", true);
            SeatClassroom classroom = seatClassroomMapper.selectSeatClassroomByClassroomId(seatPlan.getClassroomId());

            AiSeatingRequest aiRequest = new AiSeatingRequest();
            aiRequest.setAiConfig(aiConfig);
            aiRequest.setPlainApiKey(aiConfig.getApiKey());
            aiRequest.setPlatformPosition(classroom == null ? "FRONT" : classroom.getPlatformPosition());
            aiRequest.setStudents(students);
            aiRequest.setSeats(allSeats);
            aiRequest.setRules(rules);
            aiRequest.setRelations(relations);
            aiRequest.setLockedSeatByStudentId(lockedSeatByStudentId);
            aiRequest.setStudentSubjectSummaries(loadCurrentExamSubjectSummaries(seatPlan.getClassId()));
            aiRequest.setTeacherPrompt(teacherPrompt);

            try
            {
                AiSeatingResponse aiResponse = aiSeatingCoordinator.generateWithAi(aiRequest);
                result = aiResponse.getSeatingResult();
                finalPlanType = "AI";
                aiReasoning = truncateText(aiResponse.getReasoning(), 1900);
            }
            catch (Exception e)
            {
                if (!autoFallback)
                {
                    throw new ServiceException("AI 大模型排座失败：" + e.getMessage());
                }
                SeatingContext fallbackCtx = buildLocalContext(seatPlan, students, usableSeats, rules, relations,
                        lockedSeatByStudentId);
                result = seatingEngine.generate(fallbackCtx);
                finalPlanType = "AI_FALLBACK";
                aiReasoning = truncateText("AI 大模型响应异常，已自动切换本地规则引擎兜底生成（原因：" + e.getMessage() + "）", 1900);
            }
        }
        else
        {
            SeatingContext localCtx = buildLocalContext(seatPlan, students, usableSeats, rules, relations,
                    lockedSeatByStudentId);
            result = seatingEngine.generate(localCtx);
        }

        if (result.getConflicts() != null && !result.getConflicts().isEmpty())
        {
            throw new ServiceException("硬规则冲突：" + String.join("；", result.getConflicts()));
        }
        SeatPlan generatedPlan = buildGeneratedPlan(seatPlan, result, finalPlanType, usedAiConfigId,
                teacherPrompt, aiReasoning, operName);
        insertSeatPlan(generatedPlan);
        saveAssignments(generatedPlan, result, lockedSeatByStudentId, operName);
        saveScores(generatedPlan, result);
        return generatedPlan;
    }

    @Override
    @Transactional
    public int confirmSeatPlan(SeatPlan seatPlan, String operName)
    {
        if (seatPlan == null || seatPlan.getPlanId() == null)
        {
            throw new ServiceException("座位方案不存在");
        }

        SeatAssignment assignmentQuery = new SeatAssignment();
        assignmentQuery.setPlanId(seatPlan.getPlanId());
        List<SeatAssignment> assignments = seatAssignmentMapper.selectSeatAssignmentList(assignmentQuery);
        if (assignments.isEmpty())
        {
            throw new ServiceException("方案没有座位分配，不能确认");
        }

        int rows = 0;
        SeatPlan activeQuery = new SeatPlan();
        activeQuery.setClassId(seatPlan.getClassId());
        activeQuery.setPlanStatus("ACTIVE");
        List<SeatPlan> activePlans = seatPlanMapper.selectSeatPlanList(activeQuery);
        for (SeatPlan activePlan : activePlans)
        {
            if (!seatPlan.getPlanId().equals(activePlan.getPlanId()))
            {
                SeatPlan archivePlan = new SeatPlan();
                archivePlan.setPlanId(activePlan.getPlanId());
                archivePlan.setPlanStatus("ARCHIVED");
                archivePlan.setUpdateBy(operName);
                archivePlan.setUpdateTime(DateUtils.getNowDate());
                rows += seatPlanMapper.updateSeatPlan(archivePlan);
            }
        }

        SeatPlan confirmPlan = new SeatPlan();
        confirmPlan.setPlanId(seatPlan.getPlanId());
        confirmPlan.setPlanStatus("ACTIVE");
        confirmPlan.setActiveTime(DateUtils.getNowDate());
        confirmPlan.setUpdateBy(operName);
        confirmPlan.setUpdateTime(DateUtils.getNowDate());
        rows += seatPlanMapper.updateSeatPlan(confirmPlan);
        return rows;
    }

    @Override
    @Transactional
    public SeatPlan copySeatPlan(SeatPlan seatPlan, String planName, String operName)
    {
        if (seatPlan == null || seatPlan.getPlanId() == null)
        {
            throw new ServiceException("座位方案不存在");
        }

        SeatPlan copiedPlan = new SeatPlan();
        copiedPlan.setClassId(seatPlan.getClassId());
        copiedPlan.setClassroomId(seatPlan.getClassroomId());
        copiedPlan.setPlanName(normalizePlanName(planName, buildCopyPlanName(seatPlan.getPlanName())));
        copiedPlan.setPlanType("MANUAL");
        copiedPlan.setPlanStatus("DRAFT");
        copiedPlan.setTotalScore(seatPlan.getTotalScore());
        copiedPlan.setAiConfigId(seatPlan.getAiConfigId());
        copiedPlan.setAiPrompt(seatPlan.getAiPrompt());
        copiedPlan.setAiReasoning(seatPlan.getAiReasoning());
        copiedPlan.setGeneratedAt(DateUtils.getNowDate());
        copiedPlan.setDelFlag("0");
        copiedPlan.setCreateBy(operName);
        copiedPlan.setRemark(seatPlan.getRemark());
        insertSeatPlan(copiedPlan);

        copyAssignments(seatPlan, copiedPlan, operName);
        copyScores(seatPlan, copiedPlan);
        return copiedPlan;
    }

    private SeatPlan buildGeneratedPlan(SeatPlan seatPlan, SeatingResult result, String planType,
            Long aiConfigId, String aiPrompt, String aiReasoning, String operName)
    {
        SeatPlan generatedPlan = new SeatPlan();
        generatedPlan.setClassId(seatPlan.getClassId());
        generatedPlan.setClassroomId(seatPlan.getClassroomId());
        String prefix = "AI".equals(planType) ? "AI排座-" : "智能排座-";
        generatedPlan.setPlanName(normalizePlanName(seatPlan.getPlanName(),
                prefix + DateUtils.dateTimeNow()));
        generatedPlan.setPlanType(planType);
        generatedPlan.setPlanStatus(StringUtils.defaultIfBlank(seatPlan.getPlanStatus(), "DRAFT"));
        generatedPlan.setTotalScore(result.getTotalScore());
        generatedPlan.setAiConfigId(aiConfigId);
        generatedPlan.setAiPrompt(truncateText(aiPrompt, 950));
        generatedPlan.setAiReasoning(aiReasoning);
        generatedPlan.setGeneratedAt(DateUtils.getNowDate());
        generatedPlan.setDelFlag("0");
        generatedPlan.setCreateBy(operName);
        generatedPlan.setRemark(seatPlan.getRemark());
        return generatedPlan;
    }

    private SeatingContext buildLocalContext(SeatPlan seatPlan, List<SeatStudent> students,
            List<SeatPosition> usableSeats, List<SeatRule> rules, List<SeatStudentRelation> relations,
            Map<Long, Long> lockedSeatByStudentId)
    {
        long randomSeed = resolveRandomSeed(seatPlan, students.size(), usableSeats.size());
        int optimizeIterations = resolveOptimizeIterations(seatPlan, students.size());
        SeatingContext ctx = new SeatingContext(students, usableSeats, rules, relations, randomSeed, optimizeIterations);
        ctx.setLockedSeatByStudentId(lockedSeatByStudentId);
        return ctx;
    }

    private void checkCurrentUserAiPermission()
    {
        Long userId = null;
        try
        {
            userId = SecurityUtils.getUserId();
        }
        catch (Exception ignored)
        {
        }
        if (userId != null && !seatUserAiPermissionService.hasAiSeatingPermission(userId))
        {
            throw new ServiceException("当前账号尚未开通 AI 大模型排座权限，请联系管理员授权");
        }
    }

    private SeatAiConfig resolveAiConfig(Long configId)
    {
        SeatAiConfig config = configId != null
                ? seatAiConfigService.selectSeatAiConfigWithPlainKey(configId)
                : seatAiConfigService.selectActiveDefaultOrFirst();
        if (config == null || !"0".equals(config.getStatus()))
        {
            throw new ServiceException("未找到可用的 AI 模型配置，请先在「AI 模型配置」中添加并启用模型");
        }
        return config;
    }

    private Map<Long, Long> loadActivePlanLockedSeats(Long classId, Long classroomId)
    {
        SeatPlan activeQuery = new SeatPlan();
        activeQuery.setClassId(classId);
        activeQuery.setClassroomId(classroomId);
        activeQuery.setPlanStatus("ACTIVE");
        List<SeatPlan> activePlans = seatPlanMapper.selectSeatPlanList(activeQuery);
        if (activePlans.isEmpty())
        {
            return Collections.emptyMap();
        }
        SeatAssignment assignQuery = new SeatAssignment();
        assignQuery.setPlanId(activePlans.get(0).getPlanId());
        List<SeatAssignment> assignments = seatAssignmentMapper.selectSeatAssignmentList(assignQuery);
        Map<Long, Long> locked = new HashMap<>();
        for (SeatAssignment item : assignments)
        {
            if ("1".equals(item.getIsLocked()) && item.getStudentId() != null && item.getSeatId() != null)
            {
                locked.put(item.getStudentId(), item.getSeatId());
            }
        }
        return locked;
    }

    private Map<Long, String> loadCurrentExamSubjectSummaries(Long classId)
    {
        SeatExam examQuery = new SeatExam();
        examQuery.setClassId(classId);
        examQuery.setStatus("0");
        List<SeatExam> exams = seatExamMapper.selectSeatExamList(examQuery);
        if (exams.isEmpty())
        {
            return Collections.emptyMap();
        }
        SeatExam picked = exams.stream()
                .filter(item -> "1".equals(item.getIsCurrent()))
                .findFirst()
                .orElse(exams.get(0));
        SeatStudentScore scoreQuery = new SeatStudentScore();
        scoreQuery.setExamId(picked.getExamId());
        List<SeatStudentScore> scores = seatStudentScoreMapper.selectSeatStudentScoreList(scoreQuery);
        if (scores.isEmpty())
        {
            return Collections.emptyMap();
        }
        Map<Long, String> summaries = new HashMap<>();
        for (SeatStudentScore s : scores)
        {
            if (s.getStudentId() == null || StringUtils.isBlank(s.getSubjectScores()))
            {
                continue;
            }
            try
            {
                JSONObject obj = JSON.parseObject(s.getSubjectScores());
                if (obj == null || obj.isEmpty())
                {
                    continue;
                }
                List<String> parts = new ArrayList<>();
                for (String key : obj.keySet())
                {
                    parts.add(key + ":" + obj.getString(key));
                }
                summaries.put(s.getStudentId(), String.join(",", parts));
            }
            catch (Exception ignored)
            {
            }
        }
        return summaries;
    }

    private String paramString(SeatPlan plan, String key)
    {
        Object val = plan.getParams() == null ? null : plan.getParams().get(key);
        return val == null ? null : val.toString();
    }

    private boolean paramBoolean(SeatPlan plan, String key, boolean defaultVal)
    {
        Object val = plan.getParams() == null ? null : plan.getParams().get(key);
        if (val == null)
        {
            return defaultVal;
        }
        if (val instanceof Boolean boolVal)
        {
            return boolVal;
        }
        String str = val.toString().trim();
        return "true".equalsIgnoreCase(str) || "1".equals(str);
    }

    private String truncateText(String text, int maxLen)
    {
        if (StringUtils.isBlank(text))
        {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private long resolveRandomSeed(SeatPlan seatPlan, int studentCount, int seatCount)
    {
        Object seedParam = seatPlan.getParams() == null ? null : seatPlan.getParams().get("seed");
        if (seedParam != null && StringUtils.isNotBlank(seedParam.toString()))
        {
            try
            {
                return Long.parseLong(seedParam.toString().trim());
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("随机种子必须是数字");
            }
        }
        return ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);
    }

    private int resolveOptimizeIterations(SeatPlan seatPlan, int studentCount)
    {
        Object iterationsParam = seatPlan.getParams() == null ? null : seatPlan.getParams().get("iterations");
        if (iterationsParam != null)
        {
            try
            {
                return Integer.parseInt(iterationsParam.toString());
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("优化次数必须是数字");
            }
        }
        return Math.max(800, studentCount * 80);
    }

    private void saveAssignments(SeatPlan plan, SeatingResult result,
            Map<Long, Long> lockedSeatByStudentId, String operName)
    {
        String assignSource = "AI".equals(plan.getPlanType()) ? "AI" : "AUTO";
        for (SeatingAssignmentResult item : result.getAssignments())
        {
            SeatAssignment assignment = new SeatAssignment();
            assignment.setPlanId(plan.getPlanId());
            assignment.setClassId(plan.getClassId());
            assignment.setClassroomId(plan.getClassroomId());
            assignment.setSeatId(item.getSeat().getSeatId());
            assignment.setStudentId(item.getStudent().getStudentId());
            assignment.setStudentNameSnapshot(item.getStudent().getStudentName());
            assignment.setRowIndex(item.getSeat().getRowIndex());
            assignment.setColIndex(item.getSeat().getColIndex());
            assignment.setIsLocked(lockedSeatByStudentId.containsKey(item.getStudent().getStudentId()) ? "1" : "0");
            assignment.setAssignSource(assignSource);
            assignment.setCreateBy(operName);
            assignment.setCreateTime(DateUtils.getNowDate());
            seatAssignmentMapper.insertSeatAssignment(assignment);
        }
    }

    private void saveScores(SeatPlan plan, SeatingResult result)
    {
        for (SeatingScoreItem item : result.getScoreItems())
        {
            SeatPlanScore score = new SeatPlanScore();
            score.setPlanId(plan.getPlanId());
            score.setRuleCode(item.getRuleCode());
            score.setRuleName(item.getRuleName());
            score.setScoreValue(item.getScoreValue());
            score.setPenaltyValue(item.getPenaltyValue());
            score.setDetailJson(item.getDetailJson());
            score.setCreateTime(DateUtils.getNowDate());
            seatPlanScoreMapper.insertSeatPlanScore(score);
        }
    }

    private String buildCopyPlanName(String planName)
    {
        String suffix = "-副本-" + DateUtils.dateTimeNow();
        String sourceName = StringUtils.defaultIfBlank(planName, "座位方案");
        int maxSourceLength = 64 - suffix.length();
        if (sourceName.length() > maxSourceLength)
        {
            sourceName = sourceName.substring(0, maxSourceLength);
        }
        return sourceName + suffix;
    }

    private String normalizePlanName(String planName, String defaultName)
    {
        String normalizedName = StringUtils.trim(StringUtils.defaultIfBlank(planName, defaultName));
        if (StringUtils.isBlank(normalizedName))
        {
            throw new ServiceException("方案名称不能为空");
        }
        if (normalizedName.length() > 64)
        {
            throw new ServiceException("方案名称长度不能超过64个字符");
        }
        return normalizedName;
    }

    private void copyAssignments(SeatPlan sourcePlan, SeatPlan targetPlan, String operName)
    {
        SeatAssignment assignmentQuery = new SeatAssignment();
        assignmentQuery.setPlanId(sourcePlan.getPlanId());
        List<SeatAssignment> assignments = seatAssignmentMapper.selectSeatAssignmentList(assignmentQuery);
        for (SeatAssignment item : assignments)
        {
            SeatAssignment copiedAssignment = new SeatAssignment();
            copiedAssignment.setPlanId(targetPlan.getPlanId());
            copiedAssignment.setClassId(targetPlan.getClassId());
            copiedAssignment.setClassroomId(targetPlan.getClassroomId());
            copiedAssignment.setSeatId(item.getSeatId());
            copiedAssignment.setStudentId(item.getStudentId());
            copiedAssignment.setStudentNameSnapshot(item.getStudentNameSnapshot());
            copiedAssignment.setRowIndex(item.getRowIndex());
            copiedAssignment.setColIndex(item.getColIndex());
            copiedAssignment.setIsLocked(item.getIsLocked());
            copiedAssignment.setAssignSource(item.getAssignSource());
            copiedAssignment.setCreateBy(operName);
            copiedAssignment.setCreateTime(DateUtils.getNowDate());
            copiedAssignment.setRemark(item.getRemark());
            seatAssignmentMapper.insertSeatAssignment(copiedAssignment);
        }
    }

    private void copyScores(SeatPlan sourcePlan, SeatPlan targetPlan)
    {
        SeatPlanScore scoreQuery = new SeatPlanScore();
        scoreQuery.setPlanId(sourcePlan.getPlanId());
        List<SeatPlanScore> scores = seatPlanScoreMapper.selectSeatPlanScoreList(scoreQuery);
        for (SeatPlanScore item : scores)
        {
            SeatPlanScore copiedScore = new SeatPlanScore();
            copiedScore.setPlanId(targetPlan.getPlanId());
            copiedScore.setRuleCode(item.getRuleCode());
            copiedScore.setRuleName(item.getRuleName());
            copiedScore.setScoreValue(item.getScoreValue());
            copiedScore.setPenaltyValue(item.getPenaltyValue());
            copiedScore.setDetailJson(item.getDetailJson());
            copiedScore.setCreateTime(DateUtils.getNowDate());
            seatPlanScoreMapper.insertSeatPlanScore(copiedScore);
        }
    }
}
