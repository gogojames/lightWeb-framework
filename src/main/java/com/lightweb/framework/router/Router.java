package com.lightweb.framework.router;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * 高性能路由系统
 * 使用Java 25新特性如模式匹配、记录类、虚拟线程等
 */
public class Router {
    private final Map<String, List<Route>> routes = new ConcurrentHashMap<>();
    private final List<Middleware> middlewares = new CopyOnWriteArrayList<>();
    private final Map<Class<? extends Exception>, ExceptionHandler> exceptionHandlers = new ConcurrentHashMap<>();
    
    // 路由记录类
    public record Route(String pattern, Pattern regex, BiConsumer<Request, Response> handler, 
                       Set<String> methods, List<String> paramNames) {
        
        public Route {
            methods = Set.copyOf(methods);
            paramNames = List.copyOf(paramNames);
        }
    }
    
    // 中间件函数式接口
    @FunctionalInterface
    public interface Middleware {
        boolean handle(Request request, Response response);
    }
    
    // 异常处理器
    @FunctionalInterface
    public interface ExceptionHandler {
        void handle(Exception e, Request request, Response response);
    }
    
    /**
     * 注册路由
     */
    public Router route(String method, String pattern, BiConsumer<Request, Response> handler) {
        var route = createRoute(method, pattern, handler);
        routes.computeIfAbsent(method.toUpperCase(), k -> new CopyOnWriteArrayList<>()).add(route);
        return this;
    }
    
    public Router get(String pattern, BiConsumer<Request, Response> handler) {
        return route("GET", pattern, handler);
    }
    
    public Router post(String pattern, BiConsumer<Request, Response> handler) {
        return route("POST", pattern, handler);
    }
    
    public Router put(String pattern, BiConsumer<Request, Response> handler) {
        return route("PUT", pattern, handler);
    }
    
    public Router delete(String pattern, BiConsumer<Request, Response> handler) {
        return route("DELETE", pattern, handler);
    }
    
    /**
     * 创建路由
     */
    private Route createRoute(String method, String pattern, BiConsumer<Request, Response> handler) {
        var paramNames = new ArrayList<String>();
        var regexPattern = buildRegex(pattern, paramNames);
        
        return new Route(
            pattern,
            regexPattern,
            handler,
            Set.of(method.toUpperCase()),
            paramNames
        );
    }
    
    /**
     * 构建正则表达式模式
     */
    private Pattern buildRegex(String pattern, List<String> paramNames) {
        var regex = new StringBuilder("^");
        var parts = pattern.split("/");
        var len = parts.length;
        for (String part : parts) {
            if (part.startsWith(":")) {
                String paramName = part.substring(1);
                paramNames.add(paramName);
                regex.append("/([^/]+)");
            } else if (!part.isEmpty()) {
                regex.append("/").append(Pattern.quote(part));
            }
        }
        
        if (len == 0) {
            regex.append("/");
        }
        
        regex.append("$");
        return Pattern.compile(regex.toString());
    }
    
    /**
     * 处理请求
     */
    public void handle(Request request, Response response) {
        try {
            // 执行中间件
            for (var middleware : middlewares) {
                if (!middleware.handle(request, response)) {
                    return; // 中间件中断处理
                }
            }
            
            // 查找匹配的路由
            var route = findRoute(request.method(), request.path());
            if (route == null) {
                response.notFound().body("404 Not Found");
                return;
            }
            
            // 提取路径参数
            var pathParams = extractPathParams(route, request.path());
            
            // 创建带有路径参数的请求对象
            var enrichedRequest = new Request(
                request.method(),
                request.path(),
                request.protocol(),
                request.headers(),
                request.queryParams(),
                pathParams,
                request.body(),
                request.rawInputStream()
            );
            
            // 执行路由处理器
            route.handler().accept(enrichedRequest, response);
            
        } catch (Exception e) {
            handleException(e, request, response);
        }
    }
    
    /**
     * 查找匹配的路由
     */
    private Route findRoute(String method, String path) {
        var methodRoutes = routes.get(method.toUpperCase());
        if (methodRoutes == null) {
            return null;
        }
        System.out.printf("Finding route for method: %s, path: %s%n", method, path);
        return methodRoutes.parallelStream()
            .filter(route -> route.regex().matcher(path).matches())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 提取路径参数
     */
    private Map<String, String> extractPathParams(Route route, String path) {
        var matcher = route.regex().matcher(path);
        if (!matcher.matches()) {
            return Map.of();
        }
        
        var params = new HashMap<String, String>();
        for (int i = 0; i < route.paramNames().size(); i++) {
            String paramName = route.paramNames().get(i);
            String paramValue = matcher.group(i + 1);
            params.put(paramName, paramValue);
        }
        
        return Map.copyOf(params);
    }
    
    /**
     * 注册中间件
     */
    public Router use(Middleware middleware) {
        middlewares.add(middleware);
        return this;
    }
    
    /**
     * 注册异常处理器
     */
    public <T extends Exception> Router onException(Class<T> exceptionClass, ExceptionHandler handler) {
        exceptionHandlers.put(exceptionClass, handler);
        return this;
    }
    
    /**
     * 处理异常
     */
    private void handleException(Exception e, Request request, Response response) {
        // 查找匹配的异常处理器
        var handler = exceptionHandlers.entrySet().stream()
            .filter(entry -> entry.getKey().isInstance(e))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(this::defaultExceptionHandler);
        
        handler.handle(e, request, response);
    }
    
    /**
     * 默认异常处理器
     */
    private void defaultExceptionHandler(Exception e, Request request, Response response) {
        System.err.println("Unhandled exception: " + e.getMessage());
        e.printStackTrace();
        
        response.internalError().body("""
            <!DOCTYPE html>
            <html>
            <head><title>500 Internal Server Error</title></head>
            <body>
                <h1>500 Internal Server Error</h1>
                <p>An unexpected error occurred.</p>
            </body>
            </html>
            """);
    }
    
    /**
     * 获取路由统计信息
     */
    public Map<String, Object> getStats() {
        var stats = new HashMap<String, Object>();
        stats.put("totalRoutes", routes.values().stream().mapToInt(List::size).sum());
        stats.put("methods", routes.keySet());
        stats.put("middlewareCount", middlewares.size());
        stats.put("exceptionHandlers", exceptionHandlers.size());
        return Map.copyOf(stats);
    }
}