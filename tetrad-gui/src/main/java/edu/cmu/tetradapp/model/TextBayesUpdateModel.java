package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.Evidence;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.BayesUpdaterEditor;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Model backing the text-first Bayes updater editor.
 *
 * Holds:
 *  - the source BayesIm
 *  - Parameters for UI persistence
 *  - optional last-run cache (so results survive editor refreshes)
 *
 * This model is intentionally lightweight; all heavy computation is triggered
 * by the editor's "Do Update" button.
 */
public final class TextBayesUpdateModel implements SessionModel, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The source Bayes IM to update.
     */
    private final BayesIm bayesIm;
    /**
     * The parameters, used for UI persistence.
     */
    private final Parameters params;

    // -------------------------
    // Optional: last-run cache
    // -------------------------

    /**
     * The evidence text from the last run.
     */
    private String lastEvidenceText = "";
    /**
     * The evidence from the last run; may be null.
     */
    private Evidence lastEvidence = null;

    /**
     * The name of this model.
     */
    private String name = "";

    /**
     * Cached results rows (one per variable displayed).
     * Store primitive data only (names, indices, doubles) to keep serialization safe.
     */
    private final List<ResultRow> lastResults = new ArrayList<>();

    /**
     * Constructs a new model from the Bayes IM of the given wrapper.
     *
     * @param bayesIm the Bayes IM wrapper.
     * @param params  the parameters.
     */
    public TextBayesUpdateModel(BayesImWrapper bayesIm, Parameters params) {
        this.bayesIm = Objects.requireNonNull(bayesIm, "bayesIm").getBayesIm();
        this.params = Objects.requireNonNull(params, "params");
    }

    /**
     * Constructs a new model from the estimated Bayes IM of the given estimator wrapper.
     *
     * @param bayesEst the Bayes estimator wrapper.
     * @param params   the parameters.
     */
    public TextBayesUpdateModel(BayesEstimatorWrapper bayesEst, Parameters params) {
        this.bayesIm = Objects.requireNonNull(bayesEst, "bayesIm").getEstimatedBayesIm();
        this.params = Objects.requireNonNull(params, "params");
    }

    /**
     * Returns the source Bayes IM.
     *
     * @return the Bayes IM.
     */
    public BayesIm getBayesIm() {
        return bayesIm;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters.
     */
    public Parameters getParams() {
        return params;
    }

    // -------------------------
    // Last-run cache accessors
    // -------------------------

    /**
     * Returns the evidence text from the last run.
     *
     * @return the last evidence text; never null.
     */
    public String getLastEvidenceText() {
        return lastEvidenceText;
    }

    /**
     * Sets the evidence text from the last run; null is stored as the empty string.
     *
     * @param lastEvidenceText the last evidence text.
     */
    public void setLastEvidenceText(String lastEvidenceText) {
        this.lastEvidenceText = (lastEvidenceText == null) ? "" : lastEvidenceText;
    }

    /**
     * Returns a copy of the evidence from the last run.
     *
     * @return a copy of the last evidence, or null if none.
     */
    public Evidence getLastEvidence() {
        return lastEvidence == null ? null : new Evidence(lastEvidence);
    }

    /**
     * Stores a copy of the evidence from the last run.
     *
     * @param lastEvidence the last evidence; may be null.
     */
    public void setLastEvidence(Evidence lastEvidence) {
        this.lastEvidence = (lastEvidence == null) ? null : new Evidence(lastEvidence);
    }

    /**
     * Returns an unmodifiable copy of the cached result rows from the last run.
     *
     * @return the last result rows.
     */
    public List<ResultRow> getLastResults() {
        return List.copyOf(lastResults);
    }

    /**
     * Replaces the cached result rows from the last run.
     *
     * @param rows the result rows; null clears them.
     */
    public void setLastResults(List<ResultRow> rows) {
        lastResults.clear();
        if (rows != null) lastResults.addAll(rows);
    }

    /**
     * Clears the cached evidence text, evidence and result rows from the last run.
     */
    public void clearLastRun() {
        lastEvidenceText = "";
        lastEvidence = null;
        lastResults.clear();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        if (name == null) name = "";
        this.name = name;
    }

    /**
     * Immutable row for the wide marginals table.
     * Keep it simple and serialization-friendly.
     */
    public static final class ResultRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * The index of the variable in the source Bayes IM.
         */
        private final int nodeIndex;     // index in SOURCE BayesIm
        /**
         * The name of the variable, for display.
         */
        private final String varName;    // for display (stable)
        /**
         * The updated marginal probabilities, one per category.
         */
        private final double[] marginals; // updated marginals per category

        /**
         * Constructs a result row. The marginals array is copied.
         *
         * @param nodeIndex the index of the variable in the source Bayes IM.
         * @param varName   the name of the variable.
         * @param marginals the updated marginals per category; null is treated as empty.
         */
        public ResultRow(int nodeIndex, String varName, double[] marginals) {
            this.nodeIndex = nodeIndex;
            this.varName = Objects.requireNonNull(varName, "varName");
            this.marginals = (marginals == null) ? new double[0] : marginals.clone();
        }

        /**
         * Returns the index of the variable in the source Bayes IM.
         *
         * @return the node index.
         */
        public int getNodeIndex() {
            return nodeIndex;
        }

        /**
         * Returns the name of the variable.
         *
         * @return the variable name.
         */
        public String getVarName() {
            return varName;
        }

        /**
         * Returns a copy of the updated marginal probabilities, one per category.
         *
         * @return the marginals.
         */
        public double[] getMarginals() {
            return marginals.clone();
        }
    }
}