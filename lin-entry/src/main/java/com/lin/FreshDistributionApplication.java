package com.lin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author lin
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class FreshDistributionApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(FreshDistributionApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  fresh distribution service started.   ლ(´ڡ`ლ)ﾞ");
    }
}
