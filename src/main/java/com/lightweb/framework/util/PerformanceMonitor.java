package com.lightweb.framework.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 性能监控工具
 * 使用Java 25新特性监控内存、响应时间、QPS等关键指标
 */
public class PerformanceMonitor {
    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final ConcurrentHashMap<String, RequestStats> endpointStats = new ConcurrentHashMap<>();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // 请求统计记录类
    public record RequestStats(
        String endpoint,
        LongAdder count,
        LongAdder totalTime,
        AtomicLong minTime,
        AtomicLong maxTime
    ) {
        public RequestStats(String endpoint) {
            this(endpoint, new LongAdder(), new LongAdder(), 
                 new AtomicLong(Long.MAX_VALUE), new AtomicLong(Long.MIN_VALUE));
        }
        
        public void recordRequest(long duration) {
            count.increment();
            totalTime.add(duration);
            minTime.updateAndGet(current -> Math.min(current, duration));
            maxTime.updateAndGet(current -> Math.max(current, duration));
        }
        
        public double getAverageTime() {
            long countVal = count.sum();
            return countVal > 0 ? (double) totalTime.sum() / countVal : 0.0;
        }
    }
    
    private PerformanceMonitor() {}
    
    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * 记录请求开始
     */
    public Instant startRequest() {
        return Instant.now();
    }
    
    /**
     * 记录请求完成
     */
    public void endRequest(String endpoint, Instant startTime, boolean success) {
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        
        totalRequests.increment();
        if (success) {
            successfulRequests.increment();
        } else {
            failedRequests.increment();
        }
        
        var stats = endpointStats.computeIfAbsent(endpoint, RequestStats::new);
        stats.recordRequest(duration);
    }
    
    /**
     * 获取QPS（每秒查询数）
     */
    public double getQps() {
        long uptime = System.currentTimeMillis() - startTime.get();
        if (uptime == 0) return 0.0;
        
        return (double) totalRequests.sum() / (uptime / 1000.0);
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        long total = totalRequests.sum();
        return total > 0 ? (double) successfulRequests.sum() / total * 100 : 100.0;
    }

    /**
     * 获取总请求数
     * 
     * @return
     */
    public long getTotalRequests() {
        return totalRequests.sum();
    }
    
    /**
     * 获取内存使用情况
     */
    public MemoryStats getMemoryStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        return new MemoryStats(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getMax(),
            Runtime.getRuntime().freeMemory(),
            Runtime.getRuntime().totalMemory()
        );
    }
    
    /**
     * 获取端点性能统计
     */
    public java.util.Map<String, EndpointPerformance> getEndpointPerformance() {
        return endpointStats.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey,
                entry -> {
                    var stats = entry.getValue();
                    return new EndpointPerformance(
                        stats.count.sum(),
                        stats.getAverageTime(),
                        stats.minTime.get(),
                        stats.maxTime.get()
                    );
                }
            ));
    }
    
    /**
     * 生成性能报告
     */
    public String generateReport() {
        var memory = getMemoryStats();
        var endpointPerf = getEndpointPerformance();
        
        return String.format("""
            📊 LightWeb Performance Report
            =============================
            
            📈 General Statistics:
            • Uptime: %d seconds
            • Total Requests: %,d
            • Successful: %,d (%.1f%%)
            • Failed: %,d (%.1f%%)
            • QPS: %.2f
            
            💾 Memory Usage:
            • Heap Used: %s / %s (%.1f%%)
            • Non-Heap Used: %s / %s
            • Free Memory: %s
            • Total Memory: %s
            
            🚀 Endpoint Performance:
            %s
            """,
            getUptimeSeconds(),
            totalRequests.sum(),
            successfulRequests.sum(), getSuccessRate(),
            failedRequests.sum(), 100 - getSuccessRate(),
            getQps(),
            
            formatBytes(memory.heapUsed()), formatBytes(memory.heapMax()),
            memory.heapMax() > 0 ? (double) memory.heapUsed() / memory.heapMax() * 100 : 0,
            formatBytes(memory.nonHeapUsed()), formatBytes(memory.nonHeapMax()),
            formatBytes(memory.freeMemory()), formatBytes(memory.totalMemory()),
            
            formatEndpointPerformance(endpointPerf)
        );
    }
    
    private long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime.get()) / 1000;
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private String formatEndpointPerformance(java.util.Map<String, EndpointPerformance> perf) {
        if (perf.isEmpty()) return "    No endpoint data available\n";
        
        var sb = new StringBuilder();
        perf.forEach((endpoint, stats) -> {
            sb.append(String.format("    • %s:\n", endpoint))
              .append(String.format("      Requests: %,d\n", stats.requestCount()))
              .append(String.format("      Avg Time: %.2fms\n", stats.averageTime()))
              .append(String.format("      Min/Max: %dms / %dms\n\n", stats.minTime(), stats.maxTime()));
        });
        return sb.toString();
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        startTime.set(System.currentTimeMillis());
        totalRequests.reset();
        successfulRequests.reset();
        failedRequests.reset();
        endpointStats.clear();
    }
    
    // 记录类定义
    public record MemoryStats(
        long heapUsed,
        long heapMax,
        long nonHeapUsed,
        long nonHeapMax,
        long freeMemory,
        long totalMemory
    ) {}
    
    public record EndpointPerformance(
        long requestCount,
        double averageTime,
        long minTime,
        long maxTime
    ) {}
}