package com.lightweb.framework.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * HTTP请求解析器
 * 使用Java 25新特性如模式匹配、文本块等实现高效解析
 */
public final class RequestParser {
    private static final Pattern HEADER_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("([^&=]+)=([^&]*)");
    
    private RequestParser() {}
    
    /**
     * 解析HTTP请求
     */
    public static Request parse(InputStream inputStream) throws Exception {
        
        // 使用系统默认编码，后续根据Content-Type头部调整
        var reader = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));
        
        // 解析请求行
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            throw new IllegalArgumentException("Invalid request line");
        }
        
        var requestParts = requestLine.split(" ");
        if (requestParts.length != 3) {
            throw new IllegalArgumentException("Malformed request line: " + requestLine);
        }
        
        String method = requestParts[0];
        String fullPath = URLDecoder.decode(requestParts[1], StandardCharsets.UTF_8);
        String protocol = requestParts[2];
        
        // 验证请求方法
        if (!isValidMethod(method)) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        // 分离路径和查询参数
        var pathAndQuery = parsePathAndQuery(fullPath);
        String path = pathAndQuery.getKey();
        Map<String, String> queryParams = pathAndQuery.getValue();
        
        // 解析头部
        Map<String, String> headers = parseHeaders(reader);
        
        // 解析请求体
        String body = parseBody(reader, headers);
        
        return new Request(method, path, protocol, headers, queryParams, Map.of(), body, inputStream);
    }
    
    /**
     * 解析路径和查询参数
     */
    private static Map.Entry<String, Map<String, String>> parsePathAndQuery(String fullPath) {
        int queryStart = fullPath.indexOf('?');
        String path, queryString;
        
        if (queryStart != -1) {
            path = fullPath.substring(0, queryStart);
            queryString = fullPath.substring(queryStart + 1);
        } else {
            path = fullPath;
            queryString = "";
        }
        
        Map<String, String> queryParams = parseQueryString(queryString);
        return Map.entry(path, queryParams);
    }
    
    /**
     * 解析查询字符串
     */
    private static Map<String, String> parseQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return Map.of();
        }
        
        var params = new HashMap<String, String>();
        var matcher = QUERY_PARAM_PATTERN.matcher(queryString);
        
        while (matcher.find()) {
            String key = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        
        return Map.copyOf(params);
    }
    
    /**
     * 解析HTTP头部
     */
    private static Map<String, String> parseHeaders(BufferedReader reader) throws Exception {
        var headers = new HashMap<String, String>();
        String line;
        
        while ((line = reader.readLine()) != null && !line.isBlank()) {
            var matcher = HEADER_PATTERN.matcher(line);
            if (matcher.matches()) {
                String name = matcher.group(1).trim();
                String value = matcher.group(2).trim();
                // 规范化头部名称（可选，保持大小写但统一处理）
                headers.put(name.toLowerCase(), value);
            }
        }
        
        return Map.copyOf(headers);
    }
    
    /**
     * 解析请求体
     */
    private static String parseBody(BufferedReader reader, Map<String, String> headers) throws Exception {
        var contentLength = headers.get("content-length");
        if (contentLength == null || contentLength.isBlank()) {
            return "";
        }
        
        try {
            int length = Integer.parseInt(contentLength);
            if (length <= 0) {
                return "";
            }
            
            // 限制最大请求体大小，防止内存溢出
            final int MAX_BODY_SIZE = Math.max(Integer.MAX_VALUE,50 * 1024 * 1024); // 2G
            if (length > MAX_BODY_SIZE) {
                throw new IllegalArgumentException("Request body too large: " + length + " bytes");
            }
            
            char[] buffer = new char[length];
            int read = reader.read(buffer, 0, length);
            if (read != length) {
                read = length;
            }
            
            // 使用正确的String构造函数，然后进行字符集转换
            String bodyText = new String(buffer, 0, read);
            Charset charset = getCharsetFromHeaders(headers);
            
            // 如果字符集不是UTF-8，进行转换
            if (!charset.equals(StandardCharsets.UTF_8)) {
                byte[] bytes = bodyText.getBytes(StandardCharsets.UTF_8);
                bodyText = new String(bytes, charset);
            }
            
            return bodyText;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid content-length: " + contentLength);
        }
    }
    
    /**
     * 从头部获取字符集
     */
    private static Charset getCharsetFromHeaders(Map<String, String> headers) {
       String contentType = headers.get("content-type");
        if (contentType != null) {
            String[] parts = contentType.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.toLowerCase().startsWith("charset=")) {
                    String charsetName = part.substring(8).trim();
                    try {
                        Charset charset = Charset.forName(charsetName);
                        // 仅允许安全字符集
                        if (Set.of("UTF-8", "ISO-8859-1").contains(charsetName.toUpperCase())) {
                            return charset;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return StandardCharsets.UTF_8; // 默认使用UTF-8
    }
    
    /**
     * 验证请求方法
     */
    public static boolean isValidMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" -> true;
            default -> false;
        };
    }
    
    /**
     * 获取内容长度
     */
    public static OptionalInt getContentLength(Map<String, String> headers) {
        String value = headers.get("content-length");
        if (value == null) {
            return OptionalInt.empty();
        }
        value = value.trim();
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            int len = Integer.parseInt(value);
            return len >= 0 ? OptionalInt.of(len) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}