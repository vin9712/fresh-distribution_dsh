package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.CustomerSku;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.TempProduct;
import com.lin.distribution.mapper.CustomerSkuMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.TempProductMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.CustomerSkuService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户商品服务实现（deepseek_redesign.md §3.7 / §5.1 / §5.2）
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class CustomerSkuServiceImpl implements CustomerSkuService {

    private final CustomerSkuMapper customerSkuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final BizCodeService bizCodeService;
    private final TempProductMapper tempProductMapper;

    @Override
    public CustomerSku selectCustomerSkuById(Long id) {
        return customerSkuMapper.selectCustomerSkuById(id);
    }

    @Override
    public List<CustomerSku> selectCustomerSkuList(CustomerSku customerSku) {
        return customerSkuMapper.selectCustomerSkuList(customerSku);
    }

    @Override
    @Transactional
    public int insertCustomerSku(CustomerSku customerSku) {
        if (customerSku.getCustomerId() == null) {
            throw new ServiceException("客户id不能为空");
        }
        if (customerSku.getSkuId() == null) {
            throw new ServiceException("sku id不能为空");
        }
        // 唯一性：同一客户同一SKU只能有一条
        CustomerSku exist = customerSkuMapper.selectByCustomerAndSku(customerSku.getCustomerId(), customerSku.getSkuId());
        if (exist != null) {
            throw new ServiceException("该客户已存在此商品");
        }
        // unit 默认继承标准SKU单位
        if (StringUtils.isBlank(customerSku.getUnit())) {
            ProductSku sku = productSkuMapper.selectProductSkuById(customerSku.getSkuId());
            if (sku != null) {
                customerSku.setUnit(sku.getUnit());
            }
        }
        if (StringUtils.isBlank(customerSku.getCustomerCode())) {
            customerSku.setCustomerCode(bizCodeService.nextCustomerSkuCode(customerSku.getCustomerId()));
        }
        if (customerSku.getMinOrderQty() == null) {
            customerSku.setMinOrderQty(java.math.BigDecimal.ONE);
        }
        if (customerSku.getOrderStep() == null) {
            customerSku.setOrderStep(java.math.BigDecimal.ONE);
        }
        if (customerSku.getIsFollowDefault() == null) {
            customerSku.setIsFollowDefault(0);
        }
        if (customerSku.getStatus() == null) {
            customerSku.setStatus(1);
        }
        return customerSkuMapper.insertCustomerSku(customerSku);
    }

    /**
     * 粘贴文本快速同步客户商品：每行一条。
     * 格式：「客户叫法=内部商品名」（分隔符：= 、: ：）或直接「商品名」。
     * 匹配顺序：内部 SKU 名称精确 → 助记码（忽略大小写）；未匹配的行不影响其它行。
     */
    @Override
    @Transactional
    public String syncCustomerSkuText(Long customerId, String text, Boolean unmatchedToTemp) {
        if (customerId == null) {
            throw new ServiceException("客户id不能为空");
        }
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("同步内容不能为空！");
        }
        // 一次性加载全部 SKU，内存中按名称/助记码建索引
        List<ProductSku> allSku = productSkuMapper.selectProductSkuList(new ProductSku());
        Map<String, ProductSku> nameMap = new HashMap<>();
        Map<String, ProductSku> mnemonicMap = new HashMap<>();
        for (ProductSku sku : allSku) {
            if (StringUtils.isNotBlank(sku.getName())) {
                nameMap.putIfAbsent(sku.getName().trim(), sku);
            }
            if (StringUtils.isNotBlank(sku.getMnemonicCode())) {
                mnemonicMap.putIfAbsent(sku.getMnemonicCode().trim().toUpperCase(), sku);
            }
        }

        int successNum = 0;
        int tempNum = 0;
        int duplicateNum = 0;
        int failureNum = 0;
        StringBuilder detailMsg = new StringBuilder();

        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (StringUtils.isEmpty(line)) {
                continue;
            }
            int lineNo = i + 1;
            try {
                // 解析「客户叫法=内部商品名」，无分隔符则客户叫法为空
                String alias = null;
                String keyword = line;
                String[] parts = line.split("[=:：]", 2);
                if (parts.length > 1 && StringUtils.isNotBlank(parts[0]) && StringUtils.isNotBlank(parts[1])) {
                    alias = parts[0].trim();
                    keyword = parts[1].trim();
                }
                // 匹配内部 SKU：名称精确 → 助记码
                ProductSku sku = nameMap.get(keyword);
                String matchType = "名称";
                if (sku == null) {
                    sku = mnemonicMap.get(keyword.toUpperCase());
                    matchType = "助记码";
                }

                // 未匹配：按开关处理——查同名临时商品（含已转正）或新建临时商品
                if (sku == null) {
                    boolean toTemp = unmatchedToTemp == null || unmatchedToTemp;
                    String tempName = StringUtils.isNotBlank(alias) ? alias : keyword;
                    TempProduct tq = new TempProduct();
                    tq.setCustomerId(customerId);
                    tq.setName(tempName);
                    tq.setShowAll(true);
                    TempProduct temp = tempProductMapper.selectTempProductList(tq).stream()
                            .filter(t -> tempName.equals(t.getName()))
                            .findFirst().orElse(null);
                    if (temp != null && temp.getConvertedSkuId() != null) {
                        // 同名临时商品已转正，直接用转正后的 SKU 入池
                        ProductSku converted = productSkuMapper.selectProductSkuById(temp.getConvertedSkuId());
                        if (converted != null) {
                            sku = converted;
                            matchType = "已转正临时商品";
                        }
                    }
                    if (sku == null) {
                        if (!toTemp) {
                            failureNum++;
                            detailMsg.append("<br/>").append(failureNum).append("、第").append(lineNo)
                                    .append("行 [").append(line).append("] 未匹配到标准SKU");
                            continue;
                        }
                        if (temp != null) {
                            duplicateNum++;
                            detailMsg.append("<br/>").append(duplicateNum).append("、第").append(lineNo)
                                    .append("行 [").append(tempName).append("] 已有相同临时商品待转正，跳过");
                            continue;
                        }
                        TempProduct tp = new TempProduct();
                        tp.setCustomerId(customerId);
                        tp.setName(tempName);
                        tempProductMapper.insertTempProduct(tp);
                        tempNum++;
                        detailMsg.append("<br/>").append(tempNum).append("、第").append(lineNo)
                                .append("行 [").append(tempName).append("] 未匹配到标准SKU，已存为临时商品（可在临时商品管理中转正）");
                        continue;
                    }
                }

                // 唯一约束：(customer_id, sku_id)，已存在则跳过
                if (customerSkuMapper.selectByCustomerAndSku(customerId, sku.getId()) != null) {
                    duplicateNum++;
                    detailMsg.append("<br/>").append(duplicateNum).append("、第").append(lineNo)
                            .append("行 商品 ").append(sku.getName()).append(" 已在该客户商品池中，跳过");
                    continue;
                }
                CustomerSku customerSku = new CustomerSku();
                customerSku.setCustomerId(customerId);
                customerSku.setSkuId(sku.getId());
                customerSku.setAlias(alias);
                insertCustomerSku(customerSku);
                successNum++;
                detailMsg.append("<br/>").append(successNum).append("、第").append(lineNo)
                        .append("行 [").append(line).append("] → ").append(sku.getName())
                        .append("（按").append(matchType).append("匹配）同步成功");
            } catch (Exception e) {
                failureNum++;
                detailMsg.append("<br/>第").append(lineNo).append("行 同步失败：").append(e.getMessage());
            }
        }

        StringBuilder message = new StringBuilder("<b>同步完成：入池 ").append(successNum)
                .append(" 条，转临时商品 ").append(tempNum).append(" 条，重复跳过 ").append(duplicateNum)
                .append(" 条，失败 ").append(failureNum).append(" 条</b>")
                .append(detailMsg);
        return message.toString();
    }

    @Override
    public int updateCustomerSku(CustomerSku customerSku) {
        if (customerSku.getId() == null) {
            throw new ServiceException("客户商品id不能为空");
        }
        // 仅更新传入字段；个性化（is_follow_default=0、清 source_template_id）由调用方显式传入
        return customerSkuMapper.updateCustomerSku(customerSku);
    }

    @Override
    public int deleteCustomerSkuByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return customerSkuMapper.deleteCustomerSkuByIds(ids);
    }

    @Override
    public List<CustomerSku> listCustomerProducts(Long customerId, Long deliveryPointId, String keyword) {
        CustomerSku query = new CustomerSku();
        query.setCustomerId(customerId);
        query.setKeyword(keyword);
        // 配送点白名单过滤：通用池 ∪ 本点专属池（dept_id 为空=通用，见 sql/s11_customer_sku_dept_scoping.sql）
        query.setDeliveryPointId(deliveryPointId);
        return customerSkuMapper.selectCustomerSkuList(query);
    }
}
