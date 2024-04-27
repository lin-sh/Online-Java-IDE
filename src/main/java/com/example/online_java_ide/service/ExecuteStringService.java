package com.example.online_java_ide.service;

import org.olexec.compile.StringSourceCompiler;
import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class ExecuteStringService {
    // 限制程序运行时间，单位为秒
    private static final int RUN_TIME_LIMIT = 5;

    // 限制最大并发线程数
    private static final int MAX_THREAD_NUM = 4 + 1;

    // 运行代码的线程池
    private static final ExecutorService executorService = new ThreadPoolExecutor(MAX_THREAD_NUM, MAX_THREAD_NUM, 0l,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_THREAD_NUM));

    public String executeString(String code, String systemIn) {
        // 诊断信息收集器
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // 代码执行器
        byte[] classBytes = StringSourceCompiler.compile(code, diagnostics);

        // 如果编译出错
        if (classBytes == null) {
            // 获取错误信息
            List<Diagnostic<? extends JavaFileObject>> compileError = diagnostics.getDiagnostics();
            StringBuilder errorInfo = new StringBuilder();
            for (Diagnostic diagnostic : compileError) {
                errorInfo.append("Compilation error on line " + diagnostic.getLineNumber());
                errorInfo.append(".");
                errorInfo.append(System.lineSeparator());
            }
            return errorInfo.toString();
        }
    }
}
