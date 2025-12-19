package com.lightweb.framework;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import com.lightweb.framework.router.Router;
import com.lightweb.framework.error.ErrorHandler;
import com.lightweb.framework.error.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LightWeb框架单元测试
 * 使用JUnit 5进行全面的功能测试
 */
class LightWebServerTest {
    
    // private LightWebServer server;
    // private Router router;
    
    // @BeforeEach
    // void setUp() {
    //     router = new Router();
    //     server = LightWebServer.builder()
    //         .port(8081) // 使用不同的端口避免冲突
    //         .router(router)
    //         .build();
    // }
    
    // @AfterEach
    // void tearDown() {
    //     if (server != null) {
    //         server.stop();
    //     }
    // }
    
    // @Test
    // void testServerBuilder() {
    //     assertNotNull(server);
    //     assertEquals(8081, server.getPort());
    //     assertNotNull(server.getRouter());
    // }
    
    // @Test
    // void testRouterRegistration() {
    //     router.get("/test", (req, res) -> res.body("GET Test"));
    //     router.post("/test", (req, res) -> res.body("POST Test"));
        
    //     var stats = router.getStats();
    //     assertEquals(2, stats.get("totalRoutes"));
    //     assertTrue(stats.get("methods").toString().contains("GET"));
    //     assertTrue(stats.get("methods").toString().contains("POST"));
    // }
    
    // @Test
    // void testRouteWithPathParameters() {
    //     router.get("/users/:id", (req, res) -> {
    //         String userId = req.getPathParam("id").orElse("unknown");
    //         res.json(String.format("{\"user_id\": \"%s\"}", userId));
    //     });
        
    //     // 模拟请求
    //     var request = new Request("GET", "/users/123", "HTTP/1.1", 
    //         Map.of(), Map.of(), Map.of("id", "123"), "", null);
    //     var response = new Response();
        
    //     router.handle(request, response);
        
    //     assertTrue(response.toBytes().equals("\"user_id\": \"123\"".getBytes(StandardCharsets.UTF_8)));
    // }
    
    // @Test
    // void testMiddlewareExecution() {
    //     var middlewareCalled = new boolean[]{false};
        
    //     router.use((req, res) -> {
    //         middlewareCalled[0] = true;
    //         return true; // 继续处理
    //     });
        
    //     router.get("/middleware", (req, res) -> res.body("Middleware Test"));
        
    //     var request = new Request("GET", "/middleware", "HTTP/1.1", 
    //         Map.of(), Map.of(), Map.of(), "", null);
    //     var response = new Response();
        
    //     router.handle(request, response);
        
    //     assertTrue(middlewareCalled[0]);
    //     assertEquals("Middleware Test", response.body(""));
    // }
    
    // @Test
    // void testMiddlewareInterruption() {
    //     router.use((req, res) -> {
    //         res.status(403).body("Access Denied");
    //         return false; // 中断处理
    //     });
        
    //     router.get("/denied", (req, res) -> res.body("Should not reach here"));
        
    //     var request = new Request("GET", "/denied", "HTTP/1.1", 
    //         Map.of(), Map.of(), Map.of(), "", null);
    //     var response = new Response();
        
    //     router.handle(request, response);
        
    //     //assertEquals(403, response.statusCode);
    //     assertEquals(new Response().body("Access Denied"), response);
    // }
    
    // @Test
    // void testExceptionHandling() {
    //     router.get("/error", (req, res) -> {
    //         throw new RuntimeException("Test error");
    //     });
        
    //     router.onException(RuntimeException.class, (e, req, res) -> {
    //         res.status(500).json(String.format("{\"error\": \"%s\"}", e.getMessage()));
    //     });
        
    //     var request = new Request("GET", "/error", "HTTP/1.1", 
    //         Map.of(), Map.of(), Map.of(), "", null);
    //     var response = new Response();
        
    //     router.handle(request, response);
        
