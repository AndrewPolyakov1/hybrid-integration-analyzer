import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Main {
    public static void main(String[] args) {
        // Get the classpath defined by java.class.path
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(File.pathSeparator);
        List<String> classes = new ArrayList<>();
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
        System.out.println("Classes: " + classes);
    }

    private static List<String> scanDirectory(File directory) {
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
                        className.ifPresent(values::add);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + directory);
        }
        return values;
    }

    private static List<String> scanJar(File jarFile) {
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
                    values.add(className);
                }
            }
        } catch (IOException e) {
            System.err.println("Error scanning JAR file: " + jarFile);
        }
        return values;
    }

    private static Optional<String> convertToClassName(Path rootPath, Path file) {
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
}
