package com.ruoyi.seating.engine.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.seating.domain.SeatPosition;
import com.ruoyi.seating.domain.SeatRule;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.domain.SeatStudentRelation;
import com.ruoyi.seating.engine.model.AiSeatingRequest;

/**
 * 构建包含真实桌对分组、过道、锁定座位、学生各科画像及老师自然语言要求的 Prompt。
 */
public class AiPromptBuilder
{
    public String buildSystemPrompt()
    {
        return """
                你是「班伴」智能排座专家。请根据教室真实座位布局（含过道与同桌配对组）、学生多维属性、排座规则以及老师的特别要求，为全班学生生成合理的座位分配方案。

                【严格约束】
                1. 只能使用「可用座位列表」中列出的 seatId，严禁虚构座位 ID 或使用过道/不可用座位。
                2. 每个学生（studentId）必须且只能分配 1 个可用座位；每个座位（seatId）最多只能坐 1 名学生。
                3. 已锁定座位（若存在）必须原样保留，不得更改其 studentId 与 seatId 对应关系。
                4. 硬规则（如指定学生坐前几排、不能同桌、不能相邻）必须优先满足；同桌关系以 Prompt 中标注的「同桌组#」为准（过道两侧不属于同桌）。
                5. 若老师提供了「本次排座特别要求」，请在不违反硬规则与锁定座位的前提下优先落实。
                6. 若当前模型开启了深度思考（Thinking / Reasoning），请仅用简短要点快速确定分排梯度与同桌配对策略，切勿在思考过程中逐人反复试排或罗列多版草稿，以留足输出配额直接生成完整 JSON。

                【输出格式要求】
                请严格只输出一个合法 JSON 对象，不要附加 Markdown 说明文字：
                {
                  "reasoning": "用中文向老师简要说明本次排座的核心思路（100~250字，说明如何落实规则与老师的特别要求）",
                  "assignments": [
                    {"studentId": 101, "seatId": 11},
                    {"studentId": 102, "seatId": 12}
                  ]
                }
                """;
    }

