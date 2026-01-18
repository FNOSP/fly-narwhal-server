package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

@ControllerAdvice
@Component
public class ResultEncryptionAdvice implements ResponseBodyAdvice<Object> {
    private static final String RSA_TRANSFORM = "RSA/ECB/PKCS1Padding";

    private final FnAuthService fnAuthService;
    private final ObjectMapper objectMapper;

    public ResultEncryptionAdvice(FnAuthService fnAuthService, ObjectMapper objectMapper) {
        this.fnAuthService = fnAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(body instanceof Result<?>)) {
            return body;
        }
        @SuppressWarnings("unchecked")
        Result<Object> result = (Result<Object>) body;
        if (selectedContentType != null && MediaType.TEXT_EVENT_STREAM.includes(selectedContentType)) {
            return body;
        }
        HttpServletRequest servletRequest = unwrapServletRequest(request);
        if (servletRequest != null) {
            String path = servletRequest.getRequestURI();
            if (path != null && path.startsWith("/api/config/auth-code")) {
                return body;
            }
        }
        if (result.getEncrypted() != null && result.getEncrypted()) {
            return body;
        }
        if (result.getData() == null) {
            return body;
        }

        PrivateKey privateKey = fnAuthService.getResponsePrivateKeyOrNull();
        if (privateKey == null) {
            return body;
        }

        try {
            Object data = result.getData();
            String dataJson = objectMapper.writeValueAsString(data);
            String encryptedBase64 = encryptWithPrivateKeyToBase64(privateKey, dataJson);
            result.setData(encryptedBase64);
            result.setEncrypted(true);
            return result;
        } catch (Exception e) {
            return body;
        }
    }

    private static HttpServletRequest unwrapServletRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servlet) {
            return servlet.getServletRequest();
        }
        return null;
    }

    private static String encryptWithPrivateKeyToBase64(PrivateKey privateKey, String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);

        byte[] input = plaintext.getBytes(StandardCharsets.UTF_8);
        int keyBytes = ((RSAPrivateKey) privateKey).getModulus().bitLength() / 8;
        int blockSize = keyBytes - 11;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int offset = 0; offset < input.length; offset += blockSize) {
            int len = Math.min(blockSize, input.length - offset);
            byte[] enc = cipher.doFinal(input, offset, len);
            out.write(enc);
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
