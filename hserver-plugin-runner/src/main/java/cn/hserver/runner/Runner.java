package cn.hserver.runner;

import java.io.Console;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class Runner {

    public static String password;


    private static void checkPassword(JarInfo manifestInfo) {
        if (manifestInfo.isEncrypt()) {
            //jar 参数读取
            password = System.getProperty("password");
            if (password == null || password.trim().length() == 0) {
                Console console = System.console();
                // 读取密码
                char[] passwordArray = console.readPassword("Please enter your password: ");
                password = new String(passwordArray);
                if (password.trim().length() == 0) {
                    System.exit(-1);
                }
            }
            System.out.println("runer password: "+ password);
        }
    }


    public static void main(String[] args) throws Exception {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        JarURLStreamHandlerFactory jarURLStreamHandlerFactory = new JarURLStreamHandlerFactory(contextClassLoader);
        URL.setURLStreamHandlerFactory(jarURLStreamHandlerFactory);
        JarInfo manifestInfo = JarInfo.getManifestInfo();
        checkPassword(manifestInfo);
        ClassLoader jceClassLoader = new URLClassLoader(manifestInfo.getLibs(), null);
        Thread.currentThread().setContextClassLoader(jceClassLoader);
        Class<?> c = Class.forName(manifestInfo.getMainClass(), true, jceClassLoader);
        Method main = c.getMethod("main", args.getClass());
        main.invoke(null, new Object[]{args});
    }

}
