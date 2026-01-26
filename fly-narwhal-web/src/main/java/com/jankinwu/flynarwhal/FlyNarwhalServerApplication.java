package com.jankinwu.flynarwhal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
@MapperScan("com.jankinwu.flynarwhal.web.mapper")
public class FlyNarwhalServerApplication {

    public static void main(String[] args) {
        enforceUtf8DefaultCharset();
        SpringApplication.run(FlyNarwhalServerApplication.class, args);
    }

    private static void enforceUtf8DefaultCharset() {
        if (StandardCharsets.UTF_8.equals(Charset.defaultCharset())) {
            return;
        }
        System.setProperty("file.encoding", StandardCharsets.UTF_8.name());
        try {
            Field field = Charset.class.getDeclaredField("defaultCharset");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception ignored) {
        }
    }
}
