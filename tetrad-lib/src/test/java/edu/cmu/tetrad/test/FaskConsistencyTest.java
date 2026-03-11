package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class FaskConsistencyTest {

    @Test
    public void testFaskConsistency() throws InterruptedException {
        int nVar = 5;
        int nData = 1000;
        
        Graph dag = RandomGraph.randomDag(nVar, 0, nVar * (nVar - 1) / 2, nVar, nVar, nVar, false);
        GeneralizedSemPm pm = new GeneralizedSemPm(dag);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        DataSet dataSet = im.simulateData(nData, false);
        
        Fask fask = new Fask(dataSet, new SemBicScore(dataSet, true));
        fask.setTwoCycleAlpha(0.01);
        Graph result1 = fask.search();
        
        // Ensure it runs and returns a graph
        assertNotNull(result1);
        
        // We can add more specific checks here if we want to ensure exact same graph after refactor
        System.out.println("FASK result edges: " + result1.getEdges());
    }
}
