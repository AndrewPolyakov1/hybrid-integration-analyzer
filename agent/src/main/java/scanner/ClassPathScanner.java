package scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassPathScanner {
    private static final String[] excludes = new String[]
            {
                    "java.*",
                    "javax.*",
                    "sun.*",
                    "net.bytebuddy.*",
                    "sunw.*",
                    "agent.*",
                    "META-INF.*"
            };

    public Set<String> scan() {
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(File.pathSeparator);
        Set<String> classes = new HashSet<>();
        for (String path : paths) {
            File file = new File(path);
            if (file.exists()) {
                if (file.isDirectory()) {
                    classes.addAll(scanDirectory(file));
                } else if (file.getName().endsWith(".jar")) {
                    classes.addAll(scanJar(file));
                }
            }
        }
        return classes;
    }

    private List<String> scanDirectory(File directory) {
        List<String> values = new ArrayList<>();
        try {
            Path rootPath = directory.toPath();
            // Walk the file tree starting from the classpath directory
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".class")) {
                        // Convert file path to fully qualified class name
                        Optional<String> className = convertToClassName(rootPath, file);
                        if (className.isPresent() && !isExcluded(className.get())) {
                            values.add(className.get());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + directory);
        }
        return values;
    }

    private List<String> scanJar(File jarFile) {
        List<String> values = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    // Convert "com/example/MyClass.class" to "com.example.MyClass"
                    String className = entry.getName()
                            .replace("/", ".")
                            .replace(".class", "");
                    if (!isExcluded(className)) {
                        values.add(className);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error scanning JAR file: " + jarFile);
        }
        return values;
    }

    private Optional<String> convertToClassName(Path rootPath, Path file) {
        Path relativePath = rootPath.relativize(file);
        // Remove extension and replace path separators with dots
        String className = relativePath.toString()
                .replace(File.separatorChar, '.')
                .replace(".class", "");

        // Filter out module-info (which isn't a real class in this context)
        if (className.equals("module-info")) {
            return Optional.empty();
        }
        return Optional.of(className);
    }

    private boolean isExcluded(String className) {
        for (String exclude : excludes) {
            Pattern pattern = Pattern.compile(exclude);
            Matcher matcher = pattern.matcher(className);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }
}
