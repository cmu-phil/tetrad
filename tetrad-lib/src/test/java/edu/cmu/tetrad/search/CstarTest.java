package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class CstarTest {

    @Test
    public void testCstarBasic() {
        // Create a simple DAG: X1 -> X2, X1 -> X3, X2 -> X3
        Graph dag = new EdgeListGraph();
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x1, x3);
        dag.addDirectedEdge(x2, x3);

        // Generate data
        SemPm semPm = new SemPm(dag);
        SemIm semIm = new SemIm(semPm);
        DataSet data = null;
        try {
            data = semIm.simulateData(1000, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        Parameters parameters = new Parameters();
        Cstar cstar = new Cstar(new FisherZ(), new SemBicScore(), parameters);
        cstar.setNumSubsamples(10); // Low number for fast test
        cstar.setParallelized(false);

        List<Node> possibleCauses = new ArrayList<>(data.getVariables());
        List<Node> possibleEffects = new ArrayList<>();
        possibleEffects.add(x3);

        String path = "test-cstar-out";
        LinkedList<LinkedList<Cstar.Record>> records = cstar.getRecords(data, possibleCauses, possibleEffects, 1, path);

        assertNotNull(records);
        assertFalse(records.isEmpty());

        LinkedList<Cstar.Record> combined = Cstar.cStar(records);
        assertNotNull(combined);
        
        System.out.println("Cstar results:");
        for (Cstar.Record record : combined) {
            System.out.println(record.getCauseNode() + " -> " + record.getEffectNode() + " pi=" + record.getPi() + " effect=" + record.getMinBeta());
        }

        // Cleanup
        deleteDirectory(new File(path));
        for (int i = 1; i < 10; i++) {
            deleteDirectory(new File(path + "." + i));
        }
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
