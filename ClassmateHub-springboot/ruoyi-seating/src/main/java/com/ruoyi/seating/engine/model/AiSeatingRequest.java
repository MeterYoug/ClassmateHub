package com.ruoyi.seating.engine.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.ruoyi.seating.domain.SeatAiConfig;
import com.ruoyi.seating.domain.SeatPosition;
import com.ruoyi.seating.domain.SeatRule;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.domain.SeatStudentRelation;

/**
 * AI 大模型排座请求上下文。
 */
public class AiSeatingRequest
{
    private SeatAiConfig aiConfig;
    private String plainApiKey;
    private String platformPosition;
    private List<SeatStudent> students;
    private List<SeatPosition> seats;
    private List<SeatRule> rules;
    private List<SeatStudentRelation> relations;
    private Map<Long, Long> lockedSeatByStudentId = Collections.emptyMap();
    private Map<Long, String> studentSubjectSummaries = Collections.emptyMap();
    private String teacherPrompt;

    public SeatAiConfig getAiConfig()
    {
        return aiConfig;
    }

    public void setAiConfig(SeatAiConfig aiConfig)
    {
        this.aiConfig = aiConfig;
    }

    public String getPlainApiKey()
    {
        return plainApiKey;
    }

    public void setPlainApiKey(String plainApiKey)
    {
        this.plainApiKey = plainApiKey;
    }

    public String getPlatformPosition()
    {
        return platformPosition;
    }

    public void setPlatformPosition(String platformPosition)
    {
        this.platformPosition = platformPosition;
    }

    public List<SeatStudent> getStudents()
    {
        return students == null ? Collections.emptyList() : students;
    }

    public void setStudents(List<SeatStudent> students)
    {
        this.students = students;
    }

    public List<SeatPosition> getSeats()
    {
        return seats == null ? Collections.emptyList() : seats;
    }

    public void setSeats(List<SeatPosition> seats)
    {
        this.seats = seats;
    }

    public List<SeatRule> getRules()
    {
        return rules == null ? Collections.emptyList() : rules;
    }

    public void setRules(List<SeatRule> rules)
    {
        this.rules = rules;
    }

    public List<SeatStudentRelation> getRelations()
    {
        return relations == null ? Collections.emptyList() : relations;
    }

    public void setRelations(List<SeatStudentRelation> relations)
    {
        this.relations = relations;
    }

    public Map<Long, Long> getLockedSeatByStudentId()
    {
        return lockedSeatByStudentId == null ? Collections.emptyMap() : lockedSeatByStudentId;
    }

    public void setLockedSeatByStudentId(Map<Long, Long> lockedSeatByStudentId)
    {
        this.lockedSeatByStudentId = lockedSeatByStudentId;
    }

    public Map<Long, String> getStudentSubjectSummaries()
    {
        return studentSubjectSummaries == null ? Collections.emptyMap() : studentSubjectSummaries;
    }

    public void setStudentSubjectSummaries(Map<Long, String> studentSubjectSummaries)
    {
        this.studentSubjectSummaries = studentSubjectSummaries;
    }

    public String getTeacherPrompt()
    {
        return teacherPrompt;
    }

    public void setTeacherPrompt(String teacherPrompt)
    {
        this.teacherPrompt = teacherPrompt;
    }
}
