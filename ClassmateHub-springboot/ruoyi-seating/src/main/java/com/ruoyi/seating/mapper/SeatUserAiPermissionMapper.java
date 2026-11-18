package com.ruoyi.seating.mapper;

import java.util.List;
import com.ruoyi.seating.domain.SeatUserAiPermission;

/**
 * 用户AI排座权限Mapper接口
 */
public interface SeatUserAiPermissionMapper
{
    public SeatUserAiPermission selectByUserId(Long userId);

    public List<SeatUserAiPermission> selectUserAiPermissionList(SeatUserAiPermission query);

    public int insertUserAiPermission(SeatUserAiPermission permission);

    public int updateUserAiPermission(SeatUserAiPermission permission);
}
