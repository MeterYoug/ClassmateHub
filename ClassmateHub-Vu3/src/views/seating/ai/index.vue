<template>
  <div class="app-container seating-page">
    <div class="seating-page-heading">
      <div>
        <h1>AI 模型配置</h1>
        <p>管理兼容 OpenAI 协议的大模型接口，并按老师账号精细授权 AI 智能排座与配置权限。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="ai-config-tabs" @tab-change="handleTabChange">
      <!-- Tab 1: 大模型接口配置 -->
      <el-tab-pane label="大模型接口配置" name="config">
        <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="72px">
          <el-form-item label="配置名称" prop="configName">
            <el-input
              v-model="queryParams.configName"
              placeholder="请输入配置名称"
              clearable
              style="width: 180px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="供应商" prop="provider">
            <el-select v-model="queryParams.provider" placeholder="全部供应商" clearable style="width: 195px">
              <el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              plain
              icon="Plus"
              @click="handleAdd"
              v-hasPermi="['seating:aiConfig:add']"
            >新增模型</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="danger"
              plain
              icon="Delete"
              :disabled="!selectedIds.length"
              @click="handleDelete()"
              v-hasPermi="['seating:aiConfig:remove']"
            >删除</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
        </el-row>

        <el-table v-loading="loading" :data="configList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="52" align="center" />
          <el-table-column label="配置名称" align="center" prop="configName" min-width="140">
            <template #default="scope">
              <span>{{ scope.row.configName }}</span>
              <el-tag v-if="scope.row.isDefault === '1'" type="success" size="small" effect="plain" class="ml6">默认</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="供应商" align="center" prop="provider" min-width="150" show-overflow-tooltip>
            <template #default="scope">{{ providerLabel(scope.row.provider) }}</template>
          </el-table-column>
          <el-table-column label="模型名称" align="center" prop="modelName" min-width="140" show-overflow-tooltip />
          <el-table-column label="思考程度" align="center" prop="reasoningEffort" width="105">
            <template #default="scope">
              <el-tag :type="reasoningEffortTagType(scope.row.reasoningEffort)" size="small" effect="plain">
                {{ reasoningEffortShortLabel(scope.row.reasoningEffort) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="API 地址" align="center" prop="apiUrl" min-width="220" show-overflow-tooltip />
          <el-table-column label="API Key" align="center" prop="apiKey" width="140" />
          <el-table-column label="温度 / 超时" align="center" width="120">
            <template #default="scope">{{ scope.row.temperature ?? 0.3 }} / {{ scope.row.timeoutSeconds ?? 120 }}s</template>
          </el-table-column>
          <el-table-column label="状态" align="center" prop="status" width="90">
            <template #default="scope">
              <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="240" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button
                link
                type="primary"
                icon="Connection"
                :loading="testingRowId === scope.row.configId"
                @click="handleRowTest(scope.row)"
                v-hasPermi="['seating:aiConfig:edit']"
              >测试连接</el-button>
              <el-button
                v-if="scope.row.isDefault !== '1'"
                link
                type="primary"
                icon="Star"
                @click="handleSetDefault(scope.row)"
                v-hasPermi="['seating:aiConfig:edit']"
              >设为默认</el-button>
              <el-button
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
                v-hasPermi="['seating:aiConfig:edit']"
              >修改</el-button>
              <el-button
                link
                type="primary"
                icon="Delete"
                @click="handleDelete(scope.row)"
                v-hasPermi="['seating:aiConfig:remove']"
              >删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <seating-table-empty-state
              :description="listEmptyDescription"
              :action-text="listEmptyActionText"
              @action="handleListEmptyAction"
            />
          </template>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </el-tab-pane>

      <!-- Tab 2: 老师 AI 权限授权 -->
      <el-tab-pane v-if="canManagePermission" label="老师 AI 权限授权" name="permission">
        <el-form :model="permQuery" ref="permQueryRef" :inline="true" label-width="72px">
          <el-form-item label="登录账号" prop="userName">
            <el-input
              v-model="permQuery.userName"
              placeholder="请输入账号"
              clearable
              style="width: 160px"
              @keyup.enter="handlePermQuery"
            />
          </el-form-item>
          <el-form-item label="老师姓名" prop="nickName">
            <el-input
              v-model="permQuery.nickName"
              placeholder="请输入姓名/昵称"
              clearable
              style="width: 160px"
              @keyup.enter="handlePermQuery"
            />
          </el-form-item>
          <el-form-item label="排座权限" prop="hasAiSeating">
            <el-select v-model="permQuery.hasAiSeating" placeholder="全部" clearable style="width: 120px">
              <el-option label="已开通" value="1" />
              <el-option label="未开通" value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handlePermQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetPermQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              plain
              icon="Check"
              :disabled="!selectedPermRows.length"
              @click="handleBatchGrant('1')"
              v-hasPermi="['seating:aiPermission:edit']"
            >批量开通 AI 排座</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="info"
              plain
              icon="Close"
              :disabled="!selectedPermRows.length"
              @click="handleBatchGrant('0')"
              v-hasPermi="['seating:aiPermission:edit']"
            >批量关闭 AI 排座</el-button>
          </el-col>
        </el-row>

        <el-table v-loading="permLoading" :data="permList" @selection-change="handlePermSelectionChange">
          <el-table-column type="selection" width="52" align="center" :selectable="row => row.userId !== 1" />
          <el-table-column label="登录账号" align="center" prop="userName" min-width="130" />
          <el-table-column label="老师姓名/昵称" align="center" prop="nickName" min-width="140" />
          <el-table-column label="账号状态" align="center" prop="userStatus" width="100">
            <template #default="scope">
              <dict-tag :options="sys_normal_disable" :value="scope.row.userStatus" />
            </template>
          </el-table-column>
          <el-table-column label="允许使用 AI 排座" align="center" width="160">
            <template #default="scope">
              <el-switch
                v-model="scope.row.hasAiSeating"
                active-value="1"
                inactive-value="0"
                :disabled="scope.row.userId === 1"
                @change="handlePermSwitchChange(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="允许管理 AI 模型配置" align="center" width="180">
            <template #default="scope">
              <el-switch
                v-model="scope.row.hasAiConfig"
                active-value="1"
                inactive-value="0"
                :disabled="scope.row.userId === 1"
                @change="handlePermSwitchChange(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="说明" align="center" min-width="180">
            <template #default="scope">
              <span v-if="scope.row.userId === 1" class="admin-hint">超级管理员默认拥有全部 AI 权限</span>
              <span v-else class="perm-hint">{{ scope.row.hasAiSeating === '1' ? '可在生成座位方案时选用 AI 大模型' : '仅可使用本地规则引擎排座' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="permTotal > 0"
          :total="permTotal"
          v-model:page="permQuery.pageNum"
          v-model:limit="permQuery.pageSize"
          @pagination="getPermList"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 添加或修改 AI 模型配置对话框 -->
    <el-dialog :title="title" v-model="open" width="620px" append-to-body>
      <el-form ref="configRef" :model="form" :rules="rules" label-width="105px">
        <el-form-item label="AI 供应商" prop="provider">
          <el-select v-model="form.provider" placeholder="请选择 AI 供应商" style="width: 100%" @change="handleProviderChange">
            <el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" clearable :placeholder="configNamePlaceholder" maxlength="64" />
        </el-form-item>
        <el-form-item label="API 地址" prop="apiUrl">
          <el-input v-model="form.apiUrl" clearable :placeholder="apiUrlPlaceholder" />
          <div class="field-help">{{ apiUrlHelpText }}</div>
        </el-form-item>
        <el-form-item label="API 密钥" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            clearable
            :placeholder="apiKeyPlaceholder"
          />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <div class="model-input-row">
            <el-select
              v-model="form.modelName"
              filterable
              allow-create
              default-first-option
              clearable
              style="flex: 1"
              :placeholder="modelPlaceholder"
              @change="handleModelNameChange"
              @blur="handleModelSelectBlur"
            >
              <el-option
                v-for="modelId in modelOptionList"
                :key="modelId"
                :label="modelId"
                :value="modelId"
              />
            </el-select>
            <el-button icon="Refresh" :loading="fetchingModels" @click="handleFetchRemoteModels">获取可用模型</el-button>
          </div>
          <div v-if="remoteModels.length" class="field-help success-help">
            <span>已获取当前账号在线支持的 {{ remoteModels.length }} 个模型，可下拉或点击切换：</span>
            <el-tag
              v-for="m in remoteModels.slice(0, 8)"
              :key="m"
              size="small"
              :type="form.modelName === m ? 'success' : 'info'"
              :effect="form.modelName === m ? 'light' : 'plain'"
              class="remote-model-tag"
              @click="handleModelQuickPick(m)"
            >{{ m }}</el-tag>
          </div>
          <div v-else-if="currentPreset?.modelHelp" class="field-help">{{ currentPreset.modelHelp }}</div>
        </el-form-item>
        <el-form-item label="思考程度" prop="reasoningEffort">
          <el-select
            v-model="form.reasoningEffort"
            placeholder="请选择思考程度"
            style="width: 100%"
            @change="handleReasoningEffortChange"
          >
            <el-option
              v-for="item in reasoningEffortOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div class="field-help">{{ currentReasoningEffortHelp }}</div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="采样温度" prop="temperature">
              <el-input-number v-model="form.temperature" :min="0" :max="1.5" :step="0.1" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大Token" prop="maxTokens" label-width="88px">
              <el-input-number v-model="form.maxTokens" :min="512" :max="65536" :step="1024" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="超时(秒)" prop="timeoutSeconds" label-width="78px">
              <el-input-number v-model="form.timeoutSeconds" :min="10" :max="300" :step="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设为默认" prop="isDefault">
              <el-radio-group v-model="form.isDefault">
                <el-radio value="1">是</el-radio>
                <el-radio value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选填写模型用途或计费说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button icon="Connection" :loading="dialogTesting" @click="handleDialogTest">测试连接</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SeatingAiConfig">
import {
  listAiConfig,
  getAiConfig,
  addAiConfig,
  updateAiConfig,
  delAiConfig,
  testAiConnection,
  listRemoteAiModels
} from "@/api/seating/aiConfig"
import {
  getMyAiPermission,
  listUserAiPermission,
  updateUserAiPermission,
  batchUpdateUserAiPermission
} from "@/api/seating/aiPermission"
import {
  AI_PROVIDER_OPTIONS,
  REASONING_EFFORT_OPTIONS,
  getProviderPreset,
  getProviderLabel,
  getReasoningEffortOption,
  getReasoningEffortLabel,
  applyReasoningEffortLinkage,
  buildDefaultConfigName,
  getModelOptionList,
  createEmptyAiConfigForm,
  applyProviderSwitch
} from "./providerPresets"

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = proxy.useDict("sys_normal_disable")

const providerOptions = AI_PROVIDER_OPTIONS
const reasoningEffortOptions = REASONING_EFFORT_OPTIONS

const activeTab = ref("config")
const showSearch = ref(true)
const loading = ref(true)
const listError = ref(false)
const configList = ref([])
const total = ref(0)
const selectedIds = ref([])
const open = ref(false)
const title = ref("")
const submitting = ref(false)
const dialogTesting = ref(false)
const fetchingModels = ref(false)
const remoteModels = ref([])
const testingRowId = ref(null)
const canManagePermission = ref(false)
const originalProvider = ref("")

const permLoading = ref(false)
const permList = ref([])
const permTotal = ref(0)
const selectedPermRows = ref([])

const data = reactive({
  form: createEmptyAiConfigForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    configName: undefined,
    provider: undefined,
    status: undefined
  },
  permQuery: {
    pageNum: 1,
    pageSize: 10,
    userName: undefined,
    nickName: undefined,
    hasAiSeating: undefined
  },
  rules: {
    provider: [{ required: true, message: "请选择 AI 供应商", trigger: "change" }],
    apiUrl: [{ required: true, message: "API 地址不能为空", trigger: "blur" }],
    modelName: [{ required: true, message: "模型名称不能为空", trigger: ["blur", "change"] }]
  }
})

const { form, queryParams, permQuery, rules } = toRefs(data)

const currentPreset = computed(() => getProviderPreset(form.value.provider))
const currentReasoningEffortHelp = computed(() => getReasoningEffortOption(form.value.reasoningEffort).help)
const isSameProviderInEdit = computed(() => !!form.value.configId && form.value.provider === originalProvider.value)
const configNamePlaceholder = computed(() => {
  if (currentPreset.value && currentPreset.value.value !== "CUSTOM") {
    return `选填，留空自动命名为「${currentPreset.value.shortLabel} - 模型名」`
  }
  return "选填，留空将自动按供应商与模型名生成"
})
const apiUrlPlaceholder = computed(() => currentPreset.value?.defaultUrl || "例如：https://api.deepseek.com/v1")
const apiUrlHelpText = computed(() => currentPreset.value?.urlHelp || "兼容 OpenAI `/chat/completions` 接口协议，填写基础地址或完整路径均可自动识别。")
const apiKeyPlaceholder = computed(() => {
  if (isSameProviderInEdit.value) {
    return "留空或保持掩码表示不修改原密钥"
  }
  return currentPreset.value?.keyPlaceholder || "请输入 API 密钥"
})
const modelPlaceholder = computed(() => {
  const sample = modelOptionList.value?.[0]
  if (sample) {
    return `请选择或直接输入模型名称（如 ${sample}）`
  }
  return "请输入模型名称"
})
const modelOptionList = computed(() => getModelOptionList(form.value.provider, remoteModels.value, form.value.modelName))
const previousModelName = ref("")

const hasActiveFilters = computed(() => Object.entries(queryParams.value).some(([k, v]) => !["pageNum", "pageSize"].includes(k) && v !== undefined && v !== null && v !== ""))
const listEmptyDescription = computed(() => listError.value ? "AI 模型配置列表加载失败，请重试。" : hasActiveFilters.value ? "没有找到匹配的 AI 模型配置。" : "暂无 AI 模型配置，新增配置后即可在座位方案中启用 AI 大模型排座。")
const listEmptyActionText = computed(() => listError.value ? "重新加载" : hasActiveFilters.value ? "重置筛选" : "新增模型")

function handleListEmptyAction() {
  if (listError.value) {
    getList()
  } else if (hasActiveFilters.value) {
    resetQuery()
  } else {
    handleAdd()
  }
}

function providerLabel(value) {
  return getProviderLabel(value)
}

function reasoningEffortShortLabel(value) {
  return getReasoningEffortLabel(value, true)
}

function reasoningEffortTagType(value) {
  return getReasoningEffortOption(value).tagType
}

function handleReasoningEffortChange(effortValue) {
  form.value = applyReasoningEffortLinkage(form.value, effortValue)
}

function syncAutoConfigName(oldModel, newModel) {
  const curName = String(form.value.configName || "").trim()
  if (!curName || !newModel) {
    previousModelName.value = newModel || ""
    return
  }
  const expectedOldName = buildDefaultConfigName(form.value.provider, oldModel)
  if (oldModel && curName === expectedOldName) {
    form.value.configName = buildDefaultConfigName(form.value.provider, newModel)
  }
  previousModelName.value = newModel
}

function handleModelNameChange(newModel) {
  syncAutoConfigName(previousModelName.value, newModel)
}

function handleModelQuickPick(modelId) {
  const oldModel = form.value.modelName
  form.value.modelName = modelId
  syncAutoConfigName(oldModel, modelId)
}

function handleModelSelectBlur(event) {
  const typed = String(event?.target?.value || "").trim()
  if (typed && typed !== form.value.modelName) {
    const oldModel = form.value.modelName
    form.value.modelName = typed
    syncAutoConfigName(oldModel, typed)
  }
}

function handleProviderChange(providerValue) {
  remoteModels.value = []
  previousModelName.value = ""
  form.value = applyProviderSwitch(form.value, providerValue)
  nextTick(() => {
    proxy.$refs["configRef"]?.clearValidate()
  })
}

function getList() {
  loading.value = true
  listError.value = false
  listAiConfig(queryParams.value).then(res => {
    configList.value = res.rows || []
    total.value = res.total || 0
  }).catch(() => {
    configList.value = []
    total.value = 0
    listError.value = true
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(item => item.configId)
}

function reset() {
  originalProvider.value = ""
  previousModelName.value = ""
  remoteModels.value = []
  form.value = createEmptyAiConfigForm()
  proxy.resetForm("configRef")
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "新增 AI 模型配置"
}

function handleUpdate(row) {
  reset()
  getAiConfig(row.configId).then(res => {
    const detail = res.data || {}
    originalProvider.value = detail.provider || ""
    previousModelName.value = detail.modelName || ""
    form.value = {
      ...createEmptyAiConfigForm(),
      ...detail,
      reasoningEffort: detail.reasoningEffort || "DEFAULT"
    }
    open.value = true
    title.value = "修改 AI 模型配置"
  })
}

function handleSetDefault(row) {
  updateAiConfig({ configId: row.configId, configName: row.configName, provider: row.provider, apiUrl: row.apiUrl, modelName: row.modelName, isDefault: "1" }).then(() => {
    proxy.$modal.msgSuccess(`已将「${row.configName}」设为默认模型`)
    getList()
  })
}

function handleRowTest(row) {
  testingRowId.value = row.configId
  testAiConnection({ configId: row.configId }).then(res => {
    const info = res.data || {}
    proxy.$modal.msgSuccess(`连接成功（耗时 ${info.latencyMs || "-"}ms，回复：${info.reply || "OK"}）`)
  }).finally(() => {
    testingRowId.value = null
  })
}

function ensureApiKeySatisfied(forTest = false) {
  const requiresKey = currentPreset.value?.requiresApiKey !== false
  if (!requiresKey) {
    return true
  }
  const keyVal = String(form.value.apiKey || "").trim()
  if (!form.value.configId && !keyVal) {
    proxy.$modal.msgError(forTest ? "请先输入 API 密钥后再测试连接" : "API 密钥不能为空")
    return false
  }
  if (form.value.configId && !isSameProviderInEdit.value && (!keyVal || keyVal.includes("****"))) {
    proxy.$modal.msgError(forTest ? "切换 AI 供应商后请填写新供应商的 API 密钥再测试" : "切换 AI 供应商后请填写新的 API 密钥")
    return false
  }
  return true
}

function handleFetchRemoteModels() {
  if (!String(form.value.apiUrl || "").trim()) {
    proxy.$modal.msgError("请先选择 AI 供应商或填写 API 地址")
    return
  }
  if (!ensureApiKeySatisfied(true)) return
  fetchingModels.value = true
  listRemoteAiModels({ ...form.value }).then(res => {
    const models = Array.isArray(res.data) ? res.data : []
    remoteModels.value = models
    if (!models.length) {
      proxy.$modal.msgWarning("未查询到在线模型列表，请直接选择预设或手动输入模型名称")
      return
    }
    if (!form.value.modelName || !models.includes(form.value.modelName)) {
      const oldModel = form.value.modelName
      form.value.modelName = models[0]
      syncAutoConfigName(oldModel, models[0])
    } else {
      previousModelName.value = form.value.modelName
    }
    proxy.$modal.msgSuccess(`成功获取到 ${models.length} 个可用模型`)
  }).finally(() => {
    fetchingModels.value = false
  })
}

function handleDialogTest() {
  proxy.$refs["configRef"].validateField(["provider", "apiUrl", "modelName"], valid => {
    if (!valid) return
    if (!ensureApiKeySatisfied(true)) return
    dialogTesting.value = true
    testAiConnection({ ...form.value }).then(res => {
      const info = res.data || {}
      proxy.$modal.msgSuccess(`连接成功（耗时 ${info.latencyMs || "-"}ms，模型回复：${info.reply || "OK"}）`)
    }).finally(() => {
      dialogTesting.value = false
    })
  })
}

function submitForm() {
  proxy.$refs["configRef"].validate(valid => {
    if (!valid) return
    if (!ensureApiKeySatisfied(false)) return
    const payload = {
      ...form.value,
      configName: String(form.value.configName || "").trim() || buildDefaultConfigName(form.value.provider, form.value.modelName)
    }
    submitting.value = true
    const req = payload.configId ? updateAiConfig(payload) : addAiConfig(payload)
    req.then(() => {
      proxy.$modal.msgSuccess(payload.configId ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

function cancel() {
  open.value = false
  reset()
}

function handleDelete(row) {
  const ids = row?.configId ? [row.configId] : selectedIds.value
  const label = row?.configName ? `「${row.configName}」` : `选中的 ${ids.length} 项配置`
  proxy.$modal.confirm(`确认删除 AI 模型配置 ${label}？`).then(() => {
    return delAiConfig(ids.join(","))
  }).then(() => {
    proxy.$modal.msgSuccess("删除成功")
    getList()
  }).catch(() => {})
}

// 老师权限 Tab
function handleTabChange(tabName) {
  if (tabName === "permission") {
    getPermList()
  }
}

function getPermList() {
  permLoading.value = true
  listUserAiPermission(permQuery.value).then(res => {
    permList.value = res.rows || []
    permTotal.value = res.total || 0
  }).finally(() => {
    permLoading.value = false
  })
}

function handlePermQuery() {
  permQuery.value.pageNum = 1
  getPermList()
}

function resetPermQuery() {
  proxy.resetForm("permQueryRef")
  handlePermQuery()
}

function handlePermSelectionChange(selection) {
  selectedPermRows.value = selection
}

function handlePermSwitchChange(row) {
  updateUserAiPermission({
    userId: row.userId,
    hasAiSeating: row.hasAiSeating,
    hasAiConfig: row.hasAiConfig,
    dailyLimit: row.dailyLimit || 50
  }).then(() => {
    proxy.$modal.msgSuccess(`已更新「${row.nickName || row.userName}」的 AI 权限`)
  }).catch(() => {
    getPermList()
  })
}

function handleBatchGrant(enableFlag) {
  if (!selectedPermRows.value.length) return
  const actionLabel = enableFlag === "1" ? "开通" : "关闭"
  const payload = selectedPermRows.value.map(row => ({
    userId: row.userId,
    hasAiSeating: enableFlag,
    hasAiConfig: row.hasAiConfig,
    dailyLimit: row.dailyLimit || 50
  }))
  proxy.$modal.confirm(`确认批量为选中的 ${payload.length} 名老师${actionLabel} AI 排座权限？`).then(() => {
    return batchUpdateUserAiPermission(payload)
  }).then(() => {
    proxy.$modal.msgSuccess(`批量${actionLabel}成功`)
    getPermList()
  }).catch(() => {})
}

function initPermissionTabVisibility() {
  getMyAiPermission().then(res => {
    const info = res.data || {}
    canManagePermission.value = !!info.isAdmin || proxy.$auth.hasPermi("seating:aiPermission:list")
  }).catch(() => {
    canManagePermission.value = proxy.$auth.hasPermi("seating:aiPermission:list")
  })
}

initPermissionTabVisibility()
getList()
</script>

<style scoped>
.ml6 {
  margin-left: 6px;
}

.model-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.field-help {
  margin-top: 4px;
  color: var(--seating-text-secondary, #909399);
  font-size: 12px;
  line-height: 1.5;
}

.success-help {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--seating-primary, #2c9486);
}

.remote-model-tag {
  cursor: pointer;
}

.admin-hint {
  color: var(--seating-primary, #2c9486);
  font-size: 12px;
}

.perm-hint {
  color: var(--seating-text-secondary, #606266);
  font-size: 12px;
}
</style>
