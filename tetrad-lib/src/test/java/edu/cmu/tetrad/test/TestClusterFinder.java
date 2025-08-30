package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.RandomMim;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ScoredClusterFinder;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class TestClusterFinder {


    private static Graph makeRandomMim(String latentGroupSpecs, int maxStructuralEdges, int measurementModelDegree,
                                       int numLatentMeasuredImpureParents, int numMeasuredMeasuredImpureParents,
                                       int numMeasuredMeasuredImpureAssociations) {

        List<RandomMim.LatentGroupSpec> specs = RandomMim.parseLatentGroupSpecs(latentGroupSpecs);

//        DataGraphUtilsFlexMim.LatentGroupSpec spec = new DataGraphUtilsFlexMim.LatentGroupSpec(
//                numStructuralNodes, numFactors, maxStructuralEdges);
        return RandomMim.constructRandomMim(
                specs,
                maxStructuralEdges,
                numLatentMeasuredImpureParents,
                numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations,
                RandomMim.LatentLinkMode.CARTESIAN_PRODUCT,
                new Random());
    }

    @Test
    public void testClusterFinder() {
        int numFactors = 1;
        int numStructuralNodes = 2;
        int maxStructuralEdges = 1;
        int measurementModelDegree = 5;
        int numLatentMeasuredImpureParents = 0;
        int numMeasuredMeasuredImpureParents = 0;
        int numMeasuredMeasuredImpureAssociations = 0;

        Graph graph = makeRandomMim("5:5(1)", maxStructuralEdges, measurementModelDegree,
                numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(5000, false);

        System.out.println(dataSet.getVariables());

        // Suppose you want clusters from a subset Vsub (e.g., all observed variables),
        // of size s=2 that score best at rank k=1.
        List<Integer> vsub = new ArrayList<>();
        for (int c = 0; c < dataSet.getNumColumns(); c++) vsub.add(c);

        ScoredClusterFinder finder = new ScoredClusterFinder(dataSet, vsub);
        finder.setPenaltyDiscount(1.0);     // like BlocksBicScore 'c'
        finder.setEbicGamma(0.0);           // EBIC extra penalty (0 to disable)
        finder.setRidge(1e-8);              // RCCA regularization
        finder.setMargins(0.0, 0.0);        // optional: require separation from k±1
        finder.setVerbose(true);

        List<ScoredClusterFinder.ClusterHit> hits = finder.findClusters(/*size=*/2, /*targetRank=*/1);
        for (var h : hits) {
            System.out.println(h + " " + toNamesCluster(h.members, dataSet.getVariables()));
        }
    }

    private @NotNull StringBuilder toNamesCluster(Collection<Integer> cluster, List<Node> nodes) { /* ... unchanged ... */
        StringBuilder _sb = new StringBuilder();
        _sb.append("[");
        int count = 0;
        for (Integer var : cluster) {
            _sb.append(nodes.get(var));
            if (count++ < cluster.size() - 1) _sb.append(", ");
        }
        _sb.append("]");
        return _sb;
    }
}
