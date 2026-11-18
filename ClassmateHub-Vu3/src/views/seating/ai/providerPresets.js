export const AI_PROVIDER_OPTIONS = [
  {
    value: "DEEPSEEK",
    label: "DeepSeek",
    shortLabel: "DeepSeek",
    defaultUrl: "https://api.deepseek.com/v1",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入 DeepSeek API Key（sk-...）",
    urlHelp: "官方兼容地址：https://api.deepseek.com/v1",
    modelHelp: "建议填入 API Key 后点击右侧「获取可用模型」核实实时在线模型。",
    models: ["deepseek-chat", "deepseek-reasoner"]
  },
  {
    value: "BYTEDANCE",
    label: "字节跳动（豆包 / 火山方舟）",
    shortLabel: "字节豆包",
    defaultUrl: "https://ark.cn-beijing.volces.com/api/v3",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入火山方舟 API Key",
    urlHelp: "火山方舟标准地址：https://ark.cn-beijing.volces.com/api/v3（Coding Plan 套餐可改为 .../api/coding/v3）",
    modelHelp: "火山方舟模型 ID 含版本日期后缀，建议点击右侧「获取可用模型」拉取当前账号已开通模型，或直接填入推理接入点 ID（ep-xxxx）。",
    models: [
      "doubao-seed-2-1-pro-260915",
      "doubao-seed-2-1-lite-260915",
      "doubao-seed-code"
    ]
  },
  {
    value: "ZHIPU",
    label: "智谱 AI（BigModel / GLM）",
    shortLabel: "智谱 AI",
    defaultUrl: "https://open.bigmodel.cn/api/paas/v4",
    defaultTemperature: 0.3,
    defaultMaxTokens: 4095,
    requiresApiKey: true,
    keyPlaceholder: "请输入智谱开放平台 API Key",
    urlHelp: "智谱开放平台标准地址：https://open.bigmodel.cn/api/paas/v4（Coding 套餐可改为 .../api/coding/paas/v4）",
    models: ["glm-5.3-flash", "glm-5.3", "glm-4.7-flash", "glm-4.7", "glm-4-flash"]
  },
  {
    value: "KIMI",
    label: "Kimi（月之暗面）",
    shortLabel: "Kimi",
    defaultUrl: "https://api.moonshot.cn/v1",
    defaultTemperature: 1.0,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入 Kimi 开放平台 API Key（sk-...）",
    urlHelp: "月之暗面官方地址：https://api.moonshot.cn/v1（旧版 moonshot-v1 / kimi-k2 已下线，推荐 K3 系列或点击「获取可用模型」）",
    models: ["kimi-k3.1-pro", "kimi-k3.1-pro-highspeed", "kimi-k3"]
  },
  {
    value: "XIAOMI",
    label: "小米（MiMo 大模型）",
    shortLabel: "小米 MiMo",
    defaultUrl: "https://api.xiaomimimo.com/v1",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入小米 MiMo 开放平台 API Key（sk-... / tp-...）",
    urlHelp: "按量付费：https://api.xiaomimimo.com/v1；Token Plan 套餐请填 https://token-plan-cn.xiaomimimo.com/v1",
    models: ["mimo-v2.6-flash", "mimo-v2.6-pro", "mimo-v2.6-pro-ultraspeed"]
  },
  {
    value: "QWEN",
    label: "通义千问（阿里云百炼）",
    shortLabel: "通义千问",
    defaultUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入阿里云百炼 API Key（sk-...）",
    urlHelp: "阿里云百炼兼容地址：https://dashscope.aliyuncs.com/compatible-mode/v1",
    models: ["qwen-plus", "qwen-max", "qwen-turbo", "qwen-flash"]
  },
  {
    value: "SILICONFLOW",
    label: "硅基流动（SiliconFlow）",
    shortLabel: "硅基流动",
    defaultUrl: "https://api.siliconflow.cn/v1",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入硅基流动 API Key（sk-...）",
    urlHelp: "硅基流动兼容地址：https://api.siliconflow.cn/v1（点击「获取可用模型」将自动按 sub_type=chat 过滤对话模型）",
    models: ["deepseek-ai/DeepSeek-V3.2", "Qwen/Qwen3-32B", "Qwen/Qwen3-8B", "zai-org/GLM-5.3-Flash"]
  },
  {
    value: "OPENAI",
    label: "OpenAI",
    shortLabel: "OpenAI",
    defaultUrl: "https://api.openai.com/v1",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入 OpenAI API Key（sk-...）",
    urlHelp: "OpenAI 官方接口地址：https://api.openai.com/v1（o1/o3/o4/gpt-5 系列将自动切换 max_completion_tokens）",
    models: ["gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini", "gpt-4o", "o4-mini"]
  },
  {
    value: "OLLAMA",
    label: "本地 Ollama",
    shortLabel: "本地 Ollama",
    defaultUrl: "http://localhost:11434/v1",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: false,
    keyPlaceholder: "本地免密服务可留空",
    urlHelp: "本地 Ollama OpenAI 兼容地址：http://localhost:11434/v1（支持点击「获取可用模型」读取本地已安装模型）",
    models: ["qwen2.5:14b", "qwen2.5:7b", "deepseek-r1:14b"]
  },
  {
    value: "CUSTOM",
    label: "自定义兼容接口",
    shortLabel: "自定义模型",
    defaultUrl: "",
    defaultTemperature: 0.3,
    defaultMaxTokens: 8192,
    requiresApiKey: true,
    keyPlaceholder: "请输入接口访问密钥",
    urlHelp: "兼容 OpenAI `/chat/completions` 接口协议，填写基础地址或完整路径均可自动识别。",
    models: []
  }
]

