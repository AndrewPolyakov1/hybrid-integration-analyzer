package agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Java agent entry point.
 *
 * <p>Installs a {@link AgentBuilder} that instruments
 * all loaded classes (except ignored ones) by applying {@link MethodInterceptor}
 * advice to their methods.
 *
 * <p>The agent also ensures required helper classes are injected into each
 * target {@link ClassLoader} before transformation.
 */
public final class Agent {

    /**
     * Called by the JVM before the application's {@code main} method.
     *
     * @param agentArgs agent arguments
     * @param inst      instrumentation instance provided by the JVM
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Agent] Started");

        List<String> filterData = agentArgs != null ? Arrays.asList(agentArgs.split("\\s*,\\s*")) : new ArrayList<>();
        System.out.println("[Agent] Filter: " + filterData);

        ElementMatcher.Junction<NamedElement> filter = null;
        for (String element : filterData) {
            if (filter == null) {
                filter = nameStartsWith(element);
                System.out.println("[Agent] start filter: " + element);
            } else {
                filter = filter.or(nameStartsWith(element));
                System.out.println("[Agent] extend filter: " + element);
            }
        }
        if (filter == null) {
            filter = any();
        }

        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .ignore(
                        nameStartsWith("net.bytebuddy.")
                                .or(nameStartsWith("java."))
                                .or(nameStartsWith("sun."))
                                .or(nameStartsWith("jdk."))
                                .or(nameStartsWith("javax."))
                                .or(nameStartsWith("ru.itmo.interceptor.impl."))
                                .or(nameStartsWith("org.xml."))
                                .or(nameStartsWith("com.sun.xml."))
                                .or(nameStartsWith("com.sun.org."))
                                .or(nameStartsWith("org.slf4j."))
                                .or(nameStartsWith("ch.qos.logback."))
                                .or(nameStartsWith("agent."))
                )
                .type(filter)
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> {
                    try {
                        InjectorHelper.ensureInjected(classLoader);
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                    }

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