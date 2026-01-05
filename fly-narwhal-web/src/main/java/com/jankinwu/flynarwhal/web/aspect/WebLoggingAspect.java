package com.jankinwu.flynarwhal.web.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web接口日志切面，记录请求参数和响应结果
 */
@Aspect
@Component
@Slf4j
public class WebLoggingAspect {

    private final ObjectMapper objectMapper;

    public WebLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 切入点：controller 包及其子包下的所有方法
     */
    @Pointcut("execution(* com.jankinwu.flynarwhal.web.controller..*.*(..))")
    public void controllerPointcut() {}

    @Around("controllerPointcut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // 记录请求日志
        try {
            log.info("Request: [{} {}] {}.{}() | Args: {}", 
                    method, uri, className, methodName, objectMapper.writeValueAsString(args));
        } catch (Exception e) {
            log.warn("Failed to serialize request args: {}", e.getMessage());
            log.info("Request: [{} {}] {}.{}()", method, uri, className, methodName);
        }

        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            // 记录响应日志
            try {
                log.info("Response: [{} {}] {}.{}() | Time: {}ms | Result: {}", 
                        method, uri, className, methodName, duration, objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                log.warn("Failed to serialize response result: {}", e.getMessage());
                log.info("Response: [{} {}] {}.{}() | Time: {}ms", method, uri, className, methodName, duration);
            }
        }
    }
}
