package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well44497b;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ViolationsTest {

    @Test
    public void testLinearCancellationFaithlessness() {

        // True DAG: Z1 -> X, Z1 -> Y, Z2 -> X, Z2 -> Y
        // X = Z1 + Z2 + eps_X
        // Y = Z1 - Z2 + eps_Y
        // By construction: Cov(X, Y) = 1*1*Var(Z1) + 1*(-1)*Var(Z2) = 0
        // So X _||_ Y marginally, but X _dep_ Y | Z1 and X _dep_ Y | Z2.
        // This causes PC and BOSS to return a wrong (impoverished) graph,
        // but the global Markov check on the true DAG should pass.

        int n = 10_000;
        double epsilon = 1e-2;

        Node z1 = new ContinuousVariable("Z1");
        Node z2 = new ContinuousVariable("Z2");
        Node x  = new ContinuousVariable("X");
        Node y  = new ContinuousVariable("Y");

        Graph truedag = new EdgeListGraph(List.of(z1, z2, x, y));
        truedag.addDirectedEdge(z1, x);
        truedag.addDirectedEdge(z1, y);
        truedag.addDirectedEdge(z2, x);
        truedag.addDirectedEdge(z2, y);

        // Simulate data
        double[] Z1 = new double[n];
        double[] Z2 = new double[n];
        double[] X  = new double[n];
        double[] Y  = new double[n];

        RandomGenerator rng = new Well44497b(42);
        NormalDistribution normal = new NormalDistribution(rng, 0, 1);

        for (int i = 0; i < n; i++) {
            Z1[i] = normal.sample();
            Z2[i] = normal.sample();
            X[i]  = Z1[i] + Z2[i]  + epsilon * normal.sample();
            Y[i]  = Z1[i] - Z2[i]  + epsilon * normal.sample();
        }

        // Build dataset
        DataSet dataset = new BoxDataSet(new DoubleDataBox(n, 4), List.of(z1, z2, x, y));
        for (int i = 0; i < n; i++) {
            dataset.setDouble(i, 0, Z1[i]);
            dataset.setDouble(i, 1, Z2[i]);
            dataset.setDouble(i, 2, X[i]);
            dataset.setDouble(i, 3, Y[i]);
        }

        double alpha = 0.01;
        IndependenceTest fisherZ = new IndTestFisherZ(dataset, alpha);

        // 1. Verify the key CI facts by hand:
        //    X _||_ Y marginally (the cancellation)
        //    X _dep_ Y | Z1
        //    X _dep_ Y | Z2
        IndependenceResult marginal = null;
        try {
            marginal = fisherZ.checkIndependence(
                    dataset.getVariable("X"), dataset.getVariable("Y"), Set.of());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertTrue("X should be independent of Y marginally due to cancellation",
                marginal.isIndependent());

        IndependenceResult givenZ1 = null;
        try {
            givenZ1 = fisherZ.checkIndependence(
                    dataset.getVariable("X"), dataset.getVariable("Y"),
                    Set.of(dataset.getVariable("Z1")));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertFalse("X should be dependent on Y given Z1",
                givenZ1.isIndependent());

        IndependenceResult givenZ2 = null;
        try {
            givenZ2 = fisherZ.checkIndependence(
                    dataset.getVariable("X"), dataset.getVariable("Y"),
                    Set.of(dataset.getVariable("Z2")));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertFalse("X should be dependent on Y given Z2",
                givenZ2.isIndependent());

        // 2. PC should fail -- it sees X _||_ Y and so omits the Z1->X, Z1->Y,
        //    Z2->X, Z2->Y structure, returning an impoverished graph.
        Graph pcGraph = null;
        try {
            pcGraph = new Pc(fisherZ).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("PC graph:");
        System.out.println(pcGraph);

        Graph trueCpdag = GraphTransforms.dagToCpdag(truedag);

        assertFalse("PC should fail to recover the true CPDAG due to faithfulness violation",
                pcGraph.equals(trueCpdag));

        // 3. The global Markov check on the true DAG should pass -- all implied
        //    independencies hold in the data, even though faithfulness does not.
        IndependenceTest fisherZForMarkov = new IndTestFisherZ(dataset, alpha);
        MarkovCheck markovCheck = new MarkovCheck(truedag, fisherZForMarkov,
                ConditioningSetType.GLOBAL_MARKOV);
        markovCheck.generateAllResults();

        double adPIndep = markovCheck.getAndersonDarlingP(true);
        double fracDepIndep = markovCheck.getFractionDependent(true);

        // AD p-value for implied independencies should be large (fail to reject uniformity)
        assertTrue("Markov check: AD p-value for implied independencies should be > 0.05",
                adPIndep > 0.05);

        // Fraction of dependent judgments should be near alpha
        assertEquals("Markov check: fraction dependent for implied independencies should be near alpha",
                alpha, fracDepIndep, 0.05);
    }
}
