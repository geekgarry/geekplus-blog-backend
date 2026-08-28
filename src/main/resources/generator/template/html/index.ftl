<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${functionName!title}管理</title>
  <!-- Bootstrap 5 + Icons（CDN，可按部署改为本地静态资源） -->
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
  <style>
    body { background: #f5f7fa; }
    .page-card { background: #fff; border-radius: .5rem; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
    .query-row + .query-row { margin-top: .5rem; }
    .query-actions { margin-top: .75rem; }
    .table-wrap { overflow-x: auto; }
    .toolbar { gap: .5rem; }
    #loadingMask {
      position: absolute; inset: 0; background: rgba(255,255,255,.65);
      display: none; align-items: center; justify-content: center; z-index: 5;
    }
    .list-panel { position: relative; min-height: 180px; }
  </style>
</head>
<body>
<div class="container-fluid py-3">
  <div class="d-flex align-items-center justify-content-between mb-3">
    <h4 class="mb-0">${functionName!title}</h4>
    <small class="text-muted">Bootstrap + AJAX · 动态条件查询（conditionsJson）</small>
  </div>

  <!-- 动态查询（对齐 vue / vue-antd：字段 + 运算符 + 值） -->
  <div id="queryPanel" class="page-card p-3 mb-3">
    <div id="queryRows"></div>
    <div class="query-actions d-flex flex-wrap align-items-center gap-2">
      <button type="button" class="btn btn-outline-secondary btn-sm" id="btnAddCond">
        <i class="bi bi-plus-lg"></i> 增加条件
      </button>
      <button type="button" class="btn btn-primary btn-sm" id="btnSearch">
        <i class="bi bi-search"></i> 搜索
      </button>
      <button type="button" class="btn btn-outline-secondary btn-sm" id="btnReset">
        <i class="bi bi-arrow-clockwise"></i> 重置
      </button>
      <div class="form-check form-switch ms-2">
        <input class="form-check-input" type="checkbox" id="cfgShowOpLabel" checked>
        <label class="form-check-label" for="cfgShowOpLabel">显示运算符文案</label>
      </div>
      <div class="input-group input-group-sm" style="width: 160px;">
        <span class="input-group-text">最大条件</span>
        <input type="number" class="form-control" id="cfgMaxCond" min="1" max="20" value="8">
      </div>
    </div>
  </div>

  <!-- 工具栏 -->
  <div class="page-card p-3 mb-3">
    <div class="d-flex flex-wrap toolbar">
      <button type="button" class="btn btn-primary btn-sm" id="btnAdd">
        <i class="bi bi-plus"></i> 新增
      </button>
      <button type="button" class="btn btn-success btn-sm" id="btnEdit" disabled>
        <i class="bi bi-pencil"></i> 修改
      </button>
      <button type="button" class="btn btn-danger btn-sm" id="btnDelete" disabled>
        <i class="bi bi-trash"></i> 删除
      </button>
      <button type="button" class="btn btn-warning btn-sm text-white" id="btnExport">
        <i class="bi bi-download"></i> 导出
      </button>
      <button type="button" class="btn btn-outline-secondary btn-sm ms-auto" id="btnToggleQuery">
        <i class="bi bi-funnel"></i> 显示/隐藏搜索
      </button>
    </div>
  </div>

  <!-- 表格 -->
  <div class="page-card p-3 list-panel">
    <div id="loadingMask"><div class="spinner-border text-primary" role="status"></div></div>
    <div class="table-wrap">
      <table class="table table-hover table-sm align-middle mb-0" id="dataTable">
        <thead class="table-light">
        <tr>
          <th style="width:42px;"><input type="checkbox" class="form-check-input" id="checkAll"></th>
<#if allColumn?exists>
<#list allColumn as column>
          <th>${column.columnComment}</th>
</#list>
</#if>
          <th style="width:140px;">操作</th>
        </tr>
        </thead>
        <tbody id="dataBody"></tbody>
      </table>
    </div>
    <div class="d-flex flex-wrap justify-content-between align-items-center mt-3 gap-2">
      <div class="text-muted small">共 <span id="totalText">0</span> 条</div>
      <nav>
        <ul class="pagination pagination-sm mb-0" id="pager"></ul>
      </nav>
      <div class="input-group input-group-sm" style="width: 140px;">
        <span class="input-group-text">每页</span>
        <select class="form-select" id="pageSize">
          <option value="10" selected>10</option>
          <option value="20">20</option>
          <option value="50">50</option>
        </select>
      </div>
    </div>
  </div>
</div>

<!-- 新增 / 修改弹窗 -->
<div class="modal fade" id="editModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog modal-lg modal-dialog-scrollable">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title" id="editModalTitle">添加数据</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
      </div>
      <div class="modal-body">
        <form id="editForm" class="row g-3">
          <input type="hidden" name="${pkColumn.smallColumnName}" id="f_${pkColumn.smallColumnName}">
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1'>
<#if (column.columnDataType=='text'||column.columnDataType=='tinytext'||column.columnDataType=='longtext'||column.columnDataType=='mediumtext')>
          <div class="col-12">
            <label class="form-label">${column.columnComment}</label>
            <textarea class="form-control" rows="3" name="${column.smallColumnName}" id="f_${column.smallColumnName}" placeholder="请输入${column.columnComment}"></textarea>
          </div>
<#elseif (column.columnDataType=='int'||column.columnDataType=='tinyint'||column.columnDataType=='smallint'||column.columnDataType=='bigint')>
          <div class="col-md-6">
            <label class="form-label">${column.columnComment}</label>
            <input type="number" class="form-control" name="${column.smallColumnName}" id="f_${column.smallColumnName}" placeholder="请输入${column.columnComment}">
          </div>
<#else>
          <div class="col-md-6">
            <label class="form-label">${column.columnComment}</label>
            <input type="text" class="form-control" name="${column.smallColumnName}" id="f_${column.smallColumnName}" placeholder="请输入${column.columnComment}">
          </div>
</#if>
</#if>
</#list>
</#if>
        </form>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
        <button type="button" class="btn btn-primary" id="btnSubmit">确定</button>
      </div>
    </div>
  </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/jquery@3.7.1/dist/jquery.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script>
(function ($) {
  'use strict';

  /** 接口前缀：相对当前站点，或改成完整后端地址如 https://api.example.com */
  var API_PREFIX = '';
  var BASE_MAPPING = '${baseRequestMapping}';
  var PK = '${pkColumn.smallColumnName}';
  var TOKEN_COOKIE = 'Plus-Token';

  var FIELD_OPTIONS = [
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1' && (column.columnDataType=='varchar'||column.columnDataType=='char'||column.columnDataType=='int'||column.columnDataType=='tinyint'||column.columnDataType=='bigint'||column.columnDataType=='smallint')>
    { value: '${column.smallColumnName}', label: '${column.columnComment?js_string}' },
</#if>
</#list>
</#if>
  ];

  var COLUMNS = [
<#if allColumn?exists>
<#list allColumn as column>
    { field: '${column.smallColumnName}', label: '${column.columnComment?js_string}', isDate: ${(column.javaType == 'Date')?string('true','false')} },
</#list>
</#if>
  ];

  var OPERATORS = [
    { value: 'eq', label: '等于', symbol: '=' },
    { value: 'ne', label: '不等于', symbol: '≠' },
    { value: 'gt', label: '大于', symbol: '>' },
    { value: 'ge', label: '大于等于', symbol: '≥' },
    { value: 'lt', label: '小于', symbol: '<' },
    { value: 'le', label: '小于等于', symbol: '≤' },
    { value: 'like', label: '包含', symbol: '∋' },
    { value: 'notLike', label: '不包含', symbol: '∌' },
    { value: 'isNull', label: '为空', symbol: '∅' },
    { value: 'isNotNull', label: '不为空', symbol: '≠∅' }
  ];

  var state = {
    pageNum: 1,
    pageSize: 10,
    total: 0,
    list: [],
    selected: [],
    maxConditions: 8,
    showOpLabel: true
  };

  var editModal;

  function getToken() {
    var m = document.cookie.match(new RegExp('(?:^|; )' + TOKEN_COOKIE.replace(/([.$?*|{}()[\]\\/+^])/g, '\\$1') + '=([^;]*)'));
    if (m) return decodeURIComponent(m[1]);
    try { return localStorage.getItem(TOKEN_COOKIE) || sessionStorage.getItem(TOKEN_COOKIE) || ''; } catch (e) { return ''; }
  }

  function ajax(opts) {
    var headers = opts.headers || {};
    var token = getToken();
    if (token) {
      headers['Plus-Token'] = token;
      headers['Authorization'] = 'Bearer ' + token;
    }
    var isJsonBody = !!(opts.contentType && String(opts.contentType).indexOf('application/json') >= 0);
    return $.ajax({
      url: API_PREFIX + opts.url,
      method: opts.method || 'GET',
      data: opts.data,
      contentType: opts.contentType,
      processData: isJsonBody ? false : true,
      dataType: 'json',
      headers: headers,
      traditional: true
    });
  }

  function isNullOp(op) {
    return op === 'isNull' || op === 'isNotNull';
  }

  function defaultConditions() {
    var first = FIELD_OPTIONS.length ? FIELD_OPTIONS[0].value : '';
    return [
      { field: first, op: 'like', value: '' },
      { field: '', op: 'eq', value: '' }
    ];
  }

  function renderOperatorOptions($select) {
    var html = OPERATORS.map(function (op) {
      var text = state.showOpLabel ? (op.label + ' (' + op.symbol + ')') : op.symbol;
      return '<option value="' + op.value + '">' + text + '</option>';
    }).join('');
    $select.html(html);
  }

  function renderFieldOptions($select, selected) {
    var html = '<option value="">选择字段</option>' + FIELD_OPTIONS.map(function (f) {
      return '<option value="' + f.value + '"' + (f.value === selected ? ' selected' : '') + '>' + f.label + '</option>';
    }).join('');
    $select.html(html);
  }

  function addQueryRow(cond) {
    cond = cond || { field: '', op: 'eq', value: '' };
    var $row = $('<div class="row g-2 align-items-end query-row"></div>');
    var $field = $('<select class="form-select form-select-sm cond-field"></select>');
    var $op = $('<select class="form-select form-select-sm cond-op"></select>');
    var $val = $('<input type="text" class="form-control form-control-sm cond-value" placeholder="请输入值">');
    var $del = $('<button type="button" class="btn btn-outline-danger btn-sm cond-del"><i class="bi bi-dash-lg"></i> 删除</button>');

    renderFieldOptions($field, cond.field);
    renderOperatorOptions($op);
    $op.val(cond.op || 'eq');
    $val.val(cond.value || '');
    if (isNullOp($op.val())) $val.prop('disabled', true).val('');

    $row.append($('<div class="col-md-3"></div>').append('<label class="form-label small mb-1">查询字段</label>').append($field));
    $row.append($('<div class="col-md-3"></div>').append('<label class="form-label small mb-1">运算符</label>').append($op));
    $row.append($('<div class="col-md-4"></div>').append('<label class="form-label small mb-1">值</label>').append($val));
    $row.append($('<div class="col-md-2"></div>').append($del));
    $('#queryRows').append($row);
  }

  function collectConditions() {
    var list = [];
    $('#queryRows .query-row').each(function () {
      var field = $(this).find('.cond-field').val();
      var op = $(this).find('.cond-op').val();
      var value = $(this).find('.cond-value').val();
      if (!field || !op) return;
      if (isNullOp(op)) {
        list.push({ field: field, op: op, value: null });
        return;
      }
      if (value === undefined || value === null || value === '') return;
      list.push({ field: field, op: op, value: value });
    });
    return list;
  }

  /** 对齐前端 buildDynamicQueryParams：conditionsJson + eq/like 扁平兼容 */
  function buildQueryParams() {
    var params = {
      pageNum: state.pageNum,
      pageSize: state.pageSize
    };
    var dyn = collectConditions();
    dyn.forEach(function (c) {
      if (c.op === 'eq' || c.op === 'like') {
        params[c.field] = c.value;
      }
    });
    if (dyn.length) {
      params.conditionsJson = JSON.stringify(dyn);
    }
    return params;
  }

  function setLoading(on) {
    $('#loadingMask').css('display', on ? 'flex' : 'none');
  }

  function escapeHtml(s) {
    if (s === null || s === undefined) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function formatCell(col, row) {
    var v = row[col.field];
    if (v === null || v === undefined) return '';
    if (col.isDate) {
      try {
        var d = new Date(v);
        if (!isNaN(d.getTime())) {
          return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' +
            String(d.getDate()).padStart(2, '0') + ' ' + String(d.getHours()).padStart(2, '0') + ':' +
            String(d.getMinutes()).padStart(2, '0') + ':' + String(d.getSeconds()).padStart(2, '0');
        }
      } catch (e) { /* ignore */ }
    }
    return escapeHtml(v);
  }

  function renderTable() {
    var html = '';
    if (!state.list.length) {
      html = '<tr><td colspan="' + (COLUMNS.length + 2) + '" class="text-center text-muted py-4">暂无数据</td></tr>';
    } else {
      state.list.forEach(function (row) {
        var id = row[PK];
        var checked = state.selected.indexOf(id) >= 0 ? ' checked' : '';
        html += '<tr data-id="' + escapeHtml(id) + '">';
        html += '<td><input type="checkbox" class="form-check-input row-check" value="' + escapeHtml(id) + '"' + checked + '></td>';
        COLUMNS.forEach(function (col) {
          html += '<td>' + formatCell(col, row) + '</td>';
        });
        html += '<td>';
        html += '<button type="button" class="btn btn-link btn-sm p-0 me-2 btn-row-edit" data-id="' + escapeHtml(id) + '">修改</button>';
        html += '<button type="button" class="btn btn-link btn-sm p-0 text-danger btn-row-del" data-id="' + escapeHtml(id) + '">删除</button>';
        html += '</td></tr>';
      });
    }
    $('#dataBody').html(html);
    $('#totalText').text(state.total);
    updateToolbar();
    renderPager();
  }

  function renderPager() {
    var pages = Math.max(1, Math.ceil(state.total / state.pageSize) || 1);
    if (state.pageNum > pages) state.pageNum = pages;
    var html = '';
    html += '<li class="page-item' + (state.pageNum <= 1 ? ' disabled' : '') + '"><a class="page-link" href="#" data-page="' + (state.pageNum - 1) + '">上一页</a></li>';
    var start = Math.max(1, state.pageNum - 2);
    var end = Math.min(pages, start + 4);
    for (var i = start; i <= end; i++) {
      html += '<li class="page-item' + (i === state.pageNum ? ' active' : '') + '"><a class="page-link" href="#" data-page="' + i + '">' + i + '</a></li>';
    }
    html += '<li class="page-item' + (state.pageNum >= pages ? ' disabled' : '') + '"><a class="page-link" href="#" data-page="' + (state.pageNum + 1) + '">下一页</a></li>';
    $('#pager').html(html);
  }

  function updateToolbar() {
    var n = state.selected.length;
    $('#btnEdit').prop('disabled', n !== 1);
    $('#btnDelete').prop('disabled', n === 0);
    var all = state.list.length > 0 && state.list.every(function (r) { return state.selected.indexOf(r[PK]) >= 0; });
    $('#checkAll').prop('checked', all);
  }

  function getList() {
    setLoading(true);
    ajax({
      url: BASE_MAPPING + '/list',
      method: 'GET',
      data: buildQueryParams()
    }).done(function (res) {
      var ok = res && (res.code === 200 || res.code === '200' || res.code === 0);
      state.list = (ok ? (res.rows || res.data || []) : []) || [];
      state.total = ok ? (res.total || state.list.length || 0) : 0;
      state.selected = [];
      renderTable();
    }).fail(function (xhr) {
      toast('列表加载失败：' + (xhr.responseJSON && xhr.responseJSON.msg ? xhr.responseJSON.msg : xhr.status), 'danger');
      state.list = [];
      state.total = 0;
      renderTable();
    }).always(function () {
      setLoading(false);
    });
  }

  function toast(msg, type) {
    type = type || 'primary';
    var $el = $('<div class="alert alert-' + type + ' position-fixed top-0 start-50 translate-middle-x mt-3 shadow" style="z-index:2000;min-width:240px;">' + escapeHtml(msg) + '</div>');
    $('body').append($el);
    setTimeout(function () { $el.fadeOut(300, function () { $el.remove(); }); }, 2200);
  }

  function resetForm() {
    $('#editForm')[0].reset();
    $('#f_' + PK).val('');
  }

  function fillForm(data) {
    resetForm();
    if (!data) return;
    Object.keys(data).forEach(function (k) {
      var $el = $('#f_' + k);
      if ($el.length) $el.val(data[k] == null ? '' : data[k]);
    });
  }

  function openAdd() {
    resetForm();
    $('#editModalTitle').text('添加数据');
    editModal.show();
  }

  function openEdit(id) {
    if (!id) return;
    setLoading(true);
    ajax({
      url: BASE_MAPPING + '/' + id,
      method: 'GET'
    }).done(function (res) {
      var data = res && (res.data || res);
      if (!data || (res.code && res.code !== 200 && res.code !== '200' && res.code !== 0)) {
        toast((res && res.msg) || '获取详情失败', 'danger');
        return;
      }
      fillForm(data);
      $('#editModalTitle').text('修改数据');
      editModal.show();
    }).fail(function () {
      toast('获取详情失败', 'danger');
    }).always(function () {
      setLoading(false);
    });
  }

  function submitForm() {
    var payload = {};
    $('#editForm').serializeArray().forEach(function (item) {
      payload[item.name] = item.value;
    });
    var idVal = payload[PK];
    var isUpdate = idVal !== undefined && idVal !== null && String(idVal) !== '';
    var url = BASE_MAPPING + (isUpdate ? '/update' : '/add');
    setLoading(true);
    ajax({
      url: url,
      method: 'POST',
      data: JSON.stringify(payload),
      contentType: 'application/json;charset=UTF-8'
    }).done(function (res) {
      var ok = res && (res.code === 200 || res.code === '200' || res.code === 0);
      if (!ok) {
        toast((res && res.msg) || '保存失败', 'danger');
        return;
      }
      toast(isUpdate ? '修改成功' : '新增成功', 'success');
      editModal.hide();
      getList();
    }).fail(function (xhr) {
      toast('保存失败：' + (xhr.responseJSON && xhr.responseJSON.msg ? xhr.responseJSON.msg : xhr.status), 'danger');
    }).always(function () {
      setLoading(false);
    });
  }

  function doDelete(ids) {
    if (!ids || !ids.length) return;
    if (!window.confirm('是否确认删除编号为 "' + ids.join(',') + '" 的数据？')) return;
    setLoading(true);
    ajax({
      url: BASE_MAPPING + '/' + ids.join(','),
      method: 'DELETE'
    }).done(function (res) {
      var ok = res && (res.code === 200 || res.code === '200' || res.code === 0);
      if (!ok) {
        toast((res && res.msg) || '删除失败', 'danger');
        return;
      }
      toast('删除成功', 'success');
      getList();
    }).fail(function () {
      toast('删除失败', 'danger');
    }).always(function () {
      setLoading(false);
    });
  }

  function doExport() {
    if (!window.confirm('是否确认导出当前查询条件下的数据？')) return;
    var params = buildQueryParams();
    var qs = $.param(params);
    var url = API_PREFIX + BASE_MAPPING + '/export' + (qs ? ('?' + qs) : '');
    var token = getToken();
    // 简单方式：带 token 的新窗口（若后端要求 Header，请改为 blob 下载）
    if (token) {
      url += (url.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(token);
    }
    window.open(url, '_blank');
  }

  function refreshOpLabels() {
    $('#queryRows .query-row').each(function () {
      var $op = $(this).find('.cond-op');
      var cur = $op.val();
      renderOperatorOptions($op);
      $op.val(cur);
    });
  }

  $(function () {
    editModal = new bootstrap.Modal(document.getElementById('editModal'));

    defaultConditions().forEach(addQueryRow);

    $('#btnAddCond').on('click', function () {
      if ($('#queryRows .query-row').length >= state.maxConditions) {
        toast('最多添加 ' + state.maxConditions + ' 个条件', 'warning');
        return;
      }
      addQueryRow();
    });

    $('#queryRows').on('click', '.cond-del', function () {
      if ($('#queryRows .query-row').length <= 1) return;
      $(this).closest('.query-row').remove();
    });

    $('#queryRows').on('change', '.cond-op', function () {
      var $val = $(this).closest('.query-row').find('.cond-value');
      if (isNullOp($(this).val())) {
        $val.prop('disabled', true).val('');
      } else {
        $val.prop('disabled', false);
      }
    });

    $('#btnSearch').on('click', function () {
      state.pageNum = 1;
      getList();
    });

    $('#btnReset').on('click', function () {
      $('#queryRows').empty();
      defaultConditions().forEach(addQueryRow);
      state.pageNum = 1;
      getList();
    });

    $('#cfgMaxCond').on('change', function () {
      var n = parseInt($(this).val(), 10);
      if (isNaN(n) || n < 1) n = 1;
      if (n > 20) n = 20;
      state.maxConditions = n;
      $(this).val(n);
    });

    $('#cfgShowOpLabel').on('change', function () {
      state.showOpLabel = $(this).is(':checked');
      refreshOpLabels();
    });

    $('#btnToggleQuery').on('click', function () {
      $('#queryPanel').slideToggle(150);
    });

    $('#btnAdd').on('click', openAdd);
    $('#btnEdit').on('click', function () {
      if (state.selected.length === 1) openEdit(state.selected[0]);
    });
    $('#btnDelete').on('click', function () {
      doDelete(state.selected.slice());
    });
    $('#btnExport').on('click', doExport);
    $('#btnSubmit').on('click', submitForm);

    $('#dataBody').on('change', '.row-check', function () {
      var id = $(this).val();
      // 尽量保留原始类型（数字主键）
      var raw = state.list.map(function (r) { return r[PK]; }).filter(function (x) { return String(x) === String(id); })[0];
      id = raw !== undefined ? raw : id;
      if ($(this).is(':checked')) {
        if (state.selected.indexOf(id) < 0) state.selected.push(id);
      } else {
        state.selected = state.selected.filter(function (x) { return String(x) !== String(id); });
      }
      updateToolbar();
    });

    $('#checkAll').on('change', function () {
      var on = $(this).is(':checked');
      state.selected = on ? state.list.map(function (r) { return r[PK]; }) : [];
      renderTable();
    });

    $('#dataBody').on('click', '.btn-row-edit', function () {
      openEdit($(this).data('id'));
    });
    $('#dataBody').on('click', '.btn-row-del', function () {
      doDelete([$(this).data('id')]);
    });

    $('#pager').on('click', 'a.page-link', function (e) {
      e.preventDefault();
      var p = parseInt($(this).data('page'), 10);
      var pages = Math.max(1, Math.ceil(state.total / state.pageSize) || 1);
      if (isNaN(p) || p < 1 || p > pages || p === state.pageNum) return;
      state.pageNum = p;
      getList();
    });

    $('#pageSize').on('change', function () {
      state.pageSize = parseInt($(this).val(), 10) || 10;
      state.pageNum = 1;
      getList();
    });

    getList();
  });
})(jQuery);
</script>
</body>
</html>
