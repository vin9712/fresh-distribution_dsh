package com.lin.jmreport;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.jmreport.desreport.render.handler.convert.ApiDataConvertAdapter;
import org.springframework.stereotype.Component;

/**
 * JimuReport API 数据集格式转换器——取 columns 列表（配合 jimu_report_db.api_convert 使用）
 *
 * <p>专用于总单长表模板（横向动态列，通用模板设计文档 §4.5）：响应 {head, columns, rows} 里
 * columns 是配送点列定义（deptSeq/deptName/...），作为横向分组表头数据集（#{hc.groupRight(deptName)}）。</p>
 *
 * <p>规则：columns 直接作为列表；无 columns 回退 rows，再兜底 head 单行。</p>
 *
 * @author dsh
 */
@Slf4j
@Component("deliveryMatrixColumnsConvertAdapter")
public class DeliveryMatrixColumnsConvertAdapter implements ApiDataConvertAdapter {

    @Override
    public String getData(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        JSONArray columns = jsonObject.getJSONArray("columns");
        if (columns != null) {
            return columns.toJSONString();
        }
        JSONArray rows = jsonObject.getJSONArray("rows");
        if (rows != null) {
            return rows.toJSONString();
        }
        JSONObject head = jsonObject.getJSONObject("head");
        if (head != null) {
            JSONArray single = new JSONArray();
            single.add(head);
            return single.toJSONString();
        }
        return null;
    }

    @Override
    public String getLinks(JSONObject jsonObject) {
        return null;
    }

    @Override
    public String getTotal(JSONObject jsonObject) {
        return getCount(jsonObject);
    }

    @Override
    public String getCount(JSONObject jsonObject) {
        if (jsonObject == null) {
            return "0";
        }
        JSONArray columns = jsonObject.getJSONArray("columns");
        if (columns != null) {
            return String.valueOf(columns.size());
        }
        JSONArray rows = jsonObject.getJSONArray("rows");
        return rows == null ? "0" : String.valueOf(rows.size());
    }
}
