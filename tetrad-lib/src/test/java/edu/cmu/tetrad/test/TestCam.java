package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Cam;
import edu.cmu.tetrad.search.score.CamAdditivePsplineBic;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class TestCam {

    @Test
    public void testScoringIntercept() {
        int n = 100;
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        DataSet data = new BoxDataSet(new edu.cmu.tetrad.data.VerticalDoubleDataBox(n, vars.size()), vars);
        Random rand = new Random(42);

        // X and Y are independent, but Y has a large non-zero mean
        double offset = 100.0;
        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, rand.nextGaussian());
            data.setDouble(i, 1, rand.nextGaussian() + offset);
        }

        CamAdditivePsplineBic scorer = new CamAdditivePsplineBic(data);
        double scoreNoParents = scorer.localScore(1); // Y with no parents
        double scoreWithX = scorer.localScore(1, 0); // Y with X as parent

        System.out.println("Score no parents: " + scoreNoParents);
        System.out.println("Score with X: " + scoreWithX);

        // Score with X should be higher (worse) than score with no parents because X adds complexity but no fit
        assertTrue("Score with X should be worse than no parents", scoreWithX > scoreNoParents);

        // Verify that the score is the same even if we add a huge offset to X
        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, data.getDouble(i, 0) + 1000.0);
        }
        double scoreWithXOffset = scorer.localScore(1, 0);
        assertEquals("Score should be invariant to parent offset", scoreWithX, scoreWithXOffset, 1e-6);
    }

    @Test
    public void testMultiParentBackfitting() {
        int n = 200;
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X1"));
        vars.add(new ContinuousVariable("X2"));
        vars.add(new ContinuousVariable("Y"));
        DataSet data = new BoxDataSet(new edu.cmu.tetrad.data.VerticalDoubleDataBox(n, vars.size()), vars);
        Random rand = new Random(42);

        // Y = sin(X1) + X2^2 + noise
        for (int i = 0; i < n; i++) {
            double x1 = rand.nextGaussian();
            double x2 = rand.nextGaussian();
            double y = Math.sin(x1) + x2 * x2 + 0.1 * rand.nextGaussian();
            data.setDouble(i, 0, x1);
            data.setDouble(i, 1, x2);
            data.setDouble(i, 2, y);
        }

        CamAdditivePsplineBic scorer = new CamAdditivePsplineBic(data);
        scorer.setMaxBackfitIters(50);
        double scoreBoth = scorer.localScore(2, 0, 1); // Y with X1, X2 as parents
        double scoreX1 = scorer.localScore(2, 0); // Y with X1 only
        double scoreX2 = scorer.localScore(2, 1); // Y with X2 only

        System.out.println("Score with both: " + scoreBoth);
        System.out.println("Score with X1: " + scoreX1);
        System.out.println("Score with X2: " + scoreX2);

        // Having both parents should be better (lower score) than having only one
        assertTrue("Score with both parents should be better than X1 only", scoreBoth < scoreX1);
        assertTrue("Score with both parents should be better than X2 only", scoreBoth < scoreX2);
    }

    @Test
    public void testCamLinear() throws InterruptedException {
        int n = 200;
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        vars.add(new ContinuousVariable("Z"));

        DataSet data = new BoxDataSet(new edu.cmu.tetrad.data.VerticalDoubleDataBox(n, vars.size()), vars);
        Random rand = new Random(42);

        // X -> Y -> Z
        for (int i = 0; i < n; i++) {
            double x = rand.nextGaussian();
            double y = 0.8 * x + 0.2 * rand.nextGaussian();
            double z = 0.7 * y + 0.3 * rand.nextGaussian();
            data.setDouble(i, 0, x);
            data.setDouble(i, 1, y);
            data.setDouble(i, 2, z);
        }

        Cam cam = new Cam(data);
        cam.setVerbose(true);
        Graph graph = cam.search();

        System.out.println(graph);
        assertNotNull(graph);
        assertTrue(graph.getNodes().size() == 3);
        // We expect at least some edges to be recovered correctly
        assertTrue(graph.getEdge(data.getVariable("X"), data.getVariable("Y")) != null);
        assertTrue(graph.getEdge(data.getVariable("Y"), data.getVariable("Z")) != null);
    }

    @Test
    public void testCamNonLinear() throws InterruptedException {
        int n = 300;
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));

        DataSet data = new BoxDataSet(new edu.cmu.tetrad.data.VerticalDoubleDataBox(n, vars.size()), vars);
        Random rand = new Random(42);

        // X -> Y, Y is a non-linear function of X
        for (int i = 0; i < n; i++) {
            double x = rand.nextGaussian();
            double y = x * x + 0.2 * rand.nextGaussian();
            data.setDouble(i, 0, x);
            data.setDouble(i, 1, y);
        }

        Cam cam = new Cam(data);
        cam.setVerbose(true);
        Graph graph = cam.search();

        System.out.println(graph);
        assertNotNull(graph);
        // Expect X -> Y
        assertTrue(graph.isDirectedFromTo(data.getVariable("X"), data.getVariable("Y")));
    }
}
