package com.lightweb.framework.websocket;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/**
 * WebSocket握手协议处理器
 * 实现RFC 6455标准的WebSocket握手协议
 */
public final class WebSocketHandshake {
    
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String WEBSOCKET_VERSION = "13";
    
    private WebSocketHandshake() {
        // 工具类，防止实例化
    }
    
    /**
     * 验证WebSocket升级请求
     */
    public static boolean isValidWebSocketRequest(Request request) {
        return request.isMethod("GET") &&
               isWebSocketUpgrade(request) &&
               hasWebSocketVersion(request) &&
               hasWebSocketKey(request) &&
               isSupportedVersion(request);
    }
    
    /**
     * 生成WebSocket握手响应
     */
    public static Response createHandshakeResponse(Request request) {
        if (!isValidWebSocketRequest(request)) {
            return new Response().badRequest().body("Invalid WebSocket request");
        }
        
        String webSocketKey = request.getHeader("sec-websocket-key")
            .orElseThrow(() -> new IllegalArgumentException("Missing Sec-WebSocket-Key"));
        
        String acceptKey = generateAcceptKey(webSocketKey);
        
        return new Response()
            .status(101)
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Accept", acceptKey)
            .header("Sec-WebSocket-Version", WEBSOCKET_VERSION);
    }
    
    /**
     * 生成Sec-WebSocket-Accept响应头
     */
    private static String generateAcceptKey(String webSocketKey) {
        try {
            String combined = webSocketKey + WEBSOCKET_GUID;
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }
    
    /**
     * 检查是否为WebSocket升级请求
     */
    private static boolean isWebSocketUpgrade(Request request) {
        return request.getHeader("upgrade")
            .map(upgrade -> upgrade.equalsIgnoreCase("websocket"))
            .orElse(false) &&
               request.getHeader("connection")
            .map(connection -> connection.toLowerCase().contains("upgrade"))
            .orElse(false);
    }
    
    /**
     * 检查是否包含WebSocket版本
     */
    private static boolean hasWebSocketVersion(Request request) {
        return request.getHeader("sec-websocket-version").isPresent();
    }
    
    /**
     * 检查是否包含WebSocket密钥
     */
    private static boolean hasWebSocketKey(Request request) {
        return request.getHeader("sec-websocket-key").isPresent();
    }
    
    /**
     * 检查是否支持请求的WebSocket版本
     */
    private static boolean isSupportedVersion(Request request) {
        return request.getHeader("sec-websocket-version")
            .map(version -> version.equals(WEBSOCKET_VERSION))
            .orElse(false);
    }
    
    /**
     * 获取WebSocket协议子协议
     */
    public static Optional<String> getSubProtocol(Request request) {
        return request.getHeader("sec-websocket-protocol");
    }
    
    /**
     * 获取WebSocket扩展
     */
    public static Optional<String> getExtensions(Request request) {
        return request.getHeader("sec-websocket-extensions");
    }
}