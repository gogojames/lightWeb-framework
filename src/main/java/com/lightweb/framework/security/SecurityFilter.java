package com.lightweb.framework.security;

import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 安全过滤器
 * 提供XSS过滤、CSRF防护、请求参数校验等安全功能
 */
public class SecurityFilter {
    private final Set<String> allowedOrigins = ConcurrentHashMap.newKeySet();
    private final Map<String, CsrfToken> csrfTokens = new ConcurrentHashMap<>();
    private final Pattern xssPattern;
    private final Pattern sqlInjectionPattern;
    
    // 安全配置
    private boolean enableXssFilter = true;
    private boolean enableCsrfProtection = true;
    private boolean enableInputValidation = true;
    private boolean enableSecurityHeaders = true;
    
    public SecurityFilter() {
        // 构建安全正则表达式
        this.xssPattern = buildXssPattern();
        this.sqlInjectionPattern = buildSqlInjectionPattern();
        
        // 添加默认允许的源
        allowedOrigins.add("http://localhost:8080");
        allowedOrigins.add("http://127.0.0.1:8080");
    }
    
    /**
     * 安全过滤主方法
     */
    public boolean filter(Request request, Response response) {
        try {
            // 1. 安全检查
            if (!performSecurityChecks(request, response)) {
                return false;
            }
            
            // 2. 设置安全头部
            if (enableSecurityHeaders) {
                setSecurityHeaders(response);
            }
            
            // 3. CSRF防护
            if (enableCsrfProtection && requiresCsrfCheck(request)) {
                if (!validateCsrfToken(request)) {
                    response.forbidden().body("CSRF token validation failed");
                    return false;
                }
            }
            
            // 4. 输入验证
            if (enableInputValidation && !validateInput(request)) {
                response.badRequest().body("Input validation failed");
                return false;
            }
            
            // 5. XSS过滤
            if (enableXssFilter) {
                filterXss(request);
            }
            
            return true;
            
        } catch (SecurityException e) {
            response.forbidden().body("Security violation detected");
            return false;
        }
    }
    
    /**
     * 执行安全检查
     */
    private boolean performSecurityChecks(Request request, Response response) {
        // 检查HTTP方法
        if (!isValidHttpMethod(request.method())) {
            response.badRequest().body("Invalid HTTP method");
            return false;
        }
        
        // 检查路径遍历攻击
        if (containsPathTraversal(request.path())) {
            response.forbidden().body("Path traversal detected");
            return false;
        }
        
        // 检查内容长度限制
        if (exceedsContentLengthLimit(request)) {
            response.badRequest().body("Content length exceeds limit");
            return false;
        }
        
        return true;
    }
    
    /**
     * 设置安全HTTP头部
     */
    private void setSecurityHeaders(Response response) {
        response.header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("X-XSS-Protection", "1; mode=block")
                .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .header("Content-Security-Policy", "default-src 'self'")
                .header("Access-Control-Allow-Origin", getAllowedOriginsHeader())
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRF-Token");
    }
    
    private String getAllowedOriginsHeader() {
        return allowedOrigins.isEmpty() ? "*" : String.join(", ", allowedOrigins);
    }
    
    /**
     * 验证CSRF令牌
     */
    private boolean validateCsrfToken(Request request) {
        String sessionId = getSessionId(request);
        if (sessionId == null) {
            return false; // 没有会话ID
        }
        
        CsrfToken csrfToken = csrfTokens.get(sessionId);
        if (csrfToken == null || csrfToken.isExpired()) {
            csrfTokens.remove(sessionId); // 清理过期令牌
            return false; // 没有预期的令牌或令牌已过期
        }
        
        // 从请求中获取令牌
        String actualToken = request.getHeader("x-csrf-token")
            .or(() -> request.getQueryParam("csrf_token"))
            .orElse("");
        
        return csrfToken.token().equals(actualToken);
    }
    
    /**
     * 生成CSRF令牌
     */
    public String generateCsrfToken(String sessionId) {
        String token = UUID.randomUUID().toString();
        csrfTokens.put(sessionId, new CsrfToken(token, System.currentTimeMillis()));
        return token;
    }
    
    /**
     * 输入验证
     */
    private boolean validateInput(Request request) {
        // 验证路径参数
        for (String param : request.pathParams().values()) {
            if (!isSafeInput(param)) {
                return false;
            }
        }
        
        // 验证查询参数
        for (String param : request.queryParams().values()) {
            if (!isSafeInput(param)) {
                return false;
            }
        }
        
        // 验证请求体（如果是表单数据）
        if (request.isJson()) {
            return validateJsonInput(request.body());
        }
        
        return true;
    }
    
    /**
     * XSS过滤
     */
    private void filterXss(Request request) {
        // 在实际实现中，这里会对请求参数进行XSS过滤
        // 由于Request是不可变的，这里主要进行验证
        validateForXss(request);
    }
    
