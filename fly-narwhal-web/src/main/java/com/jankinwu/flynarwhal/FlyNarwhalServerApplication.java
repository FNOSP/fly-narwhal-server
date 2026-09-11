package com.jankinwu.flynarwhal;

import com.jankinwu.flynarwhal.web.config.MybatisLoggingWarmup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlyNarwhalServerApplication {

    public static void main(String[] args) {
        MybatisLoggingWarmup.warmUp();
        SpringApplication.run(FlyNarwhalServerApplication.class, args);
    }

}
