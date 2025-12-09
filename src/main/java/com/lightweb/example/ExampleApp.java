package com.lightweb.example;

import com.lightweb.framework.LightWebServer;
import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import com.lightweb.framework.router.Router;
import com.lightweb.framework.security.SecurityFilter;
import com.lightweb.framework.util.PerformanceMonitor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * LightWeb框架示例应用
 * 演示框架的核心功能和API使用
 */
public class ExampleApp {

     // 创建安全过滤器
     private static final SecurityFilter securityFilter = createSecurityFilter();
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting LightWeb Example Application...");
        
        // 创建路由器
        Router router = createRouter();
        
        // 创建并启动服务器
        LightWebServer server = LightWebServer.builder()
            .port(8080)
            .router(router)
            .securityFilter(securityFilter)
            .build();
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down server...");
            server.stop();
            
            // 输出最终性能报告
            var monitor = PerformanceMonitor.getInstance();
            System.out.println(monitor.generateReport());
        }));
        
        // 启动服务器
        server.start();
    }
    
    /**
     * 创建路由配置
     */
    private static Router createRouter() {
        Router router = new Router();
        
        // 添加性能监控中间件
        router.use((req, res) -> {
            var monitor = PerformanceMonitor.getInstance();
            var startTime = monitor.startRequest();
            
            // 在响应完成后记录性能数据
            return true; // 继续处理
        });
        
        // 添加日志中间件
        router.use((req, res) -> {
            System.out.printf("📝 %s %s - User-Agent: %s%n", 
                req.method(), req.path(), 
                req.getUserAgent().orElse("Unknown"));
            return true;
        });
        
        // 基础路由
        router.get("/", (req, res) -> {
            res.html("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>LightWeb Framework</title>
                    <style>
                        body { 
                            font-family: Arial, sans-serif; 
                            max-width: 800px; 
                            margin: 0 auto; 
                            padding: 20px;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            color: white;
                        }
                        .container { 
                            background: rgba(255,255,255,0.1); 
                            padding: 40px; 
                            border-radius: 10px; 
                            backdrop-filter: blur(10px);
                        }
                        h1 { color: #fff; text-align: center; }
                        .endpoint { 
                            background: rgba(255,255,255,0.2); 
                            padding: 15px; 
                            margin: 10px 0; 
                            border-radius: 5px;
                        }
                        code { background: rgba(0,0,0,0.3); padding: 2px 5px; border-radius: 3px; }
                        a { color: #a3e635; text-decoration: none; }
                        a:hover { text-decoration: underline; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🚀 LightWeb Framework</h1>
                        <p>基于Java 25 TLS版本的高性能轻量级Web框架</p>
                        
                        <h2>📊 可用端点</h2>
                        
                        <div class="endpoint">
                            <strong><a href="/api/hello">GET /api/hello</a></strong><br>
                            <em>基础问候接口</em>
                        </div>
                        
                        <div class="endpoint">
                            <strong><a href="/api/users/123">GET /api/users/:id</a></strong><br>
                            <em>路径参数示例</em>
                        </div>
                        
                        <div class="endpoint">
                            <strong>POST /api/users</strong><br>
                            <em>JSON数据接收示例</em>
                        </div>
                        
                        <div class="endpoint">
                            <strong><a href="/api/performance">GET /api/performance</a></strong><br>
                            <em>性能监控数据</em>
                        </div>
                        
                        <div class="endpoint">
                            <strong><a href="/api/error">GET /api/error</a></strong><br>
                            <em>错误处理演示</em>
                        </div>
                        
                        <h2>🛡️ 安全特性</h2>
                        <ul>
                            <li>XSS攻击防护</li>
                            <li>CSRF令牌验证</li>
                            <li>输入参数自动校验</li>
                            <li>安全HTTP头部自动设置</li>
                        </ul>
                        
                        <h2>⚡ 性能特性</h2>
                        <ul>
                            <li>虚拟线程支持高并发</li>
                            <li>低内存占用设计</li>
                            <li>快速冷启动 (<500ms)</li>
                            <li>实时性能监控</li>
                        </ul>
                    </div>
                </body>
                </html>
                """);
        });
        
        // API路由
        router.get("/api/hello", (req, res) -> {
            String name = req.getQueryParam("name").orElse("World");
            res.json(String.format("""
                {
                    "message": "Hello, %s!",
                    "timestamp": "%s",
                    "framework": "LightWeb"
                }
                """, name, java.time.Instant.now()));
        });
        
        router.get("/api/users/:id", (req, res) -> {
            String userId = req.getPathParam("id").orElse("unknown");
            
            var sessionId=java.util.UUID.randomUUID().toString();
            res.cookie("sessionid", sessionId, Map.of(
                "HttpOnly", "true",
                "SameSite", "Strict",
                "Secure", "true",
                "Path", "/",
                "Max-Age", "3600"
            ));
            var csrf_token = securityFilter.generateCsrfToken(sessionId);
            res.json(String.format("""
                {
                    "user": {
                        "id": "%s",
                        "name": "User %s",
                        "email": "user%s@example.com",
                        "created_at": "%s",
                        "csrf_token": %s,
                    }
                }
                """, userId, userId, userId, java.time.LocalDate.now(),csrf_token));
        });
        
        router.post("/api/users", (req, res) -> {
            if (!req.isJson()) {
                res.status(400).json("""
                    {
                        "error": "INVALID_CONTENT_TYPE",
                        "message": "Expected application/json"
                    }
                    """);
                return;
            }
            
            // 简单的JSON解析（实际应用中可以使用JSON库）
            String body = req.body().trim().replaceAll("[\\n\\r]+","");
            res.status(201).json(String.format("""
                {
                    "message": "User created successfully",
                    "data": %s,
                    "id": "%s"
                }
                """, body, java.util.UUID.randomUUID().toString()));
        });
        
        router.get("/api/performance", (req, res) -> {
            var monitor = PerformanceMonitor.getInstance();
            var memory = monitor.getMemoryStats();
            var endpoints = monitor.getEndpointPerformance();
            
            res.json(String.format("""
                {
                    "performance": {
                        "qps": %.2f,
                        "success_rate": %.1f,
                        "uptime_seconds": %.3f,
                        "total_requests": %d
                    },
                    "memory": {
                        "heap_used": %d,
                        "heap_max": %d,
                        "non_heap_used": %d,
                        "free_memory": %d
                    },
                    "endpoints": %s
                }
                """, 
                monitor.getQps(),
                monitor.getSuccessRate(),
                monitor.getQps(),
                monitor.getTotalRequests(),
                memory.heapUsed(), memory.heapMax(),
                memory.nonHeapUsed(), memory.freeMemory(),
                formatEndpointsForJson(endpoints)
            ));
        });
        
        router.get("/api/error", (req, res) -> {
            // 演示错误处理
            throw new RuntimeException("This is a test error for demonstration");
        });
        
        // 注册错误处理器
        router.onException(RuntimeException.class, (e, req, res) -> {
            res.status(500).json(String.format("""
                {
                    "error": "DEMONSTRATION_ERROR",
                    "message": "%s",
                    "path": "%s",
                    "handled_by": "CustomExceptionHandler"
                }
                """, e.getMessage(), req.path()));
        });
        
        return router;
    }
    
    /**
     * 创建安全过滤器配置
     */
    private static SecurityFilter createSecurityFilter() {
        return new SecurityFilter()
            .enableXssFilter(true)
            .enableCsrfProtection(true)
            .enableInputValidation(true)
            .enableSecurityHeaders(true)
            .addAllowedOrigin("http://localhost:3000")
            .addAllowedOrigin("https://example.com");
    }
    
    /**
     * 格式化端点性能数据为JSON
     */
    private static String formatEndpointsForJson(Map<String, PerformanceMonitor.EndpointPerformance> endpoints) {
        if (endpoints.isEmpty()) {
            return "{}";
        }
        
        var sb = new StringBuilder("{");
        var iterator = endpoints.entrySet().iterator();
        
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var perf = (PerformanceMonitor.EndpointPerformance) entry.getValue();
            
            sb.append(String.format("\n        \"%s\": {", entry.getKey()))
              .append(String.format("\n            \"request_count\": %d,", perf.requestCount()))
              .append(String.format("\n            \"average_time_ms\": %.2f,", perf.averageTime()))
              .append(String.format("\n            \"min_time_ms\": %d,", perf.minTime()))
              .append(String.format("\n            \"max_time_ms\": %d", perf.maxTime()))
              .append("\n        }");
            
            if (iterator.hasNext()) {
                sb.append(",");
            }
        }
        
        sb.append("\n    }");
        return sb.toString();
    }
    private static String TEST_FILE_PATH = "";
    record StartEndRecord(long chunkStart,long chunkEnd){}
    private static void readFile() throws IOException {
    List<StartEndRecord> records = new ArrayList<>();
    long numberOfChunks = 3l;
    try (RandomAccessFile file = new RandomAccessFile(TEST_FILE_PATH, "r")) {
        FileChannel channel = file.getChannel();
        long fileSize = channel.size();
        MemorySegment map = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, Arena.global());

        long start = 0;
        long chunkSize = fileSize / numberOfChunks;

        for (long i = 0; i < numberOfChunks; i++) {
            long endCandidate = start + chunkSize;
            while (endCandidate < fileSize && map.get(ValueLayout.JAVA_BYTE, endCandidate) != '\n') {
                endCandidate++;
            }
            records.add(new StartEndRecord(start, Math.min(endCandidate, fileSize)));
            start = Math.min(endCandidate, fileSize);
        }

        Map<String, DoubleSummaryStatistics> stringDoubleSummaryStatisticsMap = processFile((int) numberOfChunks, records, map);
        System.out.println(stringDoubleSummaryStatisticsMap);
    }
}

private static Map<String, DoubleSummaryStatistics> processFile (int numChunks, List<StartEndRecord> records, MemorySegment map) {
    Map<String, DoubleSummaryStatistics> valueMap = new HashMap<>();
    try (ExecutorService service = Executors.newFixedThreadPool(numChunks)) {
        records.parallelStream().forEach(r -> {
            service.execute(() -> {
                long chunkStart = r.chunkStart;
                long chunkEnd = r.chunkEnd;
                long index = chunkStart;
                String name = "";
                String number = "";
                // TODO: for the reader, this could and should be extracted to another method
                while (index < chunkEnd) {
                    char currentChar = (char)map.get(ValueLayout.JAVA_BYTE, index);
                    while (currentChar != ';' && index < chunkEnd) {
                        name += currentChar;
                        index++;
                        currentChar = (char)map.get(ValueLayout.JAVA_BYTE, index);
                    }
                    index++;
                    if (index >= chunkEnd) {
                        break;
                    }
                    currentChar = (char)map.get(ValueLayout.JAVA_BYTE, index);

                    while (currentChar != '\n') {
                        number += currentChar;
                        index++;
                        if (index >= chunkEnd) {
                            break;
                        }
                        currentChar = (char)map.get(ValueLayout.JAVA_BYTE, index);
                    }

                    double d = Double.parseDouble(number);
                    DoubleSummaryStatistics stats = valueMap.containsKey(name) ? valueMap.get(name) : new DoubleSummaryStatistics();
                    if (valueMap.containsKey(name)) {
                        stats.accept(d);
                    } else {
                        stats.accept(d);
                    }
                    stats.accept(d);
                    valueMap.put(name, stats);
                    name = "";
                    number = "";
                }
            });
        });
        try {
            service.shutdown();
            while (!service.awaitTermination(3, TimeUnit.MINUTES)) {
                System.out.println("Not terminated yet");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return valueMap;
    }
}
}