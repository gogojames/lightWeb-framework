package com.lightweb.framework.error;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一错误处理系统
 * 使用Java 25模式匹配和记录类实现智能错误处理
 */
public class ErrorHandler {
    private final Map<Class<? extends Exception>, ErrorProcessor> processors = new ConcurrentHashMap<>();
    
    // 错误处理器记录类
    public record ErrorProcessor(
        Class<? extends Exception> exceptionType,
        String errorCode,
        int httpStatus,
        ErrorHandlerFunction handler
    ) {}
    
    // 错误处理函数接口
    @FunctionalInterface
    public interface ErrorHandlerFunction {
        void handle(Exception e, Request request, Response response);
    }
    
    public ErrorHandler() {
        registerDefaultHandlers();
    }
    
    /**
     * 注册默认错误处理器
     */
    private void registerDefaultHandlers() {
        // 404 Not Found
        register(NotFoundException.class, "NOT_FOUND", 404, (e, req, res) -> {
            res.notFound().html("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>404 Not Found</title>
                    <style>
                        body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                        h1 { color: #d32f2f; }
                        p { color: #666; }
                    </style>
                </head>
                <body>
                    <h1>404 - Page Not Found</h1>
                    <p>The requested resource was not found on this server.</p>
                    <p><a href="/">Return to Home</a></p>
                </body>
                </html>
                """);
        });
        
        // 400 Bad Request
        register(ValidationException.class, "VALIDATION_ERROR", 400, (e, req, res) -> {
            res.badRequest().json(String.format("""
                {
                    "error": "VALIDATION_ERROR",
                    "message": "%s",
                    "path": "%s"
                }
                """, e.getMessage(), req.path()));
        });
        
        // 401 Unauthorized
        register(UnauthorizedException.class, "UNAUTHORIZED", 401, (e, req, res) -> {
            res.unauthorized().html("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>401 Unauthorized</title>
                    <style>
                        body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                        h1 { color: #f57c00; }
                    </style>
                </head>
                <body>
                    <h1>401 - Unauthorized</h1>
                    <p>Authentication is required to access this resource.</p>
                </body>
                </html>
                """);
        });
        
        // 403 Forbidden
        register(ForbiddenException.class, "FORBIDDEN", 403, (e, req, res) -> {
            res.forbidden().json(String.format("""
                {
                    "error": "FORBIDDEN",
                    "message": "%s",
                    "required_permissions": []
                }
                """, e.getMessage()));
        });
        
        // 500 Internal Server Error
        register(Exception.class, "INTERNAL_ERROR", 500, (e, req, res) -> {
            res.internalError().html("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>500 Internal Server Error</title>
                    <style>
                        body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                        h1 { color: #d32f2f; }
                        .error-details { 
                            background: #f5f5f5; 
                            padding: 20px; 
                            margin: 20px auto; 
                            max-width: 600px; 
                            text-align: left;
                            border-radius: 5px;
                        }
                    </style>
                </head>
                <body>
                    <h1>500 - Internal Server Error</h1>
                    <p>An unexpected error occurred while processing your request.</p>
                    <div class="error-details">
                        <strong>Error:</strong> %s<br>
                        <strong>Path:</strong> %s<br>
                        <strong>Method:</strong> %s
                    </div>
                </body>
                </html>
                """.formatted(e.getMessage(), req.path(), req.method()));
        });
    }
    
    /**
     * 注册自定义错误处理器
     */
    public <T extends Exception> void register(Class<T> exceptionType, 
                                              String errorCode, 
                                              int httpStatus,
                                              ErrorHandlerFunction handler) {
        processors.put(exceptionType, new ErrorProcessor(exceptionType, errorCode, httpStatus, handler));
    }
    
    /**
     * 处理异常
     */
    public void handle(Exception e, Request request, Response response) {
        var processor = findBestMatchProcessor(e);
        
        // 记录错误日志
        logError(e, request, processor);
        
        // 执行错误处理
        processor.handler().handle(e, request, response);
    }
    
    /**
     * 查找最佳匹配的处理器
     */
    private ErrorProcessor findBestMatchProcessor(Exception e) {
        // 精确匹配
        var exactMatch = processors.get(e.getClass());
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // 查找父类匹配
        return processors.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(e.getClass()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseGet(() -> processors.get(Exception.class)); // 默认处理器
    }
    
    /**
     * 记录错误日志
     */
    private void logError(Exception e, Request request, ErrorProcessor processor) {
        var logMessage = String.format("""
            🚨 Error [%s] - %s
            📍 Path: %s %s
            🔧 Handler: %s
            📝 Message: %s
            """, 
            processor.errorCode(),
            e.getClass().getSimpleName(),
            request.method(),
            request.path(),
            processor.exceptionType().getSimpleName(),
            e.getMessage()
        );
        
        System.err.println(logMessage);
        
        // 记录堆栈跟踪（仅用于调试）
        if (processor.httpStatus() >= 500) {
            e.printStackTrace();
        }
    }
    
    /**
     * 获取错误处理统计信息
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "total_handlers", processors.size(),
            "handlers", processors.values().stream()
                .map(p -> Map.of(
                    "exception_type", p.exceptionType().getSimpleName(),
                    "error_code", p.errorCode(),
                    "http_status", p.httpStatus()
                ))
                .toList()
        );
    }
}