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

    public static boolean isLegalName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static String getProtocolDescription() {
        return """
Names must begin with a non-numeric character and may not contain
spaces or tabs. Quotes are allowed.
""";
    }
}