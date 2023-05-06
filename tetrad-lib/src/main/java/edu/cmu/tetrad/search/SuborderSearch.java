package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface to help implement suborder searches for various types of permutation algorithms.
 * A "suborder search" is a search for permutation &lt;x1a,...x1n, x2a,...,x2m, x3a,...,x3l&gt>
 * that searches for a good permutation of x2a,...,x2m with x1a,...,x1n as a prefix.
 * This is used by PermutationSearch to form a complete permutation search algorithm,
 * where PermutationSearch handles an optimization for tiered knowledge where each
 * tier can be search separately in order. (See the documentation for that class.)
 *
 * @author bryanandrews
 * @see PermutationSearch
 */
public interface SuborderSearch {

    /**
     * Searches the suburder.
     *
     * @param prefix   The prefix of the suborder.
     * @param suborder The suborder.
     * @param gsts     The GrowShinkTree being used to do caching of scores.
     * @see GrowShrinkTree
     */
    void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts);

    /**
     * The knowledge being used.
     *
     * @param knowledge This knowledge.
     * @see Knowledge
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * The list of all variables, in order. They should satisfy the suborder requirements.
     *
     * @return This list.
     * @see Node
     * @see edu.cmu.tetrad.data.Variable
     */
    List<Node> getVariables();

    /**
     * The map from nodes to parents resulting from the search.
     *
     * @return This map.
     */
    Map<Node, Set<Node>> getParents();

    /**
     * The score being used.
     *
     * @return This score.
     * @see Score
     */
    Score getScore();
}
