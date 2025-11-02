package com.lightweb.framework.websocket;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket握手协议测试
 */
class WebSocketHandshakeTest {
    
    @Test
    void testValidWebSocketRequest() {
        Request request = createValidWebSocketRequest();
        assertTrue(WebSocketHandshake.isValidWebSocketRequest(request));
    }
    
    @Test
    void testInvalidMethod() {
        Request request = new Request("POST", "/ws", "HTTP/1.1", 
            createWebSocketHeaders(), Map.of(), Map.of(), "", null);
        assertFalse(WebSocketHandshake.isValidWebSocketRequest(request));
    }
    
    @Test
    void testMissingUpgradeHeader() {
        Map<String, String> headers = createWebSocketHeaders();
        headers.remove("upgrade");
        
        Request request = new Request("GET", "/ws", "HTTP/1.1", 
            headers, Map.of(), Map.of(), "", null);
        assertFalse(WebSocketHandshake.isValidWebSocketRequest(request));
    }
    
    @Test
    void testInvalidWebSocketVersion() {
        Map<String, String> headers = createWebSocketHeaders();
        headers.put("sec-websocket-version", "8"); // 不支持的版本
        
        Request request = new Request("GET", "/ws", "HTTP/1.1", 
            headers, Map.of(), Map.of(), "", null);
        assertFalse(WebSocketHandshake.isValidWebSocketRequest(request));
    }
    
    @Test
    void testCreateHandshakeResponse() {
        Request request = createValidWebSocketRequest();
        Response response = WebSocketHandshake.createHandshakeResponse(request);
        
        // 通过响应内容验证状态码和状态消息
        String responseString = new String(response.toBytes());
        assertTrue(responseString.contains("HTTP/1.1 101 Switching Protocols"));
        assertTrue(responseString.contains("Upgrade: websocket"));
        assertTrue(responseString.contains("Connection: Upgrade"));
        assertTrue(response.toBytes().length > 0);
    }
    
    @Test
    void testGenerateAcceptKey() {
        String webSocketKey = "dGhlIHNhbXBsZSBub25jZQ==";
        String expectedAcceptKey = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
        
        // 由于generateAcceptKey是私有的，我们通过创建握手响应来测试
        Map<String, String> headers = createWebSocketHeaders();
        headers.put("sec-websocket-key", webSocketKey);
        
        Request request = new Request("GET", "/ws", "HTTP/1.1", 
            headers, Map.of(), Map.of(), "", null);
        
        Response response = WebSocketHandshake.createHandshakeResponse(request);
        String responseString = new String(response.toBytes());
        
        assertTrue(responseString.contains("Sec-WebSocket-Accept: " + expectedAcceptKey));
    }
    
    @Test
    void testGetSubProtocol() {
        Map<String, String> headers = createWebSocketHeaders();
        headers.put("sec-websocket-protocol", "chat, superchat");
        
        Request request = new Request("GET", "/ws", "HTTP/1.1", 
            headers, Map.of(), Map.of(), "", null);
        
        var subProtocol = WebSocketHandshake.getSubProtocol(request);
        assertTrue(subProtocol.isPresent());
        assertEquals("chat, superchat", subProtocol.get());
    }
    
    @Test
    void testGetExtensions() {
        Map<String, String> headers = createWebSocketHeaders();
        headers.put("sec-websocket-extensions", "permessage-deflate");
        
        Request request = new Request("GET", "/ws", "HTTP/1.1", 
            headers, Map.of(), Map.of(), "", null);
        
        var extensions = WebSocketHandshake.getExtensions(request);
        assertTrue(extensions.isPresent());
        assertEquals("permessage-deflate", extensions.get());
    }
    
    private Request createValidWebSocketRequest() {
        return new Request("GET", "/ws", "HTTP/1.1", 
            createWebSocketHeaders(), Map.of(), Map.of(), "", null);
    }
    
    private Map<String, String> createWebSocketHeaders() {
        return Map.of(
            "upgrade", "websocket",
            "connection", "Upgrade",
            "sec-websocket-version", "13",
            "sec-websocket-key", "dGhlIHNhbXBsZSBub25jZQ==",
            "host", "localhost:8080"
        );
    }
}