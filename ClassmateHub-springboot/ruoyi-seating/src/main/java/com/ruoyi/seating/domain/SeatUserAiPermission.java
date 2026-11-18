package com.ruoyi.seating.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 用户AI排座权限配置对象 seat_user_ai_permission
 */
public class SeatUserAiPermission extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long permissionId;

    /** 用户ID */
    private Long userId;

    /** 登录账号（关联查询展示） */
    private String userName;

    /** 用户昵称（关联查询展示） */
    private String nickName;

    /** 账号状态（关联查询展示） */
    private String userStatus;

    /** 允许使用AI排座（0否 1是） */
    private String hasAiSeating;

    /** 允许管理AI配置（0否 1是） */
    private String hasAiConfig;

    /** 每日AI排座调用上限（0不限） */
    private Integer dailyLimit;

    public Long getPermissionId()
    {
        return permissionId;
    }

    public void setPermissionId(Long permissionId)
    {
        this.permissionId = permissionId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getUserStatus()
    {
        return userStatus;
    }

    public void setUserStatus(String userStatus)
    {
        this.userStatus = userStatus;
    }

    public String getHasAiSeating()
    {
        return hasAiSeating;
    }

    public void setHasAiSeating(String hasAiSeating)
    {
        this.hasAiSeating = hasAiSeating;
    }

    public String getHasAiConfig()
    {
        return hasAiConfig;
    }

    public void setHasAiConfig(String hasAiConfig)
    {
        this.hasAiConfig = hasAiConfig;
    }

    public Integer getDailyLimit()
    {
        return dailyLimit;
    }

    public void setDailyLimit(Integer dailyLimit)
    {
        this.dailyLimit = dailyLimit;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("permissionId", getPermissionId())
                .append("userId", getUserId())
                .append("userName", getUserName())
                .append("nickName", getNickName())
                .append("hasAiSeating", getHasAiSeating())
                .append("hasAiConfig", getHasAiConfig())
                .append("dailyLimit", getDailyLimit())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
