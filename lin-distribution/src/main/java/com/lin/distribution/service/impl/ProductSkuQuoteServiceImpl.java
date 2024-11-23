package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.CommonConstants;
import com.lin.distribution.constant.ProductSkuQuoteStatus;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.ProductSkuQuoteDetail;
import com.lin.distribution.dto.ProductSkuQuoteCreateDTO;
import com.lin.distribution.dto.ProductSkuQuoteUpdateStatusDTO;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.ProductSkuQuoteDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
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
    private final ProductSkuQuoteMapper productSkuQuoteMapper;
    private final ProductSkuQuoteDetailMapper productSkuQuoteDetailMapper;
    private final RedissonClient redissonClient;

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
                .status(0)
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
        switch (status) {
            case PUBLISHED -> {
                LocalDate now = LocalDate.now();
                if (now.isAfter(quote.getEffectiveStartDate()) && now.isBefore(quote.getEffectiveEndDate())) {
                    // set current quote is valid
                    quote.setValid(CommonConstants.YES);

                    // set active one is invalid
                    ProductSkuQuote activeQuote = productSkuQuoteMapper.selectCustomerActiveQuote(quote.getCustomerId());
                    if (activeQuote != null && !Objects.equals(activeQuote.getId(), quoteId)) {
                        activeQuote.setValid(CommonConstants.NO);
                        activeQuote.setUpdateTime(DateUtils.getNowDate());
                        productSkuQuoteMapper.updateProductSkuQuote(activeQuote);
                    }
                }
            }
            case NEW, INVALID -> quote.setValid(CommonConstants.NO);
            default -> throw new ServiceException("invalid status");
        }
        // save to db
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

    private void checkCreateOrUpdateQuoteRequest(ProductSkuQuoteCreateDTO request) {
        // check effective date range is legal
        LocalDate from = request.getEffectiveStartDate();
        LocalDate to = request.getEffectiveEndDate();
        if (from.isAfter(to)) {
            throw new ServiceException("effective end date must after effective start date");
        }

        // check effective start date with latest active quote
        ProductSkuQuote activeQuote = productSkuQuoteMapper.selectCustomerActiveQuote(request.getCustomerId());
        LocalDate activeEffectiveEndDate = activeQuote != null ? activeQuote.getEffectiveEndDate() : null;
        if (activeEffectiveEndDate != null && !from.isAfter(activeEffectiveEndDate)) {
            throw new ServiceException("effective start date must not be after than active effective end date");
        }

        if (request.getQuoteId() == null) {
            // check quote code
            String quoteCode = request.getQuoteCode();
            ProductSkuQuote quote = productSkuQuoteMapper.selectProductSkuQuoteByCode(quoteCode);
            if (quote != null) {
                throw new ServiceException("product sku no existed");
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
        boolean isValidQuoteDetailList = quoteDetailList.stream().anyMatch(it -> BigDecimal.ZERO.compareTo(it.getPrice()) < 0)
                && quoteDetailList.stream().noneMatch(it -> BigDecimal.ZERO.compareTo(it.getPrice()) > 0);
        if (!isValidQuoteDetailList) {
            throw new ServiceException("quote detail list contains invalid item");
        }

    }

    private void checkUniqueQuote(ProductSkuQuote productSkuQuote) {
        if (productSkuQuote == null) {
            throw new ServiceException("productSkuQuote is null");
        }

        ProductSkuQuote skuQuote = productSkuQuoteMapper.selectProductSkuQuoteByCode(productSkuQuote.getCode());
        if (skuQuote != null) {
            throw new ServiceException("product sku no existed");
        }
    }

    private String genSkuQuoteNo(Boolean refresh) {
        return genSkuQuoteNo(refresh, null);
    }

    private String genSkuQuoteNo(Boolean refresh, String currentCode) {
        String date = DateUtils.dateTime();
        String prefix = "BJ" + date;
        RMap<String, Integer> rMap = redissonClient.getMap("skuQuoteNo");
        // get current redis seq
        int redisSeq = rMap.getOrDefault(date, 0);
        String redisQuoteCode = prefix + String.format("%05d", redisSeq);
        // if current code = redis code, return
        if (StringUtils.equals(redisQuoteCode, currentCode)) {
            return redisQuoteCode;
        }

        int seqNbr = BooleanUtils.isTrue(refresh) ? rMap.addAndGet(date, 1) : redisSeq;
        String seqNbrStr = String.format("%05d", seqNbr);
        return prefix + seqNbrStr;
    }
}
