package com.lightweb.framework.websocket;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import com.lightweb.framework.router.Router;
import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * WebSocket适配器
 * 将WebSocket功能集成到LightWeb框架中
 */
public class WebSocketAdapter {
    
    private final WebSocketServer webSocketServer;
    private final Router router;
    
    public WebSocketAdapter(int port, Router router) {
        this.webSocketServer = new WebSocketServer(port);
        this.router = router;
    }
    
    /**
     * 启动WebSocket适配器
     */
    public void start() throws IOException {
        // 注册WebSocket路由
        registerWebSocketRoutes();
        
        // 启动WebSocket服务器
        webSocketServer.start();
        
        System.out.println("🔌 WebSocket Adapter started on port " + webSocketServer.getPort());
    }
    
    /**
     * 注册WebSocket路由
     */
    private void registerWebSocketRoutes() {
        // 注册WebSocket握手端点
        router.get("/ws", this::handleWebSocketUpgrade);
        router.get("/ws/:channel", this::handleWebSocketUpgrade);
        
        // 注册WebSocket状态端点
        router.get("/ws/status", this::handleStatusRequest);
    }
    
    /**
     * 处理WebSocket升级请求
     */
    private void handleWebSocketUpgrade(Request request, Response response) {
        // 检查是否为WebSocket升级请求
        if (WebSocketHandshake.isValidWebSocketRequest(request)) {
            try {
                // 实际处理WebSocket升级
                Response handshakeResponse = WebSocketHandshake.createHandshakeResponse(request);
                // 直接使用握手响应，因为当前架构中WebSocket服务器是独立的
                // 这里主要进行握手验证，实际连接由独立的WebSocket服务器处理
                System.out.println("🔗 WebSocket握手成功: " + request.path());
            } catch (Exception e) {
                response.status(500).body("WebSocket upgrade failed: " + e.getMessage());
            }
        } else {
            response.status(400)
                   .contentType("application/json")
                   .json("""
                   {
                     "error": "Invalid WebSocket request",
                     "required_headers": ["Upgrade", "Connection", "Sec-WebSocket-Version", "Sec-WebSocket-Key"]
                   }
                   """);
        }
    }
    
    /**
     * 处理状态请求
     */
    private void handleStatusRequest(Request request, Response response) {
        response.contentType("application/json")
               .json(String.format("""
               {
                 "websocket_server": {
                   "port": %d,
                   "active_connections": %d,
                   "total_connections": %d,
                   "status": "running"
                 },
                 "lightweb_framework": {
                   "version": "1.0.0",
                   "websocket_support": true
                 }
               }
               """, 
               webSocketServer.getPort(),
               webSocketServer.getConnectionCount(),
               webSocketServer.getTotalConnections()));
    }
    
    /**
     * 注册WebSocket消息处理器
     */
    public WebSocketAdapter onMessage(BiConsumer<WebSocketConnection, String> handler) {
        webSocketServer.onMessage(handler);
        return this;
    }
    
    /**
     * 注册WebSocket二进制消息处理器
     */
    public WebSocketAdapter onBinaryMessage(BiConsumer<WebSocketConnection, byte[]> handler) {
        webSocketServer.onBinaryMessage(handler);
        return this;
    }
    
    /**
     * 注册WebSocket握手处理器
     */
    public WebSocketAdapter onHandshake(BiConsumer<WebSocketConnection, Request> handler) {
        webSocketServer.onHandshake(handler);
        return this;
    }
    
    /**
     * 注册WebSocket关闭处理器
     */
    public WebSocketAdapter onClose(BiConsumer<WebSocketConnection, Integer> handler) {
        webSocketServer.onClose(handler);
        return this;
    }
    
    /**
     * 注册WebSocket错误处理器
     */
    public WebSocketAdapter onError(BiConsumer<WebSocketConnection, Exception> handler) {
        webSocketServer.onError(handler);
        return this;
    }
    
    /**
     * 广播消息给所有WebSocket连接
     */
    public void broadcast(String message) {
        webSocketServer.broadcast(message);
    }
    
    /**
     * 广播二进制消息给所有WebSocket连接
     */
    public void broadcast(byte[] data) {
        webSocketServer.broadcast(data);
    }
    
    /**
     * 获取活跃连接数
     */
    public int getConnectionCount() {
        return webSocketServer.getConnectionCount();
    }
    
    /**
     * 停止WebSocket适配器
     */
    public void stop() {
        webSocketServer.close();
        System.out.println("🔌 WebSocket Adapter stopped");
    }
    
    /**
     * 构建器模式
     */
    public static class Builder {
        private int port = 8081;
        private Router router;
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder router(Router router) {
            this.router = router;
            return this;
        }
        
        public WebSocketAdapter build() {
            if (router == null) {
                throw new IllegalStateException("Router is required");
            }
            return new WebSocketAdapter(port, router);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}