    public String buildUserPrompt(AiSeatingRequest request, Map<Long, Integer> deskPairIndexes)
    {
        StringBuilder sb = new StringBuilder(4096);
        List<SeatStudent> students = request.getStudents();
        List<SeatPosition> seats = request.getSeats();
        Map<Long, SeatStudent> studentMap = new HashMap<>();
        for (SeatStudent s : students)
        {
            studentMap.put(s.getStudentId(), s);
        }
        Map<Long, SeatPosition> seatMap = new HashMap<>();
        for (SeatPosition p : seats)
        {
            seatMap.put(p.getSeatId(), p);
        }

        // 1. 教室布局与桌对拓扑
        String platform = StringUtils.defaultIfBlank(request.getPlatformPosition(), "FRONT");
        sb.append("【一、教室布局与同桌拓扑】（讲台方向：").append(platformLabel(platform))
                .append("，rowIndex=1 为最靠近前排第1排）\n");

        Map<Long, List<SeatPosition>> rowMap = new TreeMap<>();
        int usableCount = 0;
        for (SeatPosition pos : seats)
        {
            if (pos.getRowIndex() == null)
            {
                continue;
            }
            rowMap.computeIfAbsent(pos.getRowIndex(), k -> new ArrayList<>()).add(pos);
            if (isUsable(pos))
            {
                usableCount++;
            }
        }

        for (Map.Entry<Long, List<SeatPosition>> entry : rowMap.entrySet())
        {
            List<SeatPosition> rowSeats = entry.getValue();
            rowSeats.sort(Comparator.comparing(SeatPosition::getColIndex, Comparator.nullsLast(Long::compareTo)));
            sb.append("第").append(entry.getKey()).append("排：");
            List<String> segments = new ArrayList<>();
            Map<Integer, List<String>> pairBuckets = new LinkedHashMap<>();
            for (SeatPosition pos : rowSeats)
            {
                if ("2".equals(pos.getSeatType()))
                {
                    flushPairs(segments, pairBuckets);
                    segments.add("[过道 C" + pos.getColIndex() + "]");
                }
                else if (!isUsable(pos))
                {
                    flushPairs(segments, pairBuckets);
                    segments.add("[不可用 C" + pos.getColIndex() + "]");
                }
                else
                {
                    String code = StringUtils.defaultIfBlank(pos.getSeatCode(),
                            "R" + pos.getRowIndex() + "C" + pos.getColIndex());
                    String label = code + "(seatId=" + pos.getSeatId() + ",列" + pos.getColIndex() + ")";
                    Integer pairIdx = deskPairIndexes.get(pos.getSeatId());
                    if (pairIdx != null)
                    {
                        pairBuckets.computeIfAbsent(pairIdx, k -> new ArrayList<>()).add(label);
                    }
                    else
                    {
                        flushPairs(segments, pairBuckets);
                        segments.add("单座[" + label + "]");
                    }
                }
            }
            flushPairs(segments, pairBuckets);
            sb.append(String.join(" | ", segments)).append("\n");
        }
        sb.append("可用座位总数：").append(usableCount).append("，待排学生总数：").append(students.size()).append("\n\n");

        // 2. 已锁定座位
        Map<Long, Long> lockedMap = request.getLockedSeatByStudentId();
        if (!lockedMap.isEmpty())
        {
            sb.append("【二、必须原样保留的已锁定座位】\n");
            for (Map.Entry<Long, Long> entry : lockedMap.entrySet())
            {
                SeatStudent stu = studentMap.get(entry.getKey());
                SeatPosition pos = seatMap.get(entry.getValue());
                if (stu != null && pos != null && isUsable(pos))
                {
                    sb.append("- studentId=").append(stu.getStudentId())
                            .append("(").append(stu.getStudentName()).append(") 固定在 seatId=")
                            .append(pos.getSeatId()).append("(第").append(pos.getRowIndex())
                            .append("排第").append(pos.getColIndex()).append("列)\n");
                }
            }
            sb.append("\n");
        }

        // 3. 学生列表
        Map<Long, String> subjectSummaries = request.getStudentSubjectSummaries();
        sb.append("【三、班级学生画像】\n");
        for (SeatStudent stu : students)
        {
            sb.append("- studentId=").append(stu.getStudentId())
                    .append(", 姓名=").append(stu.getStudentName())
                    .append(", 性别=").append(genderText(stu.getGender()));
            if (stu.getHeightCm() != null)
            {
                sb.append(", 身高=").append(stu.getHeightCm()).append("cm");
            }
            sb.append(", 视力=").append(visionText(stu.getVisionLevel()));
            if (StringUtils.isNotBlank(stu.getScoreLevel()))
            {
                sb.append(", 综合成绩等级=").append(stu.getScoreLevel());
            }
            String subSummary = subjectSummaries.get(stu.getStudentId());
            if (StringUtils.isNotBlank(subSummary))
            {
                sb.append(", 各科成绩=[").append(subSummary).append("]");
            }
            if (StringUtils.isNotBlank(stu.getDisciplineLevel()) && !"0".equals(stu.getDisciplineLevel()))
            {
                sb.append(", 纪律=").append(disciplineText(stu.getDisciplineLevel()));
            }
            if (StringUtils.isNotBlank(stu.getSpecialNeed()))
            {
                sb.append(", 特殊需求=").append(stu.getSpecialNeed().trim());
            }
            sb.append("\n");
        }
        sb.append("\n");

        // 4. 启用的排座规则与学生关系约束
        sb.append("【四、已启用的排座规则】\n");
        int ruleCount = 0;
        for (SeatRule rule : request.getRules())
        {
            if (!"1".equals(rule.getEnabled()) || !"0".equals(rule.getStatus()))
            {
                continue;
            }
            ruleCount++;
            sb.append("- [").append("HARD".equals(rule.getRuleCategory()) ? "硬规则-必须满足" : "软规则-权重" + rule.getRuleWeight())
                    .append("] ").append(rule.getRuleName()).append(" (").append(rule.getRuleCode()).append(")");
            String configDesc = describeRuleConfig(rule, studentMap);
            if (StringUtils.isNotBlank(configDesc))
            {
                sb.append("：").append(configDesc);
            }
            sb.append("\n");
        }
        for (SeatStudentRelation rel : request.getRelations())
        {
            if (!"1".equals(rel.getEnabled()))
            {
                continue;
            }
            SeatStudent left = studentMap.get(rel.getStudentId());
            SeatStudent right = studentMap.get(rel.getRelatedId());
            if (left != null && right != null)
            {
                ruleCount++;
                sb.append("- [学生关系约束] ").append(left.getStudentName()).append("(studentId=").append(left.getStudentId())
                        .append(") 与 ").append(right.getStudentName()).append("(studentId=").append(right.getStudentId())
                        .append(")：").append(relationText(rel.getRelationType())).append("\n");
            }
        }
        if (ruleCount == 0)
        {
            sb.append("- 暂无预设规则，请按常规视力近视靠前、高个靠后、男女搭配均衡原则排座。\n");
        }

        // 5. 老师自然语言特别要求
        if (StringUtils.isNotBlank(request.getTeacherPrompt()))
        {
            sb.append("\n【五、老师本次排座特别要求（重点落实）】\n")
                    .append(request.getTeacherPrompt().trim()).append("\n");
        }

        sb.append("\n请为全部 ").append(students.size()).append(" 名学生分配可用 seatId，并按指定 JSON 结构返回。");
        return sb.toString();
    }

