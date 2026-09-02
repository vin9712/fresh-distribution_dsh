package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliveryPrintPackage;
import com.lin.distribution.domain.DeliveryPrintTask;
import com.lin.distribution.domain.PrintTemplate;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliveryPrintPackageMapper;
import com.lin.distribution.mapper.DeliveryPrintTaskMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.service.DeliveryPrintPackageService;
import com.lin.distribution.service.PrintTemplateService;
import com.lin.distribution.vo.DeliveryPrintPackageVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 送货单打印包服务实现（P2/D-050）
 *
 * <p>流程：建包（收集批次待打印单）→ 列包内任务 → 汇总预览（标记已预览+留痕）→
 * 开始打印（前端按 seq_no 队列逐张开票据）→ 单张回执（成功才 print_count+1/推进 PRINTED；
 * 失败不计数、标记失败即停队、可重试）。</p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPrintPackageServiceImpl implements DeliveryPrintPackageService {

    private final DeliveryPrintPackageMapper printPackageMapper;
    private final DeliveryPrintTaskMapper printTaskMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final DeliveryOrderService deliveryOrderService;
    private final PrintTemplateService printTemplateService;
    private final BizCodeService bizCodeService;

    /**
     * 建打印包：收集该批次下 未作废 且 未送达 的送货单（点单与总单都允许入包），
     * 解析各自模板生成包内任务；已有未完成包则复用（幂等）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintPackageVO createPackage(Long customerId, LocalDate deliveryDate) {
        if (customerId == null || deliveryDate == null) {
            throw new ServiceException("客户与配送日期不能为空");
        }
        // 幂等：该批次已有未完成包则复用
        DeliveryPrintPackage query = new DeliveryPrintPackage();
        query.setCustomerId(customerId);
        query.setDeliveryDate(java.sql.Date.valueOf(deliveryDate));
        List<DeliveryPrintPackage> exists = printPackageMapper.selectPrintPackageList(query);
        DeliveryPrintPackage active = exists.stream()
                .filter(p -> p.getStatus() != null && p.getStatus() < 2)
                .findFirst().orElse(null);
        if (active != null) {
            return toVO(active.getId());
        }

        // 收集批次有效送货单（未作废且未送达；已送达无再打印意义）
        DeliveryOrder q = new DeliveryOrder();
        q.setCustomerId(customerId);
        q.setDeliveryDate(deliveryDate);
        List<DeliveryOrder> orders = deliveryOrderMapper.selectDeliveryOrderList(q).stream()
                .filter(o -> !Objects.equals(o.getStatus(), DeliveryOrderStatus.VOIDED.getCode())
                        && !Objects.equals(o.getStatus(), DeliveryOrderStatus.DELIVERED.getCode()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(orders)) {
            throw new ServiceException("该批次没有可打印的送货单（已送达/已作废除外）");
        }

        String operator = resolveOperator();
        Date now = DateUtils.getNowDate();
        DeliveryPrintPackage pkg = DeliveryPrintPackage.builder()
                .customerId(customerId)
                .deliveryDate(java.sql.Date.valueOf(deliveryDate))
                .packageNo(bizCodeService.nextDailyCode("printPackage", "PK", 3))
                .mediaType("A4")
                .status(DeliveryPrintPackage.STATUS_PENDING)
                .totalCount(orders.size())
                .successCount(0)
                .failCount(0)
                .build();
        pkg.setCreateBy(operator);
        pkg.setCreateTime(now);
        printPackageMapper.insertPrintPackage(pkg);

        int seq = 0;
        for (DeliveryOrder order : orders) {
            seq++;
            PrintTemplate template;
            try {
                template = printTemplateService.resolveForDeliveryOrder(order);
            } catch (ServiceException e) {
                // 该单未配模板：任务仍建，模板留空，前端可改模板后再打
                log.warn("[print package] 送货单 {} 未解析到模板：{}", order.getCode(), e.getMessage());
                template = null;
            }
            DeliveryPrintTask task = DeliveryPrintTask.builder()
                    .packageId(pkg.getId())
                    .deliveryOrderId(order.getId())
                    .seqNo(seq)
                    .templateId(template == null ? null : template.getId())
                    .copies(template == null ? 1 : (template.getCopies() == null ? 1 : template.getCopies()))
                    .status(DeliveryPrintTask.STATUS_PENDING)
                    .attemptNo(1)
                    .build();
            task.setCreateBy(operator);
            task.setCreateTime(now);
            printTaskMapper.batchInsertPrintTask(List.of(task));
        }
        log.info("[print package] 建包 {}：客户 {} {}，{} 张", pkg.getPackageNo(), customerId, deliveryDate, orders.size());
        return toVO(pkg.getId());
    }

    @Override
    public DeliveryPrintPackageVO getPackage(Long packageId) {
        return toVO(packageId);
    }

    @Override
    public List<DeliveryPrintPackageVO> listPackages(Long customerId, LocalDate deliveryDate) {
        DeliveryPrintPackage query = new DeliveryPrintPackage();
        query.setCustomerId(customerId);
        query.setDeliveryDate(deliveryDate == null ? null : java.sql.Date.valueOf(deliveryDate));
        List<DeliveryPrintPackage> list = printPackageMapper.selectPrintPackageList(query);
        return list.stream().map(p -> toVO(p.getId())).collect(Collectors.toList());
    }

    /**
     * 汇总预览：包内全部待打任务 → 已预览，记录预览时间（留痕）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintPackageVO previewPackage(Long packageId) {
        DeliveryPrintPackage pkg = requirePackage(packageId);
        if (Objects.equals(pkg.getStatus(), DeliveryPrintPackage.STATUS_DONE)) {
            throw new ServiceException("打印包已完成，无法再预览");
        }
        List<DeliveryPrintTask> tasks = printTaskMapper.selectTasksByPackageId(packageId);
        for (DeliveryPrintTask task : tasks) {
            if (Objects.equals(task.getStatus(), DeliveryPrintTask.STATUS_PENDING)) {
                DeliveryPrintTask update = DeliveryPrintTask.builder().id(task.getId())
                        .status(DeliveryPrintTask.STATUS_PREVIEWED).build();
                update.setUpdateTime(DateUtils.getNowDate());
                printTaskMapper.updatePrintTask(update);
            }
        }
        DeliveryPrintPackage update = DeliveryPrintPackage.builder().id(packageId)
                .status(DeliveryPrintPackage.STATUS_PRINTING).previewTime(DateUtils.getNowDate()).build();
        update.setUpdateTime(DateUtils.getNowDate());
        printPackageMapper.updatePrintPackage(update);
        return toVO(packageId);
    }

    /**
     * 开始打印：包状态 → 打印中（任务队列由前端按 seq_no 逐张开票据、回执）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintPackageVO startPackage(Long packageId) {
        DeliveryPrintPackage pkg = requirePackage(packageId);
        if (Objects.equals(pkg.getStatus(), DeliveryPrintPackage.STATUS_DONE)) {
            throw new ServiceException("打印包已完成");
        }
        DeliveryPrintPackage update = DeliveryPrintPackage.builder().id(packageId)
                .status(DeliveryPrintPackage.STATUS_PRINTING).build();
        update.setUpdateTime(DateUtils.getNowDate());
        printPackageMapper.updatePrintPackage(update);
        return toVO(packageId);
    }

    /**
     * 单张回执：成功 → delivery.print_count+1 + 状态 PRINTED + 任务成功/回执时间；
     * 失败 → 任务失败 + 错误信息（不计数；停队由前端控制）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintTask receipt(Long taskId, boolean success, String errorMsg) {
        DeliveryPrintTask task = requireTask(taskId);
        Date now = DateUtils.getNowDate();
        DeliveryPrintTask update = DeliveryPrintTask.builder().id(taskId)
                .status(success ? DeliveryPrintTask.STATUS_SUCCESS : DeliveryPrintTask.STATUS_FAILED)
                .receiptTime(now)
                .errorMsg(success ? null : StringUtils.abbreviate(errorMsg, 490))
                .build();
        update.setUpdateTime(now);
        printTaskMapper.updatePrintTask(update);
        if (success) {
            // 成功才计次/推进状态（与单张打印同口径 markPrinted）
            deliveryOrderService.markPrinted(task.getDeliveryOrderId());
        }
        // 汇总包计数
        refreshPackageCounters(task.getPackageId());
        return printTaskMapper.selectTaskById(taskId);
    }

    /**
     * 逐张改模板/份数（仅本次生效）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintTask updateTask(Long taskId, Long templateId, Integer copies) {
        DeliveryPrintTask task = requireTask(taskId);
        DeliveryPrintTask update = DeliveryPrintTask.builder()
                .id(taskId)
                .templateId(templateId)
                .copies(copies)
                .build();
        update.setUpdateTime(DateUtils.getNowDate());
        printTaskMapper.updatePrintTask(update);
        return printTaskMapper.selectTaskById(taskId);
    }

    // ==================== 辅助 ====================

    private DeliveryPrintPackage requirePackage(Long packageId) {
        if (packageId == null) {
            throw new ServiceException("打印包ID不能为空");
        }
        DeliveryPrintPackage pkg = printPackageMapper.selectPrintPackageById(packageId);
        if (pkg == null) {
            throw new ServiceException("打印包不存在");
        }
        return pkg;
    }

    private DeliveryPrintTask requireTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        DeliveryPrintTask task = printTaskMapper.selectTaskById(taskId);
        if (task == null) {
            throw new ServiceException("打印任务不存在");
        }
        return task;
    }

    private DeliveryPrintPackageVO toVO(Long packageId) {
        DeliveryPrintPackage pkg = requirePackage(packageId);
        List<DeliveryPrintTask> tasks = printTaskMapper.selectTasksByPackageId(packageId);
        // 合计数量/金额：任务对应送货单明细求和
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (DeliveryPrintTask task : tasks) {
            List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(task.getDeliveryOrderId());
            for (DeliveryOrderDetail d : details) {
                totalQty = totalQty.add(d.getNum() == null ? BigDecimal.ZERO : d.getNum());
                totalAmt = totalAmt.add(d.getAmount() == null ? BigDecimal.ZERO : d.getAmount());
            }
        }
        return DeliveryPrintPackageVO.builder()
                .id(pkg.getId())
                .batchId(pkg.getBatchId())
                .customerId(pkg.getCustomerId())
                .customerName(pkg.getCustomerName())
                .deliveryDate(pkg.getDeliveryDate())
                .packageNo(pkg.getPackageNo())
                .mediaType(pkg.getMediaType())
                .status(pkg.getStatus())
                .totalCount(pkg.getTotalCount())
                .successCount(pkg.getSuccessCount())
                .failCount(pkg.getFailCount())
                .previewTime(pkg.getPreviewTime())
                .createBy(pkg.getCreateBy())
                .createTime(pkg.getCreateTime())
                .remark(pkg.getRemark())
                .tasks(tasks)
                .totalQuantity(totalQty)
                .totalAmount(totalAmt)
                .build();
    }

    /** 按包内任务状态刷新包计数（success/fail/total） */
    private void refreshPackageCounters(Long packageId) {
        List<java.util.Map<String, Object>> counts = printTaskMapper.countByStatus(packageId);
        int success = 0;
        int fail = 0;
        for (java.util.Map<String, Object> row : counts) {
            int status = (int) ((Number) row.get("status")).intValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            if (status == DeliveryPrintTask.STATUS_SUCCESS) {
                success += cnt;
            } else if (status == DeliveryPrintTask.STATUS_FAILED) {
                fail += cnt;
            }
        }
        DeliveryPrintPackage update = DeliveryPrintPackage.builder().id(packageId)
                .successCount(success).failCount(fail).build();
        update.setUpdateTime(DateUtils.getNowDate());
        printPackageMapper.updatePrintPackage(update);
    }

    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
