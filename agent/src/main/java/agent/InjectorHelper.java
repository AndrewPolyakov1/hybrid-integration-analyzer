package agent;

import net.bytebuddy.dynamic.loading.ClassInjector;
import scanner.ClassPathScanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InjectorHelper {

    private static final Map<ClassLoader, Boolean> injectedLoaders = new ConcurrentHashMap<>();

    // List ALL classes that MethodInterceptor needs to run.
    // Order doesn't strictly matter for the Map, but ensuring they are all there is critical.
    private static final String[] CLASSES_TO_INJECT = {
            "ru.itmo.interceptor.InterceptorAction",        // The Interface
            "ru.itmo.interceptor.impl.LoggingInterceptorAction", // The Implementation
            "ru.itmo.interceptor.impl.AsyncJsonFileLoggingInterceptorAction", // The Implementation
            "agent.MethodInterceptor"                      // The Advice Class
    };

    public static void ensureInjected(ClassLoader targetLoader) {
        if (targetLoader == null) {
            return; // Bootstrap loader
        }

        if (injectedLoaders.containsKey(targetLoader)) {
            return;
        }

        synchronized (InjectorHelper.class) {
            if (injectedLoaders.containsKey(targetLoader)) {
                return;
            }

            try {
                Map<String, byte[]> injectionMap = new HashMap<>();
                Set<String> classNames = new ClassPathScanner().scan();
                // 1. Load bytes for ALL required classes
                for (String className : CLASSES_TO_INJECT) {
                    byte[] bytes = getClassBytes(className);
                    injectionMap.put(className, bytes);
                }

                // 2. Inject them all at once
                ClassInjector injector = new ClassInjector.UsingUnsafe(targetLoader);
                injector.injectRaw(injectionMap);

                injectedLoaders.put(targetLoader, true);
                System.out.println("[Agent] Successfully injected dependencies into: " + targetLoader);

            } catch (Exception e) {
                System.err.println("[Agent] Failed to inject classes into " + targetLoader);
                e.printStackTrace();
                // Rethrow so the user knows it failed
                throw new RuntimeException("Injection failed", e);
            }
        }
    }

    public static byte[] getClassBytes(String className) throws IOException {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream is = InjectorHelper.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("Could not find class resource: " + resource +
                        ". Check if it's in the JAR.");
            }
            byte[] bytes = new byte[]{};
            is.read(bytes);
            return bytes;
        }
    }
}