package com.ruoyi.seating.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.seating.domain.SeatAiConfig;
import com.ruoyi.seating.engine.ai.OpenAiCompatibleClient;
import com.ruoyi.seating.mapper.SeatAiConfigMapper;
import com.ruoyi.seating.service.ISeatAiConfigService;

/**
 * AI大模型排座配置Service业务层处理
 */
@Service
public class SeatAiConfigServiceImpl implements ISeatAiConfigService
{
    private static final String MASK_TOKEN = "****";

    @Autowired
    private SeatAiConfigMapper seatAiConfigMapper;

    @Autowired
    private OpenAiCompatibleClient openAiCompatibleClient;

    @Override
    public SeatAiConfig selectSeatAiConfigByConfigId(Long configId)
    {
        SeatAiConfig config = seatAiConfigMapper.selectSeatAiConfigByConfigId(configId);
        if (config != null)
        {
            config.setApiKey(maskApiKey(decodeApiKey(config.getApiKey())));
        }
        return config;
    }

    @Override
    public SeatAiConfig selectSeatAiConfigWithPlainKey(Long configId)
    {
        SeatAiConfig config = seatAiConfigMapper.selectSeatAiConfigByConfigId(configId);
        if (config != null)
        {
            config.setApiKey(decodeApiKey(config.getApiKey()));
        }
        return config;
    }

    @Override
    public SeatAiConfig selectActiveDefaultOrFirst()
    {
        SeatAiConfig query = new SeatAiConfig();
        query.setStatus("0");
        List<SeatAiConfig> activeList = seatAiConfigMapper.selectSeatAiConfigList(query);
        if (activeList.isEmpty())
        {
            return null;
        }
        SeatAiConfig picked = activeList.get(0);
        picked.setApiKey(decodeApiKey(picked.getApiKey()));
        return picked;
    }

    @Override
    public List<SeatAiConfig> selectSeatAiConfigList(SeatAiConfig seatAiConfig)
    {
        List<SeatAiConfig> list = seatAiConfigMapper.selectSeatAiConfigList(seatAiConfig);
        for (SeatAiConfig item : list)
        {
            item.setApiKey(maskApiKey(decodeApiKey(item.getApiKey())));
        }
        return list;
    }

    @Override
    @Transactional
    public int insertSeatAiConfig(SeatAiConfig seatAiConfig)
    {
        normalizeDefaults(seatAiConfig);
        String rawKey = StringUtils.trim(seatAiConfig.getApiKey());
        if ("OLLAMA".equalsIgnoreCase(seatAiConfig.getProvider()) && StringUtils.isBlank(rawKey))
        {
            rawKey = "ollama";
        }
        if (StringUtils.isBlank(rawKey) || rawKey.contains(MASK_TOKEN))
        {
            throw new ServiceException("API 密钥不能为空");
        }
        seatAiConfig.setApiKey(encodeApiKey(rawKey));
        seatAiConfig.setDelFlag("0");
        seatAiConfig.setCreateTime(DateUtils.getNowDate());

        // 如果当前没有任何启用配置，自动将首个正常配置设为默认
        SeatAiConfig existingQuery = new SeatAiConfig();
        if (seatAiConfigMapper.selectSeatAiConfigList(existingQuery).isEmpty())
        {
            seatAiConfig.setIsDefault("1");
        }
        int rows = seatAiConfigMapper.insertSeatAiConfig(seatAiConfig);
        if ("1".equals(seatAiConfig.getIsDefault()))
        {
            seatAiConfigMapper.clearDefaultExcept(seatAiConfig.getConfigId());
        }
        return rows;
    }