export const REASONING_EFFORT_OPTIONS = [
  {
    value: "DEFAULT",
    label: "跟随模型默认",
    shortLabel: "默认",
    tagType: "info",
    help: "不额外注入思考控制参数，兼容所有对话模型、推理模型与自定义接口。"
  },
  {
    value: "OFF",
    label: "关闭思考（极速）",
    shortLabel: "关闭思考",
    tagType: "info",
    help: "关闭深度思维链，响应最快且最省 Token，适合常规规则排座。"
  },
  {
    value: "LOW",
    label: "轻量思考（排座推荐）",
    shortLabel: "轻量思考",
    tagType: "success",
    help: "适度推演老师的自然语言特别要求，兼顾排座质量与响应速度。"
  },
  {
    value: "MEDIUM",
    label: "适中思考",
    shortLabel: "适中思考",
    tagType: "primary",
    help: "标准推理强度，适合多条软硬约束组合场景。"
  },
  {
    value: "HIGH",
    label: "深度思考",
    shortLabel: "深度思考",
    tagType: "warning",
    help: "高强度推演复杂排座要求，耗时与 Token 消耗较高，建议最大 Token 设为 16384 以上、超时 120 秒以上。"
  }
]

export function getReasoningEffortOption(effortValue) {
  const key = String(effortValue || "DEFAULT").trim().toUpperCase() || "DEFAULT"
  return REASONING_EFFORT_OPTIONS.find(item => item.value === key) || REASONING_EFFORT_OPTIONS[0]
}

export function getReasoningEffortLabel(effortValue, short = false) {
  const opt = getReasoningEffortOption(effortValue)
  return short ? opt.shortLabel : opt.label
}

export function applyReasoningEffortLinkage(currentForm = {}, effortValue = "DEFAULT") {
  const normalized = getReasoningEffortOption(effortValue).value
  const next = {
    ...currentForm,
    reasoningEffort: normalized
  }
  if (normalized === "LOW" || normalized === "MEDIUM" || normalized === "HIGH") {
    if (currentForm.provider !== "ZHIPU" && (Number(next.maxTokens) || 0) < 16384) {
      next.maxTokens = 16384
    }
    if ((Number(next.timeoutSeconds) || 0) < 120) {
      next.timeoutSeconds = 120
    }
  }
  return next
}

export function getProviderPreset(providerValue) {
  return AI_PROVIDER_OPTIONS.find(item => item.value === providerValue) || null
}

export function getProviderLabel(providerValue) {
  return getProviderPreset(providerValue)?.label || providerValue || "-"
}

export function buildDefaultConfigName(providerValue, modelName) {
  const preset = getProviderPreset(providerValue)
  const title = preset?.shortLabel || "自定义模型"
  const trimmedModel = String(modelName || "").trim()
  if (!trimmedModel) {
    return title
  }
  return `${title} - ${trimmedModel}`.slice(0, 64)
}

export function getModelOptionList(providerValue, remoteModels = [], currentModel = "") {
  const preset = getProviderPreset(providerValue)
  const baseModels = Array.isArray(remoteModels) && remoteModels.length
    ? [...remoteModels]
    : [...(preset?.models || [])]
  const trimmedCurrent = String(currentModel || "").trim()
  if (trimmedCurrent && !baseModels.includes(trimmedCurrent)) {
    baseModels.unshift(trimmedCurrent)
  }
  return baseModels
}

export function getModelSuggestions(providerValue, queryString = "", remoteModels = []) {
  const preset = getProviderPreset(providerValue)
  const baseModels = Array.isArray(remoteModels) && remoteModels.length
    ? remoteModels
    : (preset?.models || [])
  const keyword = String(queryString || "").trim().toLowerCase()
  const exactMatch = keyword && baseModels.some(model => model.toLowerCase() === keyword)
  const matched = keyword && !exactMatch
    ? baseModels.filter(model => model.toLowerCase().includes(keyword))
    : baseModels
  return matched.map(value => ({ value }))
}

export function createEmptyAiConfigForm() {
  return {
    configId: null,
    provider: "",
    configName: "",
    apiUrl: "",
    apiKey: "",
    modelName: "",
    reasoningEffort: "DEFAULT",
    temperature: 0.3,
    maxTokens: 8192,
    timeoutSeconds: 120,
    isDefault: "1",
    status: "0",
    remark: ""
  }
}

export function applyProviderSwitch(currentForm = {}, providerValue = "") {
  const preset = getProviderPreset(providerValue)
  return {
    configId: currentForm.configId ?? null,
    provider: providerValue,
    configName: "",
    apiUrl: preset?.defaultUrl || "",
    apiKey: "",
    modelName: "",
    reasoningEffort: "DEFAULT",
    temperature: preset?.defaultTemperature ?? 0.3,
    maxTokens: preset?.defaultMaxTokens ?? 8192,
    timeoutSeconds: 120,
    isDefault: currentForm.isDefault ?? "1",
    status: currentForm.status ?? "0",
    remark: ""
  }
}
