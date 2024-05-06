package com.example.online_java_ide.utils;

import com.example.online_java_ide.config.CustomClassLoader;
import com.example.online_java_ide.utils.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class mainExecutor {
    public static String execute(byte[] classByte, String systemIn) {
        ClassModifier classModifier = new ClassModifier(classByte);

        byte[] modifyBytes = classModifier.modifyConstantUTF8Value("java/lang/System", "/com/example/online_java_ide/utils/CustomSystem");
//        System.out.println(System.getProperty("java.lang.System"));
        modifyBytes = classModifier.modifyConstantUTF8Value("java/util/Scanner", "/com/example/online_java_ide/utils/CustomScanner");

        ((CustomInputStream) CustomSystem.in).set(systemIn);

        CustomClassLoader classLoader = new CustomClassLoader();
        Class aClass = classLoader.loadByte(modifyBytes);

        try {
            Method method = aClass.getMethod("main", new Class[]{String[].class});
            method.invoke(null, new String[]{null});
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace(CustomSystem.err);
        }

        String res = CustomSystem.getBufferString();
        CustomSystem.closeBuffer();
        return res;
    }
}
