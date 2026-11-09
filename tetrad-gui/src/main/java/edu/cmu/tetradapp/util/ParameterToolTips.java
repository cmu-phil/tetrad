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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.ParamDescription;

import javax.swing.*;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Builds the tooltip shown for a parameter in the GUI's parameter panels.
 * <p>
 * Added 2026-8-24. Previously the raw long description from the manual was set verbatim as the tooltip text, which Swing
 * renders as a single unbroken line; a two-sentence description became a tooltip wider than the screen. The tooltip was
 * also attached only to the label, not the input control the mouse is usually over, and did not show the parameter's
 * identifier, so a user had no way to connect a GUI field to the {@code Params} constant or the py-tetrad setter that
 * controls the same thing.
 * <p>
 * The tooltip built here is HTML, wrapped to a fixed width, and shows: the short description as a heading, the parameter
 * identifier (the same string used in scripts and py-tetrad), the long description, and the default value plus range
 * where a range is meaningful. Values are shown exactly as the manual states them; nothing here changes behavior.
 */
public final class ParameterToolTips {

    /**
     * Width, in pixels, at which the tooltip body wraps.
     */
    private static final int WRAP_WIDTH_PX = 360;

    private static final DecimalFormat DOUBLE_FORMAT = new DecimalFormat("0.######");

    private ParameterToolTips() {
    }

    /**
     * Returns the HTML tooltip text for the given parameter description.
     *
     * @param desc the parameter description from the manual; may not be null.
     * @return an HTML string suitable for {@link JComponent#setToolTipText(String)}.
     */
    public static String forParameter(ParamDescription desc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><div style='width:").append(WRAP_WIDTH_PX).append("px'>");

        String shortDesc = desc.getShortDescription();
        if (shortDesc != null && !shortDesc.isBlank()) {
            sb.append("<b>").append(escape(shortDesc.trim())).append("</b><br>");
        }

        String name = desc.getParamName();
        if (name != null && !name.isBlank()) {
            sb.append("<span style='color:gray'>Parameter: <code>")
                    .append(escape(name.trim())).append("</code></span>");
        }

        String longDesc = desc.getLongDescription();
        if (longDesc != null && !longDesc.isBlank()) {
            sb.append("<p style='margin-top:4px'>").append(escape(collapseWhitespace(longDesc))).append("</p>");
        }

        String facts = defaultAndRange(desc);
        if (!facts.isEmpty()) {
            sb.append("<p style='margin-top:4px;color:gray'>").append(escape(facts)).append("</p>");
        }

        sb.append("</div></html>");
        return sb.toString();
    }

    /**
     * Attaches the tooltip for the given parameter to both the label and the input control, so it appears wherever the
     * mouse rests on the row.
     *
     * @param desc  the parameter description.
     * @param label the row's label; may be null.
     * @param field the row's input control; may be null.
     */
    public static void apply(ParamDescription desc, JComponent label, JComponent field) {
        String tip = forParameter(desc);
        if (label != null) label.setToolTipText(tip);
        if (field != null) field.setToolTipText(tip);
    }

    /**
     * Renders "Default: x; range: [a, b]" (or the parts of that which apply) as plain text. Bounds equal to the type's
     * extreme values are treated as unbounded and omitted; a fully unbounded range is omitted entirely.
     *
     * @param desc the parameter description.
     * @return the facts line, or the empty string if there is nothing to say.
     */
    public static String defaultAndRange(ParamDescription desc) {
        Object def = desc.getDefaultValue();
        StringBuilder sb = new StringBuilder();

        if (def instanceof Boolean || def instanceof String) {
            if (def instanceof String s && s.isBlank()) return "";
            sb.append("Default: ").append(def);
            List<String> allowed = desc.getAllowedValues();
            if (def instanceof String && allowed != null && !allowed.isEmpty()) {
                sb.append("; choices: ").append(String.join(", ", allowed));
            }
            return sb.toString();
        }

        if (def instanceof Integer) {
            sb.append("Default: ").append(def);
            appendRange(sb, boundOrNull(desc.getLowerBoundInt(), Integer.MIN_VALUE),
                    boundOrNull(desc.getUpperBoundInt(), Integer.MAX_VALUE));
            return sb.toString();
        }

        if (def instanceof Long) {
            sb.append("Default: ").append(def);
            appendRange(sb, boundOrNull(desc.getLowerBoundLong(), Long.MIN_VALUE),
                    boundOrNull(desc.getUpperBoundLong(), Long.MAX_VALUE));
            return sb.toString();
        }

        if (def instanceof Double d) {
            sb.append("Default: ").append(DOUBLE_FORMAT.format(d));
            appendRange(sb, boundOrNull(desc.getLowerBoundDouble()), boundOrNull(desc.getUpperBoundDouble()));
            return sb.toString();
        }

        return "";
    }

    private static void appendRange(StringBuilder sb, String lower, String upper) {
        if (lower == null && upper == null) return;
        sb.append("; range: ");
        if (lower != null && upper != null) {
            sb.append("[").append(lower).append(", ").append(upper).append("]");
        } else if (lower != null) {
            sb.append("at least ").append(lower);
        } else {
            sb.append("at most ").append(upper);
        }
    }

    private static String boundOrNull(int value, int sentinel) {
        return value == sentinel ? null : Integer.toString(value);
    }

    private static String boundOrNull(long value, long sentinel) {
        return value == sentinel ? null : Long.toString(value);
    }

    private static String boundOrNull(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return null;
        if (value == Double.MAX_VALUE || value == -Double.MAX_VALUE) return null;
        if (value == Double.MIN_VALUE) return null; // used in the manual as "unbounded below" for some doubles
        return DOUBLE_FORMAT.format(value);
    }

    private static String collapseWhitespace(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    /**
     * Minimal HTML escaping for text that will be embedded in HTML labels or tooltips.
     *
     * @param s the text.
     * @return the escaped text.
     */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
