package com.finance.core.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public com.fasterxml.jackson.databind.Module hibernate6Module() {
        // By default, FORCE_LAZY_LOADING is false.
        // This prevents LazyInitializationException for uninitialized properties
        return new Hibernate6Module();
    }
}
