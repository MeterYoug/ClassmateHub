package com.ruoyi.seating.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI大模型排座配置对象 seat_ai_config
 */
public class SeatAiConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** AI配置主键 */
    private Long configId;

    /** 配置名称 */
    @Excel(name = "配置名称")
    private String configName;

    /** 供应商标识 */
    @Excel(name = "供应商")
    private String provider;

    /** API基础地址或完整Endpoint */
    @Excel(name = "API地址")
    private String apiUrl;

    /** API密钥（存储为Base64，返回前端时脱敏） */
    private String apiKey;

    /** 模型名称 */
    @Excel(name = "模型名称")
    private String modelName;

    /** 思考程度（DEFAULT默认 OFF关闭 LOW轻量 MEDIUM适中 HIGH深度） */
    @Excel(name = "思考程度", readConverterExp = "DEFAULT=默认,OFF=关闭,LOW=轻量,MEDIUM=适中,HIGH=深度")
    private String reasoningEffort;

    /** 采样温度 */
    @Excel(name = "采样温度")
    private BigDecimal temperature;

    /** 最大输出Token数 */
    @Excel(name = "最大Token数")
    private Integer maxTokens;

    /** 请求超时秒数 */
    @Excel(name = "超时秒数")
    private Integer timeoutSeconds;

    /** 是否默认配置（0否 1是） */
    @Excel(name = "默认配置", readConverterExp = "0=否,1=是")
    private String isDefault;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    @NotBlank(message = "配置名称不能为空")
    public String getConfigName()
    {
        return configName;
    }

    public void setConfigName(String configName)
    {
        this.configName = configName;
    }

    @NotBlank(message = "供应商不能为空")
    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    @NotBlank(message = "API地址不能为空")
    public String getApiUrl()
    {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl)
    {
        this.apiUrl = apiUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    @NotBlank(message = "模型名称不能为空")
    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getReasoningEffort()
    {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort)
    {
        this.reasoningEffort = reasoningEffort;
    }

    public BigDecimal getTemperature()
    {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature)
    {
        this.temperature = temperature;
    }

    public Integer getMaxTokens()
    {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens)
    {
        this.maxTokens = maxTokens;
    }

    public Integer getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getIsDefault()
    {
        return isDefault;
    }

    public void setIsDefault(String isDefault)
    {
        this.isDefault = isDefault;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("configId", getConfigId())
                .append("configName", getConfigName())
                .append("provider", getProvider())
                .append("apiUrl", getApiUrl())
                .append("modelName", getModelName())
                .append("reasoningEffort", getReasoningEffort())
                .append("temperature", getTemperature())
                .append("maxTokens", getMaxTokens())
                .append("timeoutSeconds", getTimeoutSeconds())
                .append("isDefault", getIsDefault())
                .append("status", getStatus())
                .append("delFlag", getDelFlag())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
