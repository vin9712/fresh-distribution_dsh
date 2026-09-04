package com.lin.jmreport;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.jmreport.desreport.render.handler.convert.ApiDataConvertAdapter;
import org.springframework.stereotype.Component;

/**
 * JimuReport API 数据集格式转换器——取列表行（配合 jimu_report_db.api_convert 使用）
 *
 * <p>与 {@link DeliveryDataConvertAdapter}（head 优先）配套：当 head 与 rows 同在一个响应里
 * （如 /print/deliveryMatrixData 返回 {head, columns, rows}），列表数据集需取 rows——
 * 若也配 head 优先的适配器，会把 head 对象当成单行数据，明细渲染为空。</p>
 *
 * <p>规则：rows 直接作为列表；无 rows 时回退 head 包成单行数组（兜底）。</p>
 *
 * @author dsh
 */
@Slf4j
@Component("deliveryRowsConvertAdapter")
public class DeliveryRowsConvertAdapter implements ApiDataConvertAdapter {

    @Override
    public String getData(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
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
        JSONArray rows = jsonObject.getJSONArray("rows");
        return rows == null ? "0" : String.valueOf(rows.size());
    }
}
