package com.finance.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
public class TestCoreApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(CoreApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
