package com.ict.workday.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ChinaWorkdayAutoConfiguration.class)
public @interface EnableChinaWorkday {
}
