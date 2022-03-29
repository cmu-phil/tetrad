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

    public static AdLeafTree getAdLeafTree(final DataSet dataSet) {
        AdLeafTree tree = adTrees.get(dataSet);

        if (tree == null) {
            tree = new AdLeafTree(dataSet);
            adTrees.put(dataSet, tree);
        }

        return tree;
    }
}
