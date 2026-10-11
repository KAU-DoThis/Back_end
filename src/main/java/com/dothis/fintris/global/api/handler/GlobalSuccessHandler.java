package com.dothis.fintris.global.api.handler;

import com.dothis.fintris.global.api.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

// 성공 응답에 대해 ApiResponse 안의 HttpStatus를 실제 HTTP Status로 반영하는 핸들러
@RestControllerAdvice(annotations = {RestController.class})
public class GlobalSuccessHandler implements ResponseBodyAdvice<Object> {

    /**
     * Indicates that this advice applies to response bodies.
     *
     * @return {@code true} for all response types
     */
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 바디가 ApiResponse인 경우에만 beforeBodyWrite에서 처리
        return true;
    }

    /**
     * Applies an {@link ApiResponse}'s HTTP status to the outgoing response when available.
     *
     * @param body the response body
     * @return the original response body
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        if (body instanceof ApiResponse<?> apiResponse &&
                response instanceof ServletServerHttpResponse servletResponse) {
            if (apiResponse.getHttpStatus() != null) {
                servletResponse.setStatusCode(apiResponse.getHttpStatus());
            }
        }

        return body;
    }
}