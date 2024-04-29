package com.example.online_java_ide.utils;

import javax.crypto.spec.PSource;
import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringCompiler {
    private static Map<String, JavaFileObject> fileObjectMap = new ConcurrentHashMap<String, JavaFileObject>();

    private static Pattern CLASS_PATTERN = Pattern.compile("class\\s+([$_a-zA-Z][$_a-zA-Z0-9]*)\\s*");

    public static byte[] compile(String code, DiagnosticCollector<JavaFileObject> diagnostics) {
        // 获取系统编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        JavaFileManager javaFileManager = new TmpJavaFileManager(compiler.getStandardFileManager(diagnostics, null, null));

        Matcher matcher = CLASS_PATTERN.matcher(code);
        String className;
        if (matcher.find()) {
            className = matcher.group(1);
        } else {
            throw new IllegalArgumentException("No valid class");
        }

        TmpJavaFileObject tmpJavaFileObject = new TmpJavaFileObject(className, code);

        Boolean result = compiler.getTask(null, javaFileManager, diagnostics,
                null, null, Arrays.asList(tmpJavaFileObject)).call();

        JavaFileObject javaFileObject = fileObjectMap.get(className);
        if (result && javaFileObject != null) {
            return ((TmpJavaFileObject)javaFileObject).getCompiledBytes();
        }
        return null;

    }

    public static class TmpJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        protected TmpJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForInput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind) throws IOException {
            JavaFileObject javaFileObject = fileObjectMap.get(className);
            if (javaFileObject == null) {
                return super.getJavaFileForInput(location, className, kind);
            }
            return javaFileObject;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            JavaFileObject javaFileObject = new TmpJavaFileObject(className, kind);
            fileObjectMap.put(className, javaFileObject);
            return javaFileObject;
        }
    }

    private static class TmpJavaFileObject extends SimpleJavaFileObject {
        private String code;
        private ByteArrayOutputStream outputStream;

        public TmpJavaFileObject(String name, String code) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        public TmpJavaFileObject(String name, Kind kind) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), kind);
            this.code = null;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            if (code == null) {
                throw new IllegalArgumentException("code == null");
            }
            return code;
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            outputStream = new ByteArrayOutputStream();
            return outputStream;
        }

        public byte[] getCompiledBytes() {
            return outputStream.toByteArray();
        }
    }
}
