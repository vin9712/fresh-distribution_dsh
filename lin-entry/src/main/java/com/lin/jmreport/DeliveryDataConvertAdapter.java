package com.lin.jmreport;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.jmreport.desreport.render.handler.convert.ApiDataConvertAdapter;
import org.springframework.stereotype.Component;

/**
 * JimuReport API 数据集格式转换器（配合 jimu_report_db.api_convert 使用）
 * 本系统打印数据接口返回业务对象：
 *   /print/deliveryHead → {"head":{...}}    （单值数据集 hd）
 *   /print/deliveryData → {"rows":[...]}    （列表数据集 dd）
 * 转换规则：head 取对象包成单行数组；rows 直接作为列表。
 *
 * @author dsh
 */
@Slf4j
@Component("deliveryDataConvertAdapter")
public class DeliveryDataConvertAdapter implements ApiDataConvertAdapter {

    @Override
    public String getData(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        JSONObject head = jsonObject.getJSONObject("head");
        if (head != null) {
            JSONArray single = new JSONArray();
            single.add(head);
            return single.toJSONString();
        }
        JSONArray rows = jsonObject.getJSONArray("rows");
        return rows == null ? null : rows.toJSONString();
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
        JSONObject head = jsonObject.getJSONObject("head");
        if (head != null) {
            return "1";
        }
        JSONArray rows = jsonObject.getJSONArray("rows");
        return rows == null ? "0" : String.valueOf(rows.size());
    }
}
