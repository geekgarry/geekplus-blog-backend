package com.geekplus.webapp.common.monitor.controller;

import com.geekplus.common.config.WebAppConfig;
import com.geekplus.common.constant.Constant;
import com.geekplus.common.domain.Result;
import com.geekplus.webapp.common.monitor.entity.JvmMetric;
import com.geekplus.webapp.common.monitor.mapper.JvmMetricMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.management.MBeanServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * author     : geekplus
 * email      :
 * date       : 4/11/26 8:06 PM
 * description: //JVM监控
 */
@RestController
@RequestMapping("/monitor/jvm")
public class JvmMonitorController {

//    @Autowired
//    private JvmMetricMapper jvmMetricMapper;

    // 假设的日志和转储文件存储目录
    private final String LOG_DIR = WebAppConfig.getProfile()+"/logs";
    private final String DUMP_DIR = WebAppConfig.getProfile()+"/dumps";
    private final String GC_LOG_FILE = LOG_DIR + "/gc.log";

    /**
     * 获取实时JVM和系统指标
     */
    @GetMapping("/metrics")
    public Result getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // 内存指标
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        metrics.put("heapInit", heapUsage.getInit());
        metrics.put("heapUsed", heapUsage.getUsed());
        metrics.put("heapMax", heapUsage.getMax());
        metrics.put("heapCommitted", heapUsage.getCommitted());

        metrics.put("nonHeapUsed", nonHeapUsage.getUsed());
        metrics.put("nonHeapMax", nonHeapUsage.getMax());

        // 计算 JVM 内存使用率
        double jvmMemoryUsageRate = heapUsage.getMax() > 0 ? (double) heapUsage.getUsed() / heapUsage.getMax() : 0.0;
        metrics.put("jvmMemoryUsageRate", jvmMemoryUsageRate);

        // 线程指标
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        metrics.put("threadCount", threadMXBean.getThreadCount());
        metrics.put("peakThreadCount", threadMXBean.getPeakThreadCount());

        // 操作系统和CPU指标
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        metrics.put("systemLoadAverage", osBean.getSystemLoadAverage());

        // 注意：获取精确的进程CPU使用率和物理内存需要转换为 com.sun.management.OperatingSystemMXBean
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            metrics.put("processCpuLoad", sunOsBean.getProcessCpuLoad());
            metrics.put("systemCpuLoad", sunOsBean.getSystemCpuLoad());

            // 新增：服务器物理内存指标
            long totalPhysicalMemory = sunOsBean.getTotalPhysicalMemorySize();
            long freePhysicalMemory = sunOsBean.getFreePhysicalMemorySize();
            metrics.put("totalPhysicalMemory", totalPhysicalMemory);
            metrics.put("freePhysicalMemory", freePhysicalMemory);
            metrics.put("usedPhysicalMemory", totalPhysicalMemory - freePhysicalMemory);
            // 计算 CPU 内存使用率
            double cpuMemoryUsageRate = totalPhysicalMemory > 0 ? (double) (totalPhysicalMemory - freePhysicalMemory) / totalPhysicalMemory : 0.0;
            metrics.put("cpuMemoryUsageRate", cpuMemoryUsageRate);
        }

        return Result.success(metrics);
    }

    /**
     * 手动触发GC
     */
    @PostMapping("/gc")
    public Result triggerGc() {
        System.gc();
        // 也可以使用Linux命令: executeLinuxCommand("jcmd " + getPid() + " GC.run");
        return Result.success("GC请求已发送至JVM");
    }

    /**
     * 获取线程堆栈跟踪
     */
    @GetMapping("/thread-dump")
    public Result getThreadDump() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
        List<Map<String, Object>> dump = new ArrayList<>();

        for (ThreadInfo info : threadInfos) {
            Map<String, Object> t = new HashMap<>();
            t.put("threadId", info.getThreadId());
            t.put("threadName", info.getThreadName());
            t.put("threadState", info.getThreadState().name());

            // 格式化堆栈信息
            StringBuilder stackTrace = new StringBuilder();
            for (StackTraceElement element : info.getStackTrace()) {
                stackTrace.append(element.toString()).append("\n");
            }
            t.put("stackTrace", stackTrace.toString());
            dump.add(t);
        }
        return Result.success(dump);
    }

    /**
     * 动态调整JVM参数 (模拟接口)
     * 注意：标准HotSpot JVM不支持在运行时动态修改堆大小(-Xmx)、新生代比例(-XX:NewRatio)或更改垃圾回收器。
     * 这些操作必须通过修改启动脚本并重启进程来实现。
     */
    /**
     * 使用 Linux 命令动态调整 JVM 参数或系统内存
     * 警告：生产环境中执行Shell命令存在极大安全风险，需严格校验权限防范命令注入！
     */
    @PostMapping("/linux-tune")
    public Result linuxTune(@RequestBody Map<String, String> params) {
        Map<String, Object> response = new HashMap<>();
        String action = params.get("action");
        String pid = getPid();

        try {
            String command = "";
            if ("drop_caches".equals(action)) {
                // 清理Linux系统OS缓存 (需root权限或sudo)
                command = "sync; echo 3 > /proc/sys/vm/drop_caches";
            } else if ("jinfo_flag".equals(action)) {
                // 动态修改JVM manageable参数 (如: +PrintGCDetails)
                String flag = params.get("flag");
                command = "jinfo -flag " + flag + " " + pid;
            } else if ("restart_with_new_args".equals(action)) {
                // 模拟修改启动脚本并重启服务
                String newXmx = params.get("xmx");
                String newNewRatio = params.get("newRatio");
                // 示例：使用sed替换启动脚本中的参数，然后重启systemd服务
                command = String.format("sed -i 's/-Xmx[0-9]*[a-zA-Z]*/-Xmx%s/g' /opt/myapp/start.sh && " +
                        "sed -i 's/-XX:NewRatio=[0-9]*/-XX:NewRatio=%s/g' /opt/myapp/start.sh && " +
                        "systemctl restart myapp", newXmx, newNewRatio);
                response.put("message", "重启命令已下发，服务即将重启");
                response.put("command", command);
                return Result.success(response);
            } else {
                response.put("error", "未知的操作指令");
                return Result.success(response);
            }

            // 执行Linux命令
            String[] cmd = { "/bin/sh", "-c", command };
            Process process = Runtime.getRuntime().exec(cmd);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();

            response.put("status", "success");
            response.put("output", output.toString());
            response.put("command", command);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return Result.success(response);
    }

    private String getPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return name.split("@")[0];
    }

    /**
     * 保存指标到MySQL (MyBatis)
     */
