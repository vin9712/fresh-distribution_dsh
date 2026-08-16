package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.PinYinConvertUtils;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.ProductSku;
import com.lin.distribution.domain.TempProduct;
import com.lin.distribution.dto.TempProductConvertDTO;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.ProductSkuMapper;
import com.lin.distribution.mapper.TempProductMapper;
import com.lin.distribution.service.ProductService;
import com.lin.distribution.service.TempProductService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * 临时商品Service业务层处理
 *
 * @author lin
 * @date 2024-11-20
 */
@Service
public class TempProductServiceImpl implements TempProductService
{
    @Autowired
    private TempProductMapper tempProductMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private ProductService productService;

    /**
     * 查询临时商品
     *
     * @param id 临时商品主键
     * @return 临时商品
     */
    @Override
    public TempProduct selectTempProductById(Long id)
    {
        return tempProductMapper.selectTempProductById(id);
    }

    /**
     * 查询临时商品列表
     *
     * @param tempProduct 临时商品
     * @return 临时商品
     */
    @Override
    public List<TempProduct> selectTempProductList(TempProduct tempProduct)
    {
        return tempProductMapper.selectTempProductList(tempProduct);
    }

    /**
     * 新增临时商品
     *
     * @param tempProduct 临时商品
     * @return 结果
     */
    @Override
    public int insertTempProduct(TempProduct tempProduct)
    {
        tempProduct.setCreateTime(DateUtils.getNowDate());
        return tempProductMapper.insertTempProduct(tempProduct);
    }

    /**
     * 修改临时商品
     *
     * @param tempProduct 临时商品
     * @return 结果
     */
    @Override
    public int updateTempProduct(TempProduct tempProduct)
    {
        tempProduct.setUpdateTime(DateUtils.getNowDate());
        return tempProductMapper.updateTempProduct(tempProduct);
    }

    /**
     * 批量删除临时商品
     *
     * @param ids 需要删除的临时商品主键
     * @return 结果
     */
    @Override
    public int deleteTempProductByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return tempProductMapper.deleteTempProductByIds(ids);
    }

    /**
     * 临时商品转正为正式SKU：
     * 生成 SKU 记录（t_product_sku），返回新 skuId，临时商品记录保留并在备注标记"已转正式SKU:id"。
     *
     * @param dto 转正请求（id 必填；customerId/categoryId/mnemonicCode 可空）
     * @return 新生成的 skuId
     */
    @Override
    @Transactional
    public Long convertToSku(TempProductConvertDTO dto)
    {
        if (dto == null || dto.getId() == null) {
            throw new ServiceException("临时商品id不能为空");
        }

        TempProduct tempProduct = tempProductMapper.selectTempProductById(dto.getId());
        if (tempProduct == null) {
            throw new ServiceException("临时商品不存在");
        }

        Long customerId = dto.getCustomerId() == null ? 0L : dto.getCustomerId();
        Long categoryId = dto.getCategoryId() == null ? 0L : dto.getCategoryId();

        // 助记码：优先使用前端传入，否则用商品名称首字母简化
        String mnemonicCode = dto.getMnemonicCode();
        if (StringUtils.isBlank(mnemonicCode)) {
            mnemonicCode = PinYinConvertUtils.toFirstChar(tempProduct.getName()).toUpperCase(Locale.ROOT);
        }
        if (StringUtils.isBlank(mnemonicCode)) {
            mnemonicCode = "P" + tempProduct.getId();
        }

        // 商品编号：客户简写(前四位) + customerId + 5位自增序号
        String customerCode = "#";
        if (customerId != 0L) {
            Customer customer = customerMapper.selectCustomerById(customerId);
            if (customer != null) {
                String mn = customer.getShowMnemonicCode();
                customerCode = StringUtils.substring(mn, 0, Math.min(mn.length(), 4));
            }
        }
        String code = productService.generateSkuNo(customerId, customerCode);

        String unit = StringUtils.isNotBlank(tempProduct.getUnit()) ? tempProduct.getUnit() : "斤";

        ProductSku productSku = ProductSku.builder()
                .customerId(customerId)
                .categoryId(categoryId)
                .name(tempProduct.getName())
                .spec(tempProduct.getSpec())
                .unit(unit)
                .code(code)
                .mnemonicCode(mnemonicCode)
                .salePrice(tempProduct.getDefaultPrice() == null ? BigDecimal.ZERO : tempProduct.getDefaultPrice())
                .saleable(1)
                .valid(1)
                .isDeleted(Boolean.FALSE)
                .build();
        productSku.setCreateTime(DateUtils.getNowDate());
        productSkuMapper.insertProductSku(productSku);
        Long skuId = productSku.getId();

        // 保留临时商品记录，更新备注
        TempProduct update = new TempProduct();
        update.setId(tempProduct.getId());
        update.setRemark("已转正式SKU:" + skuId);
        update.setUpdateTime(DateUtils.getNowDate());
        tempProductMapper.updateTempProduct(update);

        return skuId;
    }
}
