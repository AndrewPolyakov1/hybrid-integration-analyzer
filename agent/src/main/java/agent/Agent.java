package agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import static net.bytebuddy.matcher.ElementMatchers.*;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class Agent {

    static {
        // Keep your SLF4J config for the Agent itself,
        // but be aware: LoggingInterceptorAction runs in the target classloader!
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
    }

    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        System.out.println("[Agent] Started");

        new AgentBuilder.Default()
                .ignore(
                        nameStartsWith("net.bytebuddy.")
                                .or(nameStartsWith("java."))
                                .or(nameStartsWith("sun."))
                                .or(nameStartsWith("jdk."))
                                .or(nameStartsWith("javax."))
                                .or(nameStartsWith("org.xml."))
                                .or(nameStartsWith("com.sun.xml."))
                                .or(nameStartsWith("agent.")) // Ignore our own package to avoid loops
                                .or(nameStartsWith("com.sun.org."))
                                .or(nameStartsWith("org.slf4j."))
                                .or(nameStartsWith("ch.qos.logback."))
                )
                .type(any())
                // We use a custom Transformer to inject classes BEFORE building the new type
                .transform((builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
                    // 1. Inject dependencies into this specific ClassLoader
                    InjectorHelper.ensureInjected(classLoader);
                    System.out.println("[Agent] Injected inject into agent");
                    // 2. Apply the advice
                    // Note: Since inline=false, the target class will call MethodInterceptor by name.
                    // Because we injected MethodInterceptor into 'classLoader', this will work.
                    return builder.visit(
                            Advice.to(MethodInterceptor.class)
                                    .on(isMethod()
                                            .and(not(isAbstract()))
                                            .and(not(isNative())))
                    );
                })
                .installOn(inst);
    }
}
