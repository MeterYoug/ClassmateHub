package com.ruoyi.seating.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.seating.domain.SeatUserAiPermission;
import com.ruoyi.seating.mapper.SeatUserAiPermissionMapper;
import com.ruoyi.seating.service.ISeatUserAiPermissionService;

/**
 * 用户AI排座权限Service业务层处理
 */
@Service
public class SeatUserAiPermissionServiceImpl implements ISeatUserAiPermissionService
{
    @Autowired
    private SeatUserAiPermissionMapper seatUserAiPermissionMapper;

    @Override
    public SeatUserAiPermission selectByUserId(Long userId)
    {
        if (userId == null)
        {
            return null;
        }
        SeatUserAiPermission perm = seatUserAiPermissionMapper.selectByUserId(userId);
        if (perm != null && SecurityUtils.isAdmin(userId))
        {
            perm.setHasAiSeating("1");
            perm.setHasAiConfig("1");
        }
        return perm;
    }

    @Override
    public List<SeatUserAiPermission> selectUserAiPermissionList(SeatUserAiPermission query)
    {
        List<SeatUserAiPermission> list = seatUserAiPermissionMapper.selectUserAiPermissionList(query);
        for (SeatUserAiPermission item : list)
        {
            if (SecurityUtils.isAdmin(item.getUserId()))
            {
                item.setHasAiSeating("1");
                item.setHasAiConfig("1");
            }
        }
        return list;
    }

    @Override
    public boolean hasAiSeatingPermission(Long userId)
    {
        if (userId == null)
        {
            return false;
        }
        if (SecurityUtils.isAdmin(userId))
        {
            return true;
        }
        SeatUserAiPermission perm = seatUserAiPermissionMapper.selectByUserId(userId);
        return perm != null && "1".equals(perm.getHasAiSeating());
    }

    @Override
    public boolean hasAiConfigPermission(Long userId)
    {
        if (userId == null)
        {
            return false;
        }
        if (SecurityUtils.isAdmin(userId))
        {
            return true;
        }
        SeatUserAiPermission perm = seatUserAiPermissionMapper.selectByUserId(userId);
        return perm != null && "1".equals(perm.getHasAiConfig());
    }

    @Override
    @Transactional
    public int saveUserAiPermission(SeatUserAiPermission permission, String operName)
    {
        if (permission == null || permission.getUserId() == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        if (SecurityUtils.isAdmin(permission.getUserId()))
        {
            permission.setHasAiSeating("1");
            permission.setHasAiConfig("1");
        }
        else
        {
            permission.setHasAiSeating("1".equals(permission.getHasAiSeating()) ? "1" : "0");
            permission.setHasAiConfig("1".equals(permission.getHasAiConfig()) ? "1" : "0");
        }
        if (permission.getDailyLimit() == null || permission.getDailyLimit() < 0)
        {
            permission.setDailyLimit(50);
        }

        SeatUserAiPermission existing = seatUserAiPermissionMapper.selectByUserId(permission.getUserId());
        if (existing == null)
        {
            throw new ServiceException("目标用户不存在");
        }
        if (existing.getPermissionId() == null)
        {
            permission.setCreateBy(operName);
            permission.setCreateTime(DateUtils.getNowDate());
            return seatUserAiPermissionMapper.insertUserAiPermission(permission);
        }
        permission.setUpdateBy(operName);
        permission.setUpdateTime(DateUtils.getNowDate());
        return seatUserAiPermissionMapper.updateUserAiPermission(permission);
    }

    @Override
    @Transactional
    public int batchSaveUserAiPermissions(List<SeatUserAiPermission> permissions, String operName)
    {
        if (permissions == null || permissions.isEmpty())
        {
            return 0;
        }
        int count = 0;
        for (SeatUserAiPermission item : permissions)
        {
            count += saveUserAiPermission(item, operName);
        }
        return count;
    }
}
