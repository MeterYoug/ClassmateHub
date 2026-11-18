package com.ruoyi.seating.engine.model;

/**
 * AI 大模型排座输出结果。
 */
public class AiSeatingResponse
{
    private final SeatingResult seatingResult;
    private final String reasoning;

    public AiSeatingResponse(SeatingResult seatingResult, String reasoning)
    {
        this.seatingResult = seatingResult;
        this.reasoning = reasoning;
    }

    public SeatingResult getSeatingResult()
    {
        return seatingResult;
    }

    public String getReasoning()
    {
        return reasoning;
    }
}
