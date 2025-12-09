package com.lightweb.framework.websocket;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket数据帧测试
 */
class WebSocketFrameTest {
    
    // @Test
    // void testOpcodeValues() {
    //     assertEquals(0x0, WebSocketFrame.Opcode.CONTINUATION.getValue());
    //     assertEquals(0x1, WebSocketFrame.Opcode.TEXT.getValue());
    //     assertEquals(0x2, WebSocketFrame.Opcode.BINARY.getValue());
    //     assertEquals(0x8, WebSocketFrame.Opcode.CLOSE.getValue());
    //     assertEquals(0x9, WebSocketFrame.Opcode.PING.getValue());
    //     assertEquals(0xA, WebSocketFrame.Opcode.PONG.getValue());
    // }
    
    // @Test
    // void testOpcodeFromValue() {
    //     assertEquals(WebSocketFrame.Opcode.TEXT, WebSocketFrame.Opcode.fromValue(0x1));
    //     assertEquals(WebSocketFrame.Opcode.BINARY, WebSocketFrame.Opcode.fromValue(0x2));
    //     assertEquals(WebSocketFrame.Opcode.CLOSE, WebSocketFrame.Opcode.fromValue(0x8));
    // }
    
    // @Test
    // void testInvalidOpcode() {
    //     assertThrows(IllegalArgumentException.class, () -> {
    //         WebSocketFrame.Opcode.fromValue(0xF); // 无效的操作码
    //     });
    // }
    
    // @Test
    // void testControlFrameDetection() {
    //     assertFalse(WebSocketFrame.Opcode.TEXT.isControlFrame());
    //     assertFalse(WebSocketFrame.Opcode.BINARY.isControlFrame());
    //     assertTrue(WebSocketFrame.Opcode.CLOSE.isControlFrame());
    //     assertTrue(WebSocketFrame.Opcode.PING.isControlFrame());
    //     assertTrue(WebSocketFrame.Opcode.PONG.isControlFrame());
    // }
    
    // @Test
    // void testCreateTextFrame() {
    //     String message = "Hello, WebSocket!";
    //     ByteBuffer frame = WebSocketFrame.createTextFrame(message, false);
        
    //     assertNotNull(frame);
    //     assertTrue(frame.remaining() > 0);
        
    //     // 解析帧并验证内容
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     assertEquals(WebSocketFrame.Opcode.TEXT, parsedFrame.getOpcode());
    //     assertTrue(parsedFrame.isFin());
    //     assertEquals(message, parsedFrame.getPayloadAsText());
    // }
    
    // @Test
    // void testCreateBinaryFrame() {
    //     byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
    //     ByteBuffer frame = WebSocketFrame.createBinaryFrame(data, false);
        
    //     assertNotNull(frame);
    //     assertTrue(frame.remaining() > 0);
        
    //     // 解析帧并验证内容
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     assertEquals(WebSocketFrame.Opcode.BINARY, parsedFrame.getOpcode());
    //     assertArrayEquals(data, parsedFrame.getPayload());
    // }
    
    // @Test
    // void testCreateCloseFrame() {
    //     int statusCode = 1000;
    //     String reason = "Normal closure";
    //     ByteBuffer frame = WebSocketFrame.createCloseFrame(statusCode, reason, false);
        
    //     assertNotNull(frame);
    //     assertTrue(frame.remaining() > 0);
        
    //     // 解析帧并验证内容
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     assertEquals(WebSocketFrame.Opcode.CLOSE, parsedFrame.getOpcode());
        
    //     // 验证关闭帧的载荷结构
    //     byte[] payload = parsedFrame.getPayload();
    //     assertTrue(payload.length >= 2);
        
    //     // 解析状态码
    //     ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
    //     int parsedStatusCode = payloadBuffer.getShort() & 0xFFFF;
    //     assertEquals(statusCode, parsedStatusCode);
    // }
    
    // @Test
    // void testCreatePingFrame() {
    //     byte[] data = {0x01, 0x02, 0x03};
    //     ByteBuffer frame = WebSocketFrame.createPingFrame(data, false);
        
    //     assertNotNull(frame);
    //     assertTrue(frame.remaining() > 0);
        
    //     // 解析帧并验证内容
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     assertEquals(WebSocketFrame.Opcode.PING, parsedFrame.getOpcode());
    //     assertArrayEquals(data, parsedFrame.getPayload());
    // }
    
    // @Test
    // void testCreatePongFrame() {
    //     byte[] data = {0x04, 0x05, 0x06};
    //     ByteBuffer frame = WebSocketFrame.createPongFrame(data, false);
        
    //     assertNotNull(frame);
    //     assertTrue(frame.remaining() > 0);
        
    //     // 解析帧并验证内容
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     assertEquals(WebSocketFrame.Opcode.PONG, parsedFrame.getOpcode());
    //     assertArrayEquals(data, parsedFrame.getPayload());
    // }
    
    // @Test
    // void testFrameWithMasking() {
    //     String message = "Masked message";
    //     ByteBuffer frame = WebSocketFrame.createTextFrame(message, true);
        
    //     assertNotNull(frame);
    //     assertTrue(frame.remaining() > 0);
        
    //     // 解析帧并验证内容
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     assertEquals(WebSocketFrame.Opcode.TEXT, parsedFrame.getOpcode());
    //     assertEquals(message, parsedFrame.getPayloadAsText());
    // }
    
    // @Test
    // void testLargePayloadFrame() {
    //     // 创建大于125字节的载荷
    //     StringBuilder largeMessage = new StringBuilder();
    //     for (int i = 0; i < 200; i++) {
    //         largeMessage.append("Test message ").append(i).append("\n");
    //     }
        
    //     ByteBuffer frame = WebSocketFrame.createTextFrame(largeMessage.toString(), false);
    //     assertNotNull(frame);
    //     assertTrue(frame.remaining() > 0);
        
    //     // 解析帧并验证内容
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     assertEquals(WebSocketFrame.Opcode.TEXT, parsedFrame.getOpcode());
    //     assertEquals(largeMessage.toString(), parsedFrame.getPayloadAsText());
    // }
    
    // @Test
    // void testFrameParsingWithInsufficientData() {
    //     ByteBuffer buffer = ByteBuffer.allocate(1);
    //     buffer.put((byte) 0x81); // FIN=1, Opcode=1 (TEXT)
    //     buffer.flip();
        
    //     assertThrows(IllegalArgumentException.class, () -> {
    //         WebSocketFrame.parse(buffer);
    //     });
    // }
    
    // @Test
    // void testFrameHeaderProperties() {
    //     String message = "Test message";
    //     ByteBuffer frame = WebSocketFrame.createFrame(
    //         false, // FIN
    //         true,  // RSV1
    //         false, // RSV2
    //         true,  // RSV3
    //         WebSocketFrame.Opcode.TEXT,
    //         message.getBytes(),
    //         false
    //     );
        
    //     WebSocketFrame parsedFrame = WebSocketFrame.parse(frame);
    //     var header = parsedFrame.getHeader();
        
    //     assertFalse(header.fin());
    //     assertTrue(header.rsv1());
    //     assertFalse(header.rsv2());
    //     assertTrue(header.rsv3());
    //     assertEquals(WebSocketFrame.Opcode.TEXT, header.opcode());
    //     assertFalse(header.masked());
    //     assertEquals(message.length(), header.payloadLength());
    // }
}