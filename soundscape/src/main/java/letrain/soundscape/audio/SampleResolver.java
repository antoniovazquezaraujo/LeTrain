package letrain.soundscape.audio;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves material patterns of a style (e.g. {@code sea/waves-*.wav}) to classpath resources.
 * Paths are relative to the {@code sounds/} resource root unless they already include it.
 */
public class SampleResolver {

    private static final Logger log = LoggerFactory.getLogger(SampleResolver.class);
    private static final String DEFAULT_ROOT = "sounds/";

    private final ClassLoader classLoader;
    private final String root;

    public SampleResolver() {
        this(SampleResolver.class.getClassLoader(), DEFAULT_ROOT);
    }

    public SampleResolver(ClassLoader classLoader, String root) {
        this.classLoader = classLoader;
        this.root = root.endsWith("/") ? root : root + "/";
    }

    /** All resources matching a material pattern, sorted for determinism; empty when none match. */
    public List<URL> resolve(String material) {
        if (material == null || material.isBlank()) {
            return List.of();
        }
        String path = material.startsWith("/") ? material.substring(1) : material;
        if (!path.startsWith(root)) {
            path = root + path;
        }
        if (!path.contains("*")) {
            URL url = classLoader.getResource(path);
            return url == null ? List.of() : List.of(url);
        }
        int slash = path.lastIndexOf('/');
        String directory = path.substring(0, slash + 1);
        Pattern pattern = wildcardPattern(path.substring(slash + 1));
        return listDirectory(directory).stream().filter(name -> pattern.matcher(name).matches())
                .sorted().map(name -> classLoader.getResource(directory + name))
                .filter(Objects::nonNull).toList();
    }

    private List<String> listDirectory(String directory) {
        URL url = classLoader.getResource(directory);
        if (url == null) {
            return List.of();
        }
        try {
            if ("file".equals(url.getProtocol())) {
                java.nio.file.Path path = java.nio.file.Path.of(url.toURI());
                try (java.util.stream.Stream<java.nio.file.Path> files =
                        java.nio.file.Files.list(path)) {
                    return files.filter(java.nio.file.Files::isRegularFile)
                            .map(p -> p.getFileName().toString()).toList();
                }
            }
            if ("jar".equals(url.getProtocol())) {
                java.net.JarURLConnection connection =
                        (java.net.JarURLConnection) url.openConnection();
                String prefix = connection.getEntryName();
                try (java.util.jar.JarFile jar = connection.getJarFile()) {
                    return jar.stream().map(java.util.jar.JarEntry::getName)
                            .filter(name -> name.startsWith(prefix)
                                    && !name.substring(prefix.length()).contains("/"))
                            .map(name -> name.substring(prefix.length())).toList();
                }
            }
        } catch (Exception e) {
            log.warn("Could not list resource directory {}: {}", directory, e.getMessage());
        }
        return List.of();
    }

    private Pattern wildcardPattern(String wildcard) {
        StringBuilder regex = new StringBuilder();
        for (String part : wildcard.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }
}
