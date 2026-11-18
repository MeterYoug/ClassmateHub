package com.ruoyi.seating.engine.ai;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.seating.engine.SeatingEngine;
import com.ruoyi.seating.engine.ai.AiAssignmentRepairer.ParsedAiOutput;
import com.ruoyi.seating.engine.model.AiSeatingRequest;
import com.ruoyi.seating.engine.model.AiSeatingResponse;
import com.ruoyi.seating.engine.model.SeatingResult;

/**
 * AI 排座混合流水线编排器：构建桌对 Prompt -> 调用兼容接口 -> 修复锁定与漏排 -> 统一规则引擎评分。
 */
@Component
public class AiSeatingCoordinator
{
    private final AiPromptBuilder promptBuilder = new AiPromptBuilder();
    private final AiAssignmentRepairer repairer = new AiAssignmentRepairer();

    @Autowired
    private OpenAiCompatibleClient aiClient;

    @Autowired
    private SeatingEngine seatingEngine;

    public AiSeatingCoordinator()
    {
    }

    public AiSeatingCoordinator(OpenAiCompatibleClient aiClient, SeatingEngine seatingEngine)
    {
        this.aiClient = aiClient;
        this.seatingEngine = seatingEngine;
    }

    public AiSeatingResponse generateWithAi(AiSeatingRequest request)
    {
        Map<Long, Integer> deskPairIndexes = seatingEngine.computeDeskPairIndexes(request.getSeats());
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(request, deskPairIndexes);

        String jsonPayload = aiClient.chatCompletionJson(
                request.getAiConfig(), request.getPlainApiKey(), systemPrompt, userPrompt);

        ParsedAiOutput parsed = repairer.parseAndRepair(jsonPayload, request, seatingEngine);
        SeatingResult evaluated = seatingEngine.evaluate(
                parsed.assignments(), request.getSeats(), request.getRules(), request.getRelations(), 0L);

        return new AiSeatingResponse(evaluated, parsed.reasoning());
    }
}
