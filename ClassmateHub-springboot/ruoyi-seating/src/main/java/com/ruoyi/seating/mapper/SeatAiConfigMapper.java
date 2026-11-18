package com.ruoyi.seating.mapper;

import java.util.List;
import com.ruoyi.seating.domain.SeatAiConfig;

/**
 * AI大模型排座配置Mapper接口
 */
public interface SeatAiConfigMapper
{
    public SeatAiConfig selectSeatAiConfigByConfigId(Long configId);

    public List<SeatAiConfig> selectSeatAiConfigList(SeatAiConfig seatAiConfig);

    public int insertSeatAiConfig(SeatAiConfig seatAiConfig);

    public int updateSeatAiConfig(SeatAiConfig seatAiConfig);

    public int clearDefaultExcept(Long excludeConfigId);

    public int deleteSeatAiConfigByConfigId(Long configId);

    public int deleteSeatAiConfigByConfigIds(Long[] configIds);
}
