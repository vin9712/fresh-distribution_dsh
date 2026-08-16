package com.lin.distribution.service;

import java.util.List;

import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.PrintTemplate;

/**
 * 打印模板Service接口
 *
 * @author lin
 * @date 2024-12-18
 */
public interface PrintTemplateService {
    /**
     * 查询打印模板
     *
     * @param id 打印模板主键
     * @return 打印模板
     */
    PrintTemplate selectPrintTemplateById(Long id);

    /**
     * 查询打印模板列表
     *
     * @param printTemplate 打印模板
     * @return 打印模板集合
     */
    List<PrintTemplate> selectPrintTemplateList(PrintTemplate printTemplate);

    /**
     * 按送货单解析打印模板（三级绑定：客户+配送点组合 > 客户 > 全局默认；停用回退全局默认）
     *
     * @param deliveryOrder 送货单
     * @return 命中的模板（未配置任何模板则抛异常）
     */
    PrintTemplate resolveForDeliveryOrder(DeliveryOrder deliveryOrder);

    /**
     * 新增打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    int insertPrintTemplate(PrintTemplate printTemplate);

    /**
     * 修改打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    int updatePrintTemplate(PrintTemplate printTemplate);

    /**
     * 批量删除打印模板
     *
     * @param ids 需要删除的打印模板主键集合
     * @return 结果
     */
    int deletePrintTemplateByIds(Long[] ids);

    /**
     * 删除打印模板信息
     *
     * @param id 打印模板主键
     * @return 结果
     */
    int deletePrintTemplateById(Long id);
}
