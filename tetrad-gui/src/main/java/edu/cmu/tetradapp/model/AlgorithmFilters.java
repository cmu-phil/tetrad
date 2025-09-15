package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;

public final class AlgorithmFilters {
    private AlgorithmFilters() {
    }

    /**
     * Block-capable if it can accept a wrapper for a block-based test and/or a block-based score.
     */
    public static boolean supportsBlocks(AlgorithmModel model) {
        var algo = model.getAlgorithm();

        // If it's a class descriptor, check with reflection
        try {
            Class<?> clazz = algo.clazz();  // or however AlgorithmModel exposes the underlying Class
            return TakesIndependenceWrapper.class.isAssignableFrom(clazz)
                   || TakesScoreWrapper.class.isAssignableFrom(clazz);
        } catch (Exception ignored) {
            return false;
        }

//        return (algo.clazz() instanceof TakesIndependenceWrapper) || (algo instanceof UsesScoreWrapper);
    }
}