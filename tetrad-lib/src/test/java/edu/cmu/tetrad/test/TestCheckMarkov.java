package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Kci;
import edu.cmu.tetrad.search.SearchLogUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class TestCheckMarkov {

//    @Test
    public void test1() {
        Graph dag = GraphUtils.randomDag(10, 0, 10, 100, 100,
                100, false);

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);

        Kci test = new Kci(data, 0.1);
        test.setVerbose(false);
        test.setApproximate(true);
        test.setNumBootstraps(10000);
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

                boolean indep = test.isIndependent(x, y, cond);

                System.out.print(" " + (indep ? "Independent" : "Dependent"));
                System.out.print(" p = " + test.getPValue());
                System.out.println();
            }

        }
    }
}
