package com.lightweb.framework.websocket;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebSocket连接管理器
 * 管理单个WebSocket连接的完整生命周期
 */
public class WebSocketConnection implements AutoCloseable {
    
    // WebSocket关闭状态码
    public static final int CLOSE_NORMAL = 1000;
    public static final int CLOSE_GOING_AWAY = 1001;
    public static final int CLOSE_PROTOCOL_ERROR = 1002;
    public static final int CLOSE_UNSUPPORTED = 1003;
    public static final int CLOSE_NO_STATUS = 1005;
    public static final int CLOSE_ABNORMAL = 1006;
    public static final int CLOSE_INVALID_DATA = 1007;
    public static final int CLOSE_POLICY_VIOLATION = 1008;
    public static final int CLOSE_TOO_LARGE = 1009;
    public static final int CLOSE_MANDATORY_EXTENSION = 1010;
    public static final int CLOSE_SERVER_ERROR = 1011;
    public static final int CLOSE_TLS_HANDSHAKE = 1015;
    
    private final String id;
    private final SocketChannel channel;
    private final AtomicBoolean connected;
    private final AtomicBoolean closing;
    private final AtomicLong lastActivity;
    private final ConcurrentLinkedQueue<ByteBuffer> sendQueue;
    
    private static final int MAX_QUEUE_SIZE = 1000; // 最大队列大小
    
    // 事件处理器
    private Consumer<String> onMessage;
    private Consumer<byte[]> onBinaryMessage;
    private Consumer<WebSocketConnection> onOpen;
    private Consumer<WebSocketConnection> onClose;
    private Consumer<Exception> onError;
    
    // 连接属性
    private final String remoteAddress;
    private final Instant connectedAt;
    private volatile Instant closedAt;
    
    public WebSocketConnection(SocketChannel channel) throws IOException {
        this.id = UUID.randomUUID().toString();
        this.channel = channel;
        this.connected = new AtomicBoolean(true);
        this.closing = new AtomicBoolean(false);
        this.lastActivity = new AtomicLong(System.currentTimeMillis());
        this.sendQueue = new ConcurrentLinkedQueue<>();
        
        Socket socket = channel.socket();
        this.remoteAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        this.connectedAt = Instant.now();
        
        // 设置默认事件处理器
        this.onMessage = msg -> {};
        this.onBinaryMessage = data -> {};
        this.onOpen = conn -> {};
        this.onClose = conn -> {};
        this.onError = ex -> {};
    }
    
    /**
     * 发送文本消息
     */
    public void send(String message) {
        if (!isConnected()) {
            throw new IllegalStateException("Connection is closed");
        }
        
        if (sendQueue.size() >= MAX_QUEUE_SIZE) {
            throw new IllegalStateException("Send queue is full");
        }
        
        ByteBuffer frame = WebSocketFrame.createTextFrame(message, false);
        sendQueue.offer(frame);
        updateActivity();
    }
    
    /**
     * 发送二进制消息
     */
    public void send(byte[] data) {
        if (!isConnected()) {
            throw new IllegalStateException("Connection is closed");
        }
        
        ByteBuffer frame = WebSocketFrame.createBinaryFrame(data, false);
        sendQueue.offer(frame);
        updateActivity();
    }
    
    /**
     * 发送Ping帧
     */
    public void ping() {
        if (!isConnected()) {
            return;
        }
        
        ByteBuffer frame = WebSocketFrame.createPingFrame(new byte[0], false);
        sendQueue.offer(frame);
        updateActivity();
    }
    
    /**
     * 发送Pong帧
     */
    public void pong(byte[] data) {
        if (!isConnected()) {
            return;
        }
        
        ByteBuffer frame = WebSocketFrame.createPongFrame(data, false);
        sendQueue.offer(frame);
        updateActivity();
    }
    
