package com.ruoyi.seating.service.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import com.ruoyi.seating.domain.SeatClassroom;

/**
 * 新学期复制辅助方法。
 */
public final class SeatClassCopySupport
{
    private SeatClassCopySupport()
    {
    }

    public static String nextSchoolYear(String schoolYear, String semester)
    {
        if (StringUtils.isBlank(schoolYear))
        {
            return "";
        }
        if (isSecondSemester(semester))
        {
            String[] parts = schoolYear.split("-");
            if (parts.length == 2)
            {
                try
                {
                    int startYear = Integer.parseInt(parts[0].trim());
                    return (startYear + 1) + "-" + (startYear + 2);
                }
                catch (NumberFormatException ignored)
                {
                    return schoolYear;
                }
            }
        }
        return schoolYear;
    }

    public static String nextSemester(String semester)
    {
        return isFirstSemester(semester) ? "2" : "1";
    }

    public static SeatClassroom selectPrimaryClassroom(List<SeatClassroom> classroomList)
    {
        if (classroomList == null || classroomList.isEmpty())
        {
            return null;
        }
        for (SeatClassroom classroom : classroomList)
        {
            if (classroom != null && "1".equals(classroom.getIsDefault()) && "0".equals(classroom.getStatus()))
            {
                return classroom;
            }
        }
        return null;
    }

    /**
     * 将规则配置（如前排规则）中的源班级学生 ID 映射为新班级学生 ID。
     */
    public static String remapRuleConfigStudentIds(String ruleConfig, Map<Long, Long> studentIdMap)
    {
        if (StringUtils.isBlank(ruleConfig))
        {
            return ruleConfig;
        }
        JSONObject config;
        try
        {
            config = JSON.parseObject(ruleConfig);
        }
        catch (Exception e)
        {
            return ruleConfig;
        }
        if (config == null)
        {
            return ruleConfig;
        }
        boolean modified = false;
        if (config.containsKey("studentIds"))
        {
            JSONArray sourceIds = config.getJSONArray("studentIds");
            List<Long> mappedIds = new ArrayList<>();
            if (sourceIds != null && studentIdMap != null && !studentIdMap.isEmpty())
            {
                for (int i = 0; i < sourceIds.size(); i++)
                {
                    Long sourceId = sourceIds.getLong(i);
                    Long mappedId = sourceId == null ? null : studentIdMap.get(sourceId);
                    if (mappedId != null)
                    {
                        mappedIds.add(mappedId);
                    }
                }
            }
            config.put("studentIds", mappedIds);
            modified = true;
        }
        if (config.containsKey("studentId"))
        {
            Long sourceId = config.getLong("studentId");
            Long mappedId = (sourceId == null || studentIdMap == null) ? null : studentIdMap.get(sourceId);
            if (mappedId != null)
            {
                config.put("studentId", mappedId);
            }
            else
            {
                config.remove("studentId");
            }
            modified = true;
        }
        return modified ? config.toJSONString() : ruleConfig;
    }

    private static boolean isFirstSemester(String semester)
    {
        return "1".equals(semester) || "上学期".equals(semester);
    }

    private static boolean isSecondSemester(String semester)
    {
        return "2".equals(semester) || "下学期".equals(semester);
    }
}
