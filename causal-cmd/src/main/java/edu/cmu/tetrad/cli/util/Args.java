/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.cli.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 *
 * Nov 30, 2015 10:46:42 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class Args {

    private Args() {
    }

    public static double getDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(String.format("'%s' is not a double.", value));
        }
    }

    public static double getDoubleMin(String value, double minValue) {
        double doubleValue = getDouble(value);
        if (minValue > doubleValue) {
            throw new IllegalArgumentException(
                    String.format("Value (%f) must be greater than or equal to %f.", doubleValue, minValue));
        }

        return doubleValue;
    }

    public static int getInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(String.format("'%s' is not an integer.", value));
        }
    }

    public static int getIntegerMin(String value, int minValue) {
        int intValue = getInteger(value);
        if (minValue > intValue) {
            throw new IllegalArgumentException(
                    String.format("Value (%d) must be greater than or equal to %d.", intValue, minValue));
        }

        return intValue;
    }

    public static int getIntegerMinMax(String value, int minValue, int maxValue) {
        int intValue = getInteger(value);
        if (minValue <= intValue && intValue <= maxValue) {
            return intValue;
        } else {
            throw new IllegalArgumentException(
                    String.format("Value (%d) must be between %d and %d.", intValue, minValue, maxValue));
        }
    }

    public static String getDelimiterName(char delimiter) {
        switch (delimiter) {
            case ',':
                return "comma";
            case ';':
                return "semicolon";
            case ' ':
                return "space";
            case ':':
                return "colon";
            case '\t':
                return "tab";
            default:
                return "unknown";
        }
    }

    public static char getDelimiterForName(String delimiter) {
        delimiter = (delimiter == null) ? "" : delimiter.toLowerCase();
        switch (delimiter) {
            case "comma":
            case ",":
                return ',';
            case "semicolon":
            case ";":
                return ';';
            case "space":
            case " ":
                return ' ';
            case "colon":
            case ":":
                return ':';
            case "tab":
            case "\t":
            default:
                return '\t';
        }
    }

    public static List<Path> getFiles(String... files) throws FileNotFoundException {
        List<Path> fileList = new LinkedList<>();

        for (String file : files) {
            fileList.add(getPathFile(file, true));
        }

        return fileList;
    }

    public static char getCharacter(String character) {
        if (character.length() == 1) {
            return character.charAt(0);
        } else {
            throw new IllegalArgumentException(String.format("'%s' must be a single character.", character));
        }
    }

    public static Path getPathDir(String dir, boolean required) throws FileNotFoundException {
        Path path = Paths.get(dir);

        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                throw new FileNotFoundException(String.format("'%s' is not a directory.\n", dir));
            }
        } else {
            if (required) {
                throw new FileNotFoundException(String.format("Directory '%s' does not exist.\n", dir));
            }
        }

        return path;
    }

    public static Path getPathFile(String file, boolean requireNotNull) throws FileNotFoundException {
        if (file == null) {
            if (requireNotNull) {
                throw new IllegalArgumentException("File argument is null.");
            } else {
                return null;
            }
        }

        Path path = Paths.get(file);

        if (Files.exists(path)) {
            if (!Files.isRegularFile(path)) {
                throw new FileNotFoundException(String.format("'%s' is not a file.\n", file));
            }
        } else {
            throw new FileNotFoundException(String.format("File '%s' does not exist.\n", file));
        }

        return path;
    }

    public static String[] removeOption(String[] args, String option) {
        String[] arguments = new String[args.length - 2];

        int index = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                arg = arg.substring(2, arg.length());
                if (arg.equals(option)) {
                    i++;
                    continue;
                }
            } else if (arg.startsWith("-")) {
                arg = arg.substring(1, arg.length());
                if (arg.equals(option)) {
                    i++;
                    continue;
                }
            }
            arguments[index++] = args[i];
        }

        return arguments;
    }

    public static String[] removeFlags(String[] args, String... flags) {
        String[] arguments = new String[args.length - 2];

        Set<String> options = new HashSet<>();
        Collections.addAll(options, flags);

        int index = 0;
        for (String arg : args) {
            if (arg.startsWith("--")) {
                arg = arg.substring(2, arg.length());
                if (options.contains(arg)) {
                    continue;
                }
            } else if (arg.startsWith("-")) {
                arg = arg.substring(1, arg.length());
                if (options.contains(arg)) {
                    continue;
                }
            }

            arguments[index++] = arg;
        }

        return arguments;
    }

    public static String getOptionValue(String[] args, String option) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            boolean isOption = arg.startsWith("--") || arg.startsWith("-");
            if (isOption) {
                if (arg.startsWith("--")) {
                    arg = arg.substring(2, arg.length());
                } else if (arg.startsWith("-")) {
                    arg = arg.substring(1, arg.length());
                }

                if (arg.equals(option)) {
                    i++;
                    if (i < args.length) {
                        return args[i];
                    }
                }
            }
        }

        return null;
    }

    public static boolean hasLongOption(String[] args, String option) {
        if (args == null || args.length == 0 || option == null) {
            return false;
        }

        for (String arg : args) {
            if (arg.startsWith("--")) {
                arg = arg.substring(2, arg.length());
                if (arg.equals(option)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasOption(String[] args, String option) {
        if (args == null || args.length == 0 || option == null) {
            return false;
        }

        for (String arg : args) {
            if (arg.startsWith("--")) {
                arg = arg.substring(2, arg.length());
                if (arg.equals(option)) {
                    return true;
                }
            } else if (arg.startsWith("-")) {
                arg = arg.substring(1, arg.length());
                if (arg.equals(option)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void showHelp(String algorithmName, Options options) {
        StringBuilder sb = new StringBuilder("java -jar");
        try {
            JarFile jarFile = new JarFile(Args.class.getProtectionDomain().getCodeSource().getLocation().getPath(), true);
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            String artifactId = attributes.getValue("Implementation-Title");
            String version = attributes.getValue("Implementation-Version");
            sb.append(String.format(" %s-%s.jar", artifactId, version));
        } catch (IOException exception) {
            sb.append(" causal-cmd.jar");
        }
        sb.append(" --algorithm ");
        sb.append(algorithmName);

        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(-1);
        formatter.printHelp(sb.toString(), options, true);
    }

    public static String toString(String[] args, char delimiter) {
        StringBuilder sb = new StringBuilder();

        int lastIndex = args.length - 1;
        for (int i = 0; i < args.length; i++) {
            sb.append(args[i]);
            if (i < lastIndex) {
                sb.append(delimiter);
            }
        }

        return sb.toString();
    }

}
