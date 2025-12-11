package com.lightweb.framework.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
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
    InputStream rawInputStream,
    Map<String, Part> parts
) {
    
    public Request {
        headers = headers != null ? Map.copyOf(headers) : Map.of();
        // 规范化头部名称（全部小写）
        if (headers != null) {
            var normalizedHeaders = new HashMap<String, String>();
            headers.forEach((k, v) -> normalizedHeaders.put(k.toLowerCase(), v));
            headers = Map.copyOf(normalizedHeaders);
        } else {
            headers = Map.of();
        }
        queryParams = queryParams != null ? Map.copyOf(queryParams) : Map.of();
        pathParams = pathParams != null ? Map.copyOf(pathParams) : Map.of();
        body = body != null ? body : "";
        // 验证路径格式
        if (path != null && path.contains("..")) {
            throw new IllegalArgumentException("Invalid path: path traversal detected");
        }
        parts = parts != null ? Map.copyOf(parts) : Map.of();
    }

    /**
     * 获取文件上传部分
     */
    public Optional<Part> getPart(String name) {
        return Optional.ofNullable(parts.get(name));
    }

    /**
     * 文件部分封装类
     */
    public static class Part {
        private final String name;
        private final String filename;
        private final InputStream content;
        private final Map<String, String> headers;

        public Part(String name, String filename, InputStream content, Map<String, String> headers) {
            this.name = Objects.requireNonNull(name);
            this.filename = filename; // 允许为null（非文件字段）
            this.content = Objects.requireNonNull(content);
            this.headers = Map.copyOf(headers);
        }

        // Getters
        public String getName() { return name; }
        public Optional<String> getFilename() { return Optional.ofNullable(filename); }
        public InputStream getContent() { return content; }
        public Map<String, String> getHeaders() { return headers; }

        /**
         * 安全保存文件到指定目录
         */
        public void saveTo(Path targetDir) throws IOException {
            if (filename == null) return; // 跳过非文件字段
            if (filename.contains("..") || filename.contains("/")) {
                throw new SecurityException("Invalid filename: " + filename);
            }
            Path targetPath = targetDir.resolve(filename);
            Files.copy(content, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * 获取请求头
     */
    public Optional<String> getHeader(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
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
        if (name == null || name.contains("..") || name.contains("/")) {
            return Optional.empty(); // 防止路径遍历攻击
        }
        return Optional.ofNullable(pathParams.get(name));
    }
    
    /**
     * 检查是否为特定HTTP方法
     */
    public boolean isMethod(String method) {
        if (method == null || method.isBlank()) {
            return false;
        }
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
            .map(type -> type.trim().toLowerCase().startsWith("application/json"))
            .orElse(false);
    }
    
    /**
     * 获取用户代理
     */
    public Optional<String> getUserAgent() {
        return getHeader("user-agent");
    }
    
    /**
     * 便捷构造方法
     */
    public static Request of(String method, String path) {
        return new Request(method, path, "HTTP/1.1", Map.of(), Map.of(), Map.of(), "", null,Map.of());
    }
    
    public static Request of(String method, String path, Map<String, String> headers) {
        return new Request(method, path, "HTTP/1.1", headers, Map.of(), Map.of(), "", null,Map.of());
    }
    
    /**
     * 安全关闭输入流
     */
    public void close() {
        if (rawInputStream != null) {
            try {
                rawInputStream.close();
            } catch (IOException e) {
                // 静默处理关闭异常
            }
        }
    }
}