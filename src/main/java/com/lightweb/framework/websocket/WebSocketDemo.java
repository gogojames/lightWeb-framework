package com.lightweb.framework.websocket;

import com.lightweb.framework.LightWebServer;
import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import com.lightweb.framework.router.Router;
import java.io.IOException;

/**
 * WebSocket功能演示示例
 * 展示如何在LightWeb框架中使用WebSocket功能
 */
public class WebSocketDemo {
    
    public static void main(String[] args) throws IOException {
        // 创建路由器
        Router router = new Router();
        
        // 创建WebSocket适配器
        WebSocketAdapter webSocketAdapter = WebSocketAdapter.builder()
            .port(8081)
            .router(router)
            .build();
        
        // 注册WebSocket事件处理器
        webSocketAdapter.onHandshake((connection, request) -> {
            System.out.println("🔗 WebSocket连接建立: " + connection.getId());
            System.out.println("   客户端地址: " + connection.getRemoteAddress());
            System.out.println("   请求路径: " + request.path());
        });
        
        webSocketAdapter.onMessage((connection, message) -> {
            try {
                System.out.println("📨 收到消息: " + message);
                
                // 广播消息给所有连接
                String broadcastMessage = String.format(
                    "用户 %s 说: %s", 
                    connection.getId().substring(0, 8), 
                    message
                );
                webSocketAdapter.broadcast(broadcastMessage);
            } catch (Exception e) {
                System.err.println("❌ 消息处理错误: " + e.getMessage());
                // 记录错误但不中断连接
            }
        });
        
        webSocketAdapter.onClose((connection, statusCode) -> {
            System.out.println("🔌 WebSocket连接关闭: " + connection.getId());
            System.out.println("   关闭状态码: " + statusCode);
        });
        
        webSocketAdapter.onError((connection, exception) -> {
            System.err.println("❌ WebSocket错误: " + exception.getMessage());
        });
        
        // 注册HTTP路由
        router.get("/", (req, res) -> {
            res.html("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>LightWeb WebSocket Demo</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 40px; }
                        .container { max-width: 800px; margin: 0 auto; }
                        .status { padding: 10px; background: #f0f0f0; border-radius: 5px; margin-bottom: 20px; }
                        .messages { height: 300px; border: 1px solid #ccc; overflow-y: scroll; padding: 10px; margin-bottom: 10px; }
                        .input-group { display: flex; gap: 10px; }
                        input { flex: 1; padding: 10px; border: 1px solid #ccc; border-radius: 5px; }
                        button { padding: 10px 20px; background: #007bff; color: white; border: none; border-radius: 5px; cursor: pointer; }
                        button:hover { background: #0056b3; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🚀 LightWeb WebSocket Demo</h1>
                        
                        <div class="status">
                            <strong>WebSocket状态:</strong> 
                            <span id="status">未连接</span>
                            <span id="connectionCount"></span>
                        </div>
                        
                        <div class="messages" id="messages"></div>
                        
                        <div class="input-group">
                            <input type="text" id="messageInput" placeholder="输入消息..." />
                            <button onclick="sendMessage()">发送</button>
                            <button onclick="connect()">连接</button>
                            <button onclick="disconnect()">断开</button>
                        </div>
                    </div>
                    
                    <script>
                        let ws = null;
                        
                        function connect() {
                            if (ws && ws.readyState === WebSocket.OPEN) {
                                alert('已经连接');
                                return;
                            }
                            
                            ws = new WebSocket('ws://localhost:8081/ws/chat');
                            
                            ws.onopen = function(event) {
                                updateStatus('已连接', 'success');
                                addMessage('系统', '连接成功');
                            };
                            
                            ws.onmessage = function(event) {
                                const data = JSON.parse(event.data);
                                addMessage(data.user || '系统', data.message);
                            };
                            
                            ws.onclose = function(event) {
                                updateStatus('已断开', 'error');
                                addMessage('系统', '连接已断开');
                            };
                            
                            ws.onerror = function(event) {
                                updateStatus('连接错误', 'error');
                                addMessage('系统', '连接发生错误');
                            };
                        }
                        
                        function disconnect() {
                            if (ws) {
                                ws.close();
                                ws = null;
                            }
                        }
                        
                        function sendMessage() {
                            const input = document.getElementById('messageInput');
                            const message = input.value.trim();
                            
                            if (!message) {
                                alert('请输入消息');
                                return;
                            }
                            
                            if (!ws || ws.readyState !== WebSocket.OPEN) {
                                alert('请先连接WebSocket');
                                return;
                            }
                            
                            const data = {
                                type: 'message',
                                message: message,
                                timestamp: new Date().toISOString()
                            };
                            
                            ws.send(JSON.stringify(data));
                            input.value = '';
                        }
                        
                        function updateStatus(status, type) {
                            const statusElement = document.getElementById('status');
                            statusElement.textContent = status;
                            statusElement.className = type;
                        }
                        
                        function addMessage(user, message) {
                            const messagesElement = document.getElementById('messages');
                            const messageElement = document.createElement('div');
                            messageElement.innerHTML = `<strong>${user}:</strong> ${message}`;
                            messagesElement.appendChild(messageElement);
                            messagesElement.scrollTop = messagesElement.scrollHeight;
                        }
                        
                        // 回车发送消息
                        document.getElementById('messageInput').addEventListener('keypress', function(e) {
                            if (e.key === 'Enter') {
                                sendMessage();
                            }
                        });
                        
                        // 页面加载时自动连接
                        window.addEventListener('load', connect);
                    </script>
                </body>
                </html>
            """);
        });
        
        // 启动HTTP服务器
        LightWebServer httpServer = LightWebServer.builder()
            .port(8080)
            .router(router)
            .build();
        
        // 启动WebSocket适配器
        webSocketAdapter.start();
        
        // 启动HTTP服务器
        httpServer.start();
        
        System.out.println("\n🎯 演示应用已启动");
        System.out.println("📡 HTTP服务器: http://localhost:8080");
        System.out.println("🔌 WebSocket服务器: ws://localhost:8081");
        System.out.println("\n💡 使用说明:");
        System.out.println("1. 打开浏览器访问 http://localhost:8080");
        System.out.println("2. 点击[连接]按钮建立WebSocket连接");
        System.out.println("3. 发送消息进行实时通信");
        System.out.println("4. 打开多个浏览器标签页测试广播功能");
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 正在关闭服务器...");
            webSocketAdapter.stop();
            httpServer.stop();
            System.out.println("✅ 服务器已关闭");
        }));
    }
}