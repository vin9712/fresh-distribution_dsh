package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.CommonConstants;
import com.lin.distribution.constant.ProductSkuQuoteStatus;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.ProductAlias;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.domain.TempProduct;
import com.lin.distribution.dto.ProductSkuQuoteCreateDTO;
import com.lin.distribution.dto.ProductSkuQuoteImportDTO;
import com.lin.distribution.dto.ProductSkuQuoteUpdateStatusDTO;
import com.lin.distribution.dto.QuotePriceImportConfirmDTO;
import com.lin.distribution.dto.QuotePriceImportDTO;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.ProductAliasMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import com.lin.distribution.mapper.TempProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import com.lin.distribution.service.BizCodeService;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.ProductSkuQuoteMapper;
import com.lin.distribution.domain.ProductSkuQuote;
import com.lin.distribution.service.ProductSkuQuoteService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品报价Service业务层处理
 *
 * @author lin
 * @date 2024-11-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSkuQuoteServiceImpl implements ProductSkuQuoteService {
    private final CustomerMapper customerMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductSkuQuoteMapper productSkuQuoteMapper;
    private final ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;
    private final BizCodeService bizCodeService;
    private final TempProductMapper tempProductMapper;
    private final ProductAliasMapper productAliasMapper;
    private final CustomerSkuMappingMapper customerSkuMappingMapper;

    /**
     * 查询商品报价
     *
     * @param id 商品报价主键
     * @return 商品报价
     */
    @Override
    public ProductSkuQuote selectProductSkuQuoteById(Long id) {
        return productSkuQuoteMapper.selectProductSkuQuoteById(id);
    }

    /**
     * 查询商品报价列表
     *
     * @param productSkuQuote 商品报价
     * @return 商品报价
     */
    @Override
    public List<ProductSkuQuote> selectProductSkuQuoteList(ProductSkuQuote productSkuQuote) {
        return productSkuQuoteMapper.selectProductSkuQuoteList(productSkuQuote);
    }

    /**
     * 新增商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    @Override
    @Transactional
    public int insertProductSkuQuote(ProductSkuQuote productSkuQuote) {
        // check unique quote
        checkUniqueQuote(productSkuQuote);

        productSkuQuote.setCreateTime(DateUtils.getNowDate());
        return productSkuQuoteMapper.insertProductSkuQuote(productSkuQuote);
    }

    /**
     * 修改商品报价
     *
     * @param productSkuQuote 商品报价
     * @return 结果
     */
    @Override
    public int updateProductSkuQuote(ProductSkuQuote productSkuQuote) {
        productSkuQuote.setUpdateTime(DateUtils.getNowDate());
        return productSkuQuoteMapper.updateProductSkuQuote(productSkuQuote);
    }

    /**
     * 批量删除商品报价
     *
     * @param ids 需要删除的商品报价主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuQuoteByIds(Long[] ids) {
        return productSkuQuoteMapper.deleteProductSkuQuoteByIds(ids);
    }

    /**
     * 删除商品报价信息
     *
     * @param id 商品报价主键
     * @return 结果
     */
    @Override
    public int deleteProductSkuQuoteById(Long id) {
        return productSkuQuoteMapper.deleteProductSkuQuoteById(id);
    }

    /**
     * 生成商品报价单号
     *
     * @param refresh     是否刷新下一个，默认不刷新，提交表单时才更新
     * @param currentCode
     * @return
     */
    @Override
    public String generateSkuQuoteNo(Boolean refresh, String currentCode) {
        return genSkuQuoteNo(refresh, currentCode);
    }

    @Override
    @Transactional
    public ProductSkuQuote createSkuQuote(ProductSkuQuoteCreateDTO request) {
        checkCreateOrUpdateQuoteRequest(request);

        // insert quote
        ProductSkuQuote productSkuQuote = ProductSkuQuote.builder()
                .customerId(request.getCustomerId())
                .code(request.getQuoteCode())
                .effectiveStartDate(request.getEffectiveStartDate())
                .effectiveEndDate(request.getEffectiveEndDate())
                .status(ProductSkuQuoteStatus.NEW.getCode())
                .valid(0)
                .version(0)
                .isDeleted(false)
                .build();
        productSkuQuote.setRemark(request.getRemark());
        productSkuQuoteMapper.insertProductSkuQuote(productSkuQuote);

        // get quoteId
        Long quoteId = productSkuQuote.getId();

        // batch insert quote details
        List<ProductSkuQuoteDetail> quoteDetails = request.getQuoteDetails();
        quoteDetails.forEach(detail -> {
            detail.setCustomerId(request.getCustomerId());
            detail.setQuoteId(quoteId);
            detail.setValid(1);
            detail.setIsDeleted(false);
            detail.setVersion(0);
            productSkuQuoteDetailMapper.insertProductSkuQuoteDetail(detail);
        });

        // update quote code
        genSkuQuoteNo(true);

        return productSkuQuote;
    }

    @Override
    @Transactional
    public ProductSkuQuote updateSkuQuote(ProductSkuQuoteCreateDTO request) {
        checkCreateOrUpdateQuoteRequest(request);
        Long quoteId = request.getQuoteId();
        if (quoteId == null) {
            throw new ServiceException("quote id is null");
        }

        ProductSkuQuote productSkuQuote = productSkuQuoteMapper.selectProductSkuQuoteById(quoteId);

        // update quote
        productSkuQuote.setEffectiveStartDate(request.getEffectiveStartDate());
        productSkuQuote.setEffectiveEndDate(request.getEffectiveEndDate());
        productSkuQuote.setRemark(request.getRemark());
        productSkuQuoteMapper.updateProductSkuQuote(productSkuQuote);

        // delete quote details
        productSkuQuoteDetailMapper.deleteProductSkuQuoteDetailByQuoteId(quoteId);

        // batch insert quote details
        List<ProductSkuQuoteDetail> quoteDetails = request.getQuoteDetails();
        quoteDetails.forEach(detail -> {
            detail.setCustomerId(request.getCustomerId());
            detail.setQuoteId(quoteId);
            detail.setValid(1);
            detail.setIsDeleted(false);
            detail.setVersion(0);
            productSkuQuoteDetailMapper.insertProductSkuQuoteDetail(detail);
        });

        return productSkuQuote;
    }

    @Override
    @Transactional
    public void updateQuoteStatus(ProductSkuQuoteUpdateStatusDTO request) {
        Long quoteId = request.getQuoteId();
        ProductSkuQuoteStatus status = request.getStatus();
        ProductSkuQuote quote = productSkuQuoteMapper.selectProductSkuQuoteById(quoteId);
        if (quote == null) {
            throw new ServiceException("product sku quote not found");
        }
        switch (status) {
            case PUBLISHED -> {
                LocalDate now = LocalDate.now();
                if (!now.isBefore(quote.getEffectiveStartDate()) && !now.isAfter(quote.getEffectiveEndDate())) {
                    quote.setValid(CommonConstants.YES);
                    ProductSkuQuote activeQuote = productSkuQuoteMapper.selectCustomerActiveQuote(quote.getCustomerId());
                    if (activeQuote != null && !Objects.equals(activeQuote.getId(), quoteId)) {
                        activeQuote.setValid(CommonConstants.NO);
                        activeQuote.setUpdateTime(DateUtils.getNowDate());
                        productSkuQuoteMapper.updateProductSkuQuote(activeQuote);
                    }
                } else {
                    quote.setValid(CommonConstants.NO);
                }
            }
            case NEW, INVALID -> quote.setValid(CommonConstants.NO);
            default -> throw new ServiceException("invalid status");
        }
        quote.setStatus(status.getCode());
        quote.setUpdateTime(DateUtils.getNowDate());
        productSkuQuoteMapper.updateProductSkuQuote(quote);
    }

    @Override
    @Transactional
    public void syncUpdateQuoteStatus() {
        Customer c = new Customer();
        c.setValid(CommonConstants.YES);
        Set<Long> customerIds = customerMapper.selectCustomerList(c).stream().map(Customer::getId).collect(Collectors.toSet());
        // skip inactive customer
        if (CollectionUtils.isEmpty(customerIds)) {
            return;
        }

        for (Long customerId : customerIds) {
            ProductSkuQuote activeQuote = productSkuQuoteMapper.selectCustomerActiveQuote(customerId);
            // skip contains active quote
            if (activeQuote != null) {
                continue;
            }

            // set latest quote
            ProductSkuQuote quote = productSkuQuoteMapper.selectCustomerLatestQuote(customerId);
            if (quote != null) {
                Long quoteId = quote.getId();
                log.info("set quote status is active, customerId:{}, quoteId:{}", customerId, quoteId);
                ProductSkuQuoteUpdateStatusDTO request = ProductSkuQuoteUpdateStatusDTO.builder()
                        .quoteId(quoteId)
                        .status(ProductSkuQuoteStatus.PUBLISHED)
                        .build();
                this.updateQuoteStatus(request);
            }
        }
    }

    @Override
    public ProductSkuQuote getCustomerActiveQuote(Long customerId) {
        return productSkuQuoteMapper.selectCustomerActiveQuote(customerId);
    }

    private void checkCreateOrUpdateQuoteRequest(ProductSkuQuoteCreateDTO request) {
        // check effective date range is legal
        LocalDate from = request.getEffectiveStartDate();
        LocalDate to = request.getEffectiveEndDate();
        if (from.isAfter(to)) {
            throw new ServiceException("effective end date must after effective start date");
        }

        // check effective start date with the latest active quote
        ProductSkuQuote activeQuote = productSkuQuoteMapper.selectCustomerActiveQuote(request.getCustomerId());
        LocalDate activeEffectiveEndDate = activeQuote != null ? activeQuote.getEffectiveEndDate() : null;
        if (activeEffectiveEndDate != null && !from.isAfter(activeEffectiveEndDate)) {
            throw new ServiceException("effective start date must be after active effective end date");
        }

        if (request.getQuoteId() == null) {
            // check quote code
            String quoteCode = request.getQuoteCode();
            ProductSkuQuote quote = productSkuQuoteMapper.selectProductSkuQuoteByCode(quoteCode);
            if (quote != null) {
                throw new ServiceException("product sku quote no existed");
            }
        } else {
            // check quote status
            ProductSkuQuote quote = productSkuQuoteMapper.selectProductSkuQuoteById(request.getQuoteId());
            if (!ProductSkuQuoteStatus.NEW.getCode().equals(quote.getStatus())) {
                throw new ServiceException("quote status must be NEW");
            }
            // check quote valid
            if (CommonConstants.YES.equals(quote.getValid())) {
                throw new ServiceException("quote valid must be invalid");
            }
        }

        List<ProductSkuQuoteDetail> quoteDetailList = request.getQuoteDetails();
        // reject null or zero-or-negative prices; require at least one positive price
        boolean hasPositivePrice = quoteDetailList.stream().anyMatch(it -> it.getPrice() != null && BigDecimal.ZERO.compareTo(it.getPrice()) < 0);
        boolean hasNoNegativeOrZero = quoteDetailList.stream().allMatch(it -> it.getPrice() != null && BigDecimal.ZERO.compareTo(it.getPrice()) < 0);
        if (!hasPositivePrice || !hasNoNegativeOrZero) {
            throw new ServiceException("quote detail list must contain only positive prices");
        }

    }

    private void checkUniqueQuote(ProductSkuQuote productSkuQuote) {
        if (productSkuQuote == null) {
            throw new ServiceException("productSkuQuote is null");
        }

        ProductSkuQuote skuQuote = productSkuQuoteMapper.selectProductSkuQuoteByCode(productSkuQuote.getCode());
        if (skuQuote != null) {
            throw new ServiceException("product sku quote no existed");
        }
    }

    private String genSkuQuoteNo(Boolean refresh) {
        return genSkuQuoteNo(refresh, null);
    }

    private String genSkuQuoteNo(Boolean refresh, String currentCode) {
        // BJyyyyMMdd + 每日重置序号（DB 序列，Redis 非硬依赖）
        String peekCode = bizCodeService.peekDailyCode("skuQuote", "BJ", 5);
        if (StringUtils.equals(peekCode, currentCode)) {
            return peekCode;
        }
        if (BooleanUtils.isTrue(refresh)) {
            return bizCodeService.nextDailyCode("skuQuote", "BJ", 5);
        }
        return peekCode;
    }

    @Override
    @Transactional
    public String importQuoteData(List<ProductSkuQuoteImportDTO> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            throw new ServiceException("导入报价数据不能为空！");
        }

        int successQuoteNum = 0;
        int successRowNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();

        // 按客户聚合（保持行序）
        Map<Long, List<ProductSkuQuoteImportDTO>> grouped = rows.stream()
                .filter(r -> r.getCustomerId() != null)
                .collect(Collectors.groupingBy(ProductSkuQuoteImportDTO::getCustomerId, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<ProductSkuQuoteImportDTO>> entry : grouped.entrySet()) {
            Long customerId = entry.getKey();
            List<ProductSkuQuoteImportDTO> customerRows = entry.getValue();
            try {
                Customer customer = customerMapper.selectCustomerById(customerId);
                if (customer == null) {
                    throw new ServiceException("客户不存在(ID=" + customerId + ")");
                }
                // 日期区间：min(生效, 默认今天) ~ max(失效, 默认 9999-12-31)
                LocalDate start = customerRows.stream()
                        .map(r -> toLocalDate(r.getEffectiveStartDate()))
                        .filter(Objects::nonNull)
                        .min(LocalDate::compareTo)
                        .orElse(LocalDate.now());
                LocalDate end = customerRows.stream()
                        .map(r -> toLocalDate(r.getEffectiveEndDate()))
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(LocalDate.of(9999, 12, 31));

                List<ProductSkuQuoteDetail> details = new ArrayList<>();
                for (ProductSkuQuoteImportDTO row : customerRows) {
                    if (row.getSkuId() == null) {
                        throw new ServiceException("SKU ID为空");
                    }
                    ProductSku sku = productSkuMapper.selectProductSkuById(row.getSkuId());
                    if (sku == null) {
                        throw new ServiceException("SKU不存在(ID=" + row.getSkuId() + ")");
                    }
                    ProductSkuQuoteDetail detail = new ProductSkuQuoteDetail();
                    detail.setCustomerId(customerId);
                    detail.setSkuId(sku.getId());
                    detail.setPrice(row.getPrice());
                    detail.setProductName(sku.getName());
                    detail.setProductUnit(sku.getUnit());
                    detail.setProductSpec(sku.getSpecName());
                    detail.setProductCode(sku.getCode());
                    detail.setProductMnemonicCode(sku.getMnemonicCode());
                    detail.setCategoryId(sku.getCategoryId());
                    detail.setCategoryName(sku.getCategoryName());
                    details.add(detail);
                }

                String quoteCode = genSkuQuoteNo(true);
                createSkuQuote(ProductSkuQuoteCreateDTO.builder()
                        .customerId(customerId)
                        .quoteCode(quoteCode)
                        .effectiveStartDate(start)
                        .effectiveEndDate(end)
                        .remark("导入生成")
                        .quoteDetails(details)
                        .build());
                successQuoteNum++;
                successRowNum += details.size();
                successMsg.append("<br/>").append(successQuoteNum)
                        .append("、客户 ").append(customer.getName())
                        .append(" 报价单导入成功（").append(details.size()).append(" 行）");
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "、客户ID " + customerId + " 导入失败：";
                failureMsg.append(msg).append(e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 组数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，报价已全部导入成功！共 " + successQuoteNum + " 张报价单、" + successRowNum + " 行：");
        }
        return successMsg.toString();
    }

    private LocalDate toLocalDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** *************************** 价格表粘贴导入 *************************** */

    /** 常见单位词表：价格前一位若命中则识别为单位列（可空，未命中则拼入商品名） */
    private static final Set<String> COMMON_UNIT_SET = new HashSet<>(Arrays.asList(
            "斤", "公斤", "千克", "克", "吨", "kg", "g", "箱", "件", "袋", "包", "盒", "瓶", "桶", "罐",
            "个", "只", "条", "份", "扎", "筐", "板", "张", "把", "串", "杯", "提", "组", "套", "双",
            "块", "片", "根", "卷", "枚", "听", "支", "篮", "盆", "朵"));

    /** 解析一行：最后一个可解析为数字的 token 为价格；价格前一位若是常见单位则识别为单位列 */
    private QuotePriceImportDTO.Row parsePriceLine(int lineNo, String line) {
        QuotePriceImportDTO.Row row = new QuotePriceImportDTO.Row();
        row.setLineNo(lineNo);
        row.setRawName(line);
        String[] tokens = line.split("[\\s\\t，,；;：:]+", -1);
        java.math.BigDecimal price = null;
        int priceIdx = -1;
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i].replace("¥", "").replace("￥", "").trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                price = new java.math.BigDecimal(t);
                if (price.signum() < 0) {
                    continue;
                }
                priceIdx = i;
                break;
            } catch (NumberFormatException ignore) {
                // 非数字，继续向前找
            }
        }
        if (priceIdx < 0) {
            row.setError("未识别到价格");
            return row;
        }
        // 单位识别：价格前一位命中常见单位词表则提取（可空）
        String unit = null;
        int nameEnd = priceIdx;
        if (priceIdx > 0) {
            String maybeUnit = tokens[priceIdx - 1].trim();
            if (!maybeUnit.isEmpty() && COMMON_UNIT_SET.contains(maybeUnit.toLowerCase())) {
                unit = maybeUnit;
                nameEnd = priceIdx - 1;
            }
        }
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i == priceIdx || tokens[i].isEmpty() || i == priceIdx - 1 && unit != null) {
                continue;
            }
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(tokens[i]);
        }
        if (name.length() == 0) {
            row.setError("未识别到商品名");
            return row;
        }
        row.setRawName(name.toString());
        row.setUnit(unit);
        row.setPrice(price);
        return row;
    }

    @Override
    public List<QuotePriceImportDTO.Row> previewQuotePriceImport(Long customerId, String text) {
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("导入内容不能为空！");
        }
        List<QuotePriceImportDTO.Row> rows = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (StringUtils.isEmpty(line)) {
                continue;
            }
            QuotePriceImportDTO.Row row = parsePriceLine(i + 1, line);
            rows.add(row);
        }
        return matchQuotePriceRows(customerId, rows);
    }

    @Override
    public List<QuotePriceImportDTO.Row> previewQuotePriceImportExcel(Long customerId, java.io.InputStream in) {
        if (customerId == null) {
            throw new ServiceException("客户id不能为空");
        }
        // EasyExcel 无模型流式读取：每行 -> 列号到文本的映射
        List<Map<Integer, String>> excelRows = com.alibaba.excel.EasyExcel.read(in).headRowNumber(0).sheet().doReadSync();
        List<QuotePriceImportDTO.Row> rows = new ArrayList<>();
        boolean headerChecked = false;
        for (int i = 0; i < excelRows.size(); i++) {
            Map<Integer, String> cells = excelRows.get(i);
            if (cells == null || cells.isEmpty()) {
                continue;
            }
            // 按列序拼接非空单元格，交给统一的行解析器（末位数字识别为价格）
            StringBuilder line = new StringBuilder();
            cells.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .filter(e -> StringUtils.isNotBlank(e.getValue()))
                    .forEach(e -> line.append(" ").append(e.getValue().trim()));
            if (StringUtils.isBlank(line.toString())) {
                continue;
            }
            QuotePriceImportDTO.Row row = parsePriceLine(rows.size() + 1, line.toString());
            // 自动忽略第一行表头：无价格且包含表头关键字则跳过
            if (!headerChecked) {
                headerChecked = true;
                if (isHeaderRow(row)) {
                    continue;
                }
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new ServiceException("Excel中未解析到有效数据行");
        }
        return matchQuotePriceRows(customerId, rows);
    }

    /** 首行是否为表头：未识别到价格且包含常见表头关键字 */
    private boolean isHeaderRow(QuotePriceImportDTO.Row row) {
        if (row == null || !"未识别到价格".equals(row.getError())) {
            return false;
        }
        String name = row.getRawName() == null ? "" : row.getRawName();
        return name.contains("名称") || name.contains("品名") || name.contains("商品")
                || name.contains("单价") || name.contains("价格") || name.contains("单位")
                || name.contains("规格") || name.contains("sku") || name.contains("SKU");
    }

    /** 对已解析的行执行四级匹配：名称精确 → 助记码 → 全局别名 → 该客户的SKU映射 */
    private List<QuotePriceImportDTO.Row> matchQuotePriceRows(Long customerId, List<QuotePriceImportDTO.Row> rows) {
        // SKU 名称/助记码索引
        Map<String, ProductSku> nameMap = new HashMap<>();
        Map<String, ProductSku> mnemonicMap = new HashMap<>();
        for (ProductSku sku : productSkuMapper.selectProductSkuList(new ProductSku())) {
            if (StringUtils.isNotBlank(sku.getName())) {
                nameMap.putIfAbsent(sku.getName().trim(), sku);
            }
            if (StringUtils.isNotBlank(sku.getMnemonicCode())) {
                mnemonicMap.putIfAbsent(sku.getMnemonicCode().trim().toUpperCase(), sku);
            }
        }
        // 全局别名索引 alias -> skuId（重名取第一个）
        Map<String, Long> globalAliasMap = new HashMap<>();
        for (ProductAlias alias : productAliasMapper.selectProductAliasList(new ProductAlias())) {
            if (StringUtils.isNotBlank(alias.getAlias()) && alias.getSkuId() != null) {
                globalAliasMap.putIfAbsent(alias.getAlias().trim(), alias.getSkuId());
            }
        }
        // 客户映射索引 customerAlias -> skuId（重名取第一个）
        Map<String, Long> mappingMap = new HashMap<>();
        if (customerId != null) {
            CustomerSkuMapping query = new CustomerSkuMapping();
            query.setCustomerId(customerId);
            for (CustomerSkuMapping m : customerSkuMappingMapper.selectCustomerSkuMappingList(query)) {
                if (StringUtils.isNotBlank(m.getCustomerAlias()) && m.getSkuId() != null) {
                    mappingMap.putIfAbsent(m.getCustomerAlias().trim(), m.getSkuId());
                }
            }
        }

        List<QuotePriceImportDTO.Row> result = new ArrayList<>();
        for (QuotePriceImportDTO.Row row : rows) {
            result.add(row);
            if (row.getError() != null) {
                continue;
            }
            String key = row.getRawName().trim();
            ProductSku sku = nameMap.get(key);
            String matchType = "名称";
            if (sku == null) {
                sku = mnemonicMap.get(key.toUpperCase());
                matchType = "助记码";
            }
            Long aliasedSkuId = null;
            if (sku == null) {
                aliasedSkuId = globalAliasMap.get(key);
                matchType = "全局别名";
            }
            if (sku == null && customerId != null) {
                aliasedSkuId = mappingMap.get(key);
                matchType = "客户映射";
            }
            if (sku == null && aliasedSkuId != null) {
                sku = productSkuMapper.selectProductSkuById(aliasedSkuId);
            }
            if (sku != null) {
                row.setMatched(true);
                row.setMatchType(matchType);
                row.setSkuId(sku.getId());
                row.setSkuName(sku.getName());
                row.setSkuSpec(sku.getSpecName());
                row.setSkuUnit(sku.getUnit());
            } else {
                row.setMatched(false);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public String confirmQuotePriceImport(QuotePriceImportConfirmDTO dto) {
        if (dto == null || dto.getCustomerId() == null) {
            throw new ServiceException("客户id不能为空");
        }
        if (CollectionUtils.isEmpty(dto.getRows())) {
            throw new ServiceException("导入明细不能为空！");
        }
        boolean toTemp = dto.getUnmatchedToTemp() == null || dto.getUnmatchedToTemp();

        // 已匹配行 → 报价明细；同一 SKU 重复取最后一行价格
        Map<Long, ProductSkuQuoteDetail> detailMap = new LinkedHashMap<>();
        int tempCreated = 0;
        int tempSkipped = 0;
        int unmatchedKept = 0;
        for (QuotePriceImportConfirmDTO.Row row : dto.getRows()) {
            if (row.getSkuId() == null) {
                // 未匹配行
                if (!toTemp) {
                    unmatchedKept++;
                    continue;
                }
                if (StringUtils.isBlank(row.getRawName())) {
                    continue;
                }
                TempProduct tq = new TempProduct();
                tq.setCustomerId(dto.getCustomerId());
                tq.setName(row.getRawName().trim());
                tq.setShowAll(true);
                TempProduct exist = tempProductMapper.selectTempProductList(tq).stream()
                        .filter(t -> t.getName() != null && t.getName().equals(tq.getName()))
                        .findFirst().orElse(null);
                if (exist != null) {
                    tempSkipped++;
                } else {
                    TempProduct tp = new TempProduct();
                    tp.setCustomerId(dto.getCustomerId());
                    tp.setName(tq.getName());
                    tp.setUnit(row.getUnit());
                    tempProductMapper.insertTempProduct(tp);
                    tempCreated++;
                }
                continue;
            }
            ProductSku sku = productSkuMapper.selectProductSkuById(row.getSkuId());
            if (sku == null) {
                throw new ServiceException("SKU不存在(ID=" + row.getSkuId() + ")");
            }
            ProductSkuQuoteDetail detail = new ProductSkuQuoteDetail();
            detail.setCustomerId(dto.getCustomerId());
            detail.setSkuId(sku.getId());
            detail.setPrice(row.getPrice());
            // 单位可空：导入行指定了单位则用之，否则用SKU默认单位
            detail.setProductName(sku.getName());
            detail.setProductUnit(StringUtils.isNotBlank(row.getUnit()) ? row.getUnit() : sku.getUnit());
            detail.setProductSpec(sku.getSpecName());
            detail.setProductCode(sku.getCode());
            detail.setProductMnemonicCode(sku.getMnemonicCode());
            detail.setCategoryId(sku.getCategoryId());
            detail.setCategoryName(sku.getCategoryName());
            detailMap.put(sku.getId(), detail);
        }

        if (detailMap.isEmpty()) {
            throw new ServiceException("没有可定价的匹配行，报价单未生成");
        }

        LocalDate start = dto.getEffectiveStartDate() != null ? dto.getEffectiveStartDate() : LocalDate.now();
        LocalDate end = dto.getEffectiveEndDate() != null ? dto.getEffectiveEndDate() : LocalDate.of(9999, 12, 31);
        createSkuQuote(ProductSkuQuoteCreateDTO.builder()
                .customerId(dto.getCustomerId())
                .quoteCode(genSkuQuoteNo(true))
                .effectiveStartDate(start)
                .effectiveEndDate(end)
                .remark("价格表导入生成")
                .quoteDetails(new ArrayList<>(detailMap.values()))
                .build());

        return "<b>导入完成：生成报价单 1 张（" + detailMap.size() + " 行定价）</b>"
                + "<br/>转临时商品 " + tempCreated + " 条，已有相同临时商品跳过 " + tempSkipped + " 条"
                + (unmatchedKept > 0 ? "<br/>未处理未匹配行 " + unmatchedKept + " 条" : "");
    }
}
