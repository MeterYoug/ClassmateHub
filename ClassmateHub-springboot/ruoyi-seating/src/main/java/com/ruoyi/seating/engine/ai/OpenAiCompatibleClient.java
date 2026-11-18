package com.ruoyi.seating.engine.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.seating.domain.SeatAiConfig;

/**
 * 兼容 OpenAI /v1/chat/completions 协议的轻量 HTTP 客户端。
 */
@Component
public class OpenAiCompatibleClient
{
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    public String chatCompletionJson(SeatAiConfig config, String plainApiKey, String systemPrompt, String userPrompt)
    {
        if (config == null || StringUtils.isBlank(config.getApiUrl()) || StringUtils.isBlank(config.getModelName()))
        {
            throw new ServiceException("AI 模型配置不完整，请检查 API 地址和模型名称");
        }
        if (StringUtils.isBlank(plainApiKey))
        {
            throw new ServiceException("AI 模型 API Key 不能为空");
        }

        String endpoint = normalizeChatCompletionsUrl(config.getApiUrl());
        String trimmedModel = config.getModelName().trim();
        boolean reasoningStyle = isLikelyReasoningModel(trimmedModel);
        int timeoutSec = resolveEffectiveTimeoutSeconds(config, reasoningStyle);

        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().fluentPut("role", "system").fluentPut("content", systemPrompt));
        messages.add(new JSONObject().fluentPut("role", "user").fluentPut("content", userPrompt));

        JSONObject body = new JSONObject();
        body.put("model", trimmedModel);
        body.put("messages", messages);
        boolean openAiReasoningStyle = isOpenAiReasoningOrGpt5Model(trimmedModel);
        if (config.getTemperature() != null && !openAiReasoningStyle)
        {
            body.put("temperature", config.getTemperature().doubleValue());
        }
        int effectiveMaxTokens = resolveEffectiveMaxTokens(config, reasoningStyle);
        if (effectiveMaxTokens > 0)
        {
            body.put(openAiReasoningStyle ? "max_completion_tokens" : "max_tokens", effectiveMaxTokens);
        }
        applyReasoningEffort(body, config);

        String provider = StringUtils.defaultIfBlank(config.getProvider(), "CUSTOM");
        String normalizedEffort = normalizeReasoningEffort(config.getReasoningEffort());
        long startMs = System.currentTimeMillis();
        log.info("开始调用 AI 排座大模型: provider={}, model={}, endpoint={}, reasoningEffort={}, maxTokens={}, timeout={}s",
                provider, trimmedModel, endpoint, normalizedEffort, effectiveMaxTokens, timeoutSec);

