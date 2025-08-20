package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.search.blocks.BlockSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Tags an algorithm that generates clusters.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ClusterAlgorithm {
    BlockSpec getBlockSpec();
}
