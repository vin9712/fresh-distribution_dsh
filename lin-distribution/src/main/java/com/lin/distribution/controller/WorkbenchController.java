package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.mapper.WorkbenchMapper;
import com.lin.distribution.util.PendingAcceptanceReminder;
import com.lin.distribution.vo.PendingAcceptanceVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 工作台待办接口
 *
 * @author dsh
 */
@Tag(name = "工作台")
@RestController
@RequestMapping("/workbench")
public class WorkbenchController extends BaseController {

    @Autowired
    private WorkbenchMapper workbenchMapper;

    @GetMapping("/summary")
    public AjaxResult summary() {
        return success(workbenchMapper.selectSummary(LocalDate.now().plusDays(1)));
    }

    /**
     * 送货单生成异常告警（D-055：生成/定时已取消——数据源=订单实时聚合，无“生成失败”概念，恒返回空）
     */
    @GetMapping("/job-alert")
    public AjaxResult jobAlert() {
        return success(null);
    }

    /**
     * 待验收提醒（蓝图 W0-3.2）：已送达未验收送货单，按「打印满2h→黄 / 配送日当天11:30后→红 / 过期→红」分级
     */
    @GetMapping("/pending-acceptance")
    public AjaxResult pendingAcceptance() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<PendingAcceptanceVO> list = workbenchMapper.selectPendingAcceptance();
        for (PendingAcceptanceVO vo : list) {
            int level = PendingAcceptanceReminder.compute(vo.getDeliveryDate(), vo.getPrintTime(), today, now);
            vo.setReminderLevel(level);
            vo.setReminderReason(PendingAcceptanceReminder.reason(level));
        }
        return success(list);
    }
}
