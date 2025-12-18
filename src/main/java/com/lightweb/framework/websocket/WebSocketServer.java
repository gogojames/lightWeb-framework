package com.lightweb.framework.websocket;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * WebSocket服务器核心类
 * 基于NIO的非阻塞WebSocket服务器实现
 */
public class WebSocketServer implements AutoCloseable {
    
    private final int port;
    private final AtomicBoolean running;
    private final AtomicLong connectionCounter;
    private final ConcurrentHashMap<String, WebSocketConnection> connections;
    //private final ConcurrentHashMap<SocketChannel, WebSocketConnection> channelToConnectionMap;
    private final ExecutorService virtualThreadPool;
    
    // Future引用用于优雅关闭
    private CompletableFuture<Void> mainLoopFuture;
    private CompletableFuture<Void> heartbeatFuture;
    
    // NIO组件
    private ServerSocketChannel serverChannel;
    private Selector selector;
    
    // 配置参数
    private final long maxInactivityTime;
    private final int maxMessageSize;
    private final int heartbeatInterval;
    
    // 事件处理器
    private BiConsumer<WebSocketConnection, Request> onHandshake;
    private BiConsumer<WebSocketConnection, String> onMessage;
    private BiConsumer<WebSocketConnection, byte[]> onBinaryMessage;
    private BiConsumer<WebSocketConnection, Integer> onClose;
    private BiConsumer<WebSocketConnection, Exception> onError;
    
    public WebSocketServer(int port) {
        this.port = port;
        this.running = new AtomicBoolean(false);
        this.connectionCounter = new AtomicLong(0);
        this.connections = new ConcurrentHashMap<>();
        this.virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
        
        // 默认配置
        this.maxInactivityTime = Duration.ofMinutes(5).toMillis();
        this.maxMessageSize = 16 * 1024 * 1024; // 16MB
        this.heartbeatInterval = 30; // 30秒
        
        // 默认事件处理器
        this.onHandshake = (conn, req) -> {};
        this.onMessage = (conn, msg) -> {};
        this.onBinaryMessage = (conn, data) -> {};
        this.onClose = (conn, code) -> {};
        this.onError = (conn, ex) -> {};
    }
    
    /**
     * 带配置参数的构造函数
     */
    public WebSocketServer(int port, long maxInactivityTime, int maxMessageSize, int heartbeatInterval,
                          BiConsumer<WebSocketConnection, Request> onHandshake,
                          BiConsumer<WebSocketConnection, String> onMessage,
                          BiConsumer<WebSocketConnection, byte[]> onBinaryMessage,
                          BiConsumer<WebSocketConnection, Integer> onClose,
                          BiConsumer<WebSocketConnection, Exception> onError) {
        this.port = port;
        this.running = new AtomicBoolean(false);
        this.connectionCounter = new AtomicLong(0);
        this.connections = new ConcurrentHashMap<>();
        this.virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
        
        // 使用传入的配置参数
        this.maxInactivityTime = maxInactivityTime;
        this.maxMessageSize = maxMessageSize;
        this.heartbeatInterval = heartbeatInterval;
        
        // 使用传入的事件处理器
        this.onHandshake = onHandshake;
        this.onMessage = onMessage;
        this.onBinaryMessage = onBinaryMessage;
        this.onClose = onClose;
        this.onError = onError;
    }
    
