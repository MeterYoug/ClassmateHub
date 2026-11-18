package com.ruoyi.seating.engine.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.ruoyi.seating.domain.SeatRule;
import com.ruoyi.seating.domain.SeatPosition;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.domain.SeatStudentRelation;

/**
 * 排座算法输入上下文。
 */
public class SeatingContext
{
    private List<SeatStudent> students;

    private List<SeatPosition> seats;

    private List<SeatRule> rules;

    private List<SeatStudentRelation> relations;

    private long randomSeed;

    private int optimizeIterations;

    /** 已锁定座位映射（studentId -> seatId） */
    private Map<Long, Long> lockedSeatByStudentId = Collections.emptyMap();

    public SeatingContext(List<SeatStudent> students, List<SeatPosition> seats)
    {
        this.students = students;
        this.seats = seats;
    }

    public SeatingContext(List<SeatStudent> students, List<SeatPosition> seats, List<SeatRule> rules,
            List<SeatStudentRelation> relations, long randomSeed, int optimizeIterations)
    {
        this.students = students;
        this.seats = seats;
        this.rules = rules;
        this.relations = relations;
        this.randomSeed = randomSeed;
        this.optimizeIterations = optimizeIterations;
    }

    public List<SeatStudent> getStudents()
    {
        return students;
    }

    public List<SeatPosition> getSeats()
    {
        return seats;
    }

    public List<SeatRule> getRules()
    {
        return rules;
    }

    public List<SeatStudentRelation> getRelations()
    {
        return relations;
    }

    public long getRandomSeed()
    {
        return randomSeed;
    }

    public int getOptimizeIterations()
    {
        return optimizeIterations;
    }

    public Map<Long, Long> getLockedSeatByStudentId()
    {
        return lockedSeatByStudentId == null ? Collections.emptyMap() : lockedSeatByStudentId;
    }

    public void setLockedSeatByStudentId(Map<Long, Long> lockedSeatByStudentId)
    {
        this.lockedSeatByStudentId = lockedSeatByStudentId;
    }
}
