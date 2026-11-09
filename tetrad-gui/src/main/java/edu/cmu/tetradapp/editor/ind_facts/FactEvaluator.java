package edu.cmu.tetradapp.editor.ind_facts;

import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.search.test.IndependenceResult;

/**
 * Evaluates independence facts for the independence facts editor, for example by a statistical test or by
 * m-separation in a graph.
 */
public interface FactEvaluator {
    /**
     * Evaluates the given independence fact.
     *
     * @param fact the independence fact to evaluate.
     * @return the result of the evaluation.
     * @throws InterruptedException if the evaluation is interrupted.
     */
    IndependenceResult evaluate(IndependenceFact fact) throws InterruptedException;
    /**
     * Returns whether this evaluator has parameters that can be edited.
     *
     * @return true if the Params button should be shown for this evaluator.
     */
    boolean hasParams();               // whether to show the Params button
    /**
     * Returns the name of this evaluator, for display in the combo box.
     *
     * @return the display name.
     */
    String name();                     // for combo display
}