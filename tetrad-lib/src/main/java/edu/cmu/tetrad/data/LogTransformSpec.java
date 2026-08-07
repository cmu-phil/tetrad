///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.data;

/**
 * A logarithmic transform to apply to a single variable: the direction (log or unlog), the offset <i>a</i>, and the
 * base.
 * <p>
 * Logging applies f(x) = log_base(a + x); unlogging applies its inverse g(x) = base^x - a. A base of 0 means the
 * natural log and base <i>e</i>, matching the convention used by the dataset-wide transform.
 * <p>
 * All three fields are per variable rather than per dataset because their correct values depend on the variable's
 * scale. The offset in particular is scale-relative: since log(a + x) is approximately log(a) + x/a when x is much
 * smaller than a, a single offset shared across variables of different magnitude performs a genuine logarithmic
 * transform on the large-valued ones and an approximately affine rescaling on the small-valued ones. Keeping base
 * and direction alongside the offset also guarantees that a transform can be inverted with the same spec.
 *
 * @param a     The offset added before taking the log (or subtracted after exponentiating when unlogging).
 * @param base  The base of the logarithm; 0 means the natural log and base <i>e</i>.
 * @param unlog True to apply the inverse transform, false to apply the log transform.
 * @author josephramsey
 */
public record LogTransformSpec(double a, int base, boolean unlog) {

    /**
     * Applies this spec to a single value.
     *
     * @param x The value to transform.
     * @return The transformed value.
     */
    public double apply(double x) {
        if (this.unlog) {
            if (this.base == 0) {
                return org.apache.commons.math3.util.FastMath.exp(x) - this.a;
            } else {
                return org.apache.commons.math3.util.FastMath.pow(this.base, x) - this.a;
            }
        } else {
            double log = org.apache.commons.math3.util.FastMath.log(this.a + x);

            if (this.base == 0) {
                return log;
            } else {
                return log / org.apache.commons.math3.util.FastMath.log(this.base);
            }
        }
    }

    /**
     * Encodes a map of specs as a single string, for storage in a {@link edu.cmu.tetrad.util.Parameters} object and
     * hence in a saved session.
     * <p>
     * The format is one variable per line, with fields separated by tabs: name, offset, base, unlog. A string is
     * used rather than a serialized object so that the stored form is stable across versions of this class and
     * legible when a saved session is inspected; tab and newline are safe separators because a variable name
     * containing either could not have been read from a delimited data file.
     *
     * @param specs The specs to encode; may be null or empty.
     * @return The encoded string, empty if there is nothing to encode.
     */
    public static String encode(java.util.Map<String, LogTransformSpec> specs) {
        if (specs == null || specs.isEmpty()) return "";

        StringBuilder b = new StringBuilder();

        for (java.util.Map.Entry<String, LogTransformSpec> entry : specs.entrySet()) {
            LogTransformSpec spec = entry.getValue();
            if (b.length() > 0) b.append('\n');
            b.append(entry.getKey()).append('\t').append(spec.a()).append('\t')
                    .append(spec.base()).append('\t').append(spec.unlog());
        }

        return b.toString();
    }

    /**
     * Decodes a string produced by {@link #encode(java.util.Map)}. Malformed lines are skipped rather than throwing,
     * so that a session saved by a later version with extra fields still opens.
     *
     * @param encoded The encoded string; may be null or empty.
     * @return The decoded specs, empty if there was nothing to decode.
     */
    public static java.util.Map<String, LogTransformSpec> decode(String encoded) {
        java.util.Map<String, LogTransformSpec> specs = new java.util.LinkedHashMap<>();

        if (encoded == null || encoded.isBlank()) return specs;

        for (String line : encoded.split("\n")) {
            String[] fields = line.split("\t");
            if (fields.length < 4) continue;

            try {
                specs.put(fields[0], new LogTransformSpec(Double.parseDouble(fields[1]),
                        Integer.parseInt(fields[2]), Boolean.parseBoolean(fields[3])));
            } catch (NumberFormatException e) {
                // Skip a malformed line rather than failing to open the session.
            }
        }

        return specs;
    }

    /**
     * Returns the smallest offset that keeps every value of the given column strictly positive under a log
     * transform, or 0.0 if the column is already strictly positive. This is a convenience for editors that want to
     * propose a safe default; it is not applied automatically anywhere.
     *
     * @param column The column values.
     * @return A safe offset for the column, or 0.0 if none is needed.
     */
    public static double safeOffsetFor(double[] column) {
        double min = Double.POSITIVE_INFINITY;

        for (double x : column) {
            if (!Double.isNaN(x) && x < min) min = x;
        }

        if (Double.isInfinite(min) || min > 0.0) return 0.0;

        // Nudge just past zero so that log(a + min) is finite.
        double magnitude = org.apache.commons.math3.util.FastMath.abs(min);
        return magnitude + org.apache.commons.math3.util.FastMath.max(1e-6, magnitude * 1e-6);
    }
}
