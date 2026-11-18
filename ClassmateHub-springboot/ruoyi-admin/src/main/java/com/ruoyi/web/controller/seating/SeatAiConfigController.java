package com.ruoyi.web.controller.seating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.seating.domain.SeatAiConfig;
import com.ruoyi.seating.service.ISeatAiConfigService;
import com.ruoyi.seating.service.ISeatUserAiPermissionService;

/**
 * AI大模型排座配置Controller
 */
@RestController
@RequestMapping("/seating/ai-config")
public class SeatAiConfigController extends BaseController
{
    @Autowired
    private ISeatAiConfigService seatAiConfigService;

    @Autowired
    private ISeatUserAiPermissionService seatUserAiPermissionService;

    /**
     * 查询AI模型配置列表
     */
    @PreAuthorize("@ss.hasPermi('seating:aiConfig:list')")
    @GetMapping("/list")
    public TableDataInfo list(SeatAiConfig seatAiConfig)
    {
        checkAiConfigAccess();
        startPage();
        List<SeatAiConfig> list = seatAiConfigService.selectSeatAiConfigList(seatAiConfig);
        return getDataTable(list);
    }

    /**
     * 查询已启用的AI模型选项（供拥有AI排座权限的老师在生成弹窗中选择）
     */
    @GetMapping("/enabled")
    public AjaxResult listEnabled()
    {
        if (!seatUserAiPermissionService.hasAiSeatingPermission(getUserId()))
        {
            return success(new ArrayList<>());
        }
        SeatAiConfig query = new SeatAiConfig();
        query.setStatus("0");
        List<SeatAiConfig> list = seatAiConfigService.selectSeatAiConfigList(query);
        List<Map<String, Object>> options = new ArrayList<>(list.size());
        for (SeatAiConfig item : list)
        {
            Map<String, Object> opt = new HashMap<>();
            opt.put("configId", item.getConfigId());
            opt.put("configName", item.getConfigName());
            opt.put("provider", item.getProvider());
            opt.put("modelName", item.getModelName());
            opt.put("reasoningEffort", item.getReasoningEffort() != null ? item.getReasoningEffort() : "DEFAULT");
            opt.put("isDefault", item.getIsDefault());
            options.add(opt);
        }
        return success(options);
    }

    /**
     * 获取AI模型配置详细信息
     */
    @PreAuthorize("@ss.hasPermi('seating:aiConfig:query')")
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@PathVariable("configId") Long configId)
    {
        checkAiConfigAccess();
        return success(seatAiConfigService.selectSeatAiConfigByConfigId(configId));
    }

    /**
     * 新增AI模型配置
     */
    @PreAuthorize("@ss.hasPermi('seating:aiConfig:add')")
    @Log(title = "AI排座配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SeatAiConfig seatAiConfig)
    {
        checkAiConfigAccess();
        seatAiConfig.setCreateBy(getUsername());
        return toAjax(seatAiConfigService.insertSeatAiConfig(seatAiConfig));
    }

    /**
     * 修改AI模型配置
     */
    @PreAuthorize("@ss.hasPermi('seating:aiConfig:edit')")
    @Log(title = "AI排座配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SeatAiConfig seatAiConfig)
    {
        checkAiConfigAccess();
        seatAiConfig.setUpdateBy(getUsername());
        return toAjax(seatAiConfigService.updateSeatAiConfig(seatAiConfig));
    }

    /**
     * 删除AI模型配置
     */
    @PreAuthorize("@ss.hasPermi('seating:aiConfig:remove')")
    @Log(title = "AI排座配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        checkAiConfigAccess();
        return toAjax(seatAiConfigService.deleteSeatAiConfigByConfigIds(configIds));
    }

    /**
     * 测试大模型接口连通性
     */
    @PreAuthorize("@ss.hasPermi('seating:aiConfig:edit')")
    @Log(title = "AI连接测试", businessType = BusinessType.OTHER)
    @PostMapping("/test-connection")
    public AjaxResult testConnection(@RequestBody SeatAiConfig seatAiConfig)
    {
        checkAiConfigAccess();
        return success(seatAiConfigService.testConnection(seatAiConfig));
    }

    /**
     * 在线拉取当前接口支持的模型列表
     */
    @PreAuthorize("@ss.hasAnyPermi('seating:aiConfig:add,seating:aiConfig:edit')")
    @PostMapping("/models")
    public AjaxResult listRemoteModels(@RequestBody SeatAiConfig seatAiConfig)
    {
        checkAiConfigAccess();
        return success(seatAiConfigService.listRemoteModels(seatAiConfig));
    }

    private void checkAiConfigAccess()
    {
        if (!seatUserAiPermissionService.hasAiConfigPermission(getUserId()))
        {
            throw new ServiceException("当前账号无 AI 模型配置管理权限");
        }
    }
}