    /**
     * 启动WebSocket服务器
     */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("WebSocket server is already running");
        }
        
        // 初始化NIO组件
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("🚀 WebSocket Server started on port " + port);
        
        // 使用CompletableFuture确保顺序启动
        mainLoopFuture = CompletableFuture.runAsync(this::runMainLoop, virtualThreadPool);
        heartbeatFuture = mainLoopFuture.thenRunAsync(this::runHeartbeat, virtualThreadPool);
    }
    
    /**
     * 主事件循环
     */
    private void runMainLoop() {
        while (running.get()) {
            try {
                // 等待事件，超时时间为1秒
                if (selector.select(1000) > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                    
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        
                        if (!key.isValid()) {
                            continue;
                        }
                        
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    }
                }
                
                // 处理待发送的数据
                processPendingWrites();
                
            } catch (Exception e) {
                if (running.get()) {
                    if (e instanceof IOException) {
                        System.err.println("I/O error in main loop: " + e.getMessage());
                    } else if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        break; // 优雅退出
                    } else {
                        System.err.println("Unexpected error in main loop: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    /**
     * 处理新连接
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            System.out.println("New connection from: " + 
                clientChannel.socket().getInetAddress().getHostAddress());
        }
    }
    
    /**
     * 处理读取事件
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        // 查找对应的WebSocket连接
        WebSocketConnection connection = findConnectionByChannel(channel);
        
        try {
            if (connection == null) {
                // 新连接，处理握手
                handleHandshake(channel, key);
            } else {
                // 现有连接，处理WebSocket数据
                handleWebSocketData(connection, channel);
            }
        } catch (Exception e) {
            System.err.println("Error handling read event: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            safeCloseChannel(channel);
            if (connection != null) {
                connections.remove(connection.getId());
            }
        }
    }
    
    /**
     * 处理WebSocket握手
     */
    private void handleHandshake(SocketChannel channel, SelectionKey key) throws IOException {
        // 使用可扩展的缓冲区处理大消息
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        java.util.List<ByteBuffer> buffers = new java.util.ArrayList<>();
        int totalBytes = 0;
        
        while (true) {
            int bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                channel.close();
                return;
            }
            if (bytesRead == 0) break;
            
            buffer.flip();
            ByteBuffer copy = ByteBuffer.allocate(bytesRead);
            copy.put(buffer.array(), 0, bytesRead);
            copy.flip();
            buffers.add(copy);
            totalBytes += bytesRead;
            buffer.clear();
            
            if (totalBytes > maxMessageSize) {
                throw new IOException("Message too large");
            }
        }
        
        if (totalBytes > 0) {
            // 合并所有缓冲区
            ByteBuffer combinedBuffer = ByteBuffer.allocate(totalBytes);
            for (ByteBuffer buf : buffers) {
                combinedBuffer.put(buf);
            }
            combinedBuffer.flip();
            
            try {
                // 解析HTTP请求
                String requestData = new String(combinedBuffer.array(), 0, combinedBuffer.limit());
                Request request = parseHandshakeRequest(requestData);
                
                // 验证WebSocket握手请求
                if (WebSocketHandshake.isValidWebSocketRequest(request)) {
                    // 创建WebSocket连接
                    WebSocketConnection connection = new WebSocketConnection(channel);
                    connection.onOpen(conn -> {
                        connections.put(conn.getId(), conn);
                        connectionCounter.incrementAndGet();
                        onHandshake.accept(conn, request);
                    });
                    connection.onMessage(msg -> {
                        // 获取当前连接并传递给事件处理器
                        WebSocketConnection currentConn = findConnectionByChannel(channel);
                        if (currentConn != null) {
                            onMessage.accept(currentConn, msg);
                        }
                    });
                    connection.onBinaryMessage(data -> {
                        // 获取当前连接并传递给事件处理器
                        WebSocketConnection currentConn = findConnectionByChannel(channel);
                        if (currentConn != null) {
                            onBinaryMessage.accept(currentConn, data);
                        }
                    });
                    connection.onClose(conn -> {
                        connections.remove(conn.getId());
                        onClose.accept(conn, WebSocketConnection.CLOSE_NORMAL);
                    });
                    connection.onError(ex -> {
                        // 获取当前连接并传递给事件处理器
                        WebSocketConnection currentConn = findConnectionByChannel(channel);
                        if (currentConn != null) {
                            onError.accept(currentConn, ex);
                        }
                    });
                    
                    // 发送握手响应
                    Response handshakeResponse = WebSocketHandshake.createHandshakeResponse(request);
                    channel.write(ByteBuffer.wrap(handshakeResponse.toBytes()));
                    
                    // 注册写事件
                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    key.attach(connection);
                    
                    System.out.println("WebSocket handshake completed: " + connection.getId());
                } else {
                    // 无效的WebSocket请求，返回错误
                    Response errorResponse = new Response().badRequest().body("Invalid WebSocket request");
                    channel.write(ByteBuffer.wrap(errorResponse.toBytes()));
                    channel.close();
                }
                
            } catch (Exception e) {
                System.err.println("Error during handshake: " + e.getMessage());
                channel.close();
            }
        }
    }
    
    /**
     * 处理WebSocket数据
     */
    private void handleWebSocketData(WebSocketConnection connection, SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            // 连接关闭
            connection.close();
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            connection.handleData(buffer);
        }
    }
    
    /**
     * 处理写入事件
     */
    private void handleWrite(SelectionKey key) throws IOException {
        WebSocketConnection connection = (WebSocketConnection) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        
        if (connection != null && connection.hasDataToSend()) {
            ByteBuffer buffer = connection.getNextSendBuffer();
            if (buffer != null) {
                channel.write(buffer);
            }
        }
        
        // 如果没有更多数据要发送，取消写事件监听
        if (connection == null || !connection.hasDataToSend()) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }
    
    /**
     * 处理待发送的数据
     */
    private void processPendingWrites() {
        for (WebSocketConnection connection : connections.values()) {
            if (connection.hasDataToSend()) {
                try {
                    SocketChannel channel = connection.getChannel();
                    SelectionKey key = channel.keyFor(selector);
                    
                    if (key != null && key.isValid()) {
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    }
                } catch (Exception e) {
                    // 忽略异常
                }
            }
        }
    }
    
    /**
     * 运行心跳检测
     */
    private void runHeartbeat() {
        while (running.get()) {
            try {
                Thread.sleep(heartbeatInterval * 1000);
                
                // 直接使用ConcurrentHashMap的values()，它是线程安全的
                java.util.Collection<WebSocketConnection> connectionsCopy = connections.values();
                
                // 检测不活跃连接
                List<WebSocketConnection> inactiveConnections = connectionsCopy.stream()
                    .filter(conn -> conn.isConnected() && 
                            conn.getInactivityDuration() > maxInactivityTime)
                    .collect(java.util.stream.Collectors.toList());
                
                // 关闭不活跃连接
                for (WebSocketConnection connection : inactiveConnections) {
                    System.out.println("Closing inactive connection: " + connection.getId());
                    connection.close(WebSocketConnection.CLOSE_GOING_AWAY, "Inactivity timeout");
                }
                
                // 发送心跳包
                for (WebSocketConnection connection : connectionsCopy) {
                    if (connection.isConnected()) {
                        connection.ping();
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 安全关闭通道
     */
    private void safeCloseChannel(SocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                System.err.println("Error closing channel: " + e.getMessage());
            }
        }
    }
    
    /**
     * 根据通道查找连接
     */
    private WebSocketConnection findConnectionByChannel(SocketChannel channel) {
        for (WebSocketConnection connection : connections.values()) {
            if (connection.getChannel() == channel) {
                return connection;
            }
        }
        return null;
    }
    
    /**
     * 解析握手请求
     */
    private Request parseHandshakeRequest(String requestData) {
        // 简化版的HTTP请求解析，仅用于WebSocket握手
        String[] lines = requestData.split("\r?\n");
        
        if (lines.length == 0) {
            throw new IllegalArgumentException("Empty request");
        }
        
        // 解析请求行
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 3) {
            throw new IllegalArgumentException("Invalid request line");
        }
        
        String method = requestLine[0];
        String path = requestLine[1];
        String protocol = requestLine[2];
        
        // 解析头部
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                break; // 头部结束
            }
            
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }
        
        return new Request(method, path, protocol, headers, Map.of(), Map.of(), "", null,null);
    }
    
    /**
     * 广播消息给所有连接
     */
    public void broadcast(String message) {
        for (WebSocketConnection connection : connections.values()) {
            if (connection.isConnected()) {
                connection.send(message);
            }
        }
    }
    
    /**
     * 广播二进制消息给所有连接
     */
    public void broadcast(byte[] data) {
        for (WebSocketConnection connection : connections.values()) {
            if (connection.isConnected()) {
                connection.send(data);
            }
        }
    }
    
    /**
     * 获取活跃连接数
     */
    public int getConnectionCount() {
        return connections.size();
    }
    
    /**
     * 获取服务器端口
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 获取总连接数
     */
    public long getTotalConnections() {
        return connectionCounter.get();
    }
    
    /**
     * 停止服务器
     */
    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            try {
                // 关闭所有连接
                for (WebSocketConnection connection : connections.values()) {
                    connection.close();
                }
                connections.clear();
                
                // 关闭NIO组件
                if (selector != null) {
                    selector.close();
                }
                if (serverChannel != null) {
                    serverChannel.close();
                }
                
                // 关闭线程池
                virtualThreadPool.shutdown();
                if (!virtualThreadPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    virtualThreadPool.shutdownNow();
                }
                
                System.out.println("🛑 WebSocket Server stopped");
                
            } catch (IOException e) {
                System.err.println("Error stopping server: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                virtualThreadPool.shutdownNow();
            }
        }
    }
    
    // 配置设置方法
    public WebSocketServer withMaxInactivityTime(long milliseconds) {
        return new WebSocketServerBuilder(this.port)
            .maxInactivityTime(milliseconds)
            .maxMessageSize(this.maxMessageSize)
            .heartbeatInterval(this.heartbeatInterval)
            .onHandshake(this.onHandshake)
            .onMessage(this.onMessage)
            .onBinaryMessage(this.onBinaryMessage)
            .onClose(this.onClose)
            .onError(this.onError)
            .build();
    }
    
    public WebSocketServer withMaxMessageSize(int size) {
        return new WebSocketServerBuilder(this.port)
            .maxInactivityTime(this.maxInactivityTime)
            .maxMessageSize(size)
            .heartbeatInterval(this.heartbeatInterval)
            .onHandshake(this.onHandshake)
            .onMessage(this.onMessage)
            .onBinaryMessage(this.onBinaryMessage)
            .onClose(this.onClose)
            .onError(this.onError)
            .build();
    }
    
    public WebSocketServer withHeartbeatInterval(int seconds) {
        return new WebSocketServerBuilder(this.port)
            .maxInactivityTime(this.maxInactivityTime)
            .maxMessageSize(this.maxMessageSize)
            .heartbeatInterval(seconds)
            .onHandshake(this.onHandshake)
            .onMessage(this.onMessage)
            .onBinaryMessage(this.onBinaryMessage)
            .onClose(this.onClose)
            .onError(this.onError)
            .build();
    }
    
    // 事件处理器设置方法
    public WebSocketServer onHandshake(BiConsumer<WebSocketConnection, Request> handler) {
        this.onHandshake = handler;
        return this;
    }
    
    public WebSocketServer onMessage(BiConsumer<WebSocketConnection, String> handler) {
        this.onMessage = handler;
        return this;
    }
    
    public WebSocketServer onBinaryMessage(BiConsumer<WebSocketConnection, byte[]> handler) {
        this.onBinaryMessage = handler;
        return this;
    }
    
    public WebSocketServer onClose(BiConsumer<WebSocketConnection, Integer> handler) {
        this.onClose = handler;
        return this;
    }
    
    public WebSocketServer onError(BiConsumer<WebSocketConnection, Exception> handler) {
        this.onError = handler;
        return this;
    }
}