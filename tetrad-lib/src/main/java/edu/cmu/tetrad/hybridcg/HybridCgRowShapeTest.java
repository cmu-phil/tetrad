package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.GraphUtils;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Microtest: ensure HybridCgPm row shapes behave correctly
 * when a discrete child has continuous parents.
 */
public class HybridCgRowShapeTest {

    @Test
    public void testDiscChildWithContParent() {
        // Graph: X (continuous) -> Y (discrete, 3 categories)
        Node X = new ContinuousVariable("X");
        Node Y = new DiscreteVariable("Y", Arrays.asList("a", "b", "c"));
        Graph g = new EdgeListGraph();
        g.addNode(X);
        g.addNode(Y);
        g.addDirectedEdge(X, Y);

        // Build PM: Y discrete, X continuous
        List<Node> order = Arrays.asList(X, Y);
        Map<Node, Boolean> isDisc = new LinkedHashMap<>();
        isDisc.put(X, false);
        isDisc.put(Y, true);

        Map<Node, List<String>> cats = new LinkedHashMap<>();
        cats.put(X, null);
        cats.put(Y, ((DiscreteVariable) Y).getCategories());

        HybridCgModel.HybridCgPm pm = new HybridCgModel.HybridCgPm(g, order, isDisc, cats);

        // Cutpoints: 2 bins on X (edge at 0.0)
        Map<Node, double[]> cuts = new LinkedHashMap<>();
        cuts.put(X, new double[]{0.0}); // two bins
        pm.setContParentCutpointsForDiscreteChild(Y, cuts);

        int yIdx = pm.indexOf(Y);

        // Expected: 2 bins for X × 1 parent category vector (since X is cont)
        int[] rowDims = pm.getRowDims(yIdx);
        assertArrayEquals(new int[]{2}, rowDims);
        assertEquals(2, pm.getNumRows(yIdx));

        // Check rowIndexForCase agrees with manual binning
        double[] xVals = {-2.0, 0.1, 5.0};
        int[] expectedRows = {0, 1, 1}; // below cutpoint=0.0 → row 0, else row 1

        for (int i = 0; i < xVals.length; i++) {
            int row = pm.rowIndexForCase(yIdx, new double[]{xVals[i]});
            assertEquals(expectedRows[i], row);
        }

        // Construct IM and sample a small dataset
        HybridCgModel.HybridCgIm im = new HybridCgModel.HybridCgIm(pm);
        // initialize trivial params: equal probs, mean 0, variance 1
        for (int r = 0; r < pm.getNumRows(yIdx); r++) {
            for (int k = 0; k < pm.getCardinality(yIdx); k++) {
                im.setProbability(yIdx, r, k, 1.0 / pm.getCardinality(yIdx));
            }
        }

        DataSet simulated = im.toDataSet(im.sample(50, new Random(123)));
        assertNotNull(simulated);
        assertEquals(2, simulated.getNumColumns()); // X and Y present
    }
}