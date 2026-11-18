import request from '@/utils/request'

// 查询AI模型配置列表
export function listAiConfig(query) {
  return request({
    url: '/seating/ai-config/list',
    method: 'get',
    params: query
  })
}

// 查询已启用的AI模型选项（供生成弹窗下拉使用）
export function listEnabledAiConfig() {
  return request({
    url: '/seating/ai-config/enabled',
    method: 'get'
  })
}

// 查询AI模型配置详情
export function getAiConfig(configId) {
  return request({
    url: '/seating/ai-config/' + configId,
    method: 'get'
  })
}

// 新增AI模型配置
export function addAiConfig(data) {
  return request({
    url: '/seating/ai-config',
    method: 'post',
    data: data
  })
}

// 修改AI模型配置
export function updateAiConfig(data) {
  return request({
    url: '/seating/ai-config',
    method: 'put',
    data: data
  })
}

// 删除AI模型配置
export function delAiConfig(configIds) {
  return request({
    url: '/seating/ai-config/' + configIds,
    method: 'delete'
  })
}

// 测试AI模型连通性
export function testAiConnection(data) {
  return request({
    url: '/seating/ai-config/test-connection',
    method: 'post',
    data: data,
    timeout: 45000
  })
}

// 在线拉取供应商可用模型列表
export function listRemoteAiModels(data) {
  return request({
    url: '/seating/ai-config/models',
    method: 'post',
    data: data,
    timeout: 25000
  })
}
