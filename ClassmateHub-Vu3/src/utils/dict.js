import useDictStore from '@/store/modules/dict'
import { getDicts } from '@/api/system/dict/data'

// 同一字典类型的并发请求合并，避免同屏多个页面重复拉取
const pendingDicts = new Map()
const MAX_RETRY = 2
const RETRY_DELAY = 600

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function isAuthError(error) {
  const message = String(error?.message || error || '')
  return message.includes('会话') || message.includes('登录')
}

function loadDict(dictType, retry = 0) {
  return getDicts(dictType)
    .then(resp => (resp?.data || []).map(p => ({
      label: p.dictLabel,
      value: p.dictValue,
      elTagType: p.listClass,
      elTagClass: p.cssClass
    })))
    .catch(async error => {
      if (retry < MAX_RETRY && !isAuthError(error)) {
        await wait(RETRY_DELAY * (retry + 1))
        return loadDict(dictType, retry + 1)
      }
      // 兜底为空数组：DictTag 会显示原始值，且不会产生未处理的 Promise 拒绝
      console.warn(`[useDict] 字典 ${dictType} 加载失败：`, error)
      return []
    })
}

/**
 * 获取字典数据
 */
export function useDict(...args) {
  const res = ref({})
  args.forEach(dictType => {
    res.value[dictType] = []
    const cached = useDictStore().getDict(dictType)
    if (cached) {
      res.value[dictType] = cached
      return
    }
    let task = pendingDicts.get(dictType)
    if (!task) {
      task = loadDict(dictType).then(list => {
        // 失败或空结果不写缓存，下次进入页面仍会重新拉取
        if (list.length) {
          useDictStore().setDict(dictType, list)
        }
        return list
      }).finally(() => {
        pendingDicts.delete(dictType)
      })
      pendingDicts.set(dictType, task)
    }
    task.then(list => {
      res.value[dictType] = list
    })
  })
  return toRefs(res.value)
}
