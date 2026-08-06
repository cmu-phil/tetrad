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

package edu.cmu.tetrad.algcomparison.sweep;

import java.util.List;
import java.util.Map;

/**
 * The full record of a {@link ParameterSweep}: the evaluated settings with their evidence (point graphs, resample
 * edge probabilities, adjacency instability, Markov-check statistics, timing) plus enough configuration metadata to
 * make the sweep reproducible. Serializable to markdown and JSON so that the evidence behind a parameter choice can
 * be inspected, reported, or consumed by tools such as py-tetrad.
 * <p>
 * Selection among settings is a decision, not evidence, so the selection rules here are explicit methods a caller
 * invokes (or overrides with their own judgment); nothing is selected implicitly. The provided rules are the two in
 * common use: stability selection in the StARS sense and Markov adequacy.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SweepReport {

    /**
     * A description of the algorithm swept.
     */
    private final String algorithmDescription;

    /**
     * A description of the Markov-check test, or null if none was configured.
     */
    private final String markovCheckDescription;

    /**
     * The number of rows in the data.
     */
    private final int numRows;

    /**
     * The number of variables in the data.
     */
    private final int numVariables;

    /**
     * The number of resamples per setting.
     */
    private final int numResamples;

    /**
     * The fraction of the sample size drawn per resample.
     */
    private final double percentResampleSize;

    /**
     * Whether resampling was with replacement.
     */
    private final boolean withReplacement;

    /**
     * The random seed used to draw the resamples, or -1 if time-seeded.
     */
    private final long seed;

    /**
     * The per-setting results, in evaluation order; unmodifiable.
     */
    private final List<SweepResult> results;

    /**
     * Constructs a report. Intended to be called by {@link ParameterSweep}.
     *
     * @param algorithmDescription   a description of the algorithm swept.
     * @param markovCheckDescription a description of the Markov-check test, or null.
     * @param numRows                the number of rows in the data.
     * @param numVariables           the number of variables in the data.
     * @param numResamples           the number of resamples per setting.
     * @param percentResampleSize    the fraction of the sample size drawn per resample.
     * @param withReplacement        whether resampling was with replacement.
     * @param seed                   the random seed used, or -1 if time-seeded.
     * @param results                the per-setting results, in evaluation order.
     */
    public SweepReport(String algorithmDescription, String markovCheckDescription, int numRows, int numVariables,
                       int numResamples, double percentResampleSize, boolean withReplacement, long seed,
                       List<SweepResult> results) {
        if (results == null) throw new NullPointerException("results");
        this.algorithmDescription = algorithmDescription;
        this.markovCheckDescription = markovCheckDescription;
        this.numRows = numRows;
        this.numVariables = numVariables;
        this.numResamples = numResamples;
        this.percentResampleSize = percentResampleSize;
        this.withReplacement = withReplacement;
        this.seed = seed;
        this.results = List.copyOf(results);
    }

    /**
     * Returns the per-setting results, in evaluation order, unmodifiable.
     *
     * @return These results.
     */
    public List<SweepResult> getResults() {
        return this.results;
    }

    /**
     * Returns a description of the algorithm swept.
     *
     * @return This description.
     */
    public String getAlgorithmDescription() {
        return this.algorithmDescription;
    }

    /**
     * Returns a description of the Markov-check test, or null if none was configured.
     *
     * @return This description or null.
     */
    public String getMarkovCheckDescription() {
        return this.markovCheckDescription;
    }

    /**
     * Returns the random seed used to draw the resamples, or -1 if time-seeded.
     *
     * @return This seed.
     */
    public long getSeed() {
        return this.seed;
    }

    /**
     * Selects by the StARS rule as implemented historically in Tetrad's StARS class: among settings whose adjacency
     * instability is strictly below the cutoff, returns the one with the largest instability; returns null if no
     * setting qualifies. (The rationale: instability typically falls as regularization grows, so the largest
     * sub-cutoff instability marks the least-regularized acceptable setting.) This is a defaulted decision rule, not
     * evidence; callers may apply their own.
     *
     * @param instabilityCutoff the cutoff (StARS beta, commonly 0.05).
     * @return the selected result, or null if none qualifies.
     */
    public SweepResult selectByInstability(double instabilityCutoff) {
        SweepResult best = null;

        for (SweepResult r : this.results) {
            double d = r.getAdjacencyInstability();
            if (Double.isNaN(d) || d >= instabilityCutoff) continue;
            if (best == null || d > best.getAdjacencyInstability()) best = r;
        }

        return best;
    }

    /**
     * Selects the setting with the smallest adjacency instability, or null if no setting has a computed
     * instability. This is a defaulted decision rule, not evidence.
     *
     * @return the selected result, or null.
     */
    public SweepResult selectMostStable() {
        SweepResult best = null;

        for (SweepResult r : this.results) {
            double d = r.getAdjacencyInstability();
            if (Double.isNaN(d)) continue;
            if (best == null || d < best.getAdjacencyInstability()) best = r;
        }

        return best;
    }

    /**
     * Selects by Markov adequacy: the setting whose Anderson-Darling p-value for the implied independencies is
     * largest (least evidence of asserting independencies the data reject), breaking ties by the larger fraction of
     * implied dependencies detected. Returns null if no setting has Markov statistics. This is a defaulted decision
     * rule, not evidence.
     *
     * @return the selected result, or null.
     */
    public SweepResult selectByMarkovAdequacy() {
        SweepResult best = null;

        for (SweepResult r : this.results) {
            if (r.getMarkovStats() == null || Double.isNaN(r.getMarkovStats().adInd())) continue;

            if (best == null
                    || r.getMarkovStats().adInd() > best.getMarkovStats().adInd()
                    || (r.getMarkovStats().adInd() == best.getMarkovStats().adInd()
                    && r.getMarkovStats().fracDepDep() > best.getMarkovStats().fracDepDep())) {
                best = r;
            }
        }

        return best;
    }

    /**
     * Returns a markdown rendering of the report: a header describing the sweep and a table with one row per
     * setting (setting, point-graph edge count, adjacency instability, Anderson-Darling p for implied
     * independencies, fraction of implied dependencies detected, elapsed milliseconds).
     *
     * @return This rendering.
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Parameter sweep: ").append(this.algorithmDescription).append("\n\n");
        sb.append("Data: ").append(this.numRows).append(" rows, ").append(this.numVariables).append(" variables. ");
        sb.append("Resampling: ").append(this.numResamples).append(" resamples, ")
                .append((int) (100 * this.percentResampleSize)).append("% of sample size, ")
                .append(this.withReplacement ? "with" : "without").append(" replacement, seed ")
                .append(this.seed == -1 ? "time-based" : String.valueOf(this.seed)).append(". ");

        if (this.markovCheckDescription != null) {
            sb.append("Markov check: ").append(this.markovCheckDescription).append(".");
        } else {
            sb.append("Markov check: not run.");
        }

        sb.append("\n\n");
        sb.append("| Setting | Edges | Instability | AD p (indep) | Frac dep detected | ms |\n");
        sb.append("|---|---|---|---|---|---|\n");

        for (SweepResult r : this.results) {
            SweepResult.MarkovStats mc = r.getMarkovStats();
            sb.append("| ").append(r.getSetting())
                    .append(" | ").append(r.getPointGraph().getNumEdges())
                    .append(" | ").append(fmt(r.getAdjacencyInstability()))
                    .append(" | ").append(mc == null ? "-" : fmt(mc.adInd()))
                    .append(" | ").append(mc == null ? "-" : fmt(mc.fracDepDep()))
                    .append(" | ").append(r.getElapsedMillis())
                    .append(" |\n");
        }

        return sb.toString();
    }

    /**
     * Returns a JSON rendering of the report. Top-level fields: "algorithm", "markovCheck" (nullable), "numRows",
     * "numVariables", "numResamples", "percentResampleSize", "withReplacement", "seed", and "results", the last an
     * array of objects with fields "setting" (an object), "numEdges", "adjacencyInstability", "markov" (nullable
     * object with the ten Markov statistics), and "elapsedMillis". Graphs themselves are not embedded; retrieve them
     * from {@link #getResults()}.
     *
     * @return This rendering.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"algorithm\":\"").append(escape(this.algorithmDescription)).append("\",");
        sb.append("\"markovCheck\":")
                .append(this.markovCheckDescription == null ? "null"
                        : "\"" + escape(this.markovCheckDescription) + "\"").append(",");
        sb.append("\"numRows\":").append(this.numRows).append(",");
        sb.append("\"numVariables\":").append(this.numVariables).append(",");
        sb.append("\"numResamples\":").append(this.numResamples).append(",");
        sb.append("\"percentResampleSize\":").append(this.percentResampleSize).append(",");
        sb.append("\"withReplacement\":").append(this.withReplacement).append(",");
        sb.append("\"seed\":").append(this.seed).append(",");
        sb.append("\"results\":[");

        for (int i = 0; i < this.results.size(); i++) {
            SweepResult r = this.results.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"setting\":{");
            int k = 0;

            for (Map.Entry<String, Object> e : r.getSetting().entrySet()) {
                if (k++ > 0) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":").append(jsonValue(e.getValue()));
            }

            sb.append("},\"numEdges\":").append(r.getPointGraph().getNumEdges()).append(",");
            sb.append("\"adjacencyInstability\":").append(jsonNumber(r.getAdjacencyInstability())).append(",");

            SweepResult.MarkovStats mc = r.getMarkovStats();

            if (mc == null) {
                sb.append("\"markov\":null,");
            } else {
                sb.append("\"markov\":{");
                sb.append("\"adInd\":").append(jsonNumber(mc.adInd())).append(",");
                sb.append("\"adDep\":").append(jsonNumber(mc.adDep())).append(",");
                sb.append("\"ksInd\":").append(jsonNumber(mc.ksInd())).append(",");
                sb.append("\"ksDep\":").append(jsonNumber(mc.ksDep())).append(",");
                sb.append("\"binomialInd\":").append(jsonNumber(mc.binomialInd())).append(",");
                sb.append("\"binomialDep\":").append(jsonNumber(mc.binomialDep())).append(",");
                sb.append("\"fracDepInd\":").append(jsonNumber(mc.fracDepInd())).append(",");
                sb.append("\"fracDepDep\":").append(jsonNumber(mc.fracDepDep())).append(",");
                sb.append("\"numTestsInd\":").append(mc.numTestsInd()).append(",");
                sb.append("\"numTestsDep\":").append(mc.numTestsDep());
                sb.append("},");
            }

            sb.append("\"elapsedMillis\":").append(r.getElapsedMillis()).append("}");
        }

        sb.append("]}");
        return sb.toString();
    }

    /**
     * Returns the markdown rendering.
     *
     * @return This rendering.
     */
    @Override
    public String toString() {
        return toMarkdown();
    }

    //==================================== HELPERS ====================================//

    /**
     * Formats a double for the markdown table.
     *
     * @param x the value.
     * @return the formatted value.
     */
    private static String fmt(double x) {
        return Double.isNaN(x) ? "-" : String.format("%.4g", x);
    }

    /**
     * Renders a double as a JSON number, mapping non-finite values to null.
     *
     * @param x the value.
     * @return the JSON token.
     */
    private static String jsonNumber(double x) {
        return (Double.isNaN(x) || Double.isInfinite(x)) ? "null" : Double.toString(x);
    }

    /**
     * Renders a setting value as a JSON value: numbers and booleans bare, everything else as a string.
     *
     * @param v the value.
     * @return the JSON token.
     */
    private static String jsonValue(Object v) {
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "\"" + escape(String.valueOf(v)) + "\"";
    }

    /**
     * Escapes a string for embedding in JSON.
     *
     * @param s the string.
     * @return the escaped string.
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }

        return sb.toString();
    }
}
