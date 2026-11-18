<template>
  <div class="app-container seating-page">
    <div class="seating-page-heading">
      <div>
        <h1>座位方案</h1>
        <p>生成、微调、确认并导出班级座位方案。</p>
      </div>
    </div>
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="班级" prop="classId">
        <el-select v-model="queryParams.classId" placeholder="请选择班级" filterable clearable style="width: 180px">
          <el-option v-for="item in classOptions" :key="item.classId" :label="item.className" :value="item.classId" />
        </el-select>
      </el-form-item>
      <el-form-item label="教室布局" prop="classroomId">
        <el-select v-model="queryParams.classroomId" placeholder="请选择教室布局" filterable clearable style="width: 180px">
          <el-option v-for="item in classroomOptions" :key="item.classroomId" :label="item.classroomName" :value="item.classroomId" />
        </el-select>
      </el-form-item>
      <el-form-item label="方案名称" prop="planName">
        <el-input
          v-model="queryParams.planName"
          placeholder="请输入方案名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="方案状态" prop="planStatus">
        <el-select v-model="queryParams.planStatus" placeholder="请选择方案状态" clearable style="width: 130px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          icon="MagicStick"
          @click="handleGenerate"
          v-hasPermi="['seating:plan:generate']"
        >一键生成</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['seating:plan:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['seating:plan:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="CircleCheck"
          :disabled="confirmDisabled"
          @click="handleConfirm"
          v-hasPermi="['seating:plan:edit']"
        >确认方案</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="CopyDocument"
          :disabled="copyDisabled"
          @click="handleCopy"
          v-hasPermi="['seating:plan:add']"
        >复制方案</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['seating:plan:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['seating:plan:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="planList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="班级" align="center" prop="className" min-width="120" />
      <el-table-column label="教室布局" align="center" prop="classroomName" min-width="120" />
      <el-table-column label="方案名称" align="center" prop="planName" />
      <el-table-column label="方案类型" align="center" prop="planType">
        <template #default="scope">{{ optionLabel(typeOptions, scope.row.planType) }}</template>
      </el-table-column>
      <el-table-column label="方案状态" align="center" prop="planStatus">
        <template #default="scope">{{ optionLabel(statusOptions, scope.row.planStatus) }}</template>
      </el-table-column>
      <el-table-column label="方案总评分" align="center" prop="totalScore" />
      <el-table-column label="生成时间" align="center" prop="generatedAt" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.generatedAt, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用时间" align="center" prop="activeTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.activeTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">查看</el-button>
          <el-button v-if="scope.row.planStatus !== 'ACTIVE'" link type="primary" icon="CircleCheck" @click="handleConfirm(scope.row)" v-hasPermi="['seating:plan:edit']">{{ confirmActionText(scope.row) }}</el-button>
          <el-button link type="primary" icon="CopyDocument" @click="handleCopy(scope.row)" v-hasPermi="['seating:plan:add']">复制</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['seating:plan:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['seating:plan:remove']">删除</el-button>
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
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改排座方案对话框 -->
    <el-dialog :title="title" v-model="open" width="580px" append-to-body :close-on-click-modal="!submitting">
      <el-form ref="planRef" :model="form" :rules="rules" label-width="100px" :disabled="submitting">
        <el-row>
          <el-col :span="24">
            <el-form-item label="班级" prop="classId">
              <el-select v-model="form.classId" placeholder="请选择班级" filterable style="width: 100%" @change="handleClassChange">
                <el-option v-for="item in classOptions" :key="item.classId" :label="item.className" :value="item.classId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="教室布局" prop="classroomId">
              <el-select v-model="form.classroomId" placeholder="请选择教室布局" filterable style="width: 100%">
                <el-option v-for="item in formClassroomOptions" :key="item.classroomId" :label="item.classroomName" :value="item.classroomId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="方案名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入方案名称" maxlength="64" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="生成方式" prop="planType">
              <el-radio-group v-model="form.planType">
                <el-radio value="AUTO">规则引擎排座（极速）</el-radio>
                <el-tooltip :disabled="myAiPermission.hasAiSeating" content="当前账号尚未开通 AI 排座权限，请联系管理员在「AI 模型配置」中授权" placement="top">
                  <el-radio value="AI" :disabled="!myAiPermission.hasAiSeating">AI 大模型排座</el-radio>
                </el-tooltip>
                <el-radio v-if="!generateMode" value="MANUAL">空白手动创建</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>

          <template v-if="form.planType === 'AI' && form.planId == null">
            <el-col :span="24">
              <el-form-item label="AI 模型" prop="aiConfigId">
                <div class="ai-model-row">
                  <el-select v-model="form.aiConfigId" placeholder="请选择已启用的 AI 模型" style="flex: 1">
                    <el-option
                      v-for="cfg in aiConfigOptions"
                      :key="cfg.configId"
                      :label="`${cfg.configName}（${cfg.modelName}）`"
                      :value="cfg.configId"
                    />
                  </el-select>
                  <el-button v-if="myAiPermission.hasAiConfig" link type="primary" @click="goAiConfigPage">管理模型</el-button>
                </div>
                <div v-if="!aiConfigOptions.length" class="ai-empty-tip">
                  当前暂无可用模型，请先前往 <el-button link type="primary" @click="goAiConfigPage">AI 模型配置</el-button> 添加并启用模型。
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="排座要求" prop="aiPrompt">
                <el-input
                  v-model="form.aiPrompt"
                  type="textarea"
                  :rows="3"
                  maxlength="500"
                  show-word-limit
                  placeholder="选填：输入自然语言补充要求，例如“让数学好的同学带动数学偏科生做同桌，张明近期感冒坐靠门边第一排”"
                />
                <div class="prompt-tags">
                  <span class="prompt-tag-label">快捷指令：</span>
                  <el-tag
                    v-for="tag in quickPromptTags"
                    :key="tag"
                    size="small"
                    effect="plain"
                    class="prompt-tag-item"
                    @click="appendQuickPrompt(tag)"
                  >+ {{ tag }}</el-tag>
                </div>
              </el-form-item>
            </el-col>
          </template>

          <el-col v-if="form.planId == null && (form.planType === 'AUTO' || form.planType === 'AI')" :span="24">
            <el-form-item label="排座策略">
              <div class="strategy-checks">
                <el-checkbox v-model="form.keepLockedSeats">继承同班当前启用方案中的已锁定座位</el-checkbox>
                <el-checkbox v-if="form.planType === 'AI'" v-model="form.autoFallback">AI 超时或异常时自动降级到本地规则引擎兜底</el-checkbox>
              </div>
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item label="方案状态" prop="planStatus">
              <el-select v-model="form.planStatus" placeholder="请选择方案状态" style="width: 100%">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitForm">
            {{ submitting && form.planType === 'AI' ? 'AI 正在思考排座...' : '确 定' }}
          </el-button>
          <el-button :disabled="submitting" @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Plan">
import { listPlan, getPlan, delPlan, addPlan, generatePlan, confirmPlan, copyPlan, updatePlan } from "@/api/seating/plan"
import { listClass } from "@/api/seating/class"
import { listClassroom } from "@/api/seating/classroom"
import { listEnabledAiConfig } from "@/api/seating/aiConfig"
import { getMyAiPermission } from "@/api/seating/aiPermission"

const { proxy } = getCurrentInstance()
const router = useRouter()

const typeOptions = [
  { value: "AUTO", label: "规则生成" },
  { value: "AI", label: "AI 大模型" },
  { value: "AI_FALLBACK", label: "AI 降级生成" },
  { value: "MANUAL", label: "手动创建" }
]
const quickPromptTags = [
  "学科强弱互补同桌",
  "男女生尽量同桌搭配",
  "活跃关注学生分散坐",
  "矮个与近视优先前排"
]
const statusOptions = [
  { value: "DRAFT", label: "草稿" },
  { value: "ACTIVE", label: "启用" },
  { value: "ARCHIVED", label: "归档" }
]

const planList = ref([])
const classOptions = ref([])
const classroomOptions = ref([])
const formClassroomOptions = computed(() => classroomOptions.value.filter(item => item.classId === form.value.classId))
const open = ref(false)
const loading = ref(true)
const listError = ref(false)
const showSearch = ref(true)
const ids = ref([])
const selectedRows = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const generateMode = ref(false)
const submitting = ref(false)
const aiConfigOptions = ref([])
const myAiPermission = ref({ hasAiSeating: false, hasAiConfig: false, isAdmin: false })
const confirmDisabled = computed(() => selectedRows.value.length !== 1 || selectedRows.value[0].planStatus === "ACTIVE")
const copyDisabled = computed(() => selectedRows.value.length !== 1)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    classId: undefined,
    classroomId: undefined,
    planName: undefined,
    planType: undefined,
    planStatus: undefined,
    totalScore: undefined,
    generatedAt: undefined,
    activeTime: undefined,
  },
  rules: {
    classId: [
      { required: true, message: "请选择班级", trigger: "change" }
    ],
    classroomId: [
      { required: true, message: "请选择教室布局", trigger: "change" }
    ],
    planName: [
      { required: true, message: "方案名称不能为空", trigger: "blur" },
      { max: 64, message: "方案名称长度不能超过64个字符", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

const hasActiveFilters = computed(() => Object.entries(queryParams.value).some(([key, value]) => !["pageNum", "pageSize"].includes(key) && value !== undefined && value !== null && value !== ""))
const listEmptyDescription = computed(() => listError.value ? "座位方案列表加载失败，请检查网络后重试。" : hasActiveFilters.value ? "没有找到符合条件的座位方案。" : "暂无座位方案，请先新增或智能生成方案。")
const listEmptyActionText = computed(() => listError.value ? "重新加载" : hasActiveFilters.value ? "重置筛选" : "新增方案")

function handleListEmptyAction() {
  if (listError.value) {
    getList()
  } else if (hasActiveFilters.value) {
    resetQuery()
  } else {
    handleAdd()
  }
}

/** 查询排座方案列表 */
function getList() {
  loading.value = true
  listError.value = false
  listPlan(queryParams.value).then(response => {
    planList.value = response.rows
    total.value = response.total
  }).catch(() => {
    planList.value = []
    total.value = 0
    listError.value = true
  }).finally(() => {
    loading.value = false
  })
}

function getOptions() {
  return Promise.all([
    listClass({ status: "0", pageNum: 1, pageSize: 1000 }),
    listClassroom({ status: "0", pageNum: 1, pageSize: 1000 })
  ]).then(([classResponse, classroomResponse]) => {
    classOptions.value = classResponse.rows
    classroomOptions.value = classroomResponse.rows
  })
}

function optionLabel(options, value) {
  return options.find(item => item.value === value)?.label || "-"
}

function handleClassChange() {
  form.value.classroomId = null
}

/** 取消按钮 */
function cancel() {
  open.value = false
  generateMode.value = false
  reset()
}

/** 表单重置 */
function reset() {
  const defaultAiConfig = aiConfigOptions.value.find(c => c.isDefault === "1") || aiConfigOptions.value[0]
  form.value = {
    planId: null,
    classId: queryParams.value.classId || null,
    classroomId: null,
    planName: null,
    planType: "MANUAL",
    planStatus: "DRAFT",
    aiConfigId: defaultAiConfig ? defaultAiConfig.configId : null,
    aiPrompt: "",
    keepLockedSeats: true,
    autoFallback: true,
    totalScore: null,
    generatedAt: null,
    activeTime: null,
    remark: null
  }
  proxy.resetForm("planRef")
}

function loadAiContext() {
  return getMyAiPermission().then(res => {
    const info = res.data || {}
    myAiPermission.value = {
      hasAiSeating: !!info.hasAiSeating,
      hasAiConfig: !!info.hasAiConfig,
      isAdmin: !!info.isAdmin
    }
    if (myAiPermission.value.hasAiSeating) {
      return listEnabledAiConfig().then(cfgRes => {
        aiConfigOptions.value = cfgRes.data || []
        const defCfg = aiConfigOptions.value.find(c => c.isDefault === "1") || aiConfigOptions.value[0]
        if (!form.value.aiConfigId && defCfg) {
          form.value.aiConfigId = defCfg.configId
        }
      })
    }
    aiConfigOptions.value = []
  }).catch(() => {})
}

function appendQuickPrompt(tag) {
  const current = String(form.value.aiPrompt || "").trim()
  if (current.includes(tag)) return
  form.value.aiPrompt = current ? `${current}；${tag}` : tag
}

function goAiConfigPage() {
  open.value = false
  router.push("/seating/ai-config")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.planId)
  selectedRows.value = selection
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  generateMode.value = false
  loadAiContext()
  open.value = true
  title.value = "添加排座方案"
}

/** 一键生成按钮操作 */
function handleGenerate() {
  reset()
  generateMode.value = true
  form.value.planType = "AUTO"
  loadAiContext()
  open.value = true
  title.value = "生成座位方案"
}

function handleDetail(row) {
  router.push("/seating/plan-detail/index/" + row.planId)
}

function confirmActionText(row) {
  return row.planStatus === "ARCHIVED" ? "恢复" : "确认"
}

/** 确认方案按钮操作 */
function handleConfirm(row) {
  const target = row?.planId ? row : selectedRows.value[0]
  if (!target?.planId) {
    return
  }
  const actionText = target.planStatus === "ARCHIVED" ? "恢复启用" : "启用"
  proxy.$modal.confirm('确认' + actionText + '方案"' + target.planName + '"？同班级原启用方案会自动归档。').then(function() {
    return confirmPlan(target.planId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess(actionText + "成功")
  }).catch(() => {})
}

/** 复制方案按钮操作 */
function handleCopy(row) {
  const target = row?.planId ? row : selectedRows.value[0]
  if (!target?.planId) {
    return
  }
  const suggestedName = `${target.planName || "座位方案"}-副本`.slice(0, 64)
  proxy.$prompt(`请输入方案"${target.planName}"的副本名称`, "复制座位方案", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    closeOnClickModal: false,
    inputValue: suggestedName,
    inputPlaceholder: "请输入新方案名称",
    inputValidator: validatePlanName
  }).then(({ value }) => {
    return copyPlan(target.planId, { planName: value.trim() })
  }).then(response => {
    proxy.$modal.msgSuccess("复制成功")
    getList()
    if (response.data?.planId) {
      router.push("/seating/plan-detail/index/" + response.data.planId)
    }
  }).catch(() => {})
}

function validatePlanName(value) {
  const planName = String(value || "").trim()
  if (!planName) {
    return "方案名称不能为空"
  }
  if (planName.length > 64) {
    return "方案名称长度不能超过64个字符"
  }
  return true
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  generateMode.value = false
  const _planId = row.planId || ids.value
  getPlan(_planId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改排座方案"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["planRef"].validate(valid => {
    if (valid) {
      const submitData = { ...form.value }
      const shouldGenerate = generateMode.value || (submitData.planId == null && (submitData.planType === "AUTO" || submitData.planType === "AI"))
      if (shouldGenerate && submitData.planType === "AI" && !aiConfigOptions.value.length) {
        proxy.$modal.msgWarning("请先在「AI 模型配置」中添加并启用至少一个 AI 模型")
        return
      }
      submitData.params = {
        generateMode: submitData.planType === "AI" ? "AI" : "AUTO",
        keepLockedSeats: !!submitData.keepLockedSeats,
        autoFallback: submitData.autoFallback !== false
      }
      delete submitData.keepLockedSeats
      delete submitData.autoFallback
      delete submitData.className
      delete submitData.classroomName
      delete submitData.delFlag
      delete submitData.createBy
      delete submitData.createTime
      delete submitData.updateBy
      delete submitData.updateTime

      submitting.value = true
      // 新建时选「规则生成 / AI 大模型」直接执行排座；修改已有方案时不触发生成，避免多建一份
      if (shouldGenerate) {
        generatePlan(submitData).then(response => {
          const generated = response.data || {}
          if (generated.planType === "AI_FALLBACK") {
            proxy.$modal.msgWarning("AI 调用异常，已自动切换规则引擎兜底生成方案")
          } else {
            proxy.$modal.msgSuccess(generated.planType === "AI" ? "AI 大模型排座生成成功" : "生成成功")
          }
          open.value = false
          generateMode.value = false
          getList()
          if (generated.planId) {
            router.push("/seating/plan-detail/index/" + generated.planId)
          }
        }).finally(() => {
          submitting.value = false
        })
      } else if (form.value.planId != null) {
        updatePlan(submitData).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        }).finally(() => {
          submitting.value = false
        })
      } else {
        addPlan(submitData).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        }).finally(() => {
          submitting.value = false
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _planIds = row.planId || ids.value
  proxy.$modal.confirm('是否确认删除排座方案编号为"' + _planIds + '"的数据项？').then(function() {
    return delPlan(_planIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('seating/plan/export', {
    ...queryParams.value
  }, `plan_${new Date().getTime()}.xlsx`)
}

getOptions()
loadAiContext()
getList()
</script>

<style scoped>
.ai-model-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.ai-empty-tip {
  margin-top: 4px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.5;
}

.prompt-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.prompt-tag-label {
  color: #909399;
  font-size: 12px;
}

.prompt-tag-item {
  cursor: pointer;
  user-select: none;
}

.prompt-tag-item:hover {
  border-color: var(--seating-primary, #2c9486);
  color: var(--seating-primary, #2c9486);
}

.strategy-checks {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>