    @Override
    @Transactional
    public int updateSeatAiConfig(SeatAiConfig seatAiConfig)
    {
        SeatAiConfig existing = seatAiConfigMapper.selectSeatAiConfigByConfigId(seatAiConfig.getConfigId());
        if (existing == null)
        {
            throw new ServiceException("AI 模型配置不存在");
        }
        boolean providerChanged = StringUtils.isNotBlank(seatAiConfig.getProvider())
                && !StringUtils.equalsIgnoreCase(seatAiConfig.getProvider().trim(), existing.getProvider());
        String effectiveProvider = StringUtils.defaultIfBlank(StringUtils.trim(seatAiConfig.getProvider()), existing.getProvider());
        String effectiveModel = StringUtils.defaultIfBlank(StringUtils.trim(seatAiConfig.getModelName()), existing.getModelName());
        if (seatAiConfig.getConfigName() != null && StringUtils.isBlank(seatAiConfig.getConfigName()))
        {
            seatAiConfig.setConfigName(buildDefaultConfigName(effectiveProvider, effectiveModel));
        }
        if (seatAiConfig.getReasoningEffort() != null)
        {
            seatAiConfig.setReasoningEffort(OpenAiCompatibleClient.normalizeReasoningEffort(seatAiConfig.getReasoningEffort()));
        }

        String rawKey = StringUtils.trim(seatAiConfig.getApiKey());
        if (StringUtils.isBlank(rawKey) || rawKey.contains(MASK_TOKEN))
        {
            if (providerChanged)
            {
                if ("OLLAMA".equalsIgnoreCase(effectiveProvider))
                {
                    seatAiConfig.setApiKey(encodeApiKey("ollama"));
                }
                else
                {
                    throw new ServiceException("切换 AI 供应商后请填写新的 API 密钥");
                }
            }
            else
            {
                seatAiConfig.setApiKey(null);
            }
        }
        else
        {
            seatAiConfig.setApiKey(encodeApiKey(rawKey));
        }
        seatAiConfig.setUpdateTime(DateUtils.getNowDate());
        int rows = seatAiConfigMapper.updateSeatAiConfig(seatAiConfig);
        if ("1".equals(seatAiConfig.getIsDefault()))
        {
            seatAiConfigMapper.clearDefaultExcept(seatAiConfig.getConfigId());
        }
        return rows;
    }

    @Override
    public int deleteSeatAiConfigByConfigIds(Long[] configIds)
    {
        return seatAiConfigMapper.deleteSeatAiConfigByConfigIds(configIds);
    }

    @Override
    public Map<String, Object> testConnection(SeatAiConfig seatAiConfig)
    {
        String plainKey = StringUtils.trim(seatAiConfig.getApiKey());
        if ((StringUtils.isBlank(plainKey) || plainKey.contains(MASK_TOKEN)) && seatAiConfig.getConfigId() != null)
        {
            SeatAiConfig stored = seatAiConfigMapper.selectSeatAiConfigByConfigId(seatAiConfig.getConfigId());
            if (stored != null)
            {
                boolean providerChanged = StringUtils.isNotBlank(seatAiConfig.getProvider())
                        && !StringUtils.equalsIgnoreCase(seatAiConfig.getProvider().trim(), stored.getProvider());
                if (providerChanged && !"OLLAMA".equalsIgnoreCase(seatAiConfig.getProvider()))
                {
                    throw new ServiceException("切换 AI 供应商后，请填写新的 API 密钥再测试连接");
                }
                if (!providerChanged)
                {
                    plainKey = decodeApiKey(stored.getApiKey());
                }
                if (StringUtils.isBlank(seatAiConfig.getProvider()))
                {
                    seatAiConfig.setProvider(stored.getProvider());
                }
                if (StringUtils.isBlank(seatAiConfig.getApiUrl()))
                {
                    seatAiConfig.setApiUrl(stored.getApiUrl());
                }
                if (StringUtils.isBlank(seatAiConfig.getModelName()))
                {
                    seatAiConfig.setModelName(stored.getModelName());
                }
                if (StringUtils.isBlank(seatAiConfig.getReasoningEffort()))
                {
                    seatAiConfig.setReasoningEffort(stored.getReasoningEffort());
                }
                if (seatAiConfig.getTimeoutSeconds() == null)
                {
                    seatAiConfig.setTimeoutSeconds(stored.getTimeoutSeconds());
                }
            }
        }
        if ("OLLAMA".equalsIgnoreCase(seatAiConfig.getProvider()) && StringUtils.isBlank(plainKey))
        {
            plainKey = "ollama";
        }
        if (StringUtils.isBlank(plainKey) || plainKey.contains(MASK_TOKEN))
        {
            throw new ServiceException("请填写有效的 API 密钥后再测试连接");
        }
        return openAiCompatibleClient.testConnection(seatAiConfig, plainKey);
    }

    @Override
    public List<String> listRemoteModels(SeatAiConfig seatAiConfig)
    {
        String plainKey = resolveTestOrRemoteKey(seatAiConfig, "请先填写 API 密钥后再获取模型列表");
        return openAiCompatibleClient.listAvailableModels(seatAiConfig, plainKey);
    }

