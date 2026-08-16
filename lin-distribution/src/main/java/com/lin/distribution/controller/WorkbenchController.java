package com.lin.distribution.controller;

import com.lin.common.core.controller.BaseController;
import com.lin.common.core.domain.AjaxResult;
import com.lin.distribution.mapper.WorkbenchMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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
}
