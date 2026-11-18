package com.ruoyi.web.controller.seating;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.seating.domain.SeatUserAiPermission;
import com.ruoyi.seating.service.ISeatUserAiPermissionService;

/**
 * 用户AI排座权限管理Controller
 */
@RestController
@RequestMapping("/seating/ai-permission")
public class SeatUserAiPermissionController extends BaseController
{
    @Autowired
    private ISeatUserAiPermissionService seatUserAiPermissionService;

    /**
     * 查询当前登录用户的AI排座与配置权限
     */
    @GetMapping("/my")
    public AjaxResult getMyPermission()
    {
        Long userId = getUserId();
        boolean isAdmin = SecurityUtils.isAdmin(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("isAdmin", isAdmin);
        data.put("hasAiSeating", seatUserAiPermissionService.hasAiSeatingPermission(userId));
        data.put("hasAiConfig", seatUserAiPermissionService.hasAiConfigPermission(userId));
        return success(data);
    }

    /**
     * 查询用户AI权限列表
     */
    @PreAuthorize("@ss.hasPermi('seating:aiPermission:list')")
    @GetMapping("/list")
    public TableDataInfo list(SeatUserAiPermission query)
    {
        startPage();
        List<SeatUserAiPermission> list = seatUserAiPermissionService.selectUserAiPermissionList(query);
        return getDataTable(list);
    }

    /**
     * 修改单个用户的AI权限
     */
    @PreAuthorize("@ss.hasPermi('seating:aiPermission:edit')")
    @Log(title = "用户AI权限", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SeatUserAiPermission permission)
    {
        return toAjax(seatUserAiPermissionService.saveUserAiPermission(permission, getUsername()));
    }

    /**
     * 批量修改用户的AI权限
     */
    @PreAuthorize("@ss.hasPermi('seating:aiPermission:edit')")
    @Log(title = "批量设置用户AI权限", businessType = BusinessType.UPDATE)
    @PostMapping("/batch")
    public AjaxResult batchEdit(@RequestBody List<SeatUserAiPermission> permissions)
    {
        return toAjax(seatUserAiPermissionService.batchSaveUserAiPermissions(permissions, getUsername()));
    }
}
