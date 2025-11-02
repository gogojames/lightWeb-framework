package com.lightweb.framework.websocket;

import com.lightweb.framework.core.Request;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * WebSocket服务器构建器类
 * 使用构建器模式创建可配置的WebSocketServer实例
 */
public class WebSocketServerBuilder {
    
    private final int port;
    private long maxInactivityTime = Duration.ofMinutes(5).toMillis();
    private int maxMessageSize = 16 * 1024 * 1024; // 16MB
    private int heartbeatInterval = 30; // 30秒
    
    // 事件处理器
    private BiConsumer<WebSocketConnection, Request> onHandshake = (conn, req) -> {};
    private BiConsumer<WebSocketConnection, String> onMessage = (conn, msg) -> {};
    private BiConsumer<WebSocketConnection, byte[]> onBinaryMessage = (conn, data) -> {};
    private BiConsumer<WebSocketConnection, Integer> onClose = (conn, code) -> {};
    private BiConsumer<WebSocketConnection, Exception> onError = (conn, ex) -> {};
    
    public WebSocketServerBuilder(int port) {
        this.port = port;
    }
    
    public WebSocketServerBuilder maxInactivityTime(long milliseconds) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Max inactivity time must be positive");
        }
        this.maxInactivityTime = milliseconds;
        return this;
    }
    
    public WebSocketServerBuilder maxMessageSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Max message size must be positive");
        }
        this.maxMessageSize = size;
        return this;
    }
    
    public WebSocketServerBuilder heartbeatInterval(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Heartbeat interval must be positive");
        }
        this.heartbeatInterval = seconds;
        return this;
    }
    
    public WebSocketServerBuilder onHandshake(BiConsumer<WebSocketConnection, Request> handler) {
        this.onHandshake = handler;
        return this;
    }
    
    public WebSocketServerBuilder onMessage(BiConsumer<WebSocketConnection, String> handler) {
        this.onMessage = handler;
        return this;
    }
    
    public WebSocketServerBuilder onBinaryMessage(BiConsumer<WebSocketConnection, byte[]> handler) {
        this.onBinaryMessage = handler;
        return this;
    }
    
    public WebSocketServerBuilder onClose(BiConsumer<WebSocketConnection, Integer> handler) {
        this.onClose = handler;
        return this;
    }
    
    public WebSocketServerBuilder onError(BiConsumer<WebSocketConnection, Exception> handler) {
        this.onError = handler;
        return this;
    }
    
    public WebSocketServer build() {
        return new WebSocketServer(port, maxInactivityTime, maxMessageSize, heartbeatInterval,
            onHandshake, onMessage, onBinaryMessage, onClose, onError);
    }
}