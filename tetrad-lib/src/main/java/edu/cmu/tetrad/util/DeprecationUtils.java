package edu.cmu.tetrad.util;

/**
 * Utility for checking whether classes are marked {@code @Deprecated}.
 */
public final class DeprecationUtils {

    private DeprecationUtils() {
        // prevent instantiation
    }

    /**
     * @param clazz The class to check (must not be null).
     * @return true iff the class is annotated with {@link Deprecated}.
     */
    public static boolean isClassDeprecated(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class must not be null");
        }
        return clazz.isAnnotationPresent(Deprecated.class);
    }
}