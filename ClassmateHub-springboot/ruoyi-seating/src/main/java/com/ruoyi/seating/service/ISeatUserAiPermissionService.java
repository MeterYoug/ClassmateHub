package com.ruoyi.seating.service;

import java.util.List;
import com.ruoyi.seating.domain.SeatUserAiPermission;

/**
 * 用户AI排座权限Service接口
 */
public interface ISeatUserAiPermissionService
{
    public SeatUserAiPermission selectByUserId(Long userId);

    public List<SeatUserAiPermission> selectUserAiPermissionList(SeatUserAiPermission query);

    public boolean hasAiSeatingPermission(Long userId);

    public boolean hasAiConfigPermission(Long userId);

    public int saveUserAiPermission(SeatUserAiPermission permission, String operName);

    public int batchSaveUserAiPermissions(List<SeatUserAiPermission> permissions, String operName);
}
