package edu.cmu.tetrad.util;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

public final class PythonResource {

    private PythonResource() {}

    /**
     * Extracts a classpath resource to a stable cache directory (not a temp file),
     * so a long-lived Python process can safely run it.
     *
     * @param resourcePath classpath resource path, e.g. "python/kci_server.py"
     * @param cacheFileName file name to use in the cache dir, e.g. "kci_server.py"
     * @return the extracted file path
     */
    public static Path extractToUserCache(String resourcePath, String cacheFileName) throws IOException {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(cacheFileName, "cacheFileName");

        // Example cache dir: ~/.tetrad/python/
        Path cacheDir = Paths.get(System.getProperty("user.home"), ".tetrad", "python");
        Files.createDirectories(cacheDir);

        Path target = cacheDir.resolve(cacheFileName);

        try (InputStream in = PythonResource.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new FileNotFoundException("Resource not found on classpath: " + resourcePath);
            }

            // Read resource bytes
            byte[] resourceBytes = in.readAllBytes();

            // If file exists and is identical, keep it.
            if (Files.exists(target)) {
                byte[] existingBytes = Files.readAllBytes(target);
                if (MessageDigest.isEqual(sha256(existingBytes), sha256(resourceBytes))) {
                    return target;
                }
            }

            // Write atomically: write to temp in same dir, then move.
            Path tmp = Files.createTempFile(cacheDir, cacheFileName, ".tmp");
            Files.write(tmp, resourceBytes, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }

            // Best-effort POSIX executable bit isn’t needed since we call python <script>.
            return target;
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Optional debugging helper:
    public static String sha256Hex(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        return HexFormat.of().formatHex(sha256(b));
    }
}