        String rawResponse = sendAdaptiveChatRequest(endpoint, plainApiKey.trim(), body, timeoutSec, true);
        long costMs = System.currentTimeMillis() - startMs;
        log.info("AI 排座大模型响应完成: provider={}, model={}, reasoningEffort={}, costMs={}ms, {}",
                provider, trimmedModel, normalizedEffort, costMs, summarizeCompletionMetrics(rawResponse));
        return extractJsonPayload(rawResponse);
    }

    public Map<String, Object> testConnection(SeatAiConfig config, String plainApiKey)
    {
        if (config == null || StringUtils.isBlank(config.getApiUrl()) || StringUtils.isBlank(config.getModelName()))
        {
            throw new ServiceException("请先填写完整的 API 地址与模型名称");
        }
        long start = System.currentTimeMillis();
        String endpoint = normalizeChatCompletionsUrl(config.getApiUrl());
        int timeoutSec = config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
                ? Math.min(config.getTimeoutSeconds(), 30) : 20;

        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().fluentPut("role", "user").fluentPut("content", "请仅回复 OK 两个字母"));

        JSONObject body = new JSONObject();
        String trimmedModel = config.getModelName().trim();
        body.put("model", trimmedModel);
        body.put("messages", messages);
        body.put(isOpenAiReasoningOrGpt5Model(trimmedModel) ? "max_completion_tokens" : "max_tokens", 256);
        applyReasoningEffort(body, config);

        String provider = StringUtils.defaultIfBlank(config.getProvider(), "CUSTOM");
        String normalizedEffort = normalizeReasoningEffort(config.getReasoningEffort());
        log.info("开始测试 AI 模型连接: provider={}, model={}, endpoint={}, reasoningEffort={}",
                provider, trimmedModel, endpoint, normalizedEffort);

        String rawResponse = sendAdaptiveChatRequest(endpoint, plainApiKey.trim(), body, timeoutSec);
        String content = extractTestReplyText(rawResponse);
        long latencyMs = System.currentTimeMillis() - start;
        String shortReply = content.length() > 100 ? content.substring(0, 100) : content;
        log.info("AI 模型连接测试成功: provider={}, model={}, reasoningEffort={}, latencyMs={}ms, reply={}",
                provider, trimmedModel, normalizedEffort, latencyMs, shortReply);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("latencyMs", latencyMs);
        result.put("model", config.getModelName());
        result.put("endpoint", endpoint);
        result.put("reply", shortReply);
        return result;
    }

    public static boolean isOpenAiReasoningOrGpt5Model(String modelName)
    {
        String lower = modelName == null ? "" : modelName.trim().toLowerCase();
        return lower.startsWith("o1")
                || lower.startsWith("o3")
                || lower.startsWith("o4")
                || lower.startsWith("gpt-5");
    }

    public static boolean isLikelyReasoningModel(String modelName)
    {
        if (isOpenAiReasoningOrGpt5Model(modelName))
        {
            return true;
        }
        String lower = modelName == null ? "" : modelName.trim().toLowerCase();
        return lower.contains("reasoner")
                || lower.contains("deepseek-r1")
                || lower.contains("deepseek-flash")
                || lower.contains("thinking")
                || lower.contains("qwq")
                || lower.contains("qwen3")
                || lower.contains("glm-z1")
                || lower.contains("glm-5")
                || lower.contains("glm-4.7")
                || lower.contains("glm-4.5")
                || lower.contains("doubao-seed")
                || lower.contains("kimi-k2")
                || lower.contains("kimi-k3")
                || lower.contains("mimo-v2");
    }

    public static String normalizeReasoningEffort(String effort)
    {
        if (StringUtils.isBlank(effort))
        {
            return "DEFAULT";
        }
        String upper = effort.trim().toUpperCase();
        return switch (upper)
        {
            case "OFF", "LOW", "MEDIUM", "HIGH", "DEFAULT" -> upper;
            default -> "DEFAULT";
        };
    }

    public static void applyReasoningEffort(JSONObject body, SeatAiConfig config)
    {
        if (body == null || config == null)
        {
            return;
        }
        String effort = normalizeReasoningEffort(config.getReasoningEffort());
        if ("DEFAULT".equals(effort))
        {
            return;
        }
        String provider = config.getProvider() == null ? "" : config.getProvider().trim().toUpperCase();
        String urlLower = config.getApiUrl() == null ? "" : config.getApiUrl().trim().toLowerCase();
        String modelLower = config.getModelName() == null ? "" : config.getModelName().trim().toLowerCase();

        boolean isZhipu = "ZHIPU".equals(provider) || urlLower.contains("bigmodel.cn") || urlLower.contains("z.ai");
        boolean isDashScope = "QWEN".equals(provider) || urlLower.contains("dashscope.aliyuncs.com");
        boolean isVolcengine = "BYTEDANCE".equals(provider) || urlLower.contains("volces.com");
        boolean isDeepSeek = "DEEPSEEK".equals(provider) || urlLower.contains("deepseek.com");
        boolean isSiliconFlow = "SILICONFLOW".equals(provider) || urlLower.contains("siliconflow.");

        if ("OFF".equals(effort))
        {
            if (isZhipu)
            {
                if (modelLower.startsWith("glm-5.3"))
                {
                    body.put("thinking", new JSONObject().fluentPut("type", "enabled"));
                    body.put("reasoning_effort", "low");
                }
                else
                {
                    body.put("thinking", new JSONObject().fluentPut("type", "disabled"));
                }
            }
            else if (isDashScope || (isSiliconFlow && modelLower.contains("qwen3")))
            {
                body.put("enable_thinking", false);
            }
            else if (isVolcengine || isDeepSeek)
            {
                body.put("thinking", new JSONObject().fluentPut("type", "disabled"));
            }
            else if (modelLower.startsWith("o1") || modelLower.startsWith("o3") || modelLower.startsWith("o4"))
            {
                body.put("reasoning_effort", "low");
            }
            else if (modelLower.startsWith("gpt-5"))
            {
                body.put("reasoning_effort", "minimal");
            }
            else
            {
                body.put("thinking", new JSONObject().fluentPut("type", "disabled"));
            }
            return;
        }

        String effortLower = effort.toLowerCase();
        if (isZhipu)
        {
            body.put("thinking", new JSONObject().fluentPut("type", "enabled"));
            if (modelLower.startsWith("glm-5.3"))
            {
                body.put("reasoning_effort", "LOW".equals(effort) ? "low" : "high");
            }
        }
        else if (isDashScope)
        {
            body.put("enable_thinking", true);
            body.put("reasoning_effort", effortLower);
        }
        else
        {
            body.put("reasoning_effort", effortLower);
        }
    }

    public static int resolveEffectiveMaxTokens(SeatAiConfig config, boolean reasoningStyle)
    {
        int configured = config != null && config.getMaxTokens() != null && config.getMaxTokens() > 0
                ? config.getMaxTokens() : 0;
        String effort = normalizeReasoningEffort(config == null ? null : config.getReasoningEffort());
        if ("OFF".equals(effort))
        {
            return configured;
        }
        boolean needsHighBudget = "HIGH".equals(effort) || reasoningStyle;
        if (needsHighBudget && configured > 0 && configured <= 8192)
        {
            return 16384;
        }
        return configured;
    }

    public static int resolveEffectiveTimeoutSeconds(SeatAiConfig config, boolean reasoningStyle)
    {
        int configured = config != null && config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
                ? config.getTimeoutSeconds() : 120;
        String effort = normalizeReasoningEffort(config == null ? null : config.getReasoningEffort());
        if ("OFF".equals(effort))
        {
            return configured;
        }
        boolean needsExtendedTimeout = "HIGH".equals(effort) || "MEDIUM".equals(effort) || reasoningStyle;
        if (needsExtendedTimeout && configured <= 60)
        {
            return 120;
        }
        return configured;
    }

    protected String sendAdaptiveChatRequest(String endpoint, String apiKey, JSONObject initialBody, int timeoutSec)
    {
        return sendAdaptiveChatRequest(endpoint, apiKey, initialBody, timeoutSec, false);
    }

    protected String sendAdaptiveChatRequest(String endpoint, String apiKey, JSONObject initialBody,
            int timeoutSec, boolean expandOnTruncation)
    {
        JSONObject currentBody = initialBody.clone();
        int currentTimeoutSec = timeoutSec;
        boolean tokenLimitRemovedByError = false;
        for (int attempt = 0; attempt < 4; attempt++)
        {
            try
            {
                String raw = sendRequest(endpoint, apiKey, currentBody.toJSONString(), currentTimeoutSec);
                String normalized = Boolean.TRUE.equals(currentBody.getBoolean("stream"))
                        ? aggregateSseToChatCompletionJson(raw)
                        : raw;
                if (expandOnTruncation && !tokenLimitRemovedByError && isTruncatedBeforeJsonComplete(normalized))
                {
                    if (expandTokenBudget(currentBody))
                    {
                        currentTimeoutSec = Math.max(currentTimeoutSec, 150);
                        log.info("检测到模型输出因 Token 配额截断，自动扩容重试 (attempt={}): model={}, newLimit={}",
                                attempt + 1, currentBody.getString("model"), resolveCurrentTokenLimit(currentBody));
                        continue;
                    }
                }
                return normalized;
            }
            catch (ServiceException ex)
            {
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                boolean adjusted = false;
                if (msg.contains("max_completion_tokens") && currentBody.containsKey("max_tokens"))
                {
                    Object val = currentBody.remove("max_tokens");
                    currentBody.put("max_completion_tokens", val);
                    adjusted = true;
                }
                else if (msg.contains("max_completion_tokens") && currentBody.containsKey("max_completion_tokens"))
                {
                    currentBody.remove("max_completion_tokens");
                    tokenLimitRemovedByError = true;
                    adjusted = true;
                }
                else if (msg.contains("max_tokens") && currentBody.containsKey("max_tokens"))
                {
                    currentBody.remove("max_tokens");
                    tokenLimitRemovedByError = true;
                    adjusted = true;
                }
                if (msg.contains("temperature") && currentBody.containsKey("temperature"))
                {
                    currentBody.remove("temperature");
                    adjusted = true;
                }
                boolean streamRequiredMsg = msg.contains("only supports stream")
                        || msg.contains("stream mode")
                        || msg.contains("stream must be true")
                        || msg.contains("non-streaming")
                        || msg.contains("non-stream");
                if (streamRequiredMsg && !Boolean.TRUE.equals(currentBody.getBoolean("stream")))
                {
                    if (!Boolean.TRUE.equals(currentBody.getBoolean("enable_thinking")))
                    {
                        currentBody.remove("enable_thinking");
                    }
                    currentBody.put("stream", true);
                    adjusted = true;
                }
                else if (msg.contains("enable_thinking"))
                {
                    if (currentBody.containsKey("enable_thinking"))
                    {
                        currentBody.remove("enable_thinking");
                    }
                    else
                    {
                        currentBody.put("enable_thinking", false);
                    }
                    adjusted = true;
                }
                if (msg.contains("reasoning_effort") && currentBody.containsKey("reasoning_effort"))
                {
                    currentBody.remove("reasoning_effort");
                    adjusted = true;
                }
                if (msg.contains("thinking") && !msg.contains("enable_thinking") && currentBody.containsKey("thinking"))
                {
                    currentBody.remove("thinking");
                    adjusted = true;
                }
                if (!adjusted && isGenericUnsupportedParamError(msg)
                        && (currentBody.containsKey("reasoning_effort")
                                || currentBody.containsKey("thinking")
                                || currentBody.containsKey("enable_thinking")))
                {
                    currentBody.remove("reasoning_effort");
                    currentBody.remove("thinking");
                    currentBody.remove("enable_thinking");
                    adjusted = true;
                }
                if (!adjusted)
                {
                    throw ex;
                }
                log.info("根据大模型接口参数约束自动调整请求参数重试 (attempt={}): model={}", attempt + 1, currentBody.getString("model"));
            }
        }
        String raw = sendRequest(endpoint, apiKey, currentBody.toJSONString(), currentTimeoutSec);
        return Boolean.TRUE.equals(currentBody.getBoolean("stream")) ? aggregateSseToChatCompletionJson(raw) : raw;
    }

    private static boolean isGenericUnsupportedParamError(String lowerMsg)
    {
        if (StringUtils.isBlank(lowerMsg))
        {
            return false;
        }
        return lowerMsg.contains("unknown parameter")
                || lowerMsg.contains("unknown field")
                || lowerMsg.contains("unrecognized request argument")
                || lowerMsg.contains("extra inputs are not permitted")
                || lowerMsg.contains("extra_forbidden")
                || lowerMsg.contains("unexpected field")
                || lowerMsg.contains("invalid combination");
    }

    public static boolean isTruncatedBeforeJsonComplete(String rawResponse)
    {
        try
        {
            JSONObject json = JSON.parseObject(rawResponse);
            JSONArray choices = json == null ? null : json.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                return false;
            }
            JSONObject firstChoice = choices.getJSONObject(0);
            if (firstChoice == null)
            {
                return false;
            }
            String finishReason = firstChoice.getString("finish_reason");
            JSONObject message = firstChoice.getJSONObject("message");
            String content = message == null ? null : message.getString("content");
            String reasoning = message == null ? null : message.getString("reasoning_content");
            String strippedContent = stripThinkingBlocks(content);

            boolean lengthStopped = "length".equalsIgnoreCase(finishReason)
                    || "max_tokens".equalsIgnoreCase(finishReason);
            if (StringUtils.isBlank(strippedContent))
            {
                return lengthStopped
                        || StringUtils.isNotBlank(reasoning)
                        || (content != null && content.contains("<think>"));
            }
            if (lengthStopped)
            {
                int firstBrace = strippedContent.indexOf('{');
                int lastBrace = strippedContent.lastIndexOf('}');
                if (firstBrace < 0 || lastBrace <= firstBrace)
                {
                    return true;
                }
                try
                {
                    JSONObject parsed = JSON.parseObject(strippedContent.substring(firstBrace, lastBrace + 1));
                    return parsed == null || (!parsed.containsKey("assignments") && !parsed.containsKey("data"));
                }
                catch (Exception e)
                {
                    return true;
                }
            }
            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static boolean expandTokenBudget(JSONObject body)
    {
        String tokenKey = body.containsKey("max_completion_tokens") ? "max_completion_tokens"
                : (body.containsKey("max_tokens") ? "max_tokens" : null);
        if (tokenKey == null)
        {
            return false;
        }
        int current = body.getIntValue(tokenKey, 0);
        int maxCap = 32768;
        if (current <= 0 || current >= maxCap)
        {
            return false;
        }
        int next = current < 16384 ? 16384 : maxCap;
        body.put(tokenKey, next);
        return true;
    }

    private static int resolveCurrentTokenLimit(JSONObject body)
    {
        if (body.containsKey("max_completion_tokens"))
        {
            return body.getIntValue("max_completion_tokens", 0);
        }
        return body.getIntValue("max_tokens", 0);
    }

    public static String stripThinkingBlocks(String text)
    {
        if (StringUtils.isBlank(text))
        {
            return "";
        }
        String stripped = text.replaceAll("(?s)<think>.*?</think>", "");
        stripped = stripped.replaceAll("(?s)<think>.*$", "");
        return stripped.trim();
    }

    public static String aggregateSseToChatCompletionJson(String rawSse)
    {
        if (StringUtils.isBlank(rawSse))
        {
            throw new ServiceException("大模型流式响应为空");
        }
        String trimmed = rawSse.trim();
        if (trimmed.startsWith("{"))
        {
            return trimmed;
        }
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        String finishReason = null;
        JSONObject lastUsage = null;
        for (String line : rawSse.split("\\r?\\n"))
        {
            String t = line.trim();
            if (!t.startsWith("data:"))
            {
                continue;
            }
            String payload = t.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload))
            {
                continue;
            }
            try
            {
                JSONObject chunk = JSON.parseObject(payload);
                if (chunk != null && chunk.getJSONObject("usage") != null)
                {
                    lastUsage = chunk.getJSONObject("usage");
                }
                JSONArray choices = chunk == null ? null : chunk.getJSONArray("choices");
                if (choices != null && !choices.isEmpty())
                {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    String fr = firstChoice == null ? null : firstChoice.getString("finish_reason");
                    if (StringUtils.isNotBlank(fr))
                    {
                        finishReason = fr;
                    }
                    JSONObject delta = firstChoice == null ? null : firstChoice.getJSONObject("delta");
                    if (delta != null)
                    {
                        String c = delta.getString("content");
                        if (c != null)
                        {
                            contentBuilder.append(c);
                        }
                        String r = delta.getString("reasoning_content");
                        if (r != null)
                        {
                            reasoningBuilder.append(r);
                        }
                    }
                }
            }
            catch (Exception ignored)
            {
            }
        }
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", contentBuilder.toString());
        if (!reasoningBuilder.isEmpty())
        {
            message.put("reasoning_content", reasoningBuilder.toString());
        }
        JSONObject choiceObj = new JSONObject().fluentPut("message", message);
        if (finishReason != null)
        {
            choiceObj.put("finish_reason", finishReason);
        }
        JSONObject synthetic = new JSONObject();
        synthetic.put("choices", new JSONArray().fluentAdd(choiceObj));
        if (lastUsage != null)
        {
            synthetic.put("usage", lastUsage);
        }
        return synthetic.toJSONString();
    }

    public static String summarizeCompletionMetrics(String rawResponse)
    {
        if (StringUtils.isBlank(rawResponse))
        {
            return "usage=-, finishReason=-";
        }
        try
        {
            JSONObject json = JSON.parseObject(rawResponse);
            if (json == null)
            {
                return "usage=-, finishReason=-";
            }
            String finishReason = "-";
            int reasoningChars = 0;
            int contentChars = 0;
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty() && choices.getJSONObject(0) != null)
            {
                JSONObject first = choices.getJSONObject(0);
                finishReason = StringUtils.defaultIfBlank(first.getString("finish_reason"), "-");
                JSONObject msg = first.getJSONObject("message");
                if (msg != null)
                {
                    String rc = msg.getString("reasoning_content");
                    String rawContent = msg.getString("content");
                    String stripped = stripThinkingBlocks(rawContent);
                    contentChars = stripped.length();
                    if (StringUtils.isNotBlank(rc))
                    {
                        reasoningChars = rc.length();
                    }
                    else if (rawContent != null && rawContent.length() > stripped.length())
                    {
                        reasoningChars = rawContent.length() - stripped.length();
                    }
                }
            }
            JSONObject usage = json.getJSONObject("usage");
            if (usage == null)
            {
                return String.format("promptTokens=-, completionTokens=-, reasoningTokens=-, reasoningChars=%d, contentChars=%d, finishReason=%s",
                        reasoningChars, contentChars, finishReason);
            }
            Integer promptTokens = usage.getInteger("prompt_tokens");
            Integer completionTokens = usage.getInteger("completion_tokens");
            Integer reasoningTokens = null;
            JSONObject details = usage.getJSONObject("completion_tokens_details");
            if (details != null)
            {
                reasoningTokens = details.getInteger("reasoning_tokens");
            }
            if (reasoningTokens == null)
            {
                reasoningTokens = usage.getInteger("reasoning_tokens");
            }
            return String.format("promptTokens=%s, completionTokens=%s, reasoningTokens=%s, reasoningChars=%d, contentChars=%d, finishReason=%s",
                    promptTokens != null ? promptTokens : "-",
                    completionTokens != null ? completionTokens : "-",
                    reasoningTokens != null ? reasoningTokens : "-",
                    reasoningChars,
                    contentChars,
                    finishReason);
        }
        catch (Exception e)
        {
            return "usage=parse_skip";
        }
    }

    public static String normalizeChatCompletionsUrl(String apiUrl)
    {
        String trimmed = apiUrl == null ? "" : apiUrl.trim();
        while (trimmed.endsWith("/"))
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/chat/completions"))
        {
            return trimmed;
        }
        if (trimmed.endsWith("/v1") || trimmed.matches(".*/v\\d+$"))
        {
            return trimmed + "/chat/completions";
        }
        try
        {
            URI uri = URI.create(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.isEmpty() || "/".equals(path))
            {
                if (host.endsWith("bigmodel.cn") || host.endsWith("z.ai"))
                {
                    return trimmed + "/api/paas/v4/chat/completions";
                }
                if (host.endsWith("volces.com"))
                {
                    return trimmed + "/api/v3/chat/completions";
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return trimmed + "/v1/chat/completions";
    }

    public static String extractJsonPayload(String rawResponse)
    {
        JSONObject firstChoice = parseFirstChoice(rawResponse);
        String finishReason = firstChoice.getString("finish_reason");
        JSONObject message = firstChoice.getJSONObject("message");
        String rawContent = message == null ? null : message.getString("content");
        String reasoning = message == null ? null : message.getString("reasoning_content");

        String text = stripThinkingBlocks(rawContent);
        boolean lengthStopped = "length".equalsIgnoreCase(finishReason)
                || "max_tokens".equalsIgnoreCase(finishReason);
        if (StringUtils.isBlank(text))
        {
            if (lengthStopped || StringUtils.isNotBlank(reasoning)
                    || (rawContent != null && rawContent.contains("<think>")))
            {
                throw new ServiceException("大模型思考过程耗尽了输出 Token 配额，未能输出最终排座 JSON，请在「AI 模型配置」中调大「最大Token」（建议 16384 以上）或切换非深度思考模型");
            }
            throw new ServiceException("大模型返回消息内容为空");
        }
        if (text.startsWith("```json"))
        {
            text = text.substring(7).trim();
        }
        else if (text.startsWith("```"))
        {
            text = text.substring(3).trim();
        }
        if (text.endsWith("```"))
        {
            text = text.substring(0, text.length() - 3).trim();
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace)
        {
            String candidate = text.substring(firstBrace, lastBrace + 1);
            if (lengthStopped)
            {
                try
                {
                    JSON.parseObject(candidate);
                }
                catch (Exception e)
                {
                    throw new ServiceException("大模型排座 JSON 输出因达到最大 Token 限制被截断，请在「AI 模型配置」中调大「最大Token」（建议 16384 以上）");
                }
            }
            return candidate;
        }
        if (lengthStopped)
        {
            throw new ServiceException("大模型输出因达到最大 Token 限制被截断，请在「AI 模型配置」中调大「最大Token」（建议 16384 以上）");
        }
        throw new ServiceException("AI 未返回有效的 JSON 排座结构");
    }

    public static String extractTestReplyText(String rawResponse)
    {
        JSONObject message = parseFirstChoice(rawResponse).getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        if (StringUtils.isNotBlank(content))
        {
            String stripped = stripThinkingBlocks(content);
            if (StringUtils.isNotBlank(stripped))
            {
                return stripped;
            }
            if (content.contains("<think>"))
            {
                return "OK（推理模型已响应）";
            }
        }
        String reasoning = message == null ? null : message.getString("reasoning_content");
        if (StringUtils.isNotBlank(reasoning))
        {
            return "OK（推理模型已响应）";
        }
        throw new ServiceException("大模型返回消息内容为空");
    }

    private static JSONObject parseFirstChoice(String rawResponse)
    {
        JSONObject json;
        try
        {
            json = JSON.parseObject(rawResponse);
        }
        catch (Exception e)
        {
            throw new ServiceException("无法解析大模型接口响应 JSON");
        }
        if (json == null)
        {
            throw new ServiceException("大模型接口响应为空");
        }
        if (json.containsKey("error"))
        {
            JSONObject err = json.getJSONObject("error");
            String msg = err != null ? err.getString("message") : json.getString("error");
            throw new ServiceException("大模型接口返回错误：" + StringUtils.defaultIfBlank(msg, "未知错误"));
        }
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty() || choices.getJSONObject(0) == null)
        {
            throw new ServiceException("大模型响应中缺少 choices 内容");
        }
        return choices.getJSONObject(0);
    }

    public List<String> listAvailableModels(SeatAiConfig config, String plainApiKey)
    {
        if (config == null || StringUtils.isBlank(config.getApiUrl()))
        {
            throw new ServiceException("请先填写 API 地址");
        }
        String modelsEndpoint = normalizeModelsUrl(config.getApiUrl());
        String key = plainApiKey == null ? "" : plainApiKey.trim();
        try
        {
            List<String> openAiModels = parseOpenAiModelsList(sendGetRequest(modelsEndpoint, key, 15));
            if (!openAiModels.isEmpty())
            {
                return openAiModels;
            }
        }
        catch (ServiceException ex)
        {
            if (!isLocalOllamaConfig(config))
            {
                throw ex;
            }
        }
        if (isLocalOllamaConfig(config))
        {
            return fetchOllamaNativeTags(config.getApiUrl());
        }
        return new ArrayList<>();
    }

    private List<String> parseOpenAiModelsList(String rawResponse)
    {
        JSONObject json;
        try
        {
            json = JSON.parseObject(rawResponse);
        }
        catch (Exception e)
        {
            throw new ServiceException("无法解析模型列表响应 JSON");
        }
        JSONArray data = json == null ? null : json.getJSONArray("data");
        List<String> models = new ArrayList<>();
        if (data != null)
        {
            for (int i = 0; i < data.size(); i++)
            {
                JSONObject item = data.getJSONObject(i);
                String id = item == null ? null : StringUtils.trim(item.getString("id"));
                if (StringUtils.isNotBlank(id) && isLikelyChatModel(id))
                {
                    models.add(id);
                }
            }
        }
        return models;
    }

    private List<String> fetchOllamaNativeTags(String apiUrl)
    {
        List<String> models = new ArrayList<>();
        try
        {
            URI uri = URI.create(apiUrl.trim());
            String base = uri.getScheme() + "://" + uri.getAuthority() + "/api/tags";
            String raw = sendGetRequest(base, "", 10);
            JSONObject obj = JSON.parseObject(raw);
            JSONArray arr = obj == null ? null : obj.getJSONArray("models");
            if (arr != null)
            {
                for (int i = 0; i < arr.size(); i++)
                {
                    JSONObject m = arr.getJSONObject(i);
                    String name = m == null ? null : StringUtils.trim(m.getString("name"));
                    if (StringUtils.isNotBlank(name) && isLikelyChatModel(name))
                    {
                        models.add(name);
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return models;
    }

    private static boolean isLocalOllamaConfig(SeatAiConfig config)
    {
        if ("OLLAMA".equalsIgnoreCase(config.getProvider()))
        {
            return true;
        }
        String url = config.getApiUrl() == null ? "" : config.getApiUrl().toLowerCase();
        return url.contains(":11434");
    }

    public static String normalizeModelsUrl(String apiUrl)
    {
        String chatUrl = normalizeChatCompletionsUrl(apiUrl);
        String modelsUrl = chatUrl.endsWith("/chat/completions")
                ? chatUrl.substring(0, chatUrl.length() - "/chat/completions".length()) + "/models"
                : chatUrl + "/models";
        String lower = modelsUrl.toLowerCase();
        if ((lower.contains("siliconflow.cn") || lower.contains("siliconflow.com")) && !lower.contains("sub_type="))
        {
            return modelsUrl + "?sub_type=chat";
        }
        return modelsUrl;
    }

    private static boolean isLikelyChatModel(String modelId)
    {
        String lower = modelId.toLowerCase();
        return !lower.contains("embedding")
                && !lower.contains("rerank")
                && !lower.contains("-asr")
                && !lower.contains("-tts")
                && !lower.contains("whisper")
                && !lower.contains("moderation")
                && !lower.contains("dall-e")
                && !lower.contains("cogview")
                && !lower.contains("seedream")
                && !lower.contains("seedance")
                && !lower.contains("stable-diffusion")
                && !lower.contains("flux")
                && !lower.contains("cosyvoice")
                && !lower.contains("sensevoice")
                && !lower.contains("paraformer")
                && !lower.contains("bge-");
    }

    protected String sendRequest(String endpoint, String apiKey, String jsonBody, int timeoutSeconds)
    {
        try
        {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(15, timeoutSeconds)))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300)
            {
                log.warn("AI 接口非 2xx 响应: status={}, url={}, body={}", status, endpoint, response.body());
                throw new ServiceException(formatFriendlyHttpError(status, response.body()));
            }
            return response.body();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (java.net.http.HttpTimeoutException e)
        {
            throw new ServiceException("大模型响应超时（超过 " + timeoutSeconds + " 秒）");
        }
        catch (Exception e)
        {
            log.error("调用 AI 接口异常: url={}, err={}", endpoint, e.getMessage());
            throw new ServiceException("连接大模型服务失败：" + e.getMessage());
        }
    }

    protected String sendGetRequest(String endpoint, String apiKey, int timeoutSeconds)
    {
        try
        {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(10, timeoutSeconds)))
                    .build();
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/json")
                    .GET();
            if (StringUtils.isNotBlank(apiKey))
            {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300)
            {
                log.warn("AI 模型列表接口非 2xx 响应: status={}, url={}, body={}", status, endpoint, response.body());
                throw new ServiceException(formatFriendlyHttpError(status, response.body()));
            }
            return response.body();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("获取在线模型列表失败：" + e.getMessage());
        }
    }

    public static String formatFriendlyHttpError(int status, String body)
    {
        String rawMsg = extractRawErrorMessage(body);
        String lower = rawMsg.toLowerCase();
        if (status == 402 || lower.contains("insufficient") || lower.contains("balance") || lower.contains("arrears"))
        {
            return "大模型账户可用余额不足，请前往对应开放平台充值或核对套餐地址（HTTP " + status + ": " + rawMsg + "）";
        }
        if (status == 401 || lower.contains("invalid api key") || lower.contains("invalid_key") || lower.contains("unauthorized"))
        {
            return "API 密钥无效或与当前接口地址不匹配，请核对 API Key 与接口地址（HTTP " + status + ": " + rawMsg + "）";
        }
        if (lower.contains("unsupported model") || lower.contains("model_not_found") || lower.contains("model not exist") || lower.contains("does not exist"))
        {
            return "当前供应商接口不支持所选模型，请在「AI 模型配置」中点击「获取可用模型」更换模型（HTTP " + status + ": " + rawMsg + "）";
        }
        if (status == 429 || lower.contains("rate limit") || lower.contains("too many requests"))
        {
            return "请求触发大模型平台速率或并发限制，请稍后重试（HTTP 429: " + rawMsg + "）";
        }
        return "大模型请求失败（HTTP " + status + (StringUtils.isNotBlank(rawMsg) ? ": " + rawMsg : "") + "）";
    }

    private static String extractRawErrorMessage(String body)
    {
        if (StringUtils.isBlank(body))
        {
            return "";
        }
        try
        {
            JSONObject obj = JSON.parseObject(body);
            if (obj != null && obj.containsKey("error"))
            {
                Object errObj = obj.get("error");
                if (errObj instanceof JSONObject errJson && StringUtils.isNotBlank(errJson.getString("message")))
                {
                    return errJson.getString("message");
                }
                return errObj.toString();
            }
        }
        catch (Exception ignored)
        {
        }
        String trimmed = body.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }
}
