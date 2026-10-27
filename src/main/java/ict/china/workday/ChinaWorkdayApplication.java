package ict.china.workday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 中国节假日/工作日公共服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ChinaWorkdayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChinaWorkdayApplication.class, args);
    }
}
