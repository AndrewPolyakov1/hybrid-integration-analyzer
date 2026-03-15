import scanner.ClassPathScanner;

public class LocalMain {
    public static void main(String[] args) {
        ClassPathScanner scanner = new ClassPathScanner();
        var values = scanner.scan();

        for (String className : values) {
            System.out.println(className);
        }
    }
}
