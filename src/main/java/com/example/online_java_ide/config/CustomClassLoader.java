package com.example.online_java_ide.config;

public class CustomClassLoader extends ClassLoader{
    public Class loadByte(byte[] classBytes) {
        return defineClass(null, classBytes, 0, classBytes.length);
    }
}