    /**
     * 构建XSS检测模式
     */
    private Pattern buildXssPattern() {
        String[] xssPatterns = {
            "(?i)(<\\s*script|javascript\\s*:|on\\w+\\s*=)",
            "(?i)(eval\\s*\\(|expression\\s*\\(|vbscript\\s*:)",
            "(?i)(<\\s*(iframe|object|embed|applet))",
            "(?i)(\\b(data|about):)"
        };
        
        String pattern = String.join("|", xssPatterns);
        return Pattern.compile(pattern);
    }
    
    /**
     * 构建SQL注入检测模式
     */
    private Pattern buildSqlInjectionPattern() {
        String[] sqlPatterns = {
            "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|EXEC|ALTER)\\b)",
            "(--|#|/*)", "([';]|(\\\\)+)"
        };
        
        String pattern = String.join("|", sqlPatterns);
        return Pattern.compile(pattern);
    }
    
    /**
     * 验证输入安全性
     */
    private boolean isSafeInput(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }
        
        // 检查XSS
        if (xssPattern.matcher(input).find()) {
            return false;
        }
        
        // 检查SQL注入
        if (sqlInjectionPattern.matcher(input).find()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证JSON输入
     */
    private boolean validateJsonInput(String json) {
        try {
            if (json == null || json.length() > 10000) { // 限制JSON大小
                return false;
            }
            
            // 改进的JSON语法验证（不依赖第三方库）
            return isValidJsonStructure(json);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 验证JSON结构（零依赖实现）
     */
    private boolean isValidJsonStructure(String json) {
        json = json.trim();
        if (json.isEmpty()) return false;
        
        char firstChar = json.charAt(0);
        char lastChar = json.charAt(json.length() - 1);
        
        // 检查基本结构：对象或数组
        if ((firstChar == '{' && lastChar == '}') || 
            (firstChar == '[' && lastChar == ']')) {
            
            // 检查括号平衡
            int depth = 0;
            boolean inString = false;
            char prevChar = '\0';
            
            for (char c : json.toCharArray()) {
                if (c == '"' && prevChar != '\\') {
                    inString = !inString;
                } else if (!inString) {
                    if (c == '{' || c == '[') depth++;
                    if (c == '}' || c == ']') depth--;
                    if (depth < 0) return false; // 括号不匹配
                }
                prevChar = c;
            }
            
            return depth == 0; // 括号必须平衡
        }
        
        return false;
    }
    
    /**
     * 验证XSS攻击
     */
    private void validateForXss(Request request) {
        // 验证所有参数
        var allParams = new ArrayList<String>();
        allParams.addAll(request.pathParams().values());
        allParams.addAll(request.queryParams().values());
        
        for (String param : allParams) {
            if (xssPattern.matcher(param).find()) {
                throw new SecurityException("XSS attack detected");
            }
        }
    }
    
    /**
     * 检查是否需要CSRF验证
     */
    private boolean requiresCsrfCheck(Request request) {
        return switch (request.method().toUpperCase()) {
            case "POST", "PUT", "DELETE", "PATCH" -> true;
            default -> false;
        };
    }
    
    /**
     * 获取会话ID
     */
    private String getSessionId(Request request) {
        return request.getHeader("cookie")
            .map(cookie -> {
                // 安全的Cookie解析
                Pattern cookiePattern = Pattern.compile("\\bsessionid\\s*=\\s*([^;]+)");
                Matcher matcher = cookiePattern.matcher(cookie);
                if (matcher.find()) {
                    String sessionId = matcher.group(1).trim();
                    // 验证会话ID格式（UUID格式）
                    if (sessionId.matches("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")) {
                        return sessionId;
                    }
                }
                return null;
            })
            .orElse(null);
    }
    
    /**
     * 检查HTTP方法有效性
     */
    private boolean isValidHttpMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS" -> true;
            default -> false;
        };
    }
    
    /**
     * 检查路径遍历攻击
     */
    private boolean containsPathTraversal(String path) {
        return path.contains("..") || path.contains("//") || path.contains("~");
    }
    
    /**
     * 检查内容长度限制
     */
    private boolean exceedsContentLengthLimit(Request request) {
        return request.getHeader("content-length")
            .map(Integer::parseInt)
            .map(length -> length > 10 * 1024 * 1024) // 10MB限制
            .orElse(false);
    }
    
    // 配置方法
    public SecurityFilter enableXssFilter(boolean enable) {
        this.enableXssFilter = enable;
        return this;
    }
    
    public SecurityFilter enableCsrfProtection(boolean enable) {
        this.enableCsrfProtection = enable;
        return this;
    }
    
    public SecurityFilter enableInputValidation(boolean enable) {
        this.enableInputValidation = enable;
        return this;
    }
    
    public SecurityFilter enableSecurityHeaders(boolean enable) {
        this.enableSecurityHeaders = enable;
        return this;
    }
    
    public SecurityFilter addAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("Origin cannot be null or blank");
        }
        if (!origin.matches("^https?://[\\w\\-\\.]+(:\\d+)?$")) {
            throw new IllegalArgumentException("Invalid origin format: " + origin);
        }
        this.allowedOrigins.add(origin);
        return this;
    }
    
    /**
     * CSRF令牌记录类（带过期时间）
     */
    private record CsrfToken(String token, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30 * 60 * 1000; // 30分钟过期
        }
    }
}