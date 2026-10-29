package com.ict.workday.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "china-workday", name = "schema-init", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnSchemaInit {
}
