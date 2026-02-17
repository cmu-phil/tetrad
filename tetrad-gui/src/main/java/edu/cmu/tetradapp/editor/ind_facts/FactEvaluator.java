package edu.cmu.tetradapp.editor.ind_facts;

import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.search.test.IndependenceResult;

public interface FactEvaluator {
    IndependenceResult evaluate(IndependenceFact fact) throws InterruptedException;
    boolean hasParams();               // whether to show the Params button
    String name();                     // for combo display
}