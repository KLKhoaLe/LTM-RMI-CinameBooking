import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

import javax.tools.ToolProvider;

/** Build ngoại tuyến bằng JDK 21+, không cần cài Maven hoặc tải thư viện. */
public class Build {
    static final Path ROOT = Path.of("").toAbsolutePath();
    static final String COMMON = "CinemaCommon", SERVER = "CinemaServer", CLIENT = "CinemaClient";

    public static void main(String[] args) throws Exception {
        if (ToolProvider.getSystemJavaCompiler() == null)
            throw new IllegalStateException("Cần JDK 21+, không chỉ JRE.");
        compile(COMMON, "");
        String commonClasses = classes(COMMON).toString();
        compile(SERVER, commonClasses);
        compile(CLIENT, commonClasses);
        Path commonJar = ROOT.resolve("build/cinema-common-1.0.0.jar");
        jar(commonJar, null, classes(COMMON));
        jar(
                ROOT.resolve("build/cinema-common-1.0.0-sources.jar"),
                null,
                ROOT.resolve(COMMON + "/src/main/java"));
        for (String module : List.of(SERVER, CLIENT)) {
            Path repo = ROOT.resolve(module + "/repository/vn/khoa/cinema-common/1.0.0");
            Files.createDirectories(repo);
            Files.copy(
                    commonJar,
                    repo.resolve(commonJar.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(
                    ROOT.resolve("build/cinema-common-1.0.0-sources.jar"),
                    repo.resolve("cinema-common-1.0.0-sources.jar"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(
                    repo.resolve("cinema-common-1.0.0.pom"),
                    "<project><modelVersion>4.0.0</modelVersion><groupId>vn.khoa</groupId><artifactId>cinema-common</artifactId><version>1.0.0</version></project>");
            String main =
                    module.equals(SERVER)
                            ? "vn.khoa.cinema.server.ServerMain"
                            : "vn.khoa.cinema.client.ClientMain";
            jar(
                    ROOT.resolve(module + "/app/" + module + ".jar"),
                    main,
                    classes(COMMON),
                    classes(module));
        }
        System.out.println(
                "BUILD OK: CinemaServer/app/CinemaServer.jar + CinemaClient/app/CinemaClient.jar");
        if (args.length > 0 && args[0].equals("test")) {
            String cp =
                    String.join(
                            File.pathSeparator,
                            classes(COMMON).toString(),
                            classes(SERVER).toString(),
                            classes(CLIENT).toString());
            Path testOutput = ROOT.resolve("build/tests");
            compileFiles(ROOT.resolve("tests"), testOutput, cp);
            Process process =
                    new ProcessBuilder(
                                    Path.of(System.getProperty("java.home"), "bin", "java")
                                            .toString(),
                                    "-cp",
                                    cp + File.pathSeparator + testOutput,
                                    "vn.khoa.cinema.server.IntegrationTest")
                            .inheritIO()
                            .start();
            if (process.waitFor() != 0) throw new IllegalStateException("TEST FAILED");
        }
    }

    static Path classes(String module) {
        return ROOT.resolve("build/" + module);
    }

    static void compile(String module, String cp) throws Exception {
        compileFiles(ROOT.resolve(module + "/src/main/java"), classes(module), cp);
    }

    static void compileFiles(Path source, Path output, String cp) throws Exception {
        if (Files.exists(output))
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        Files.createDirectories(output);
        List<String> options =
                new ArrayList<>(
                        List.of(
                                "-encoding",
                                "UTF-8",
                                "-source",
                                "21",
                                "-target",
                                "21",
                                "-d",
                                output.toString()));
        if (!cp.isEmpty()) options.addAll(List.of("-classpath", cp));
        try (var paths = Files.walk(source)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .forEach(p -> options.add(p.toString()));
        }
        int result =
                ToolProvider.getSystemJavaCompiler()
                        .run(null, System.out, System.err, options.toArray(String[]::new));
        if (result != 0) throw new IllegalStateException("Compile failed: " + source);
    }

    static void jar(Path file, String main, Path... folders) throws Exception {
        Files.createDirectories(file.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        if (main != null) manifest.getMainAttributes().putValue("Main-Class", main);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            for (Path folder : folders)
                try (var paths = Files.walk(folder)) {
                    for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                        String name =
                                folder.relativize(path).toString().replace(File.separatorChar, '/');
                        out.putNextEntry(new JarEntry(name));
                        Files.copy(path, out);
                        out.closeEntry();
                    }
                }
        }
    }
}
