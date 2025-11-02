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
    }
    
    // 状态码设置方法
    public Response status(int statusCode) {
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
        headers.put(name, value);
        return this;
    }
    
    public Response contentType(String contentType) {
        return header("Content-Type", contentType);
    }
    
    // 响应体设置方法
    public Response body(String body) {
        this.body = body;
        return contentType("text/plain; charset=utf-8");
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
            sb.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
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
    
    // Cookie记录类
    public record Cookie(String name, String value, Map<String, String> attributes) {
        public Cookie(String name, String value) {
            this(name, value, Map.of());
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