    private void flushPairs(List<String> segments, Map<Integer, List<String>> pairBuckets)
    {
        for (Map.Entry<Integer, List<String>> pair : pairBuckets.entrySet())
        {
            segments.add("同桌组#" + pair.getKey() + "[" + String.join(", ", pair.getValue()) + "]");
        }
        pairBuckets.clear();
    }

    private boolean isUsable(SeatPosition pos)
    {
        return pos != null && "0".equals(pos.getSeatType())
                && "1".equals(pos.getIsAvailable()) && !"1".equals(pos.getStatus());
    }

    private String describeRuleConfig(SeatRule rule, Map<Long, SeatStudent> studentMap)
    {
        if (StringUtils.isBlank(rule.getRuleConfig()))
        {
            return "";
        }
        try
        {
            JSONObject obj = JSON.parseObject(rule.getRuleConfig());
            if (obj == null)
            {
                return "";
            }
            JSONArray ids = obj.getJSONArray("studentIds");
            List<String> names = new ArrayList<>();
            if (ids != null)
            {
                for (int i = 0; i < ids.size(); i++)
                {
                    Long id = ids.getLong(i);
                    SeatStudent stu = id == null ? null : studentMap.get(id);
                    names.add(stu != null ? stu.getStudentName() + "(studentId=" + id + ")" : "studentId=" + id);
                }
            }
            if ("FRONT_ROW".equals(rule.getRuleCode()))
            {
                long frontRows = obj.getLongValue("frontRows", 2L);
                return "要求坐在前 " + frontRows + " 排的学生：" + String.join("、", names);
            }
            if (!names.isEmpty())
            {
                return "涉及学生：" + String.join("、", names);
            }
        }
        catch (Exception ignored)
        {
        }
        return "";
    }

    private String platformLabel(String pos)
    {
        return switch (pos)
        {
            case "BACK" -> "后方";
            case "LEFT" -> "左侧";
            case "RIGHT" -> "右侧";
            default -> "前方";
        };
    }

    private String genderText(String gender)
    {
        if ("0".equals(gender))
        {
            return "男";
        }
        if ("1".equals(gender))
        {
            return "女";
        }
        return "未知";
    }

    private String visionText(String vision)
    {
        return switch (vision == null ? "0" : vision)
        {
            case "1" -> "轻度近视";
            case "2" -> "中度近视";
            case "3" -> "重度近视";
            default -> "正常";
        };
    }

    private String disciplineText(String level)
    {
        return switch (level == null ? "0" : level)
        {
            case "1" -> "需关注";
            case "2" -> "重点关注(尽量分散)";
            default -> "正常";
        };
    }

    private String relationText(String type)
    {
        return switch (type == null ? "" : type)
        {
            case "NOT_DESKMATE" -> "禁止同桌（硬约束）";
            case "NOT_ADJACENT" -> "禁止上下左右相邻（硬约束）";
            case "PREFER_DESKMATE" -> "优先安排同桌（软约束）";
            default -> type;
        };
    }
}
