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

    private final BayesIm bayesIm;
    private final Parameters params;

    // -------------------------
    // Optional: last-run cache
    // -------------------------

    private String lastEvidenceText = "";
    private Evidence lastEvidence = null;

    private String name = "";

    /**
     * Cached results rows (one per variable displayed).
     * Store primitive data only (names, indices, doubles) to keep serialization safe.
     */
    private final List<ResultRow> lastResults = new ArrayList<>();

    public TextBayesUpdateModel(BayesImWrapper bayesIm, Parameters params) {
        this.bayesIm = Objects.requireNonNull(bayesIm, "bayesIm").getBayesIm();
        this.params = Objects.requireNonNull(params, "params");
    }

    public TextBayesUpdateModel(BayesEstimatorWrapper bayesEst, Parameters params) {
        this.bayesIm = Objects.requireNonNull(bayesEst, "bayesIm").getEstimatedBayesIm();
        this.params = Objects.requireNonNull(params, "params");
    }

    public BayesIm getBayesIm() {
        return bayesIm;
    }

    public Parameters getParams() {
        return params;
    }

    // -------------------------
    // Last-run cache accessors
    // -------------------------

    public String getLastEvidenceText() {
        return lastEvidenceText;
    }

    public void setLastEvidenceText(String lastEvidenceText) {
        this.lastEvidenceText = (lastEvidenceText == null) ? "" : lastEvidenceText;
    }

    public Evidence getLastEvidence() {
        return lastEvidence == null ? null : new Evidence(lastEvidence);
    }

    public void setLastEvidence(Evidence lastEvidence) {
        this.lastEvidence = (lastEvidence == null) ? null : new Evidence(lastEvidence);
    }

    public List<ResultRow> getLastResults() {
        return List.copyOf(lastResults);
    }

    public void setLastResults(List<ResultRow> rows) {
        lastResults.clear();
        if (rows != null) lastResults.addAll(rows);
    }

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

        private final int nodeIndex;     // index in SOURCE BayesIm
        private final String varName;    // for display (stable)
        private final double[] marginals; // updated marginals per category

        public ResultRow(int nodeIndex, String varName, double[] marginals) {
            this.nodeIndex = nodeIndex;
            this.varName = Objects.requireNonNull(varName, "varName");
            this.marginals = (marginals == null) ? new double[0] : marginals.clone();
        }

        public int getNodeIndex() {
            return nodeIndex;
        }

        public String getVarName() {
            return varName;
        }

        public double[] getMarginals() {
            return marginals.clone();
        }
    }
}