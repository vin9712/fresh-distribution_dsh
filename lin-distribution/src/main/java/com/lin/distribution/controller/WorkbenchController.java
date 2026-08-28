package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.domain.JobRunLog;
import com.lin.distribution.mapper.JobRunLogMapper;
import com.lin.distribution.mapper.WorkbenchMapper;
import com.lin.distribution.service.DeliveryGenerationService;
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

    @Autowired
    private JobRunLogMapper jobRunLogMapper;

    @GetMapping("/summary")
    public AjaxResult summary() {
        return success(workbenchMapper.selectSummary(LocalDate.now().plusDays(1)));
    }

    /**
     * 送货单生成异常告警（S14/Q36/D-037）：读 t_job_run_log 最近一次 DELIVERY_GENERATE 运行记录，
     * 状态为失败(1)/部分失败(2)时返回记录驱动首页高优告警；成功(0)或无记录返回 null。
     */
    @GetMapping("/job-alert")
    public AjaxResult jobAlert() {
        JobRunLog latest = jobRunLogMapper.selectLatestByJobName(DeliveryGenerationService.JOB_NAME_DELIVERY_GENERATE);
        if (latest == null || latest.getStatus() == null || latest.getStatus() == JobRunLog.STATUS_SUCCESS) {
            return success(null);
        }
        return success(latest);
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
