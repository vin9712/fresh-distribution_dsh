package com.lin.distribution.service;

import com.lin.distribution.domain.PriceTemplate;
import com.lin.distribution.domain.PriceTemplateCustomer;
import com.lin.distribution.domain.PriceTemplateSku;

import java.util.List;

/**
 * 报价模板Service接口
 *
 * @author dsh
 */
public interface PriceTemplateService {

    /** 查询报价模板 */
    PriceTemplate selectPriceTemplateById(Long id);

    /** 查询报价模板列表 */
    List<PriceTemplate> selectPriceTemplateList(PriceTemplate priceTemplate);

    /** 新增报价模板 */
    int insertPriceTemplate(PriceTemplate priceTemplate);

    /** 修改报价模板 */
    int updatePriceTemplate(PriceTemplate priceTemplate);

    /** 批量删除报价模板（级联删除 SKU 价与客户绑定） */
    int deletePriceTemplateByIds(Long[] ids);

    /** 设为默认模板（事务：本模板 is_default=1，其余置 0） */
    int setDefault(Long id);

    /** 绑定客户（先删该客户在其他模板的绑定再插入，保证一个客户最多绑一个模板） */
    int bindCustomer(Long templateId, Long customerId);

    /** 解绑客户 */
    int unbindCustomer(Long templateId, Long customerId);

    /** 查询模板 SKU 价格明细列表 */
    List<PriceTemplateSku> selectPriceTemplateSkuList(Long templateId);

    /** 新增模板 SKU 价格明细 */
    int insertPriceTemplateSku(PriceTemplateSku priceTemplateSku);

    /** 修改模板 SKU 价格明细 */
    int updatePriceTemplateSku(PriceTemplateSku priceTemplateSku);

    /** 批量删除模板 SKU 价格明细 */
    int deletePriceTemplateSkuByIds(Long[] ids);

    /** 查询模板绑定的客户列表 */
    List<PriceTemplateCustomer> selectPriceTemplateCustomerList(Long templateId);
}
