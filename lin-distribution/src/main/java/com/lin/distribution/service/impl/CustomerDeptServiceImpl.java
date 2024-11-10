package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.service.CustomerDeptService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户部门Service业务层处理
 *
 * @author lin
 * @date 2024-11-09
 */
@Service
@RequiredArgsConstructor
public class CustomerDeptServiceImpl implements CustomerDeptService {

    private final CustomerDeptMapper customerDeptMapper;
    private final RedissonClient redissonClient;

    /**
     * 查询客户部门
     *
     * @param id 客户部门主键
     * @return 客户部门
     */
    @Override
    public CustomerDept selectCustomerDeptById(Long id) {
        return customerDeptMapper.selectCustomerDeptById(id);
    }

    @Override
    public CustomerDept selectOneParentCustomerDept(Long customerId) {
        CustomerDept cp = new CustomerDept();
        cp.setCustomerId(customerId);
        cp.setParentId(0L);
        return customerDeptMapper.selectCustomerDeptList(cp).stream().findFirst()
                .orElseThrow(() -> new ServiceException("customer dept not found"));
    }

    /**
     * 查询客户部门列表
     *
     * @param customerDept 客户部门
     * @return 客户部门
     */
    @Override
    public List<CustomerDept> selectCustomerDeptList(CustomerDept customerDept) {
        return customerDeptMapper.selectCustomerDeptList(customerDept);
    }

    /**
     * 新增客户部门
     *
     * @param customerDept 客户部门
     * @return 结果
     */
    @Override
    public int insertCustomerDept(CustomerDept customerDept) {
        // check unique customer dept
        checkUniqueCustomerDept(customerDept);

        // get parent customer dept to set parentId & code
        if (customerDept.getParentId() == null) {
            CustomerDept parent = this.selectOneParentCustomerDept(customerDept.getCustomerId());
            customerDept.setParentId(parent.getId());
            String customerDeptCode = generateCustomerDeptNo(customerDept.getCustomerId(), parent.getMnemonicCode(), false);
            customerDept.setCode(customerDeptCode);
        }

        customerDept.setCreateTime(DateUtils.getNowDate());
        return customerDeptMapper.insertCustomerDept(customerDept);
    }

    /**
     * 修改客户部门
     *
     * @param customerDept 客户部门
     * @return 结果
     */
    @Override
    public int updateCustomerDept(CustomerDept customerDept) {
        // check unique customer dept
        checkUniqueCustomerDept(customerDept);

        customerDept.setUpdateTime(DateUtils.getNowDate());
        return customerDeptMapper.updateCustomerDept(customerDept);
    }

    /**
     * 批量删除客户部门
     *
     * @param ids 需要删除的客户部门主键
     * @return 结果
     */
    @Override
    public int deleteCustomerDeptByIds(Long[] ids) {
        return customerDeptMapper.deleteCustomerDeptByIds(ids);
    }

    /**
     * 删除客户部门信息
     *
     * @param id 客户部门主键
     * @return 结果
     */
    @Override
    public int deleteCustomerDeptById(Long id) {
        return customerDeptMapper.deleteCustomerDeptById(id);
    }

    /**
     * 生成客户部门编号
     * rule: 助记码 + 5位数自增序号
     *
     * @param mnemonicCode 客户助记码
     * @return
     */
    @Override
    public String generateCustomerDeptNo(Long customerId, String mnemonicCode, Boolean isParent) {
        String date = DateUtils.dateTimeNow("yyyyMMdd");
        if (BooleanUtils.isTrue(isParent)) {
            return mnemonicCode + date + "00000";
        }
        RMap<Long, Integer> rMap = redissonClient.getMap("customerDeptNo");
        int seqNbr = rMap.addAndGet(customerId, 1);
        String seqNbrStr = String.format("%05d", seqNbr);
        return mnemonicCode + date + seqNbrStr;
    }

    private void checkUniqueCustomerDept(CustomerDept customerDept) {
        if (customerDept == null) {
            throw new ServiceException("customerDept is null");
        }

        // check dept unique
        List<CustomerDept> customerDeptList = customerDeptMapper.checkUniqueCustomerDept(customerDept);
        long count = 0;
        if (customerDept.getId() != null) {
            count = customerDeptList.stream()
                    .filter(item -> !item.getId().equals(customerDept.getId()))
                    .count();
        } else {
            count = customerDeptList.size();
        }

        if (count > 0) {
            throw new ServiceException("customerDept name is exist");
        }
    }
}
