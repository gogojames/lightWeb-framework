package com.lightweb.framework.core;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP请求封装类
 * 使用Java 25记录类(Record)和模式匹配等新特性
 */
public record Request(
    String method,
    String path,
    String protocol,
    Map<String, String> headers,
    Map<String, String> queryParams,
    Map<String, String> pathParams,
    String body,
    InputStream rawInputStream
) {
    
    public Request {
        headers = headers != null ? Map.copyOf(headers) : Map.of();
        queryParams = queryParams != null ? Map.copyOf(queryParams) : Map.of();
        pathParams = pathParams != null ? Map.copyOf(pathParams) : Map.of();
        body = body != null ? body : "";
    }
    
    /**
     * 获取请求头
     */
    public Optional<String> getHeader(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase()));
    }
    
    /**
     * 获取查询参数
     */
    public Optional<String> getQueryParam(String name) {
        return Optional.ofNullable(queryParams.get(name));
    }
    
    /**
     * 获取路径参数
     */
    public Optional<String> getPathParam(String name) {
        return Optional.ofNullable(pathParams.get(name));
    }
    
    /**
     * 检查是否为特定HTTP方法
     */
    public boolean isMethod(String method) {
        return this.method.equalsIgnoreCase(method);
    }
    
    /**
     * 模式匹配处理不同HTTP方法
     */
    public String processMethod() {
        return switch (method.toUpperCase()) {
            case "GET" -> "处理GET请求";
            case "POST" -> "处理POST请求";
            case "PUT" -> "处理PUT请求";
            case "DELETE" -> "处理DELETE请求";
            default -> "处理未知方法: " + method;
        };
    }
    
    /**
     * 获取内容类型
     */
    public Optional<String> getContentType() {
        return getHeader("content-type");
    }
    
    /**
     * 检查是否为JSON请求
     */
    public boolean isJson() {
        return getContentType()
            .map(type -> type.contains("application/json"))
            .orElse(false);
    }
    
    /**
     * 获取用户代理
     */
    public Optional<String> getUserAgent() {
        return getHeader("user-agent");
    }
}