    //     assertEquals(new Response().internalError(), response);
    //     //assertTrue(response.body().contains("\"error\": \"Test error\""));
    // }
    
    // @Test
    // void testRequestParsing() throws Exception {
    //     String httpRequest = """
    //         GET /test?param=value HTTP/1.1
    //         Host: localhost:8080
    //         User-Agent: Test-Agent
    //         Content-Type: application/json
            
    //         {"test": "data"}
    //         """;
        
    //     var inputStream = new ByteArrayInputStream(httpRequest.getBytes());
    //     var request = com.lightweb.framework.core.RequestParser.parse(inputStream);
        
    //     assertEquals("GET", request.method());
    //     assertEquals("/test", request.path());
    //     assertEquals("HTTP/1.1", request.protocol());
    //     assertEquals("localhost:8080", request.getHeader("host").orElse(""));
    //     assertEquals("value", request.getQueryParam("param").orElse(""));
    //     assertTrue(request.body().contains("\"test\": \"data\""));
    // }
    
    // @Test
    // void testResponseGeneration() {
    //     var response = new Response()
    //         .status(201)
    //         .contentType("application/json")
    //         .json("{\"message\": \"created\"}")
    //         .cookie("session", "abc123")
    //         .header("X-Custom", "value");
        
    //     byte[] responseBytes = response.toBytes();
    //     String responseString = new String(responseBytes);
        
    //     assertTrue(responseString.contains("HTTP/1.1 201 Created"));
    //     assertTrue(responseString.contains("Content-Type: application/json"));
    //     assertTrue(responseString.contains("Set-Cookie: session=abc123"));
    //     assertTrue(responseString.contains("X-Custom: value"));
    //     assertTrue(responseString.contains("\"message\": \"created\""));
    // }
    
    // @Test
    // void testPerformanceMonitor() {
    //     var monitor = com.lightweb.framework.util.PerformanceMonitor.getInstance();
    //     monitor.reset();
        
    //     var start = monitor.startRequest();
    //     monitor.endRequest("/test", start, true);
        
    //     var perf = monitor.getEndpointPerformance();
    //     assertTrue(perf.containsKey("/test"));
    //     assertEquals(1, perf.get("/test").requestCount());
        
    //     assertTrue(monitor.getQps() > 0);
    //     assertEquals(100.0, monitor.getSuccessRate());
        
    //     var memoryStats = monitor.getMemoryStats();
    //     assertTrue(memoryStats.heapUsed() > 0);
    // }
    
    // @Test
    // void testSecurityFilter() {
    //     var securityFilter = new com.lightweb.framework.security.SecurityFilter();
        
    //     // 测试XSS检测
    //     var xssRequest = new Request("GET", "/test", "HTTP/1.1", 
    //         Map.of(), Map.of("param", "<script>alert('xss')</script>"), Map.of(), "", null);
    //     var xssResponse = new Response();
        
    //     boolean xssResult = securityFilter.filter(xssRequest, xssResponse);
    //     assertFalse(xssResult); // 应该被拦截
        
    //     // 测试正常请求
    //     var normalRequest = new Request("GET", "/test", "HTTP/1.1", 
    //         Map.of(), Map.of("param", "normal value"), Map.of(), "", null);
    //     var normalResponse = new Response();
        
    //     boolean normalResult = securityFilter.filter(normalRequest, normalResponse);
    //     assertTrue(normalResult); // 应该通过
    // }
    
    // @Test
    // void testErrorHandler() {
    //     var errorHandler = new ErrorHandler();
        
    //     var request = new Request("GET", "/test", "HTTP/1.1", 
    //         Map.of(), Map.of(), Map.of(), "", null);
    //     var response = new Response();
        
    //     // 测试404处理
    //     errorHandler.handle(new NotFoundException("Not found"), 
    //                         request, response);
        
    //    assertEquals(new Response().status(404), response);
    //     //assertTrue(response.body("").contains("404 - Page Not Found"));
    // }
}