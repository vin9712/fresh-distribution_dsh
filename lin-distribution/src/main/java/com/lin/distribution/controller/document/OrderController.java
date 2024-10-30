package com.lin.distribution.controller.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author vinga
 * @date 2024/10/30
 */
@Tag(name = "订单信息管理")
@RestController
@RequestMapping("/v1/distribution")
public class OrderController {

    @Operation(summary = "测试订单")
    @GetMapping("/order")
    public String getOrderById(){
        System.out.println("测试模块内 controller");
        return "1";
    }

}
