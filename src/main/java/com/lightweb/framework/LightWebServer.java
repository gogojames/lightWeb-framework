package com.lightweb.framework;

import com.lightweb.framework.core.*;
import com.lightweb.framework.router.Router;
import com.lightweb.framework.security.SecurityFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轻量级Web服务器主类
 * 使用Java 25新特性实现高性能网络框架
 */
//public sealed class LightWebServer permits LightWebServer.Builder {
public final class LightWebServer {
    private final int port;
    private final Router router;
    private final SecurityFilter securityFilter;
    private final ExecutorService workerPool;
    private final AtomicBoolean running;
    private ServerSocket serverSocket;
    
    private LightWebServer(Builder builder) {
        this.port = builder.port;
        this.router = builder.router;
        this.securityFilter = builder.securityFilter;
        this.workerPool = Executors.newVirtualThreadPerTaskExecutor();
        this.running = new AtomicBoolean(false);
    }

    public int getPort() {
        return port;
    }

    public Router getRouter() {
        return router;
    }
    
    /**
     * 启动服务器
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }
        
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(port));
        
        System.out.println("🚀 LightWeb Server started on port " + port);
        
        // 使用虚拟线程处理连接
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                workerPool.submit(() -> handleConnection(clientSocket));
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 处理客户端连接
     */
    private void handleConnection(Socket clientSocket) {
        try (clientSocket) {
            var request = RequestParser.parse(clientSocket.getInputStream());
            var response = new Response();
            
            // 安全过滤
            if (!securityFilter.filter(request, response)) {
                sendResponse(clientSocket, response);
                return;
            }
            
            // 路由处理
            router.handle(request, response);
            
            sendResponse(clientSocket, response);
        } catch (Exception e) {
            System.err.println("Error handling connection: " + e.getMessage());
        }
    }
    
    /**
     * 发送响应
     */
    private void sendResponse(Socket clientSocket, Response response) throws IOException {
        var output = clientSocket.getOutputStream();
        output.write(response.toBytes());
        output.flush();
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                workerPool.shutdown();
                System.out.println("🛑 LightWeb Server stopped");
            } catch (IOException e) {
                System.err.println("Error stopping server: " + e.getMessage());
            }
        }
    }
    
    /**
     * 构建器模式
     */
    public static final class Builder{
        private int port = 8080;
        private Router router = new Router();
        private SecurityFilter securityFilter = new SecurityFilter();
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder router(Router router) {
            this.router = router;
            return this;
        }
        
        public Builder securityFilter(SecurityFilter securityFilter) {
            this.securityFilter = securityFilter;
            return this;
        }
        
        public LightWebServer build() {
            return new LightWebServer(this);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}