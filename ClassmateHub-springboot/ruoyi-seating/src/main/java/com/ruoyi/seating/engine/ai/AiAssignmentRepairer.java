package com.ruoyi.seating.engine.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.seating.domain.SeatPosition;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.engine.SeatingEngine;
import com.ruoyi.seating.engine.model.AiSeatingRequest;
import com.ruoyi.seating.engine.model.SeatingAssignmentResult;
import com.ruoyi.seating.engine.model.SeatingResult;

/**
 * 校验并修复 AI 大模型返回的排座 JSON（保障锁定座位不可变、无重座、无漏排，并对硬规则违例做局部微调）。
 */
public class AiAssignmentRepairer
{
    public record ParsedAiOutput(String reasoning, List<SeatingAssignmentResult> assignments)
    {
    }

    public ParsedAiOutput parseAndRepair(String jsonPayload, AiSeatingRequest request, SeatingEngine seatingEngine)
    {
        JSONObject root;
        try
        {
            root = JSON.parseObject(jsonPayload);
        }
        catch (Exception e)
        {
            throw new ServiceException("AI 返回的排座 JSON 格式不合法");
        }
        if (root == null)
        {
            throw new ServiceException("AI 返回的排座数据为空");
        }

        String reasoning = StringUtils.trim(root.getString("reasoning"));
        JSONArray rawArray = root.getJSONArray("assignments");
        if (rawArray == null)
        {
            rawArray = root.getJSONArray("data");
        }
        if (rawArray == null || rawArray.isEmpty())
        {
            throw new ServiceException("AI 返回结果中缺少 assignments 座位分配列表");
        }

        List<SeatStudent> students = request.getStudents();
        List<SeatPosition> allSeats = request.getSeats();
        Map<Long, SeatStudent> studentMap = new HashMap<>();
        for (SeatStudent s : students)
        {
            studentMap.put(s.getStudentId(), s);
        }

        List<SeatPosition> usableSeats = new ArrayList<>();
        Map<Long, SeatPosition> usableSeatById = new HashMap<>();
        Map<String, SeatPosition> usableSeatByCoord = new HashMap<>();
        for (SeatPosition seat : allSeats)
        {
            if (isUsable(seat))
            {
                usableSeats.add(seat);
                usableSeatById.put(seat.getSeatId(), seat);
                usableSeatByCoord.put(seat.getRowIndex() + "-" + seat.getColIndex(), seat);
            }
        }
        usableSeats.sort(Comparator.comparing(SeatPosition::getRowIndex, Comparator.nullsLast(Long::compareTo))
                .thenComparing(SeatPosition::getColIndex, Comparator.nullsLast(Long::compareTo)));

        List<SeatingAssignmentResult> repaired = new ArrayList<>();
        Set<Long> assignedStudents = new HashSet<>();
        Set<Long> occupiedSeats = new HashSet<>();

        // 1. 优先锁定不可变座位
        Map<Long, Long> lockedMap = request.getLockedSeatByStudentId();
        for (Map.Entry<Long, Long> entry : lockedMap.entrySet())
        {
            SeatStudent stu = studentMap.get(entry.getKey());
            SeatPosition seat = usableSeatById.get(entry.getValue());
            if (stu != null && seat != null && occupiedSeats.add(seat.getSeatId()))
            {
                repaired.add(new SeatingAssignmentResult(stu, seat));
                assignedStudents.add(stu.getStudentId());
            }
        }

        // 2. 采纳 AI 返回的有效分配
        for (int i = 0; i < rawArray.size(); i++)
        {
            JSONObject item = rawArray.getJSONObject(i);
            if (item == null)
            {
                continue;
            }
            Long studentId = item.getLong("studentId");
            if (studentId == null || !studentMap.containsKey(studentId) || assignedStudents.contains(studentId))
            {
                continue;
            }
            SeatPosition targetSeat = resolveSeat(item, usableSeatById, usableSeatByCoord);
            if (targetSeat == null || occupiedSeats.contains(targetSeat.getSeatId()))
            {
                continue;
            }
            occupiedSeats.add(targetSeat.getSeatId());
            assignedStudents.add(studentId);
            repaired.add(new SeatingAssignmentResult(studentMap.get(studentId), targetSeat));
        }

        // 3. 将 AI 遗漏的学生补入剩余可用座位
        int seatCursor = 0;
        for (SeatStudent stu : students)
        {
            if (assignedStudents.contains(stu.getStudentId()))
            {
                continue;
            }
            while (seatCursor < usableSeats.size() && occupiedSeats.contains(usableSeats.get(seatCursor).getSeatId()))
            {
                seatCursor++;
            }
            if (seatCursor < usableSeats.size())
            {
                SeatPosition freeSeat = usableSeats.get(seatCursor);
                occupiedSeats.add(freeSeat.getSeatId());
                assignedStudents.add(stu.getStudentId());
                repaired.add(new SeatingAssignmentResult(stu, freeSeat));
            }
        }

        // 4. 若存在硬规则冲突，仅在非锁定座位之间尝试最小代价局部交换修复
        polishHardConflicts(repaired, usableSeats, occupiedSeats, lockedMap.keySet(), request, seatingEngine);

        if (StringUtils.isBlank(reasoning))
        {
            reasoning = "已综合教室桌对布局、学生多维画像及排座规则完成智能分配。";
        }
        return new ParsedAiOutput(reasoning, repaired);
    }