//    @PostMapping("/save-metric")
//    public Result saveMetric(@RequestBody JvmMetric metric) {
//        jvmMetricMapper.insert(metric);
//        return Result.success("指标保存成功");
//    }

    @GetMapping("/gc-by-pid")
    public Result jvmGCPid(@RequestParam Integer pid) throws Exception {
        triggerJVMGC(pid);
        return Result.success();
    }

    // 触发 JVM GC（需知道 Java 进程 PID）
    public static void triggerJVMGC(int pid) throws Exception {
        Process process = new ProcessBuilder("jcmd", String.valueOf(pid), "GC.run").start();
        process.waitFor();
    }

    // 释放 Linux 系统缓存（需 root 权限）
    public static void dropSystemCaches() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "sync && echo 3 > /proc/sys/vm/drop_caches").start();
        process.waitFor();
    }

    // ==================== GC 日志管理 ====================

    /**
     * 查看 GC 日志 (读取最后 N 行)
     */
    @GetMapping("/gc-log/view")
    public Result viewGcLog(@RequestParam(defaultValue = "100") int lines) {
        Map<String, Object> response = new HashMap<>();
        File file = new File(GC_LOG_FILE);
        if (!file.exists()) {
            response.put("error", "GC日志文件不存在: " + GC_LOG_FILE);
            return Result.success(response);
        }
        try {
            // 简单实现：读取所有行并取最后N行。生产环境建议使用 RandomAccessFile 逆向读取以提高大文件性能
            List<String> allLines = Files.readAllLines(file.toPath());
            int start = Math.max(0, allLines.size() - lines);
            // 自动适配系统的换行符
            // jdk7前的换行：System.getProperty("line.separator");
            // jdk8+：System.lineSeparator()
            String content = String.join(System.lineSeparator(), allLines.subList(start, allLines.size()));
            response.put("content", content);
            response.put("totalLines", allLines.size());
        } catch (IOException e) {
            response.put("error", "读取GC日志失败: " + e.getMessage());
        }
        return Result.success(response);
    }

    /**
     * 下载 GC 日志
     */
    @GetMapping("/gc-log/download")
    public ResponseEntity<Resource> downloadGcLog() {
        File file = new File(GC_LOG_FILE);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gc.log\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    // ==================== 堆转储 (Heap Dump) 管理 ====================

    /**
     * 生成 Heap Dump
     */
    @PostMapping("/heap-dump/generate")
    public Result generateHeapDump() {
        Map<String, Object> response = new HashMap<>();
        try {
            File dir = new File(DUMP_DIR);
            if (!dir.exists()) dir.mkdirs();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "heap_" + timestamp + ".hprof";
            String filePath = DUMP_DIR + File.separator + fileName;

            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            com.sun.management.HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                    server, "com.sun.management:type=HotSpotDiagnostic", com.sun.management.HotSpotDiagnosticMXBean.class);

            // true 表示只 dump 存活的对象 (live objects)
            mxBean.dumpHeap(filePath, true);

            response.put("status", "success");
            response.put("message", "Heap Dump 生成成功: " + fileName);
            response.put("fileName", fileName);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "生成 Heap Dump 失败: " + e.getMessage());
        }
        return Result.success(response);
    }

    /**
     * 获取 Heap Dump 文件列表
     */
    @GetMapping("/heap-dump/list")
    public Result listHeapDumps() {
        File dir = new File(DUMP_DIR);
        if (!dir.exists()) return Result.error("文件不存在！");

        File[] files = dir.listFiles((d, name) -> name.endsWith(".hprof"));
        if (files == null) return Result.error("内容为空！");

        return Result.success(Arrays.stream(files).map(file -> {
            Map<String, Object> map = new HashMap<>();
            map.put("fileName", file.getName());
            map.put("size", file.length());
            map.put("lastModified", file.lastModified());
            return map;
        }).sorted((a, b) -> Long.compare((Long) b.get("lastModified"), (Long) a.get("lastModified")))
                .collect(Collectors.toList()));
    }

    /**
     * 下载 Heap Dump 文件
     */
    @GetMapping("/heap-dump/download/{fileName}")
    public ResponseEntity<Resource> downloadHeapDump(@PathVariable String fileName) {
        // 安全校验：防止目录遍历攻击
        if (fileName.contains("..") || fileName.contains("/")) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(DUMP_DIR + File.separator + fileName);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
