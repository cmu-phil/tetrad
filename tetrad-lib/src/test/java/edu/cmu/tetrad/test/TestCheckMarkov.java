package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceResult;
import edu.cmu.tetrad.search.Kci;
import edu.cmu.tetrad.search.SearchLogUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.util.Collections;
import java.util.List;

public class TestCheckMarkov {

    public static void main(String... args) {
        new TestCheckMarkov().test1();
    }

    public void test1() {
        double alpha = 0.05;
        int numIndep = 0;
        int total = 0;

        Graph dag = GraphUtils.randomDag(10, 0, 10, 100, 100,
                100, false);

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);

        Kci test = new Kci(data, alpha);
        test.setApproximate(true);
        test.setNumBootstraps(1000);
        test.setWidthMultiplier(1.0);
//
//        IndTestFisherZ test = new IndTestFisherZ(data, alpha);

        test.setVerbose(false);

        dag = GraphUtils.replaceNodes(dag, test.getVariables());

        System.out.println("DAG = " + dag);

        for (Node x : dag.getNodes()) {

            List<Node> desc = dag.getDescendants(Collections.singletonList(x));

            List<Node> nondesc = dag.getNodes();
            nondesc.removeAll(desc);

            List<Node> cond = dag.getParents(x);

            System.out.println("Node " + x + " parents = " + cond
                    + " non-descendants = " + nondesc);

            for (Node y : nondesc) {
                System.out.print("\t" + SearchLogUtils.independenceFact(x, y, cond));

                IndependenceResult result = test.checkIndependence(x, y, cond);

                if (result.independent()) {
                    numIndep++;
                }

                total++;

                System.out.print(" " + (result.independent() ? "Independent" : "Dependent"));
                System.out.print(" p = " + result.getPValue());
                System.out.println();
            }

        }

        System.out.println();
        System.out.println("Alpha = " + alpha + " % Dependent = " +
                 NumberFormatUtil.getInstance().getNumberFormat().format(
                         1d - numIndep / (double) total));
    }
}
