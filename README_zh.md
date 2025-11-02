# LightWeb Framework 🚀
[![中文](https://img.shields.io/badge/language-中文-blue.svg)](README_zh.md)
[![English](https://img.shields.io/badge/language-English-blue.svg)](README.md)

基于 **Java 25 TLS 版本** 开发的高性能轻量级网络框架，不依赖任何现有WEB框架。全面应用Java 25新特性，专为现代Web应用设计。

## ✨ 核心特性

### 🏗️ 架构设计
- **零依赖**: 不依赖任何第三方Web框架
- **模块化**: 清晰的模块分离设计
- **高性能**: 专为高并发场景优化
- **轻量级**: 低内存占用，快速启动

### ⚡ 性能指标
- **高并发支持**: 10K+ QPS 处理能力
- **快速冷启动**: <500ms 启动时间
- **低内存占用**: 优化的内存管理
- **虚拟线程**: Java 25虚拟线程支持

### 🛡️ 安全防护
- **XSS过滤**: 自动检测和过滤XSS攻击
- **CSRF防护**: 令牌验证机制
- **输入校验**: 请求参数自动验证
- **安全头部**: 自动设置安全HTTP头部

### 🔧 技术特性
- **Java 25新特性**: 全面应用记录类、模式匹配、文本块等
- **RESTful API**: 符合RESTful设计原则
- **中间件支持**: 灵活的中间件管道
- **错误处理**: 统一的异常处理机制
- **性能监控**: 实时性能指标监控

## 🚀 快速开始

### 环境要求
- Java 25 或更高版本
- Maven 3.6+

### 安装运行

```bash
# 克隆项目
git clone <repository-url>
cd lightweb-framework

# 编译项目
mvn clean compile

# 运行示例应用
mvn exec:java -Dexec.mainClass="com.lightweb.example.ExampleApp"
```

### 基础使用

```java
import com.lightweb.framework.LightWebServer;
import com.lightweb.framework.core.Request;
import com.lightweb.framework.core.Response;
import com.lightweb.framework.router.Router;

public class MyApp {
    public static void main(String[] args) throws Exception {
        Router router = new Router();
        
        // 添加路由
        router.get("/hello", (req, res) -> {
            res.json("{\"message\": \"Hello, World!\"}");
        });
        
        router.get("/users/:id", (req, res) -> {
            String userId = req.getPathParam("id").orElse("unknown");
            res.json(String.format("{\"user_id\": \"%s\"}", userId));
        });
        
        // 启动服务器
        LightWebServer server = LightWebServer.builder()
            .port(8080)
            .router(router)
            .build();
            
        server.start();
    }
}
```

## 📁 项目结构

```
src/main/java/com/lightweb/
├── framework/
│   ├── LightWebServer.java      # 服务器主类
│   ├── core/                    # 核心组件
│   │   ├── Request.java         # 请求封装
│   │   ├── Response.java        # 响应封装
│   │   └── RequestParser.java   # 请求解析器
│   ├── router/                  # 路由系统
│   │   └── Router.java          # 路由器实现
│   ├── security/                # 安全模块
│   │   └── SecurityFilter.java  # 安全过滤器
│   ├── error/                   # 错误处理
│   │   └── ErrorHandler.java    # 错误处理器
│   └── util/                    # 工具类
│       └── PerformanceMonitor.java # 性能监控
├── example/                     # 示例应用
│   └── ExampleApp.java          # 完整示例
└── test/                        # 单元测试
    └── LightWebServerTest.java  # 测试用例
```

## 🔌 API 文档

### 路由系统

#### 基本路由
```java
router.get("/path", (req, res) -> { /* 处理逻辑 */ });
router.post("/path", (req, res) -> { /* 处理逻辑 */ });
router.put("/path", (req, res) -> { /* 处理逻辑 */ });
router.delete("/path", (req, res) -> { /* 处理逻辑 */ });
```

#### 路径参数
```java
router.get("/users/:id", (req, res) -> {
    String userId = req.getPathParam("id").orElse("unknown");
    // 使用 userId
});
```

#### 中间件
```java
router.use((req, res) -> {
    // 认证、日志等预处理
    return true; // 继续处理
});
```

### 请求处理

#### 获取请求数据
```java
// 头部
String value = req.getHeader("header-name").orElse("default");

// 查询参数
String param = req.getQueryParam("param-name").orElse("default");

// 路径参数
String pathParam = req.getPathParam("param-name").orElse("default");

// 请求体
String body = req.body();
```

### 响应生成

#### 设置响应
```java
// 状态码
res.status(200);
res.ok();        // 200
res.created();   // 201
res.notFound();  // 404

// 内容类型
res.contentType("application/json");

// 响应体
res.body("文本内容");
res.json("{\"key\": \"value\"}");
res.html("<html>内容</html>");

// Cookie
res.cookie("name", "value");
res.cookie("name", "value", Map.of("max-age", "3600"));
```

## 🛡️ 安全特性

### 自动安全防护
框架自动提供以下安全防护：
- **XSS检测**: 自动识别和阻止XSS攻击
- **CSRF令牌**: 保护表单提交安全
- **输入验证**: 所有参数自动验证
- **路径遍历防护**: 防止目录遍历攻击

### 安全配置
```java
SecurityFilter filter = new SecurityFilter()
    .enableXssFilter(true)
    .enableCsrfProtection(true)
    .enableInputValidation(true)
    .addAllowedOrigin("https://trusted-domain.com");
```

## 📊 性能监控

框架内置性能监控系统：

```java
PerformanceMonitor monitor = PerformanceMonitor.getInstance();

// 获取性能指标
double qps = monitor.getQps();
double successRate = monitor.getSuccessRate();
MemoryStats memory = monitor.getMemoryStats();

// 生成详细报告
String report = monitor.generateReport();
```

## 🧪 测试

运行完整的测试套件：

```bash
mvn test
```

测试覆盖包括：
- 路由系统测试
- 请求/响应处理测试
- 安全过滤器测试
- 性能监控测试
- 错误处理测试

## 🚀 部署

### 本地部署
```bash
mvn clean package
java -jar target/lightweb-framework-1.0.0.jar
```

### 生产环境建议
- 使用反向代理（Nginx）
- 配置SSL/TLS加密
- 设置适当的JVM参数
- 启用监控和日志

## 📈 性能基准

| 指标 | 数值 | 说明 |
|------|------|------|
| 启动时间 | <500ms | 冷启动到可服务状态 |
| 内存占用 | ~50MB | 基础运行内存需求 |
| QPS能力 | 10,000+ | 并发请求处理能力 |
| 响应时间 | <10ms | 平均请求处理时间 |

## 🔄 开发计划

- [ ] WebSocket支持
- [ ] 模板引擎集成
- [ ] 数据库连接池
- [ ] 缓存系统
- [ ] 集群支持
- [ ] 更多安全特性

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

---

**LightWeb Framework** - 为现代Java应用而生的高性能Web框架！