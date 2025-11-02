package com.lightweb.framework.websocket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * WebSocket数据帧处理器
 * 实现RFC 6455标准的WebSocket帧格式
 */
public final class WebSocketFrame {
    
    // WebSocket帧操作码
    public enum Opcode {
        CONTINUATION(0x0),
        TEXT(0x1),
        BINARY(0x2),
        CLOSE(0x8),
        PING(0x9),
        PONG(0xA);
        
        private final int value;
        
        Opcode(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static Opcode fromValue(int value) {
            return Arrays.stream(values())
                .filter(opcode -> opcode.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid opcode: " + value));
        }
        
        public boolean isControlFrame() {
            return value >= 0x8;
        }
    }
    
    // WebSocket帧状态
    public record FrameHeader(
        boolean fin,
        boolean rsv1,
        boolean rsv2,
        boolean rsv3,
        Opcode opcode,
        boolean masked,
        long payloadLength
    ) {}
    
    private final FrameHeader header;
    private final byte[] maskingKey;
    private final byte[] payload;
    
    public WebSocketFrame(FrameHeader header, byte[] maskingKey, byte[] payload) {
        this.header = header;
        this.maskingKey = maskingKey;
        this.payload = payload != null ? payload : new byte[0];
    }
    
    /**
     * 从字节缓冲区解析WebSocket帧
     */
    public static WebSocketFrame parse(ByteBuffer buffer) {
        if (buffer.remaining() < 2) {
            throw new IllegalArgumentException("Insufficient data for WebSocket frame");
        }
        
        // 读取第一个字节
        byte firstByte = buffer.get();
        boolean fin = (firstByte & 0x80) != 0;
        boolean rsv1 = (firstByte & 0x40) != 0;
        boolean rsv2 = (firstByte & 0x20) != 0;
        boolean rsv3 = (firstByte & 0x10) != 0;
        int opcodeValue = firstByte & 0x0F;
        Opcode opcode = Opcode.fromValue(opcodeValue);
        
        // 读取第二个字节
        byte secondByte = buffer.get();
        boolean masked = (secondByte & 0x80) != 0;
        long payloadLength = secondByte & 0x7F;
        
        // 处理扩展的载荷长度
        if (payloadLength == 126) {
            if (buffer.remaining() < 2) {
                throw new IllegalArgumentException("Insufficient data for extended payload length");
            }
            payloadLength = buffer.getShort() & 0xFFFF;
        } else if (payloadLength == 127) {
            if (buffer.remaining() < 8) {
                throw new IllegalArgumentException("Insufficient data for extended payload length");
            }
            payloadLength = buffer.getLong();
        }
        
        // 读取掩码密钥
        byte[] maskingKey = null;
        if (masked) {
            if (buffer.remaining() < 4) {
                throw new IllegalArgumentException("Insufficient data for masking key");
            }
            maskingKey = new byte[4];
            buffer.get(maskingKey);
        }
        
        // 读取载荷数据
        if (buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException("Insufficient data for payload");
        }
        
        byte[] payload = new byte[(int) payloadLength];
        buffer.get(payload);
        
        // 如果使用了掩码，进行去掩码操作
        if (masked && maskingKey != null) {
            applyMask(payload, maskingKey);
        }
        
        FrameHeader header = new FrameHeader(fin, rsv1, rsv2, rsv3, opcode, masked, payloadLength);
        return new WebSocketFrame(header, maskingKey, payload);
    }
    
    /**
     * 构造WebSocket帧
     */
    public static ByteBuffer createFrame(Opcode opcode, byte[] payload, boolean masked) {
        return createFrame(true, false, false, false, opcode, payload, masked);
    }
    
    public static ByteBuffer createFrame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, 
                                        Opcode opcode, byte[] payload, boolean masked) {
        int payloadLength = payload != null ? payload.length : 0;
        
        // 计算帧头长度
        int headerLength = 2; // 基础头长度
        if (payloadLength > 65535) {
            headerLength += 8; // 64位长度
        } else if (payloadLength > 125) {
            headerLength += 2; // 16位长度
        }
        
        if (masked) {
            headerLength += 4; // 掩码密钥
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(headerLength + payloadLength);
        
        // 构造第一个字节
        byte firstByte = 0;
        if (fin) firstByte |= 0x80;
        if (rsv1) firstByte |= 0x40;
        if (rsv2) firstByte |= 0x20;
        if (rsv3) firstByte |= 0x10;
        firstByte |= opcode.getValue();
        buffer.put(firstByte);
        
        // 构造第二个字节
        byte secondByte = 0;
        if (masked) secondByte |= 0x80;
        
        if (payloadLength <= 125) {
            secondByte |= payloadLength;
            buffer.put(secondByte);
        } else if (payloadLength <= 65535) {
            secondByte |= 126;
            buffer.put(secondByte);
            buffer.putShort((short) payloadLength);
        } else {
            secondByte |= 127;
            buffer.put(secondByte);
            buffer.putLong(payloadLength);
        }
        
        // 添加掩码密钥
        byte[] maskingKey = null;
        if (masked) {
            maskingKey = generateMaskingKey();
            buffer.put(maskingKey);
        }
        
        // 添加载荷数据
        if (payload != null && payload.length > 0) {
            if (masked) {
                byte[] maskedPayload = payload.clone();
                applyMask(maskedPayload, maskingKey);
                buffer.put(maskedPayload);
            } else {
                buffer.put(payload);
            }
        }
        
        buffer.flip();
        return buffer;
    }
    
    /**
     * 应用掩码操作
     */
    private static void applyMask(byte[] payload, byte[] maskingKey) {
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= maskingKey[i % 4];
        }
    }
    
    /**
     * 生成随机掩码密钥
     */
    private static byte[] generateMaskingKey() {
        byte[] key = new byte[4];
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(key);
        return key;
    }
    
    // Getter方法
    public FrameHeader getHeader() {
        return header;
    }
    
    public byte[] getPayload() {
        return payload.clone();
    }
    
    public String getPayloadAsText() {
        return new String(payload, StandardCharsets.UTF_8);
    }
    
    public boolean isFin() {
        return header.fin();
    }
    
    public Opcode getOpcode() {
        return header.opcode();
    }
    
    public boolean isControlFrame() {
        return header.opcode().isControlFrame();
    }
    
    public long getPayloadLength() {
        return header.payloadLength();
    }
    
    /**
     * 创建文本帧
     */
    public static ByteBuffer createTextFrame(String text, boolean masked) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        return createFrame(Opcode.TEXT, payload, masked);
    }
    
    /**
     * 创建二进制帧
     */
    public static ByteBuffer createBinaryFrame(byte[] data, boolean masked) {
        return createFrame(Opcode.BINARY, data, masked);
    }
    
    /**
     * 创建关闭帧
     */
    public static ByteBuffer createCloseFrame(int statusCode, String reason, boolean masked) {
        ByteBuffer payload = ByteBuffer.allocate(2 + (reason != null ? reason.getBytes(StandardCharsets.UTF_8).length : 0));
        payload.putShort((short) statusCode);
        if (reason != null) {
            payload.put(reason.getBytes(StandardCharsets.UTF_8));
        }
        payload.flip();
        return createFrame(Opcode.CLOSE, payload.array(), masked);
    }
    
    /**
     * 创建Ping帧
     */
    public static ByteBuffer createPingFrame(byte[] data, boolean masked) {
        return createFrame(Opcode.PING, data, masked);
    }
    
    /**
     * 创建Pong帧
     */
    public static ByteBuffer createPongFrame(byte[] data, boolean masked) {
        return createFrame(Opcode.PONG, data, masked);
    }
}