    private String resolveTestOrRemoteKey(SeatAiConfig seatAiConfig, String missingMsg)
    {
        String plainKey = StringUtils.trim(seatAiConfig.getApiKey());
        if ((StringUtils.isBlank(plainKey) || plainKey.contains(MASK_TOKEN)) && seatAiConfig.getConfigId() != null)
        {
            SeatAiConfig stored = seatAiConfigMapper.selectSeatAiConfigByConfigId(seatAiConfig.getConfigId());
            if (stored != null)
            {
                boolean providerChanged = StringUtils.isNotBlank(seatAiConfig.getProvider())
                        && !StringUtils.equalsIgnoreCase(seatAiConfig.getProvider().trim(), stored.getProvider());
                if (providerChanged && !"OLLAMA".equalsIgnoreCase(seatAiConfig.getProvider()))
                {
                    throw new ServiceException("切换 AI 供应商后，请先输入新的 API 密钥");
                }
                if (!providerChanged)
                {
                    plainKey = decodeApiKey(stored.getApiKey());
                }
                if (StringUtils.isBlank(seatAiConfig.getProvider()))
                {
                    seatAiConfig.setProvider(stored.getProvider());
                }
                if (StringUtils.isBlank(seatAiConfig.getApiUrl()))
                {
                    seatAiConfig.setApiUrl(stored.getApiUrl());
                }
            }
        }
        if ("OLLAMA".equalsIgnoreCase(seatAiConfig.getProvider()) && StringUtils.isBlank(plainKey))
        {
            plainKey = "ollama";
        }
        if (StringUtils.isBlank(plainKey) || plainKey.contains(MASK_TOKEN))
        {
            throw new ServiceException(missingMsg);
        }
        return plainKey;
    }

    private void normalizeDefaults(SeatAiConfig config)
    {
        config.setProvider(StringUtils.defaultIfBlank(StringUtils.trim(config.getProvider()), "CUSTOM"));
        config.setApiUrl(StringUtils.trim(config.getApiUrl()));
        config.setModelName(StringUtils.trim(config.getModelName()));
        config.setReasoningEffort(OpenAiCompatibleClient.normalizeReasoningEffort(config.getReasoningEffort()));
        String trimmedName = StringUtils.trim(config.getConfigName());
        config.setConfigName(StringUtils.isNotBlank(trimmedName)
                ? trimmedName
                : buildDefaultConfigName(config.getProvider(), config.getModelName()));
        if (config.getTemperature() == null)
        {
            config.setTemperature("KIMI".equalsIgnoreCase(config.getProvider())
                    ? new BigDecimal("1.00") : new BigDecimal("0.30"));
        }
        if (config.getMaxTokens() == null || config.getMaxTokens() <= 0)
        {
            config.setMaxTokens("ZHIPU".equalsIgnoreCase(config.getProvider()) ? 4095 : 8192);
        }
        else if ("ZHIPU".equalsIgnoreCase(config.getProvider()) && config.getMaxTokens() == 4096)
        {
            config.setMaxTokens(4095);
        }
        if (config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0)
        {
            config.setTimeoutSeconds(120);
        }
        config.setIsDefault("1".equals(config.getIsDefault()) ? "1" : "0");
        config.setStatus(StringUtils.defaultIfBlank(config.getStatus(), "0"));
    }

    public static String buildDefaultConfigName(String provider, String modelName)
    {
        String providerTitle = switch (StringUtils.defaultIfBlank(provider, "CUSTOM").toUpperCase())
        {
            case "DEEPSEEK" -> "DeepSeek";
            case "BYTEDANCE" -> "字节豆包";
            case "ZHIPU" -> "智谱 AI";
            case "KIMI" -> "Kimi";
            case "XIAOMI" -> "小米 MiMo";
            case "QWEN" -> "通义千问";
            case "SILICONFLOW" -> "硅基流动";
            case "OPENAI" -> "OpenAI";
            case "OLLAMA" -> "本地 Ollama";
            default -> "自定义模型";
        };
        if (StringUtils.isBlank(modelName))
        {
            return providerTitle;
        }
        String candidate = providerTitle + " - " + modelName.trim();
        return candidate.length() > 64 ? candidate.substring(0, 64) : candidate;
    }

    public static String encodeApiKey(String plainKey)
    {
        if (StringUtils.isBlank(plainKey))
        {
            return "";
        }
        return Base64.getEncoder().encodeToString(plainKey.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeApiKey(String encodedKey)
    {
        if (StringUtils.isBlank(encodedKey))
        {
            return "";
        }
        try
        {
            return new String(Base64.getDecoder().decode(encodedKey), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException e)
        {
            return encodedKey;
        }
    }

    public static String maskApiKey(String plainKey)
    {
        if (StringUtils.isBlank(plainKey))
        {
            return "";
        }
        int len = plainKey.length();
        if (len <= 8)
        {
            return MASK_TOKEN;
        }
        return plainKey.substring(0, 3) + MASK_TOKEN + plainKey.substring(len - 4);
    }
}
