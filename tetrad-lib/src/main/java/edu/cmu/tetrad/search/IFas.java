package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.SepsetMap;

public interface IFas extends IGraphSearch {
    /**
     * Run adjacency search and return the skeleton graph.
     */
    @Override
    Graph search() throws InterruptedException;

    /**
     * Sep-sets discovered during the search.
     */
    SepsetMap getSepsets();

    void setKnowledge(Knowledge knowledge);

    void setDepth(int depth);

    void setVerbose(boolean verbose);

    void setStable(boolean stable);
}