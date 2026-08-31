import request from '@/utils/request'

/**
 * 分页查询（默认 GET）。
 * conditionsJson 走 query；可选 Header X-GP-Conditions-Json。
 */
export function list${className}(query) {
  const params = { ...(query || {}) }
  const headers = {}
  if (params.conditionsJson) {
    headers['X-GP-Conditions-Json'] = params.conditionsJson
  }
  return request({
    url: '${baseRequestMapping}/list',
    method: 'get',
    params,
    headers
  })
}

/**
 * 分页查询（POST 备用）：body 传筛选项 / conditionsJson。
 */
export function list${className}Post(query) {
  const q = query || {}
  const { pageNum, pageSize, ...body } = q
  const conditionsJson = body.conditionsJson
  const headers = {}
  if (conditionsJson) {
    headers['X-GP-Conditions-Json'] = conditionsJson
  }
  const params = { pageNum, pageSize }
  if (conditionsJson) {
    params.conditionsJson = conditionsJson
  }
  return request({
    url: '${baseRequestMapping}/list',
    method: 'post',
    params,
    data: body,
    headers
  })
}

// 查询单条数据
export function get${className}(id) {
  return request({
    url: '${baseRequestMapping}/'+id,
    method: 'get'
  })
}

// 删除单条数据
export function delete${className}(query) {
return request({
    url: '${baseRequestMapping}/remove',
    method: 'get',
    params: query
  })
}

// 批量删除数据
export function del${className}(ids) {
  return request({
    url: '${baseRequestMapping}/' + ids,
    method: 'delete'
  })
}

// 修改数据
export function update${className}(data) {
  return request({
    url: '${baseRequestMapping}/edit',
    method: 'post',
    data: data
  })
}

// 添加数据
export function add${className}(data) {
  return request({
    url: '${baseRequestMapping}/add',
    method: 'post',
    data: data
  })
}

//导出数据列表
export function export${className}(query){
  return request({
    url: '${baseRequestMapping}/export',
    method: 'get',
    params: query
  })
}
