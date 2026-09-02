package com.lin.distribution.service;

import java.time.LocalDate;
import java.util.List;

import com.lin.distribution.domain.DeliveryPrintPackage;
import com.lin.distribution.domain.DeliveryPrintTask;
import com.lin.distribution.vo.DeliveryPrintPackageVO;

/**
 * 送货单打印包服务（P2/D-050：《送货单矩阵总表与批次视图设计》§七）
 *
 * <p>B 类客户 N 张点单一次输出：建包 → 包内任务清单 → 汇总预览 → 队列连续输出 →
 * 逐张回执（成功才 print_count+1 / 状态推进 PRINTED；失败不计数、可重试）。</p>
 *
 * @author dsh
 */
public interface DeliveryPrintPackageService {

    /**
     * 建打印包：按 客户+配送日期 收集该批次下全部待打印/未作废送货单，生成包 + 包内任务。
     * 已有未完成包则复用（幂等）。
     *
     * @param customerId   客户ID
     * @param deliveryDate 配送日期
     * @return 打印包（含任务清单）
     */
    DeliveryPrintPackageVO createPackage(Long customerId, LocalDate deliveryDate);

    /**
     * 查打印包（含任务清单与汇总预览字段）
     *
     * @param packageId 打印包ID
     * @return 打印包 VO
     */
    DeliveryPrintPackageVO getPackage(Long packageId);

    /**
     * 查批次当日打印包列表
     *
     * @param customerId   客户ID（可空）
     * @param deliveryDate 配送日期（可空）
     * @return 打印包集合
     */
    List<DeliveryPrintPackageVO> listPackages(Long customerId, LocalDate deliveryDate);

    /**
     * 汇总预览：标记包内全部待打任务为「已预览」并记录预览时间（留痕）。
     *
     * @param packageId 打印包ID
     * @return 打印包 VO
     */
    DeliveryPrintPackageVO previewPackage(Long packageId);

    /**
     * 开始打印（队列）：按 seq_no 顺序逐张执行，任一失败立即停止（保留未执行清单）。
     * 执行动作 = 打开打印票据（前端窗口）→ 用户确认后回执（POST /print-task/{id}/receipt）。
     *
     * @param packageId 打印包ID
     * @return 打印包 VO（含任务状态）
     */
    DeliveryPrintPackageVO startPackage(Long packageId);

    /**
     * 单张回执：成功才 print_count+1 / 状态推进 PRINTED；失败不计数（失败即停队，前端停止后续）。
     *
     * @param taskId 任务ID
     * @param success 是否成功
     * @param errorMsg 失败原因（失败时）
     * @return 任务（更新后）
     */
    DeliveryPrintTask receipt(Long taskId, boolean success, String errorMsg);

    /**
     * 逐张改模板/份数（仅本次生效；设为默认需另走模板绑定）。
     *
     * @param taskId     任务ID
     * @param templateId 模板主键（可空=不改）
     * @param copies     份数（可空=不改）
     * @return 任务（更新后）
     */
    DeliveryPrintTask updateTask(Long taskId, Long templateId, Integer copies);
}
