package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores AD trees for data sets for reuse.
 *
 * @author jdramsey
 */
public class AdTrees {
    private static final Map<DataSet, AdLeafTree> adTrees = new HashMap<>();

    /**
     * Returns an ADLeafTree for the given dataset.
     * @param dataSet A discrete dataset.
     * @return The AdLeafTree
     * @see AdLeafTree
     */
    public static AdLeafTree getAdLeafTree(DataSet dataSet) {
        AdLeafTree tree = AdTrees.adTrees.get(dataSet);

        if (tree == null) {
            tree = new AdLeafTree(dataSet);
            AdTrees.adTrees.put(dataSet, tree);
        }

        return tree;
    }
}
