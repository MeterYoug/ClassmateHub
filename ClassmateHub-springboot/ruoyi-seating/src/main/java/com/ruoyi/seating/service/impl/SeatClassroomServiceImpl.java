package com.ruoyi.seating.service.impl;

import java.util.List;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.seating.mapper.SeatClassroomMapper;
import com.ruoyi.seating.mapper.SeatPlanMapper;
import com.ruoyi.seating.domain.SeatClassroom;
import com.ruoyi.seating.domain.SeatPlan;
import com.ruoyi.seating.service.ISeatClassroomService;

/**
 * 排座教室布局Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-06-15
 */
@Service
public class SeatClassroomServiceImpl implements ISeatClassroomService 
{
    @Autowired
    private SeatClassroomMapper seatClassroomMapper;

    @Autowired
    private SeatPlanMapper seatPlanMapper;

    /**
     * 查询排座教室布局
     * 
     * @param classroomId 排座教室布局主键
     * @return 排座教室布局
     */
    @Override
    public SeatClassroom selectSeatClassroomByClassroomId(Long classroomId)
    {
        return seatClassroomMapper.selectSeatClassroomByClassroomId(classroomId);
    }

    /**
     * 查询排座教室布局列表
     * 
     * @param seatClassroom 排座教室布局
     * @return 排座教室布局
     */
    @Override
    public List<SeatClassroom> selectSeatClassroomList(SeatClassroom seatClassroom)
    {
        return seatClassroomMapper.selectSeatClassroomList(seatClassroom);
    }

    /**
     * 新增排座教室布局
     * 
     * @param seatClassroom 排座教室布局
     * @return 结果
     */
    @Override
    public int insertSeatClassroom(SeatClassroom seatClassroom)
    {
        seatClassroom.setCreateTime(DateUtils.getNowDate());
        return seatClassroomMapper.insertSeatClassroom(seatClassroom);
    }

    /**
     * 修改排座教室布局
     * 
     * @param seatClassroom 排座教室布局
     * @return 结果
     */
    @Override
    public int updateSeatClassroom(SeatClassroom seatClassroom)
    {
        seatClassroom.setUpdateTime(DateUtils.getNowDate());
        return seatClassroomMapper.updateSeatClassroom(seatClassroom);
    }

    /**
     * 批量删除排座教室布局
     * 
     * @param classroomIds 需要删除的排座教室布局主键
     * @return 结果
     */
    @Override
    public int deleteSeatClassroomByClassroomIds(Long[] classroomIds)
    {
        if (classroomIds != null)
        {
            for (Long classroomId : classroomIds)
            {
                checkClassroomDeletable(classroomId);
            }
        }
        return seatClassroomMapper.deleteSeatClassroomByClassroomIds(classroomIds);
    }

    /**
     * 删除排座教室布局信息
     * 
     * @param classroomId 排座教室布局主键
     * @return 结果
     */
    @Override
    public int deleteSeatClassroomByClassroomId(Long classroomId)
    {
        checkClassroomDeletable(classroomId);
        return seatClassroomMapper.deleteSeatClassroomByClassroomId(classroomId);
    }

    private void checkClassroomDeletable(Long classroomId)
    {
        if (classroomId == null)
        {
            return;
        }
        SeatPlan planQuery = new SeatPlan();
        planQuery.setClassroomId(classroomId);
        if (!seatPlanMapper.selectSeatPlanList(planQuery).isEmpty())
        {
            throw new ServiceException("该教室布局已被座位方案使用，请先删除关联的座位方案");
        }
    }
}
