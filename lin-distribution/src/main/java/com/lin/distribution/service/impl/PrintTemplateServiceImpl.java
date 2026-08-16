package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.DeliveryOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.PrintTemplateMapper;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.service.PrintTemplateService;

/**
 * 打印模板Service业务层处理
 *
 * @author lin
 * @date 2024-12-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateServiceImpl implements PrintTemplateService {
    private final PrintTemplateMapper printTemplateMapper;

    /**
     * 查询打印模板
     *
     * @param id 打印模板主键
     * @return 打印模板
     */
    @Override
    public PrintTemplate selectPrintTemplateById(Long id) {
        return printTemplateMapper.selectPrintTemplateById(id);
    }

    /**
     * 查询打印模板列表
     *
     * @param printTemplate 打印模板
     * @return 打印模板
     */
    @Override
    public List<PrintTemplate> selectPrintTemplateList(PrintTemplate printTemplate) {
        return printTemplateMapper.selectPrintTemplateList(printTemplate);
    }

    /**
     * 三级绑定解析（客户+配送点组合 > 客户 > 全局默认；停用模板不参与，自动回退）
     */
    @Override
    public PrintTemplate resolveForDeliveryOrder(DeliveryOrder deliveryOrder) {
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        PrintTemplate template = printTemplateMapper.selectBindTemplate(deliveryOrder.getCustomerId(), deliveryOrder.getDeliveryPointId());
        if (template == null) {
            throw new ServiceException("未配置打印模板，请先在打印模板页面配置全局默认模板");
        }
        return template;
    }

    /**
     * 新增打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    @Override
    public int insertPrintTemplate(PrintTemplate printTemplate) {
        printTemplate.setCreateTime(DateUtils.getNowDate());
        return printTemplateMapper.insertPrintTemplate(printTemplate);
    }

    /**
     * 修改打印模板
     *
     * @param printTemplate 打印模板
     * @return 结果
     */
    @Override
    public int updatePrintTemplate(PrintTemplate printTemplate) {
        printTemplate.setUpdateTime(DateUtils.getNowDate());
        return printTemplateMapper.updatePrintTemplate(printTemplate);
    }

    /**
     * 批量删除打印模板
     *
     * @param ids 需要删除的打印模板主键
     * @return 结果
     */
    @Override
    public int deletePrintTemplateByIds(Long[] ids) {
        return printTemplateMapper.deletePrintTemplateByIds(ids);
    }

    /**
     * 删除打印模板信息
     *
     * @param id 打印模板主键
     * @return 结果
     */
    @Override
    public int deletePrintTemplateById(Long id) {
        return printTemplateMapper.deletePrintTemplateById(id);
    }
}
