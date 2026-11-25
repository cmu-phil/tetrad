/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.description;

/**
 * Writes the value of a setting as text from which its kind can be read back, and reads it back. A setting with no
 * registered default gives no other clue to its kind, and the kind matters: a parameter asked for as a whole number
 * must be one, not a decimal number or a piece of text that looks like one.
 * <ul>
 * <li>Text is written in double quotes: {@code "MICE"}.</li>
 * <li>A whole number is written bare: {@code 3}. A long whole number has an L after it: {@code 42L}.</li>
 * <li>A decimal number always has a point or an exponent: {@code 2.0}, {@code 0.0001}.</li>
 * <li>A truth value is {@code true} or {@code false}.</li>
 * <li>A named constant is its name followed by its type: {@code Fgls (enum edu.cmu.tetrad.sem.ScoreType)}.</li>
 * </ul>
 *
 * @author josephramsey
 * @author Claude
 */
final class SettingLiteral {

    private static final String ENUM_OPEN = " (enum ";

    private SettingLiteral() {
    }

    /**
     * Writes a value.
     *
     * @param value the value.
     * @return the text, or null if the value is of a kind that cannot be written on one line and read back: text of
     * several lines, a list, or any other object.
     */
    static String write(Object value) {
        if (value instanceof String text) {
            if (text.contains("\n") || text.contains("\r")) {
                return null;
            }

            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }

        if (value instanceof Integer || value instanceof Boolean) {
            return String.valueOf(value);
        }

        if (value instanceof Long) {
            return value + "L";
        }

        // A decimal number always comes out with a point or an exponent, or as "NaN" or "Infinity", so it is never
        // taken for a whole number when read back.
        if (value instanceof Double number) {
            return StandardDescribers.valueText(number);
        }

        if (value instanceof Enum<?> constant) {
            return constant.name() + ENUM_OPEN + constant.getDeclaringClass().getName() + ")";
        }

        return null;
    }

    /**
     * Reads a value back.
     *
     * @param text the text, as {@link #write(Object)} gives it.
     * @return the value.
     * @throws IllegalArgumentException if the text is not of any of the forms above.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object read(String text) {
        String t = text.strip();

        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            StringBuilder out = new StringBuilder();
            String inner = t.substring(1, t.length() - 1);

            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);

                if (c == '\\' && i + 1 < inner.length()) {
                    out.append(inner.charAt(++i));
                } else {
                    out.append(c);
                }
            }

            return out.toString();
        }

        if (t.equals("true") || t.equals("false")) {
            return Boolean.valueOf(t);
        }

        if (t.endsWith(")") && t.contains(ENUM_OPEN)) {
            int open = t.lastIndexOf(ENUM_OPEN);
            String name = t.substring(0, open).strip();
            String className = t.substring(open + ENUM_OPEN.length(), t.length() - 1).strip();

            try {
                Class<?> type = Class.forName(className);

                if (!type.isEnum()) {
                    throw new IllegalArgumentException(className + " is not a type of named constants.");
                }

                return Enum.valueOf((Class<? extends Enum>) type, name);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("The type " + className + " is not known to this version of "
                                                   + "Tetrad.");
            }
        }

        try {
            if (t.matches("-?\\d+L")) {
                return Long.valueOf(t.substring(0, t.length() - 1));
            }

            if (t.matches("-?\\d+")) {
                return Integer.valueOf(t);
            }

            return Double.valueOf(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text + "\" is not a value this description can hold.");
        }
    }
}