    private void polishHardConflicts(List<SeatingAssignmentResult> assignments, List<SeatPosition> usableSeats,
            Set<Long> occupiedSeats, Set<Long> lockedStudentIds, AiSeatingRequest request, SeatingEngine seatingEngine)
    {
        SeatingResult eval = seatingEngine.evaluate(assignments, request.getSeats(),
                request.getRules(), request.getRelations(), 0L);
        if (eval.getConflicts() == null || eval.getConflicts().isEmpty())
        {
            return;
        }

        List<SeatPosition> freeSeats = new ArrayList<>();
        for (SeatPosition seat : usableSeats)
        {
            if (!occupiedSeats.contains(seat.getSeatId()))
            {
                freeSeats.add(seat);
            }
        }

        int currentConflictCount = eval.getConflicts().size();
        for (int pass = 0; pass < 3 && currentConflictCount > 0; pass++)
        {
            boolean improved = false;
            for (int i = 0; i < assignments.size() && currentConflictCount > 0; i++)
            {
                SeatingAssignmentResult left = assignments.get(i);
                if (lockedStudentIds.contains(left.getStudent().getStudentId()))
                {
                    continue;
                }
                // 先尝试移入空座位
                for (int f = 0; f < freeSeats.size(); f++)
                {
                    SeatPosition oldSeat = left.getSeat();
                    SeatPosition emptySeat = freeSeats.get(f);
                    left.setSeat(emptySeat);
                    freeSeats.set(f, oldSeat);
                    int nextConflicts = seatingEngine.evaluate(assignments, request.getSeats(),
                            request.getRules(), request.getRelations(), 0L).getConflicts().size();
                    if (nextConflicts < currentConflictCount)
                    {
                        currentConflictCount = nextConflicts;
                        improved = true;
                        break;
                    }
                    left.setSeat(oldSeat);
                    freeSeats.set(f, emptySeat);
                }
                if (currentConflictCount == 0)
                {
                    break;
                }
                // 再尝试与其他未锁定学生交换
                for (int j = i + 1; j < assignments.size(); j++)
                {
                    SeatingAssignmentResult right = assignments.get(j);
                    if (lockedStudentIds.contains(right.getStudent().getStudentId()))
                    {
                        continue;
                    }
                    SeatPosition s1 = left.getSeat();
                    SeatPosition s2 = right.getSeat();
                    left.setSeat(s2);
                    right.setSeat(s1);
                    int nextConflicts = seatingEngine.evaluate(assignments, request.getSeats(),
                            request.getRules(), request.getRelations(), 0L).getConflicts().size();
                    if (nextConflicts < currentConflictCount)
                    {
                        currentConflictCount = nextConflicts;
                        improved = true;
                        break;
                    }
                    left.setSeat(s1);
                    right.setSeat(s2);
                }
            }
            if (!improved)
            {
                break;
            }
        }
    }

    private SeatPosition resolveSeat(JSONObject item, Map<Long, SeatPosition> byId, Map<String, SeatPosition> byCoord)
    {
        Long seatId = item.getLong("seatId");
        if (seatId != null && byId.containsKey(seatId))
        {
            return byId.get(seatId);
        }
        Long row = item.getLong("rowIndex");
        if (row == null)
        {
            row = item.getLong("row");
        }
        Long col = item.getLong("colIndex");
        if (col == null)
        {
            col = item.getLong("col");
        }
        if (row != null && col != null)
        {
            SeatPosition direct = byCoord.get(row + "-" + col);
            if (direct != null)
            {
                return direct;
            }
            return byCoord.get((row + 1) + "-" + (col + 1));
        }
        return null;
    }

    private boolean isUsable(SeatPosition pos)
    {
        return pos != null && "0".equals(pos.getSeatType())
                && "1".equals(pos.getIsAvailable()) && !"1".equals(pos.getStatus());
    }
}