    /**
     * 关闭连接（带状态码和原因）
     */
    public void close(int statusCode, String reason) {
        if (closing.compareAndSet(false, true)) {
            try {
                ByteBuffer closeFrame = WebSocketFrame.createCloseFrame(statusCode, reason, false);
                sendQueue.offer(closeFrame);
                
                // 等待一段时间让关闭帧发送出去
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 立即关闭，不等待
                }
                
            } catch (Exception e) {
                // 忽略关闭过程中的异常
            } finally {
                doClose();
            }
        }
    }
    
    /**
     * 处理接收到的数据
     */
    public void handleData(ByteBuffer buffer) throws IOException {
        try {
            WebSocketFrame frame = WebSocketFrame.parse(buffer);
            handleFrame(frame);
        } catch (Exception e) {
            handleError(new IOException("Failed to parse WebSocket frame", e));
        }
    }
    
    /**
     * 处理WebSocket帧
     */
    private void handleFrame(WebSocketFrame frame) throws IOException {
        updateActivity();
        
        WebSocketFrame.Opcode opcode = frame.getOpcode();
        
        switch (opcode) {
            case TEXT:
                handleTextFrame(frame);
                break;
            case BINARY:
                handleBinaryFrame(frame);
                break;
            case CLOSE:
                handleCloseFrame(frame);
                break;
            case PING:
                handlePingFrame(frame);
                break;
            case PONG:
                handlePongFrame(frame);
                break;
            case CONTINUATION:
                handleContinuationFrame(frame);
                break;
            default:
                throw new IOException("Unsupported opcode: " + opcode);
        }
    }
    
    private void handleTextFrame(WebSocketFrame frame) {
        String message = frame.getPayloadAsText();
        onMessage.accept(message);
    }
    
    private void handleBinaryFrame(WebSocketFrame frame) {
        byte[] data = frame.getPayload();
        onBinaryMessage.accept(data);
    }
    
    private void handleCloseFrame(WebSocketFrame frame) {
        // 解析关闭状态码和原因
        byte[] payload = frame.getPayload();
        int statusCode = CLOSE_NORMAL;
        String reason = "";
        
        if (payload.length >= 2) {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            statusCode = buffer.getShort() & 0xFFFF;
            if (payload.length > 2) {
                reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
            }
        }
        
        // 发送确认关闭帧
        if (!closing.get()) {
            ByteBuffer closeFrame = WebSocketFrame.createCloseFrame(statusCode, reason, false);
            sendQueue.offer(closeFrame);
        }
        
        doClose();
    }
    
    private void handlePingFrame(WebSocketFrame frame) {
        byte[] data = frame.getPayload();
        pong(data);
    }
    
    private void handlePongFrame(WebSocketFrame frame) {
        // Pong帧通常不需要特殊处理
    }
    
    private void handleContinuationFrame(WebSocketFrame frame) {
        // 暂不支持分帧消息
        throw new UnsupportedOperationException("Continuation frames not supported");
    }
    
    /**
     * 执行实际的关闭操作
     */
    private void doClose() {
        if (connected.compareAndSet(true, false)) {
            closedAt = Instant.now();
            try {
                channel.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
            onClose.accept(this);
        }
    }
    
    /**
     * 处理错误
     */
    private void handleError(Exception error) {
        onError.accept(error);
        close(CLOSE_ABNORMAL, "Internal error");
    }
    
    /**
     * 更新活动时间
     */
    private void updateActivity() {
        lastActivity.set(System.currentTimeMillis());
    }
    
    /**
     * 获取待发送的数据
     */
    public ByteBuffer getNextSendBuffer() {
        return sendQueue.poll();
    }
    
    /**
     * 检查是否有待发送的数据
     */
    public boolean hasDataToSend() {
        return !sendQueue.isEmpty();
    }
    
    // Getter方法
    public String getId() {
        return id;
    }
    
    public SocketChannel getChannel() {
        return channel;
    }
    
    public boolean isConnected() {
        return connected.get() && channel.isOpen();
    }
    
    public String getRemoteAddress() {
        return remoteAddress;
    }
    
    public Instant getConnectedAt() {
        return connectedAt;
    }
    
    public Instant getClosedAt() {
        return closedAt;
    }
    
    public long getLastActivity() {
        return lastActivity.get();
    }
    
    public long getInactivityDuration() {
        return System.currentTimeMillis() - lastActivity.get();
    }
    
    // 事件处理器设置方法
    public WebSocketConnection onMessage(Consumer<String> handler) {
        this.onMessage = handler;
        return this;
    }
    
    public WebSocketConnection onBinaryMessage(Consumer<byte[]> handler) {
        this.onBinaryMessage = handler;
        return this;
    }
    
    public WebSocketConnection onOpen(Consumer<WebSocketConnection> handler) {
        this.onOpen = handler;
        return this;
    }
    
    public WebSocketConnection onClose(Consumer<WebSocketConnection> handler) {
        this.onClose = handler;
        return this;
    }
    
    public WebSocketConnection onError(Consumer<Exception> handler) {
        this.onError = handler;
        return this;
    }
    
    @Override
    public void close() {
        close(CLOSE_NORMAL, "Normal closure");
    }
    
    @Override
    public String toString() {
        return String.format("WebSocketConnection{id=%s, remote=%s, connected=%s}", 
                           id, remoteAddress, isConnected());
    }
}