package com.lightweb.framework.core;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HTTP响应封装类
 * 使用Java 25新特性如文本块、记录类等
 */
public class Response {
    private int statusCode = 200;
    private String statusMessage = "OK";
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";
    private final List<Cookie> cookies = new ArrayList<>();
    
    public Response() {
        setDefaultHeaders();
    }
    
    private void setDefaultHeaders() {
        headers.put("Server", "LightWeb/1.0");
        headers.put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("X-Frame-Options", "DENY");
        headers.put("X-XSS-Protection", "1; mode=block");
        headers.put("Connection", "close"); // 默认关闭连接
    }
    
    // 状态码设置方法
    public Response status(int statusCode) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Invalid HTTP status code: " + statusCode);
        }
        this.statusCode = statusCode;
        this.statusMessage = getStatusMessage(statusCode);
        return this;
    }
    
    public Response ok() { return status(200); }
    public Response created() { return status(201); }
    public Response badRequest() { return status(400); }
    public Response unauthorized() { return status(401); }
    public Response forbidden() { return status(403); }
    public Response notFound() { return status(404); }
    public Response internalError() { return status(500); }
    
    // 头部设置方法
    public Response header(String name, String value) {
        // 规范化头部名称（首字母大写，其余小写）
        String normalizedName = normalizeHeaderName(name);
        headers.put(normalizedName, value);
        return this;
    }

    /**
     * 获取响应头（不区分大小写）
     */
    public Optional<String> getHeader(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String target = name.trim();
        // 优先使用规范化名称快速查找
        String normalized = normalizeHeaderName(target);
        String v = headers.get(normalized);
        if (v != null) return Optional.of(v);
        // 回退：遍历进行不区分大小写匹配
        for (var entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(target)) {
                return Optional.ofNullable(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * 返回当前所有响应头的不可变拷贝（保留大小写）。
     */
    public Map<String, String> getHeaders() {
        return Map.copyOf(headers);
    }
    
    private String normalizeHeaderName(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }
    
    public Response contentType(String contentType) {
        return header("Content-Type", contentType);
    }
    
    // 响应体设置方法
    public Response body(String body) {
        this.body = body;
        // 不强制设置字符集，保持灵活性
        return this;
    }
    
    public Response json(String json) {
        this.body = json;
        return contentType("application/json; charset=utf-8");
    }
    
    public Response html(String html) {
        this.body = html;
        return contentType("text/html; charset=utf-8");
    }
    
    // Cookie管理
    public Response cookie(String name, String value) {
        cookies.add(new Cookie(name, value));
        return this;
    }
    
    public Response cookie(String name, String value, Map<String, String> attributes) {
        cookies.add(new Cookie(name, value, attributes));
        return this;
    }
    
    /**
     * 转换为字节数组用于网络传输
     */
    public byte[] toBytes() {
        String response = buildResponseString();
        return response.getBytes(StandardCharsets.UTF_8);
    }
    
    private String buildResponseString() {
        var sb = new StringBuilder();
        
        // 状态行
        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        
        // 头部
        headers.forEach((name, value) -> sb.append(name).append(": ").append(value).append("\r\n"));
        
        // Cookie头部
        if (!cookies.isEmpty()) {
            cookies.forEach(cookie -> sb.append("Set-Cookie: ").append(cookie).append("\r\n"));
        }
        
        // 内容长度
        if (!body.isEmpty()) {
            int contentLength = body.getBytes(StandardCharsets.UTF_8).length;
            sb.append("Content-Length: ").append(contentLength).append("\r\n");
        }
        
        // 空行分隔头部和主体
        sb.append("\r\n");
        
        // 响应主体
        if (!body.isEmpty()) {
            sb.append(body);
        }
        
        return sb.toString();
    }
    
    private String getStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Response response = (Response) o;
        return statusCode == response.statusCode &&
                Objects.equals(statusMessage, response.statusMessage) &&
                Objects.equals(headers, response.headers) &&
                Objects.equals(body, response.body) &&
                Objects.equals(cookies, response.cookies);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(statusCode, statusMessage, headers, body, cookies);
    }
    
    // Cookie记录类
    public record Cookie(String name, String value, Map<String, String> attributes) {
        public Cookie(String name, String value) {
            this(name, value, Map.of("HttpOnly", "true", "SameSite", "Lax"));
        }
        
        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append(name).append("=").append(value);
            attributes.forEach((key, val) -> sb.append("; ").append(key).append("=").append(val));
            return sb.toString();
        }
    }
}