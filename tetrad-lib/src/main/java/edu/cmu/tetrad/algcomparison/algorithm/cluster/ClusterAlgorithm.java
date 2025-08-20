package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import java.util.ArrayList;
import java.util.List;

/**
 * Tags an algorithm that generates clusters.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ClusterAlgorithm {
    default List<List<Integer>> getBlocks() {
        return new ArrayList<>();
    }
}
