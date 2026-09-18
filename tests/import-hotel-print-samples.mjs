/** 显式运行的数据导入工具。默认金鸿楼/金兴楼，--ligong 仅补丽宫。保留已有客户及配送点。
 * 环境：API_BASE、ERP_USER、ERP_PASSWORD、MYSQL_HOST、MYSQL_USER、MYSQL_PWD、MYSQL_DATABASE。
 * 默认仅创建草稿；通过预览后使用 --publish 发布。不会创建销售订单或修改参考模板。
 */
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomInt } from 'node:crypto';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const base = process.env.API_BASE || 'http://localhost:1025/dev-api';
for (const key of ['ERP_USER', 'ERP_PASSWORD', 'MYSQL_HOST', 'MYSQL_USER', 'MYSQL_PWD', 'MYSQL_DATABASE']) assert(process.env[key], `缺少环境变量 ${key}`);
const sql = text => execFileSync('mysql', ['-h', process.env.MYSQL_HOST, '-u', process.env.MYSQL_USER, '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', process.env.MYSQL_DATABASE, '-e', text], { encoding: 'utf8' }).trim();
/** 普通转义字面量（连接字符集 utf8mb4）：避免 COLLATE 混用，比较时自适应各表排序规则。 */
const literal = value => value == null ? 'NULL' : "'" + String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'";
const tableRows = (table, where) => {
  const cols = sql(`SHOW COLUMNS FROM ${table}`).split('\n').map(x => x.split('\t')[0]);
  return sql(`SELECT JSON_OBJECT(${cols.map(c => `'${c}',CAST(\`${c}\` AS CHAR)`).join(',')}) FROM ${table} WHERE ${where}`).split('\n').filter(Boolean).map(JSON.parse);
};
const insert = (table, row) => `INSERT INTO ${table} (${Object.keys(row).map(k => `\`${k}\``).join(',')}) VALUES (${Object.values(row).map(literal).join(',')});`;
const id = () => `${Date.now()}${randomInt(10000, 99999)}`;
let token;
async function api(path, method = 'GET', data) {
  const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(data === undefined ? {} : { body: JSON.stringify(data) }) });
  const result = await response.json();
  assert.equal(result.code, 200, `${method} ${path}: ${result.msg}`);
  return result;
}
token = (await api('/login', 'POST', { username: process.env.ERP_USER, password: process.env.ERP_PASSWORD })).token;
assert(token);
const reference = (await api('/partner/customer/list?name=' + encodeURIComponent('金满楼'))).data.find(c => c.name === '金满楼');
assert(reference, '缺少参考客户金满楼');
const refTemplate = tableRows('t_print_template', `customer_id=${Number(reference.id)} AND print_form='MATRIX' AND is_deleted=0`).find(t => Number(t.bind_type) === 2);
assert(refTemplate, '缺少金满楼客户级总单');
const refDatasets = tableRows('jimu_report_db', `jimu_report_id=${literal(refTemplate.content)}`);
assert.deepEqual(refDatasets.map(d => d.db_code).sort(), ['dm', 'hm']);
for (const ds of refDatasets) assert(ds.api_url.includes('/print/deliveryMatrixData?'), '参考模板不是动态矩阵 API');
const result = [];
// 班次中文名（字典优先，失败回落内置）：与后端 ShiftCodes.label 同口径
let shiftDict = [];
try { shiftDict = (await api('/system/dict/data/type/biz_shift_type')).data || []; } catch (e) { shiftDict = []; }
const shiftLabel = code => (shiftDict.find(d => d.value === code) || {}).dictLabel || ({ DAY: '白班', NIGHT: '夜班' })[code] || code;
const ligongOnly = process.argv.includes('--ligong');
const dcjOnly = process.argv.includes('--dachangjiang');
const targets = dcjOnly ? [['dachangjiang', '大长江']] : ligongOnly ? [['ligong', '丽宫']] : [['jinhong', '金鸿楼'], ['jinxing', '金兴楼']];
// 大长江：班次按「配置 + 订单维度」建模。三个配送点各自声明支持的班次，
// 矩阵列 = 配送点 × 班次；上一步把班次编码进名字的临时拆点（华铃白班等）已被本模型取代，逻辑删除保留审计。
// 注意：声明值只用于「新建配送点」；已存在点的 shift_codes 由业务在配送点页面维护，
// 脚本不覆盖（避免把人工维护的声明改回去）。
const DCJ_SHIFT_DEPTS = [
  { title: '华铃', mnemonic: 'HL', shiftCodes: 'DAY,NIGHT' },
  { title: '棠下', mnemonic: 'TX', shiftCodes: 'DAY,NIGHT' },
  { title: '大长江', mnemonic: 'DCJ', shiftCodes: 'DAY,NIGHT' },
];
const DCJ_SUPERSEDED_DEPTS = ['华铃白班', '华铃夜班', '大长江白班', '棠下白班'];
for (const [key, name] of targets) {
  const source = JSON.parse(readFileSync(root + `docs/assets/print/hotels/${key}-source.json`, 'utf8'));
  const columns = source.table.columns.slice(2);
  assert.deepEqual(columns.map(c => c.title), key === 'ligong' ? ['国中', '国点', '烧味', '饭堂', '西厨']
    : key === 'dachangjiang' ? ['华铃白班', '华铃夜班', '大长江白班', '棠下白班']
    : ['中厨', '点心', '味部', '铁板', '上杂', '福食', '鲍鱼']);
  let customer = (await api('/partner/customer/list?name=' + encodeURIComponent(name))).data.find(c => c.name === name);
  if (!customer) {
    await api('/partner/customer', 'POST', { name, type: reference.type, valid: 1, isDeleted: false, docScopeType: reference.docScopeType, docMergeSameItem: reference.docMergeSameItem, remark: '参照金满楼及总表 JSON 建档；地址电话待补充' });
    customer = (await api('/partner/customer/list?name=' + encodeURIComponent(name))).data.find(c => c.name === name);
  }
  assert(customer?.id);
  const points = (await api('/partner/customerDept/list?customerId=' + customer.id)).data;
  if (key === 'ligong' || key === 'dachangjiang') {
    // 依据自检建议：直接把序列校准到超过已有编号后缀（含已删点），避免唯一键冲突；幂等可重跑。
    sql(`START TRANSACTION;
      INSERT IGNORE INTO biz_code_seq (biz_key,seq,update_time) VALUES ('customerDeptNo:${Number(customer.id)}',0,NOW());
      UPDATE biz_code_seq SET seq=GREATEST(seq,(SELECT COALESCE(MAX(CAST(RIGHT(code,5) AS UNSIGNED)),0) FROM t_customer_dept WHERE customer_id=${Number(customer.id)})),update_time=NOW() WHERE biz_key='customerDeptNo:${Number(customer.id)}';
      COMMIT;`);
  }
  const newDepts = key === 'dachangjiang' ? DCJ_SHIFT_DEPTS : columns.map(c => ({ title: c.title, field: c.field }));
  const MNEMONIC = { zhongchu: 'ZC', dianxin: 'DX', weibu: 'WB', tieban: 'TB', shangza: 'SZ', fushi: 'FS', baoyu: 'BY', guozhong: 'GZ', guodian: 'GD', shaowei: 'SW', fantang: 'FT', xichu: 'XC' };
  if (key === 'dachangjiang') {
    // 客户级班次开关：只大长江开，其余客户不展示班次、行为与引入前一致
    const full = (await api('/partner/customer/' + customer.id)).data;
    if (full.shiftEnabled !== true) await api('/partner/customer', 'PUT', { ...full, shiftEnabled: true });
  }
  for (const dept of newDepts) {
    // 只认子配送点：同名根节点（parent_id=0）不是配送点，矩阵也不让它进列
    const exist = (await api('/partner/customerDept/list?customerId=' + customer.id)).data.find(p => p.name === dept.title && !p.isDeleted && Number(p.parentId) !== 0);
    if (!exist) {
      await api('/partner/customerDept', 'POST', { customerId: customer.id, name: dept.title, mnemonicCode: dept.mnemonic || MNEMONIC[dept.field], valid: 1, isDeleted: false, shiftCodes: dept.shiftCodes || '', remark: dept.shiftCodes ? `班次：${dept.shiftCodes}（s35）` : `来源：${source.reportName} / ${dept.field}；地址待补充` });
    } else if (exist.valid !== 1) {
      // 只恢复启用状态；**不覆盖已有点的班次声明**（业务在配送点页面维护，脚本不应改回去）
      await api('/partner/customerDept', 'PUT', { ...exist, valid: 1 });
    }
  }
  if (key === 'dachangjiang') {
    // 根节点不是配送点：清掉可能被误写的班次声明
    const root = (await api('/partner/customerDept/list?customerId=' + customer.id)).data.find(p => Number(p.parentId) === 0);
    if (root && (root.shiftCodes || '') !== '') await api('/partner/customerDept', 'PUT', { ...root, shiftCodes: '' });
  }
  if (key === 'dachangjiang') for (const title of DCJ_SUPERSEDED_DEPTS) {
    // 被班次模型取代的临时拆点：逻辑删除（无订单引用，保留审计可恢复）
    const superseded = (await api('/partner/customerDept/list?customerId=' + customer.id)).data.find(p => p.name === title && !p.isDeleted);
    if (superseded) await api('/partner/customerDept', 'PUT', { ...superseded, valid: 0, isDeleted: true, remark: '已由 s35 班次模型（配送点 + 班次）取代：不再用点名编码班次' });
  }
  const activePoints = (await api('/partner/customerDept/list?customerId=' + customer.id)).data.filter(p => Number(p.parentId) !== 0 && p.valid === 1);
  assert(activePoints.length <= 7, '七槽位模板无法覆盖新增后的全部点，请单独设计');
  // 槽位 = 矩阵可能产生的最大列数：启用班次的客户 = 各启用点声明班次之和（列 = 配送点×班次）；
  // 其余客户 = 每点一列（历史七槽位）。少给槽位会让列被挤出模板，重打时静默丢量。
  const shiftEnabledNow = key === 'dachangjiang';
  const slots = shiftEnabledNow
    ? activePoints.reduce((sum, p) => sum + (p.shiftCodes || '').split(',').map(s => s.trim()).filter(Boolean).length, 0)
    : 7;
  assert(slots <= 7, `${slots} 列超过单块七槽位，需列分页`);
  // 期望列名：启用班次的客户 = 点名 + 班次名（列 = 配送点×班次）；其余 = 点名
  const expectedColumns = shiftEnabledNow
    ? activePoints.flatMap(p => {
        const shifts = (p.shiftCodes || '').split(',').map(s => s.trim()).filter(Boolean);
        return shifts.map(s => p.name + shiftLabel(s));
      })
    : activePoints.map(p => p.name);
  let sample = tableRows('t_print_template', `name=${literal(source.reportName + '（静态样板）')} AND is_deleted=0`)[0];
  if (!sample) {
    await api('/print/template/import', 'POST', source);
    sample = tableRows('t_print_template', `name=${literal(source.reportName + '（静态样板）')} AND is_deleted=0`)[0];
  }
  assert(sample);
  const code = `HOTEL_${key.toUpperCase()}_MATRIX`;
  let template = tableRows('t_print_template', `code=${literal(code)} AND is_deleted=0`)[0];
  if (!template) {
    // 保留每家源版式；动态单据只绑定真实订单字段，绝不复制静态日期/数量/颜色标记。
    const report = tableRows('jimu_report', `id=${literal(sample.content)}`)[0];
    const reportId = id();
    const sheet = JSON.parse(report.json_str);
    if (key === 'ligong') {
      // 源样板仅五列；保留旧点心/味部不擅自合并，扩为七槽位，避免遗漏旧订单。
      for (let i = 3; i <= 10; i++) {
        sheet.cols[String(i)] = { width: 53 };
        sheet.rows['2'].cells[String(i)] = { text: i === 10 ? '合计' : '', style: 1 };
        sheet.rows['3'].cells[String(i)] = { text: '', style: i === 10 ? 6 : 1 };
      }
      sheet.dataRectWidth = 682;
      sheet.rows['0'].cells['1'].merge = [0, 9];
      sheet.rows['1'].cells = {
        '1': { text: '', style: 2, merge: [0, 4] },
        '6': { text: '点心早上6点送到', style: 4, merge: [0, 4] }
      };
      sheet.merges = ['B1:K1', 'B2:F2', 'G2:K2'];
    }
    sheet.excel_config_id = reportId;
    sheet.printConfig.watermarkShow = false;
    sheet.printConfig.watermarkText = '';
    if (key === 'dachangjiang') {
      // 大长江源样张是两级表头（华铃食堂跨白/夜班）+ 供应商备注列，均不能直接搬：
      // 矩阵是一点一列，班次维度无法表达；dm.remark 是价档备注而非供应商，不冒充。
      // 故重排为单级表头：名称|单位|各配送点|小计，列数 = 当前启用点数。
      sheet.rows = {
        len: 100,
        '0': { height: 24, cells: { '1': { text: '供货日期：${hm.deliveryDate}', style: 4, merge: [0, slots] } } },
        '1': { height: 22, cells: { '1': { text: '名称', style: 0 }, '2': { text: '单位', style: 1 } } },
        '2': { height: 22, cells: { '1': { text: '#{dm.productName}', style: 0 }, '2': { text: '#{dm.productUnit}', style: 1 } } },
        '3': { height: 24, cells: { '1': { text: '合　计', style: 6, merge: [0, 1] } } },
      };
      sheet.cols = { len: 100, 0: { width: 30 }, 1: { width: 118 }, 2: { width: 42 } };
      for (let i = 1; i <= slots; i++) {
        sheet.cols[String(i + 2)] = { width: 100 };
        sheet.rows['1'].cells[String(i + 2)] = { text: '${hm.c' + i + 'Name}', style: 1 };
        sheet.rows['2'].cells[String(i + 2)] = { text: '#{dm.c' + i + '}', style: 1 };
      }
      sheet.cols[String(slots + 3)] = { width: 70 };
      sheet.rows['1'].cells[String(slots + 3)] = { text: '小计', style: 1 };
      sheet.rows['2'].cells[String(slots + 3)] = { text: '#{dm.total}', style: 1 };
      sheet.rows['3'].cells[String(slots + 3)] = { text: '${hm.totalQuantity}', style: 6 };
      sheet.merges = ['B1:' + String.fromCharCode(66 + slots) + '1', 'B4:C4'];
    } else {
      sheet.rows['0'].cells['1'].text = '${hm.printTitle}';
      sheet.rows['1'].cells['1'].text = '配送日期：${hm.deliveryDate}  ${hm.colBlockLabel}';
      sheet.rows['3'].cells['1'].text = '#{dm.productName}';
      sheet.rows['3'].cells['2'].text = '#{dm.productUnit}';
      for (let i = 1; i <= 7; i++) {
        sheet.rows['2'].cells[String(i + 2)].text = '${hm.c' + i + 'Name}';
        sheet.rows['3'].cells[String(i + 2)].text = '#{dm.c' + i + '}';
      }
      sheet.rows['3'].cells['10'].text = '#{dm.total}';
    }
    report.id = reportId; report.code = code; report.name = name + '总单'; report.json_str = JSON.stringify(sheet);
    report.create_by = process.env.ERP_USER;
    const statements = [insert('jimu_report', report)];
    for (const ds of refDatasets) {
      const dsId = id();
      statements.push(insert('jimu_report_db', { ...ds, id: dsId, jimu_report_id: reportId, create_by: process.env.ERP_USER }));
      for (const table of ['jimu_report_db_field', 'jimu_report_db_param']) {
        const foreignKey = table === 'jimu_report_db_param' ? 'jimu_report_head_id' : 'jimu_report_db_id';
        for (const row of tableRows(table, `${foreignKey}=${literal(ds.id)}`)) statements.push(insert(table, { ...row, id: id(), [foreignKey]: dsId }));
      }
    }
    sql('START TRANSACTION;\n' + statements.join('\n') + '\nCOMMIT;');
    await api('/print/template', 'POST', { code, name: name + '总单', content: reportId, customerId: customer.id, bindType: 2, printForm: 'MATRIX', renderEngine: 'jimureport', type: 0, copies: 1, isDefault: '0', status: 0, isDeleted: false, testWatermark: true, remark: `${slots} 配送点总单；版式来自本客户 JSON，数量来自真实订单，静态样板单独保留` });
    template = tableRows('t_print_template', `code=${literal(code)} AND is_deleted=0`)[0];
    writeFileSync(root + `docs/assets/print/hotels/${key}-matrix-sheet.json`, JSON.stringify(sheet, null, 2) + '\n');
  }
  assert.equal(Number(template.customer_id), Number(customer.id));
  if (process.argv.includes('--publish') && Number(template.status) !== 2) await api(`/print/template/${template.id}/publish`, 'PUT');
  result.push({ name, customerId: customer.id, templateId: template.id, reportId: template.content, sampleReportId: sample.content, datasetCode: source.table.datasetCode, rows: source.datasets[0].jsonData.length, slots, deptNames: activePoints.map(p => p.name).sort(), expectedColumns: expectedColumns.sort() });
}
writeFileSync(root + `docs/assets/print/hotels/${dcjOnly ? 'dachangjiang-' : ligongOnly ? 'ligong-' : ''}import-result.json`, JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
