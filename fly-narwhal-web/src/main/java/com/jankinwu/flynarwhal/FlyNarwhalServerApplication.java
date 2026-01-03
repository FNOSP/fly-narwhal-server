package com.jankinwu.flynarwhal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.jankinwu.flynarwhal.web.mapper")
public class FlyNarwhalServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlyNarwhalServerApplication.class, args);
    }

}
