import request from '@/utils/request'

// 查询当前登录用户的AI权限
export function getMyAiPermission() {
  return request({
    url: '/seating/ai-permission/my',
    method: 'get',
    silent: true
  })
}

// 查询用户AI权限列表
export function listUserAiPermission(query) {
  return request({
    url: '/seating/ai-permission/list',
    method: 'get',
    params: query
  })
}

// 保存单个用户AI权限
export function updateUserAiPermission(data) {
  return request({
    url: '/seating/ai-permission',
    method: 'put',
    data: data
  })
}

// 批量保存用户AI权限
export function batchUpdateUserAiPermission(data) {
  return request({
    url: '/seating/ai-permission/batch',
    method: 'post',
    data: data
  })
}
