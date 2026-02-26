package edu.cmu.tetrad.search.vertex_repair;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.test.IndependenceTest;

public class VertexRepairSearch implements IGraphSearch {

    public VertexRepairSearch(IndependenceTest test, Graph start, Knowledge knowledge,
                              ConditioningSetType conditioningSetType) {

    }

    @Override
    public Graph search() throws InterruptedException {
        return null;
    }
}