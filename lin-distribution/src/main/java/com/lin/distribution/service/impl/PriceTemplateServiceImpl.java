package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.PriceTemplate;
import com.lin.distribution.domain.PriceTemplateCustomer;
import com.lin.distribution.domain.PriceTemplateSku;
import com.lin.distribution.mapper.PriceTemplateCustomerMapper;
import com.lin.distribution.mapper.PriceTemplateMapper;
import com.lin.distribution.mapper.PriceTemplateSkuMapper;
import com.lin.distribution.service.PriceTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 报价模板Service业务层处理
 *
 * @author dsh
 */
@Service
public class PriceTemplateServiceImpl implements PriceTemplateService {

    @Autowired
    private PriceTemplateMapper priceTemplateMapper;

    @Autowired
    private PriceTemplateSkuMapper priceTemplateSkuMapper;

    @Autowired
    private PriceTemplateCustomerMapper priceTemplateCustomerMapper;

    @Override
    public PriceTemplate selectPriceTemplateById(Long id) {
        return priceTemplateMapper.selectPriceTemplateById(id);
    }

    @Override
    public List<PriceTemplate> selectPriceTemplateList(PriceTemplate priceTemplate) {
        return priceTemplateMapper.selectPriceTemplateList(priceTemplate);
    }

    @Override
    public int insertPriceTemplate(PriceTemplate priceTemplate) {
        if (priceTemplate == null || priceTemplate.getName() == null || priceTemplate.getName().trim().isEmpty()) {
            throw new ServiceException("模板名称不能为空");
        }
        if (priceTemplate.getStatus() == null || priceTemplate.getStatus().isEmpty()) {
            priceTemplate.setStatus("0");
        }
        if (priceTemplate.getIsDefault() == null || priceTemplate.getIsDefault().isEmpty()) {
            priceTemplate.setIsDefault("0");
        }
        priceTemplate.setCreateTime(DateUtils.getNowDate());
        return priceTemplateMapper.insertPriceTemplate(priceTemplate);
    }

    @Override
    public int updatePriceTemplate(PriceTemplate priceTemplate) {
        if (priceTemplate == null || priceTemplate.getId() == null) {
            throw new ServiceException("模板id不能为空");
        }
        priceTemplate.setUpdateTime(DateUtils.getNowDate());
        return priceTemplateMapper.updatePriceTemplate(priceTemplate);
    }

    @Override
    @Transactional
    public int deletePriceTemplateByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            priceTemplateSkuMapper.deletePriceTemplateSkuByTemplateId(id);
            priceTemplateCustomerMapper.deleteByTemplateId(id);
        }
        return priceTemplateMapper.deletePriceTemplateByIds(ids);
    }

    @Override
    @Transactional
    public int setDefault(Long id) {
        if (id == null) {
            throw new ServiceException("模板id不能为空");
        }
        PriceTemplate target = priceTemplateMapper.selectPriceTemplateById(id);
        if (target == null) {
            throw new ServiceException("报价模板不存在");
        }

        // 其余模板 is_default 置 0
        List<PriceTemplate> all = priceTemplateMapper.selectPriceTemplateList(new PriceTemplate());
        for (PriceTemplate t : all) {
            if (t.getId().equals(id)) {
                continue;
            }
            PriceTemplate reset = new PriceTemplate();
            reset.setId(t.getId());
            reset.setIsDefault("0");
            reset.setUpdateTime(DateUtils.getNowDate());
            priceTemplateMapper.updatePriceTemplate(reset);
        }

        // 目标模板 is_default 置 1
        PriceTemplate update = new PriceTemplate();
        update.setId(id);
        update.setIsDefault("1");
        update.setUpdateTime(DateUtils.getNowDate());
        return priceTemplateMapper.updatePriceTemplate(update);
    }

    @Override
    @Transactional
    public int bindCustomer(Long templateId, Long customerId) {
        if (templateId == null || customerId == null) {
            throw new ServiceException("模板或客户不能为空");
        }
        if (priceTemplateMapper.selectPriceTemplateById(templateId) == null) {
            throw new ServiceException("报价模板不存在");
        }

        // 先删除该客户在其他模板的绑定（一个客户最多绑定一个模板）
        PriceTemplateCustomer existing = priceTemplateCustomerMapper.selectByCustomerId(customerId);
        if (existing != null) {
            priceTemplateCustomerMapper.deleteByTemplateIdAndCustomerId(existing.getTemplateId(), customerId);
        }

        PriceTemplateCustomer record = new PriceTemplateCustomer();
        record.setTemplateId(templateId);
        record.setCustomerId(customerId);
        return priceTemplateCustomerMapper.insert(record);
    }

    @Override
    public int unbindCustomer(Long templateId, Long customerId) {
        if (templateId == null || customerId == null) {
            throw new ServiceException("模板或客户不能为空");
        }
        return priceTemplateCustomerMapper.deleteByTemplateIdAndCustomerId(templateId, customerId);
    }

    @Override
    public List<PriceTemplateSku> selectPriceTemplateSkuList(Long templateId) {
        return priceTemplateSkuMapper.selectPriceTemplateSkuList(templateId);
    }

    @Override
    public int insertPriceTemplateSku(PriceTemplateSku priceTemplateSku) {
        if (priceTemplateSku == null || priceTemplateSku.getTemplateId() == null) {
            throw new ServiceException("模板不能为空");
        }
        if (priceTemplateSku.getSkuId() == null) {
            throw new ServiceException("SKU不能为空");
        }
        if (priceTemplateSku.getUnitPrice() == null) {
            throw new ServiceException("单价不能为空");
        }
        priceTemplateSku.setCreateTime(DateUtils.getNowDate());
        return priceTemplateSkuMapper.insertPriceTemplateSku(priceTemplateSku);
    }

    @Override
    public int updatePriceTemplateSku(PriceTemplateSku priceTemplateSku) {
        if (priceTemplateSku == null || priceTemplateSku.getId() == null) {
            throw new ServiceException("明细id不能为空");
        }
        priceTemplateSku.setUpdateTime(DateUtils.getNowDate());
        return priceTemplateSkuMapper.updatePriceTemplateSku(priceTemplateSku);
    }

    @Override
    public int deletePriceTemplateSkuByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return priceTemplateSkuMapper.deletePriceTemplateSkuByIds(ids);
    }

    @Override
    public List<PriceTemplateCustomer> selectPriceTemplateCustomerList(Long templateId) {
        return priceTemplateCustomerMapper.selectByTemplateId(templateId);
    }
}
