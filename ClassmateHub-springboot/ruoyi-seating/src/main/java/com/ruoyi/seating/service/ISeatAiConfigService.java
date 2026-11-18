package com.ruoyi.seating.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.seating.domain.SeatAiConfig;

/**
 * AI大模型排座配置Service接口
 */
public interface ISeatAiConfigService
{
    public SeatAiConfig selectSeatAiConfigByConfigId(Long configId);

    public SeatAiConfig selectSeatAiConfigWithPlainKey(Long configId);

    public SeatAiConfig selectActiveDefaultOrFirst();

    public List<SeatAiConfig> selectSeatAiConfigList(SeatAiConfig seatAiConfig);

    public int insertSeatAiConfig(SeatAiConfig seatAiConfig);

    public int updateSeatAiConfig(SeatAiConfig seatAiConfig);

    public int deleteSeatAiConfigByConfigIds(Long[] configIds);

    public Map<String, Object> testConnection(SeatAiConfig seatAiConfig);

    public List<String> listRemoteModels(SeatAiConfig seatAiConfig);
}
