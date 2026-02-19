package edu.cmu.tetrad.sem;

import edu.cmu.tetradapp.editor.DagModelScoreEditor;

public final class DagModelScoreGadgetFactory {

    private DagModelScoreGadgetFactory() {}

    public static DagModelScoreEditor createDefault() {
        DagModelScoreEditor p = new DagModelScoreEditor();

        // --- BIC-family metrics (add/remove whatever you want) ---
        // 1) Linear-Gaussian SEM BIC (classic)
        p.addMetric(DagMetric.of("SEM-BIC (LG)", DagScores::semBicLinearGaussian));

        // 2) SEM-BIC with penalty discount (common in Tetrad)
        p.addMetric(DagMetric.of("SEM-BIC (LG, discount=2.0)", (d, g) -> DagScores.semBicLinearGaussian(d, g, 2.0)));

        // 3) Discrete BIC (if you have it / want it)
        p.addMetric(DagMetric.of("Discrete BIC", DagScores::bicDiscrete));

        // 4) Conditional-Gaussian / Mixed BIC (if you have it / want it)
        p.addMetric(DagMetric.of("Mixed CG-BIC", DagScores::bicMixedConditionalGaussian));

        // --- Global distribution metric (optional) ---
        p.addMetric(DagMetric.of("Global MMD^2 (Exact RBF)", DagScores::mmd2ExactRbf));

        return p;
    }
}