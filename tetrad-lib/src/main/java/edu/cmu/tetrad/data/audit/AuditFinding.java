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

package edu.cmu.tetrad.data.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single immutable finding produced by a {@link DataAudit}: a machine-readable code, a severity, the names of the
 * variables involved, a map of named numeric values supporting the finding, and a human-readable message. Findings
 * describe properties of the data; they never recommend actions.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class AuditFinding {

    /**
     * The machine-readable code for this finding.
     */
    private final FindingCode code;

    /**
     * The severity of this finding.
     */
    private final Severity severity;

    /**
     * The names of the variables involved, in a fixed order; unmodifiable.
     */
    private final List<String> variables;

    /**
     * Named numeric values supporting the finding (statistics, counts, thresholds used); unmodifiable, in insertion
     * order.
     */
    private final Map<String, Double> values;

    /**
     * A human-readable one-line message.
     */
    private final String message;

    /**
     * Constructs a finding. Defensive unmodifiable copies are made of the collection arguments.
     *
     * @param code      the machine-readable code; may not be null.
     * @param severity  the severity; may not be null.
     * @param variables the names of the variables involved; may not be null (may be empty).
     * @param values    named numeric values supporting the finding; may not be null (may be empty).
     * @param message   a human-readable one-line message; may not be null.
     */
    public AuditFinding(FindingCode code, Severity severity, List<String> variables,
                        Map<String, Double> values, String message) {
        if (code == null) throw new NullPointerException("code");
        if (severity == null) throw new NullPointerException("severity");
        if (variables == null) throw new NullPointerException("variables");
        if (values == null) throw new NullPointerException("values");
        if (message == null) throw new NullPointerException("message");

        this.code = code;
        this.severity = severity;
        this.variables = Collections.unmodifiableList(List.copyOf(variables));
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.message = message;
    }

    /**
     * Returns the machine-readable code for this finding.
     *
     * @return This code.
     */
    public FindingCode getCode() {
        return this.code;
    }

    /**
     * Returns the severity of this finding.
     *
     * @return This severity.
     */
    public Severity getSeverity() {
        return this.severity;
    }

    /**
     * Returns the names of the variables involved in this finding, unmodifiable.
     *
     * @return These names.
     */
    public List<String> getVariables() {
        return this.variables;
    }

    /**
     * Returns the named numeric values supporting this finding, unmodifiable, in insertion order.
     *
     * @return These values.
     */
    public Map<String, Double> getValues() {
        return this.values;
    }

    /**
     * Returns the human-readable one-line message for this finding.
     *
     * @return This message.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Returns a one-line rendering of this finding, of the form "SEVERITY CODE [vars]: message".
     *
     * @return This rendering.
     */
    @Override
    public String toString() {
        return this.severity + " " + this.code + " " + this.variables + ": " + this.message;
    }

    /**
     * The severity of a finding. INFO findings are properties worth knowing (some, like non-Gaussianity, may even be
     * exploitable); WARNING findings are properties that typically degrade or invalidate common analyses if ignored.
     */
    public enum Severity {

        /**
         * A property worth knowing that is not necessarily a defect.
         */
        INFO,

        /**
         * A property that typically degrades or invalidates common analyses if ignored.
         */
        WARNING
    }
}
