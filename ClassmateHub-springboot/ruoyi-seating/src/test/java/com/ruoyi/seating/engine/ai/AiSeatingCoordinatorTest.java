package com.ruoyi.seating.engine.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.seating.domain.SeatAiConfig;
import com.ruoyi.seating.domain.SeatPosition;
import com.ruoyi.seating.domain.SeatRule;
import com.ruoyi.seating.domain.SeatStudent;
import com.ruoyi.seating.engine.SeatingEngine;
import com.ruoyi.seating.engine.model.AiSeatingRequest;
import com.ruoyi.seating.engine.model.AiSeatingResponse;
import com.ruoyi.seating.engine.model.SeatingAssignmentResult;

class AiSeatingCoordinatorTest
{
    @Test
    void normalizeUrlAndExtractJsonShouldHandleThinkingAndCodeFences()
    {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://api.deepseek.com"));
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/"));
        assertEquals("https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://ark.cn-beijing.volces.com/api/v3"));
        assertEquals("https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://ark.cn-beijing.volces.com"));
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://open.bigmodel.cn/api/paas/v4/"));
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://open.bigmodel.cn"));
        assertEquals("https://api.moonshot.cn/v1/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://api.moonshot.cn/v1"));
        assertEquals("https://api.xiaomimimo.com/v1/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://api.xiaomimimo.com/v1"));
        assertEquals("https://token-plan-cn.xiaomimimo.com/v1/chat/completions",
                OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://token-plan-cn.xiaomimimo.com/v1/"));
        assertEquals("https://api.xiaomimimo.com/v1/models",
                OpenAiCompatibleClient.normalizeModelsUrl("https://api.xiaomimimo.com/v1"));
        assertEquals("https://open.bigmodel.cn/api/paas/v4/models",
                OpenAiCompatibleClient.normalizeModelsUrl("https://open.bigmodel.cn/api/paas/v4/"));
        assertEquals("https://api.siliconflow.cn/v1/models?sub_type=chat",
                OpenAiCompatibleClient.normalizeModelsUrl("https://api.siliconflow.cn/v1"));
        assertTrue(OpenAiCompatibleClient.isOpenAiReasoningOrGpt5Model("o4-mini"));
        assertTrue(OpenAiCompatibleClient.isOpenAiReasoningOrGpt5Model("gpt-5-mini"));

        String sseStream = """
                data: {"choices":[{"delta":{"reasoning_content":"先思考"}}]}
                
                data: {"choices":[{"delta":{"content":"{\\"reasoning\\":\\"流式成功\\","}}]}
                
                data: {"choices":[{"delta":{"content":"\\"assignments\\":[{\\"studentId\\":1,\\"seatId\\":10}]}"}}]}
                
                data: [DONE]
                """;
        String aggregated = OpenAiCompatibleClient.aggregateSseToChatCompletionJson(sseStream);
        String sseJson = OpenAiCompatibleClient.extractJsonPayload(aggregated);
        assertTrue(sseJson.contains("\"reasoning\":\"流式成功\""));
        assertTrue(OpenAiCompatibleClient.formatFriendlyHttpError(402,
                "{\"error\":{\"code\":\"402\",\"message\":\"Insufficient account balance\"}}")
                .contains("可用余额不足"));
        assertTrue(OpenAiCompatibleClient.formatFriendlyHttpError(400,
                "{\"error\":{\"code\":\"400\",\"message\":\"Unsupported model mimo-v2-flash\"}}")
                .contains("不支持所选模型"));

        String reasoningOnlyReply = """
                {
                  "choices": [{
                    "message": {
                      "content": "",
                      "reasoning_content": "用户要求仅回复 OK..."
                    }
                  }]
                }
                """;
        assertEquals("OK（推理模型已响应）", OpenAiCompatibleClient.extractTestReplyText(reasoningOnlyReply));

        String rawResponse = """
                {
                  "choices": [{
                    "message": {
                      "content": "<think>思考过程...</think>\\n```json\\n{\\"reasoning\\":\\"测试说明\\",\\"assignments\\":[{\\"studentId\\":1,\\"seatId\\":10}]}\\n```"
                    }
                  }]
                }
                """;
        String extracted = OpenAiCompatibleClient.extractJsonPayload(rawResponse);
        assertTrue(extracted.contains("\"reasoning\":\"测试说明\""));
        assertTrue(extracted.startsWith("{") && extracted.endsWith("}"));
    }

    @Test
    void reasoningTruncationShouldAutoExpandTokenBudgetAndReportClearErrorWhenExhausted()
    {
        assertTrue(OpenAiCompatibleClient.isLikelyReasoningModel("deepseek-flash"));
        assertTrue(OpenAiCompatibleClient.isLikelyReasoningModel("mimo-v2.6-flash"));
        assertFalse(OpenAiCompatibleClient.isLikelyReasoningModel("deepseek-chat"));

        SeatAiConfig legacyReasoningCfg = new SeatAiConfig();
        legacyReasoningCfg.setApiUrl("https://api.deepseek.com/v1");
        legacyReasoningCfg.setModelName("deepseek-flash");
        legacyReasoningCfg.setMaxTokens(4096);
        legacyReasoningCfg.setTimeoutSeconds(60);
        assertEquals(16384, OpenAiCompatibleClient.resolveEffectiveMaxTokens(legacyReasoningCfg, true));
        assertEquals(120, OpenAiCompatibleClient.resolveEffectiveTimeoutSeconds(legacyReasoningCfg, true));
        assertEquals(4096, OpenAiCompatibleClient.resolveEffectiveMaxTokens(legacyReasoningCfg, false));

        // 模拟自定义接入点模型首次因 4096 Token 全耗在 reasoning_content 中被截断，客户端自动扩容至 16384 重试成功
        List<Integer> sentMaxTokens = new ArrayList<>();
        AtomicInteger callSeq = new AtomicInteger(0);
        OpenAiCompatibleClient fakeClient = new OpenAiCompatibleClient()
        {
            @Override
            protected String sendRequest(String endpoint, String apiKey, String jsonBody, int timeoutSeconds)
            {
                JSONObject req = JSON.parseObject(jsonBody);
                sentMaxTokens.add(req.getIntValue("max_tokens", 0));
                if (callSeq.getAndIncrement() == 0)
                {
                    return """
                            {
                              "choices": [{
                                "finish_reason": "length",
                                "message": {
                                  "role": "assistant",
                                  "content": "",
                                  "reasoning_content": "正在逐排推演50名学生的座位搭配..."
                                }
                              }]
                            }
                            """;
                }
                return """
                        {
                          "choices": [{
                            "finish_reason": "stop",
                            "message": {
                              "role": "assistant",
                              "content": "{\\"reasoning\\":\\"扩容后成功\\",\\"assignments\\":[{\\"studentId\\":1,\\"seatId\\":10}]}",
                              "reasoning_content": "简要规划完成"
                            }
                          }]
                        }
                        """;
            }
        };

        SeatAiConfig customEndpointCfg = new SeatAiConfig();
        customEndpointCfg.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        customEndpointCfg.setModelName("ep-20261118-custom");
        customEndpointCfg.setMaxTokens(4096);
        customEndpointCfg.setTimeoutSeconds(60);

        String recoveredJson = fakeClient.chatCompletionJson(customEndpointCfg, "sk-test", "sys", "user");
        assertEquals(List.of(4096, 16384), sentMaxTokens);
        assertTrue(recoveredJson.contains("\"reasoning\":\"扩容后成功\""));

        // 若扩容后仍被思维链耗尽或存在未闭合 <think> 块，应返回明确的配额耗尽提示，而非误抽思考中的片段或泛报空消息
        String truncatedReasoningOnly = """
                {
                  "choices": [{
                    "finish_reason": "length",
                    "message": {
                      "content": "",
                      "reasoning_content": "思考仍未结束..."
                    }
                  }]
                }
                """;
        ServiceException ex1 = assertThrows(ServiceException.class,
                () -> OpenAiCompatibleClient.extractJsonPayload(truncatedReasoningOnly));
        assertTrue(ex1.getMessage().contains("耗尽了输出 Token 配额"));

        String unclosedThink = """
                {
                  "choices": [{
                    "finish_reason": "length",
                    "message": {
                      "content": "<think>草稿示例 {\\"studentId\\":1,\\"seatId\\":99} 还没想完..."
                    }
                  }]
                }
                """;
        ServiceException ex2 = assertThrows(ServiceException.class,
                () -> OpenAiCompatibleClient.extractJsonPayload(unclosedThink));
        assertTrue(ex2.getMessage().contains("耗尽了输出 Token 配额"));
    }

    @Test
    void reasoningEffortShouldMapPerProviderAndStripAutomaticallyWhenUnsupported()
    {
        assertEquals("DEFAULT", OpenAiCompatibleClient.normalizeReasoningEffort(null));
        assertEquals("DEFAULT", OpenAiCompatibleClient.normalizeReasoningEffort("  "));
        assertEquals("LOW", OpenAiCompatibleClient.normalizeReasoningEffort("low"));
        assertEquals("DEFAULT", OpenAiCompatibleClient.normalizeReasoningEffort("unknown"));

        // 1. DEFAULT 不注入任何额外思考控制字段
        SeatAiConfig defaultCfg = new SeatAiConfig();
        defaultCfg.setProvider("DEEPSEEK");
        defaultCfg.setApiUrl("https://api.deepseek.com/v1");
        defaultCfg.setModelName("deepseek-flash");
        defaultCfg.setReasoningEffort("DEFAULT");
        JSONObject defaultBody = new JSONObject();
        OpenAiCompatibleClient.applyReasoningEffort(defaultBody, defaultCfg);
        assertFalse(defaultBody.containsKey("reasoning_effort"));
        assertFalse(defaultBody.containsKey("thinking"));
        assertFalse(defaultBody.containsKey("enable_thinking"));

        // 2. 各供应商 OFF / LOW / HIGH 协议映射
        SeatAiConfig zhipu53Off = new SeatAiConfig();
        zhipu53Off.setProvider("ZHIPU");
        zhipu53Off.setModelName("glm-5.3-flash");
        zhipu53Off.setReasoningEffort("OFF");
        JSONObject zhipu53Body = new JSONObject();
        OpenAiCompatibleClient.applyReasoningEffort(zhipu53Body, zhipu53Off);
        assertEquals("enabled", zhipu53Body.getJSONObject("thinking").getString("type"));
        assertEquals("low", zhipu53Body.getString("reasoning_effort"));

        SeatAiConfig zhipu47Off = new SeatAiConfig();
        zhipu47Off.setProvider("ZHIPU");
        zhipu47Off.setModelName("glm-4.7-flash");
        zhipu47Off.setReasoningEffort("OFF");
        JSONObject zhipu47Body = new JSONObject();
        OpenAiCompatibleClient.applyReasoningEffort(zhipu47Body, zhipu47Off);
        assertEquals("disabled", zhipu47Body.getJSONObject("thinking").getString("type"));
        assertFalse(zhipu47Body.containsKey("reasoning_effort"));

        SeatAiConfig qwenOff = new SeatAiConfig();
        qwenOff.setProvider("QWEN");
        qwenOff.setModelName("qwen-plus");
        qwenOff.setReasoningEffort("OFF");
        JSONObject qwenOffBody = new JSONObject();
        OpenAiCompatibleClient.applyReasoningEffort(qwenOffBody, qwenOff);
        assertEquals(Boolean.FALSE, qwenOffBody.getBoolean("enable_thinking"));

        SeatAiConfig qwenLow = new SeatAiConfig();
        qwenLow.setProvider("QWEN");
        qwenLow.setModelName("qwen-plus");
        qwenLow.setReasoningEffort("LOW");
        JSONObject qwenLowBody = new JSONObject();
        OpenAiCompatibleClient.applyReasoningEffort(qwenLowBody, qwenLow);
        assertEquals(Boolean.TRUE, qwenLowBody.getBoolean("enable_thinking"));
        assertEquals("low", qwenLowBody.getString("reasoning_effort"));

        SeatAiConfig o4Off = new SeatAiConfig();
        o4Off.setProvider("OPENAI");
        o4Off.setModelName("o4-mini");
        o4Off.setReasoningEffort("OFF");
        JSONObject o4Body = new JSONObject();
        OpenAiCompatibleClient.applyReasoningEffort(o4Body, o4Off);
        assertEquals("low", o4Body.getString("reasoning_effort"));

        // 3. 仅 OFF 不抬高推理模型的 maxTokens；LOW / MEDIUM / HIGH / DEFAULT 均保底 16384 与 120s
        SeatAiConfig lowReasoningCfg = new SeatAiConfig();
        lowReasoningCfg.setModelName("deepseek-flash");
        lowReasoningCfg.setReasoningEffort("LOW");
        lowReasoningCfg.setMaxTokens(4096);
        lowReasoningCfg.setTimeoutSeconds(45);
        assertEquals(16384, OpenAiCompatibleClient.resolveEffectiveMaxTokens(lowReasoningCfg, true));
        assertEquals(120, OpenAiCompatibleClient.resolveEffectiveTimeoutSeconds(lowReasoningCfg, true));

        SeatAiConfig offReasoningCfg = new SeatAiConfig();
        offReasoningCfg.setModelName("deepseek-flash");
        offReasoningCfg.setReasoningEffort("OFF");
        offReasoningCfg.setMaxTokens(4096);
        offReasoningCfg.setTimeoutSeconds(45);
        assertEquals(4096, OpenAiCompatibleClient.resolveEffectiveMaxTokens(offReasoningCfg, true));
        assertEquals(45, OpenAiCompatibleClient.resolveEffectiveTimeoutSeconds(offReasoningCfg, true));

        SeatAiConfig highCustomCfg = new SeatAiConfig();
        highCustomCfg.setModelName("ep-custom-endpoint");
        highCustomCfg.setReasoningEffort("HIGH");
        highCustomCfg.setMaxTokens(4096);
        highCustomCfg.setTimeoutSeconds(60);
        assertEquals(16384, OpenAiCompatibleClient.resolveEffectiveMaxTokens(highCustomCfg, false));
        assertEquals(120, OpenAiCompatibleClient.resolveEffectiveTimeoutSeconds(highCustomCfg, false));

        // 3b. 日志指标摘要应正确提取 prompt/completion/reasoning Token 与字符长度
        String responseWithUsage = """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "content": "{\\"reasoning\\":\\"ok\\",\\"assignments\\":[]}",
                      "reasoning_content": "简要规划"
                    }
                  }],
                  "usage": {
                    "prompt_tokens": 1320,
                    "completion_tokens": 2450,
                    "completion_tokens_details": {
                      "reasoning_tokens": 1800
                    }
                  }
                }
                """;
        String metrics = OpenAiCompatibleClient.summarizeCompletionMetrics(responseWithUsage);
        assertTrue(metrics.contains("promptTokens=1320"));
        assertTrue(metrics.contains("completionTokens=2450"));
        assertTrue(metrics.contains("reasoningTokens=1800"));
        assertTrue(metrics.contains("reasoningChars=4"));
        assertTrue(metrics.contains("finishReason=stop"));

        // 4. 非推理模型报错不支持 reasoning_effort 时自动剥离并重试成功
        List<JSONObject> capturedBodies = new ArrayList<>();
        OpenAiCompatibleClient adaptiveClient = new OpenAiCompatibleClient()
        {
            @Override
            protected String sendRequest(String endpoint, String apiKey, String jsonBody, int timeoutSeconds)
            {
                JSONObject parsed = JSON.parseObject(jsonBody);
                capturedBodies.add(parsed);
                if (parsed.containsKey("reasoning_effort"))
                {
                    throw new ServiceException("大模型请求失败（HTTP 400: Unrecognized request argument supplied: reasoning_effort）");
                }
                return """
                        {
                          "choices": [{
                            "finish_reason": "stop",
                            "message": {
                              "role": "assistant",
                              "content": "{\\"reasoning\\":\\"剥离后成功\\",\\"assignments\\":[{\\"studentId\\":1,\\"seatId\\":10}]}"
                            }
                          }]
                        }
                        """;
            }
        };

        SeatAiConfig nonReasoningWithLow = new SeatAiConfig();
        nonReasoningWithLow.setProvider("OPENAI");
        nonReasoningWithLow.setApiUrl("https://api.openai.com/v1");
        nonReasoningWithLow.setModelName("gpt-4o-mini");
        nonReasoningWithLow.setReasoningEffort("LOW");
        nonReasoningWithLow.setMaxTokens(4096);

        String result = adaptiveClient.chatCompletionJson(nonReasoningWithLow, "sk-test", "sys", "user");
        assertEquals(2, capturedBodies.size());
        assertEquals("low", capturedBodies.get(0).getString("reasoning_effort"));
        assertFalse(capturedBodies.get(1).containsKey("reasoning_effort"));
        assertTrue(result.contains("\"reasoning\":\"剥离后成功\""));
    }

    @Test
    void promptBuilderShouldIncludeDeskPairsAislesAndTeacherPrompt()
    {
        SeatingEngine engine = new SeatingEngine();
        AiPromptBuilder builder = new AiPromptBuilder();

        SeatPosition p1 = seat(10L, 1L, 1L, "0", "1");
        SeatPosition p2 = seat(11L, 1L, 2L, "0", "1");
        SeatPosition aisle = seat(12L, 1L, 3L, "2", "0");
        SeatPosition p3 = seat(13L, 1L, 4L, "0", "1");
        SeatPosition p4 = seat(14L, 1L, 5L, "0", "1");

        SeatStudent s1 = student(1L, "张明", "0");
        AiSeatingRequest req = new AiSeatingRequest();
        req.setStudents(List.of(s1));
        req.setSeats(List.of(p1, p2, aisle, p3, p4));
        req.setStudentSubjectSummaries(Map.of(1L, "语文:92,数学:65"));
        req.setTeacherPrompt("让数学偏科的学生和数学好的坐同桌");

        String prompt = builder.buildUserPrompt(req, engine.computeDeskPairIndexes(req.getSeats()));
        assertTrue(prompt.contains("同桌组#1"));
        assertTrue(prompt.contains("[过道 C3]"));
        assertTrue(prompt.contains("同桌组#2"));
        assertTrue(prompt.contains("语文:92,数学:65"));
        assertTrue(prompt.contains("让数学偏科的学生和数学好的坐同桌"));
    }

    @Test
    void coordinatorShouldKeepLockedSeatsFillMissingAndPolishHardConflicts()
    {
        OpenAiCompatibleClient mockClient = mock(OpenAiCompatibleClient.class);
        SeatingEngine engine = new SeatingEngine();
        AiSeatingCoordinator coordinator = new AiSeatingCoordinator(mockClient, engine);

        SeatStudent s1 = student(1L, "张三", "0");
        SeatStudent s2 = student(2L, "李四", "1");
        SeatStudent s3 = student(3L, "王五", "0");

        SeatPosition r1c1 = seat(10L, 1L, 1L, "0", "1");
        SeatPosition r1c2 = seat(11L, 1L, 2L, "0", "1");
        SeatPosition r2c1 = seat(20L, 2L, 1L, "0", "1");
        SeatPosition r2c2 = seat(21L, 2L, 2L, "0", "1");

        // 硬规则：王五(3) 必须坐第 1 排
        SeatRule frontRule = new SeatRule();
        frontRule.setRuleName("指定前排");
        frontRule.setRuleCode("FRONT_ROW");
        frontRule.setRuleCategory("HARD");
        frontRule.setRuleConfig("{\"studentIds\":[3],\"frontRows\":1}");
        frontRule.setEnabled("1");
        frontRule.setStatus("0");

        // AI 故意返回：1) 试图把已锁定在 seatId=10 的张三改到 20；2) 把王五放到第2排 seatId=21；3) 漏掉了李四
        String aiJson = """
                {
                  "reasoning": "按视力与性别综合分配座位。",
                  "assignments": [
                    {"studentId": 1, "seatId": 20},
                    {"studentId": 3, "seatId": 21}
                  ]
                }
                """;
        when(mockClient.chatCompletionJson(any(), anyString(), anyString(), anyString())).thenReturn(aiJson);

        SeatAiConfig cfg = new SeatAiConfig();
        cfg.setConfigId(1L);
        cfg.setApiUrl("https://api.example.com/v1");
        cfg.setModelName("test-model");

        AiSeatingRequest req = new AiSeatingRequest();
        req.setAiConfig(cfg);
        req.setPlainApiKey("sk-test");
        req.setStudents(List.of(s1, s2, s3));
        req.setSeats(List.of(r1c1, r1c2, r2c1, r2c2));
        req.setRules(List.of(frontRule));
        req.setLockedSeatByStudentId(Map.of(1L, 10L)); // 张三锁定在 R1C1(seatId=10)

        AiSeatingResponse response = coordinator.generateWithAi(req);

        assertEquals("按视力与性别综合分配座位。", response.getReasoning());
        assertEquals(3, response.getSeatingResult().getAssignments().size());
        assertTrue(response.getSeatingResult().getConflicts().isEmpty(), "局部微调修复后应消除前排硬规则冲突");

        // 验证张三仍然锁定在 seatId=10，且王五被自动微调到第 1 排唯一剩余座位 seatId=11
        assertEquals(10L, findSeatId(response.getSeatingResult().getAssignments(), 1L));
        assertEquals(11L, findSeatId(response.getSeatingResult().getAssignments(), 3L));
    }

    private Long findSeatId(List<SeatingAssignmentResult> list, Long studentId)
    {
        return list.stream()
                .filter(a -> studentId.equals(a.getStudent().getStudentId()))
                .findFirst()
                .orElseThrow()
                .getSeat()
                .getSeatId();
    }

    private SeatStudent student(Long id, String name, String gender)
    {
        SeatStudent s = new SeatStudent();
        s.setStudentId(id);
        s.setStudentName(name);
        s.setGender(gender);
        s.setVisionLevel("0");
        s.setDisciplineLevel("0");
        s.setStatus("0");
        return s;
    }

    private SeatPosition seat(Long id, Long row, Long col, String type, String avail)
    {
        SeatPosition p = new SeatPosition();
        p.setSeatId(id);
        p.setRowIndex(row);
        p.setColIndex(col);
        p.setSeatCode("R" + row + "C" + col);
        p.setSeatType(type);
        p.setIsAvailable(avail);
        p.setStatus("0");
        return p;
    }
}
