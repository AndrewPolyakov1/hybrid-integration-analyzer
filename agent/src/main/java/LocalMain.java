import scanner.ClassPathScanner;

import java.util.Set;

public class LocalMain {
    public static void main(String[] args) {
        ClassPathScanner scanner = new ClassPathScanner();
        Set<String> values = scanner.scan();

        for (String className : values) {
            System.out.println(className);
        }
    }
}
