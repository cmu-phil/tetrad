package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.ResolveSepsets;
import edu.cmu.tetrad.search.utils.ResolveSepsets.Method;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the ResolveSepsets dispatch fix: every pooling method must dispatch (previously stouffer, mudholkergeorge,
 * average, majority, fdr, and random recursed until StackOverflowError), return a p-value in [0, 1] where one is
 * defined, judge a strong shared dependence dependent, and hold size near alpha on a true independence (checked
 * loosely on the p-value scale for the p-value methods).
 */
public class TestResolveSepsetsPooling {

    private static List<IndependenceTest> tests(boolean dependent, int k, int n, long seed) {
        Random rnd = new Random(seed);
        List<IndependenceTest> out = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"), new ContinuousVariable("Z"));
            DataSet d = new BoxDataSet(new DoubleDataBox(n, 3), vars);
            for (int t = 0; t < n; t++) {
                double x = rnd.nextGaussian();
                double y = (dependent ? 0.6 * x : 0) + rnd.nextGaussian();
                d.setDouble(t, 0, x);
                d.setDouble(t, 1, y);
                d.setDouble(t, 2, rnd.nextGaussian());
            }
            out.add(new IndTestFisherZ(d, 0.05));
        }
        return out;
    }

    @Test
    public void testAllMethodsDispatchAndDecide() throws Exception {
        List<IndependenceTest> dep = tests(true, 5, 200, 1);
        Node x = dep.getFirst().getVariable("X"), y = dep.getFirst().getVariable("Y");
        for (Method m : Method.values()) {
            if (m == Method.averagetest) continue; // requires chi-square component tests
            boolean indep = ResolveSepsets.isIndependentPooled(m, dep, x, y, new HashSet<>());
            double p = ResolveSepsets.getPValuePooled(m, dep, x, y, new HashSet<>());
            assertTrue(m + ": p-value out of range: " + p, p >= 0 && p <= 1);
            assertFalse(m + " should judge a strong shared dependence dependent", indep);
        }
    }

    @Test
    public void testPValueMethodsHoldSize() throws Exception {
        Method[] pValueMethods = {Method.fisher, Method.fisher2, Method.tippett, Method.stouffer,
                Method.mudholkergeorge, Method.worsleyfriston, Method.average};
        int reps = 200;
        for (Method m : pValueMethods) {
            int rejects = 0;
            for (int r = 0; r < reps; r++) {
                List<IndependenceTest> ind = tests(false, 5, 100, 100 + r);
                Node x = ind.getFirst().getVariable("X"), y = ind.getFirst().getVariable("Y");
                if (!ResolveSepsets.isIndependentPooled(m, ind, x, y, new HashSet<>())) rejects++;
            }
            double rate = rejects / (double) reps;
            assertTrue(m + ": rejection rate on a true independence should be near 0.05, got " + rate, rate <= 0.12);
        }
    }
}
