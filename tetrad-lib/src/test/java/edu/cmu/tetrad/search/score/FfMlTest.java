package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FfMlTest {

    @Test
    public void testContinuousOnly() {
        int n = 100;
        List<Node> nodes = new ArrayList<>();
        nodes.add(new ContinuousVariable("X1"));
        nodes.add(new ContinuousVariable("X2"));
        DataSet ds = new BoxDataSet(new DoubleDataBox(n, nodes.size()), nodes);
        
        for (int i = 0; i < n; i++) {
            double x1 = Math.random();
            ds.setDouble(i, 0, x1);
            ds.setDouble(i, 1, 2.0 * x1 + 0.5 * Math.random());
        }

        FfMl score = new FfMl(ds);
        score.setNumFeatures(20);
        
        double s0 = score.localScore(1); // X2
        double s1 = score.localScore(1, 0); // X2 | X1
        
        System.out.println("[DEBUG_LOG] Continuous Only - Score(X2): " + s0);
        System.out.println("[DEBUG_LOG] Continuous Only - Score(X2|X1): " + s1);
        
        assertTrue("Conditional score should be higher than marginal for dependent variables", s1 > s0);
    }

    @Test
    public void testMixed() {
        int n = 100;
        List<Node> nodes = new ArrayList<>();
        nodes.add(new DiscreteVariable("D1", 2));
        nodes.add(new ContinuousVariable("X1"));
        DataSet ds = new BoxDataSet(new DoubleDataBox(n, nodes.size()), nodes);
        
        for (int i = 0; i < n; i++) {
            int d1 = (i < n / 2) ? 0 : 1;
            ds.setInt(i, 0, d1);
            ds.setDouble(i, 1, d1 * 2.0 + 0.5 * Math.random());
        }

        FfMl score = new FfMl(ds);
        score.setNumFeatures(20);
        
        double s0 = score.localScore(1); // X1
        double s1 = score.localScore(1, 0); // X1 | D1
        
        System.out.println("[DEBUG_LOG] Mixed - Score(X1): " + s0);
        System.out.println("[DEBUG_LOG] Mixed - Score(X1|D1): " + s1);
        
        assertTrue("Conditional score should be higher than marginal for dependent variables", s1 > s0);
    }
    
    @Test
    public void testConsistency() {
        int n = 50;
        List<Node> nodes = new ArrayList<>();
        nodes.add(new DiscreteVariable("D1", 2));
        nodes.add(new ContinuousVariable("X1"));
        nodes.add(new ContinuousVariable("X2"));
        DataSet ds = new BoxDataSet(new DoubleDataBox(n, nodes.size()), nodes);
        
        for (int i = 0; i < n; i++) {
            ds.setInt(i, 0, i % 2);
            ds.setDouble(i, 1, Math.random());
            ds.setDouble(i, 2, Math.random());
        }

        FfMl score = new FfMl(ds);
        score.setNumFeatures(10);
        
        double s1 = score.localScore(2, 0, 1);
        double s2 = score.localScore(2, 1, 0);
        
        assertEquals("Order of parents should not affect score", s1, s2, 1e-10);
    }
}
