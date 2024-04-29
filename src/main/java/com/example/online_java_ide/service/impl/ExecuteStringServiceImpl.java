package com.example.online_java_ide.service.impl;

import com.example.online_java_ide.service.ExecuteStringService;
import com.example.online_java_ide.utils.StringCompiler;
import com.example.online_java_ide.utils.mainExecutor;
import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.List;
import java.util.concurrent.*;

@Service
public class ExecuteStringServiceImpl implements ExecuteStringService {
    // 限制程序运行时间，单位为秒
    private static final int RUN_TIME_LIMIT = 5;

    // 限制最大并发线程数
    private static final int MAX_THREAD_NUM = 4 + 1;

    // 运行代码的线程池
    private static final ExecutorService executorService = new ThreadPoolExecutor(MAX_THREAD_NUM, MAX_THREAD_NUM, 0l,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_THREAD_NUM));

    private static final String WAIT_WARNING = "服务器忙，请稍后提交";
    private static final String NO_OUTPUT = "Nothing.";

    @Override
    public String executeString(String code, String systemIn) {
        // 诊断信息收集器
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // 代码执行器
        byte[] classBytes = StringCompiler.compile(code, diagnostics);

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

        Callable<String> runTask = new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mainExecutor.execute(classBytes, systemIn);
            }
        };

        Future<String> res = null;
        try {
            res = executorService.submit(runTask);
        } catch (RejectedExecutionException e) {
            return WAIT_WARNING;
        }

        String runResult;
        try {
            runResult = res.get(RUN_TIME_LIMIT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            runResult = "Program interrupted.";
        } catch (ExecutionException e) {
            runResult = e.getCause().getMessage();
        } catch (TimeoutException e) {
            runResult = "Time Limit Exceeded.";
        } finally {
            res.cancel(true);
        }
        return runResult != null ? runResult : NO_OUTPUT;
    }
}
