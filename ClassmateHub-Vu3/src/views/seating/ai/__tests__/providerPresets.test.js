import test from "node:test"
import assert from "node:assert/strict"
import {
  AI_PROVIDER_OPTIONS,
  REASONING_EFFORT_OPTIONS,
  getProviderPreset,
  getReasoningEffortOption,
  getReasoningEffortLabel,
  applyReasoningEffortLinkage,
  buildDefaultConfigName,
  getModelOptionList,
  getModelSuggestions,
  createEmptyAiConfigForm,
  applyProviderSwitch
} from "../providerPresets.js"

test("includes ByteDance, Zhipu, Kimi, and Xiaomi provider presets with non-retired models", () => {
  const values = AI_PROVIDER_OPTIONS.map(item => item.value)
  assert.ok(values.includes("BYTEDANCE"))
  assert.ok(values.includes("ZHIPU"))
  assert.ok(values.includes("KIMI"))
  assert.ok(values.includes("XIAOMI"))

  assert.equal(getProviderPreset("BYTEDANCE").defaultUrl, "https://ark.cn-beijing.volces.com/api/v3")
  assert.equal(getProviderPreset("ZHIPU").defaultUrl, "https://open.bigmodel.cn/api/paas/v4")
  assert.equal(getProviderPreset("ZHIPU").defaultMaxTokens, 4095)
  assert.equal(getProviderPreset("KIMI").defaultUrl, "https://api.moonshot.cn/v1")
  assert.equal(getProviderPreset("KIMI").defaultTemperature, 1.0)
  assert.ok(getProviderPreset("KIMI").models.includes("kimi-k3.1-pro"))
  assert.ok(!getProviderPreset("KIMI").models.includes("moonshot-v1-8k"))
  assert.ok(!getProviderPreset("QWEN").models.includes("qwq-plus"))
  assert.equal(getProviderPreset("XIAOMI").defaultUrl, "https://api.xiaomimimo.com/v1")
})

test("switching provider clears previous input fields and applies provider parameter caps", () => {
  const dirtyForm = {
    configId: 9,
    provider: "DEEPSEEK",
    configName: "DeepSeek 排座模型",
    apiUrl: "https://api.deepseek.com/v1",
    apiKey: "sk-****1234",
    modelName: "deepseek-chat",
    temperature: 0.2,
    maxTokens: 8192,
    timeoutSeconds: 90,
    isDefault: "1",
    status: "0",
    remark: "旧供应商备注"
  }

  const switchedXiaomi = applyProviderSwitch(dirtyForm, "XIAOMI")
  assert.equal(switchedXiaomi.configId, 9)
  assert.equal(switchedXiaomi.provider, "XIAOMI")
  assert.equal(switchedXiaomi.configName, "")
  assert.equal(switchedXiaomi.apiUrl, "https://api.xiaomimimo.com/v1")
  assert.equal(switchedXiaomi.apiKey, "")
  assert.equal(switchedXiaomi.modelName, "")
  assert.equal(switchedXiaomi.remark, "")
  assert.equal(switchedXiaomi.temperature, 0.3)
  assert.equal(switchedXiaomi.maxTokens, 8192)
  assert.equal(switchedXiaomi.timeoutSeconds, 120)

  const switchedZhipu = applyProviderSwitch(switchedXiaomi, "ZHIPU")
  assert.equal(switchedZhipu.maxTokens, 4095)
  assert.equal(switchedZhipu.timeoutSeconds, 120)

  const switchedKimi = applyProviderSwitch(switchedZhipu, "KIMI")
  assert.equal(switchedKimi.apiUrl, "https://api.moonshot.cn/v1")
  assert.equal(switchedKimi.temperature, 1.0)

  const switchedCustom = applyProviderSwitch(switchedKimi, "CUSTOM")
  assert.equal(switchedCustom.apiUrl, "")
  assert.equal(switchedCustom.configName, "")
  assert.equal(switchedCustom.modelName, "")
})

test("model suggestions and default config name generation work per provider", () => {
  const mimoModels = getModelSuggestions("XIAOMI", "")
  assert.deepEqual(mimoModels.map(item => item.value), [
    "mimo-v2.6-flash",
    "mimo-v2.6-pro",
    "mimo-v2.6-pro-ultraspeed"
  ])
  const remoteOverride = getModelSuggestions("XIAOMI", "pro", ["mimo-v2.6-flash", "mimo-v2.6-pro"])
  assert.deepEqual(remoteOverride, [{ value: "mimo-v2.6-pro" }])
  // When input already equals one of the exact fetched models, keep all fetched models visible
  const fullWhenExactSelected = getModelSuggestions("DEEPSEEK", "deepseek-flash", ["deepseek-flash", "deepseek-chat"])
  assert.deepEqual(fullWhenExactSelected.map(item => item.value), ["deepseek-flash", "deepseek-chat"])
  assert.deepEqual(getModelOptionList("DEEPSEEK", ["deepseek-flash", "deepseek-chat"], "deepseek-flash"), ["deepseek-flash", "deepseek-chat"])
  assert.equal(buildDefaultConfigName("BYTEDANCE", "doubao-seed-2-1-pro-260915"), "字节豆包 - doubao-seed-2-1-pro-260915")
})

test("reasoning effort options default cleanly and link HIGH effort to safe token/timeout floors", () => {
  assert.deepEqual(
    REASONING_EFFORT_OPTIONS.map(item => item.value),
    ["DEFAULT", "OFF", "LOW", "MEDIUM", "HIGH"]
  )
  assert.equal(createEmptyAiConfigForm().reasoningEffort, "DEFAULT")
  assert.equal(getReasoningEffortLabel(null, true), "默认")
  assert.equal(getReasoningEffortOption("low").shortLabel, "轻量思考")

  const deepseekForm = {
    ...applyProviderSwitch({}, "DEEPSEEK"),
    maxTokens: 4096,
    timeoutSeconds: 60
  }
  const linkedLow = applyReasoningEffortLinkage(deepseekForm, "LOW")
  assert.equal(linkedLow.reasoningEffort, "LOW")
  assert.equal(linkedLow.maxTokens, 16384)
  assert.equal(linkedLow.timeoutSeconds, 120)

  const linkedOff = applyReasoningEffortLinkage(deepseekForm, "OFF")
  assert.equal(linkedOff.reasoningEffort, "OFF")
  assert.equal(linkedOff.maxTokens, 4096)
  assert.equal(linkedOff.timeoutSeconds, 60)

  const linkedHigh = applyReasoningEffortLinkage(deepseekForm, "HIGH")
  assert.equal(linkedHigh.reasoningEffort, "HIGH")
  assert.equal(linkedHigh.maxTokens, 16384)
  assert.equal(linkedHigh.timeoutSeconds, 120)

  // Zhipu keeps its 4095 preset cap when switching to HIGH in form
  const zhipuForm = {
    ...applyProviderSwitch({}, "ZHIPU"),
    timeoutSeconds: 60
  }
  const zhipuHigh = applyReasoningEffortLinkage(zhipuForm, "HIGH")
  assert.equal(zhipuHigh.maxTokens, 4095)
  assert.equal(zhipuHigh.timeoutSeconds, 120)

  // Switching provider resets reasoningEffort to DEFAULT
  const resetBack = applyProviderSwitch(linkedHigh, "OPENAI")
  assert.equal(resetBack.reasoningEffort, "DEFAULT")
})
