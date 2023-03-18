package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SuborderSearch {

    void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts, int numStarts);

    void setKnowledge(Knowledge knowledge);

    List<Node> getVariables();

    Map<Node, Set<Node>> getParents();

    Score getScore();
}
