package edu.cmu.tetrad.util;

import java.util.regex.Pattern;

/**
 * Specifies the protocol used in Tetrad for variable names.
 */
public final class NamingProtocol {

    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[^0-9][^ \\t]*$");

    private NamingProtocol() {
    }

    /**
     * Evaluates whether the given name conforms to the naming protocol.
     *
     * @param name the name to be checked
     * @return {@code true} if the name is non-null and matches the pattern
     *         for legal names, {@code false} otherwise
     */
    public static boolean isLegalName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Provides a description of the naming protocol used for variable names.
     * The protocol specifies that names must begin with a non-numeric character,
     * and may not contain spaces or tabs. Quotes are allowed within names.
     *
     * @return a string describing the rules for variable names in the naming protocol
     */
    public static String getProtocolDescription() {
        return """
Names must begin with a non-numeric character and may not contain
spaces or tabs. Quotes are allowed.
""";
    }
}