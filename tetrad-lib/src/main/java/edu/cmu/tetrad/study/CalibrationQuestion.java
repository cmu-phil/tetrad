package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

public class CalibrationQuestion {

    public static void main(String... args) {
        scenario5();
    }

    private static void scenario1() {
        int c = 0;
        int total = 0;
        int sampleSize = 1000;
        int numRuns = 1000;
        int numVars = 10;
        int avgDegree = 4;
        int numEdges = avgDegree * numVars / 2;
        double p = avgDegree / (double) (numVars - 1);

        System.out.println("p = " + p);

        for (int i = 0; i < numRuns; i++) {
            Node x = new ContinuousVariable("X");
            Node y = new ContinuousVariable("Y");
            Node z = new ContinuousVariable("Z");

            List<Node> nodes = new ArrayList<>();
            nodes.add(x);
            nodes.add(y);
            nodes.add(z);

            for (int n = 3; n <= numVars; n++) {
                nodes.add(new ContinuousVariable("V" + n));
            }

            Graph gt = GraphUtils.randomGraph(nodes, 0, numEdges, avgDegree, 100, 100, false);

            gt.removeEdge(x, y);
            gt.removeEdge(y, z);
            gt.removeEdge(x, z);


            gt.addDirectedEdge(x, y);
            gt.addDirectedEdge(y, z);

            if (RandomUtil.getInstance().nextDouble() <= p) {
                gt.addDirectedEdge(x, z);
            }

            SemPm pm = new SemPm(gt);
            SemIm im = new SemIm(pm);

            DataSet data = im.simulateData(sampleSize, false);

            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new edu.cmu.tetrad.search.SemBicScore(data));

            Graph ge = fges.search();
            ge = GraphUtils.replaceNodes(ge, gt.getNodes());

            System.out.println("gt = " + gt + " ge = " + ge);

            if (ge.isAdjacentTo(x, y) && ge.isAdjacentTo(y, z)//) {// && gt.isAdjacentTo(x, y) && gt.isAdjacentTo(y, z)) {
//                    && !ge.isAdjacentTo(x, z)
            ) {
                if (gt.isAdjacentTo(x, z)) {
                    c++;
                }

                total++;
            }

            System.out.println("c = " + c + " total = " + total + " q = " + (c / (double) total));

        }

        System.out.println("p = " + p + " q = " + (c / (double) total) + " numEdges = " + numEdges);
    }

    private static void scenario2() {
        int c = 0;
        int total = 0;
        int sampleSize = 1000;
        int numRuns = 500;
        int numVars = 80;
        int avgDegree = 4;
        int numEdges = avgDegree * numVars / 2;
        double p = avgDegree / (double) (numVars - 1);

        System.out.println("p = " + p);

        for (int i = 0; i < numRuns; i++) {
            Node x = new ContinuousVariable("X");
            Node y = new ContinuousVariable("Y");
            Node z = new ContinuousVariable("Z");

            List<Node> nodes = new ArrayList<>();
            nodes.add(x);
            nodes.add(y);
            nodes.add(z);

            for (int n = 3; n <= numVars; n++) {
                nodes.add(new ContinuousVariable("V" + n));
            }

            Graph gt = GraphUtils.randomGraph(nodes, 0, numEdges, 100, 100, 100, false);

//            if (gt.isAdjacentTo(x, y) && gt.isAdjacentTo(y, z)) {

            SemPm pm = new SemPm(gt);
            SemIm im = new SemIm(pm);

            DataSet data = im.simulateData(sampleSize, false);

            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new edu.cmu.tetrad.search.SemBicScore(data));
//            edu.cmu.tetrad.search.Pc fges = new edu.cmu.tetrad.search.Pc(new edu.cmu.tetrad.search.IndTestFisherZ(data, 0.1));

            Graph ge = fges.search();
            ge = GraphUtils.replaceNodes(ge, gt.getNodes());

            if (ge.isAdjacentTo(x, y) && ge.isAdjacentTo(y, z) && ge.isAdjacentTo(x, z)) {
                if (gt.isAdjacentTo(x, z)) {
                    c++;
                }

                total++;

                System.out.println("Run " + i + " p = " + p + " c = " + c + " total = " + total + " q = " + (c / (double) total));
            }

        }
//        }

        System.out.println("p = " + p + " q = " + (c / (double) total));
    }

    private static void scenario3() {
        int c = 0;
        int d = 0;
        int totalc = 0;
        int totald = 0;
        int sampleSize = 1000;
        int numRuns = 500;
        int numVars = 30;
        int avgDegree = 4;
        int numEdges = avgDegree * numVars / 2;
        double p = avgDegree / (double) (numVars - 1);

        System.out.println("p = " + p);

        for (int i = 0; i < numRuns; i++) {
            Node x = new ContinuousVariable("X");
            Node y = new ContinuousVariable("Y");
            Node z = new ContinuousVariable("Z");

            List<Node> nodes = new ArrayList<>();
            nodes.add(x);
            nodes.add(y);
            nodes.add(z);

            for (int n = 3; n <= numVars; n++) {
                nodes.add(new ContinuousVariable("V" + n));
            }

            Graph gt = GraphUtils.randomGraph(nodes, 0, numEdges, 100, 100, 100, false);

//            if (!gt.isAdjacentTo(x, y)) gt.addDirectedEdge(x, y);
//            if (!gt.isAdjacentTo(y, z)) gt.addDirectedEdge(y, z);
//            if (!gt.isAdjacentTo(x, z)) gt.addDirectedEdge(x, z);

//            gt.removeEdge(x, z);


//            if (gt.isAdjacentTo(x, y) && gt.isAdjacentTo(y, z)) {

            SemPm pm = new SemPm(gt);

            Parameters parameters = new Parameters();
            parameters.set("coefLow", 0.1);
            parameters.set("coefHigh", 0.5);

            SemIm im = new SemIm(pm, parameters);

            DataSet data = im.simulateData(sampleSize, false);

            edu.cmu.tetrad.search.Fges s = new edu.cmu.tetrad.search.Fges(new edu.cmu.tetrad.search.SemBicScore(data));
//                edu.cmu.tetrad.search.Pc s = new edu.cmu.tetrad.search.Pc(new edu.cmu.tetrad.search.IndTestFisherZ(data, 0.1));

            Graph ge = s.search();
            ge = GraphUtils.replaceNodes(ge, gt.getNodes());

//            if (!ge.isAdjacentTo(x, y)) ge.addDirectedEdge(x, y);
//            if (!ge.isAdjacentTo(y, z)) ge.addDirectedEdge(y, z);


            {
                if (ge.isAdjacentTo(x, y) && ge.isAdjacentTo(y, z)) {
                    c++;
                }

                totalc++;
            }

            if (gt.isAdjacentTo(x, z)) {
                if (ge.isAdjacentTo(x, y) && ge.isAdjacentTo(y, z)) {
                    d++;
                }

                totald++;
            }

//            System.out.println("Run " + i + " p = " + p + " c = " + c + " totalc = " + totalc + " d = " + d
//                    + " totald = " + totald + " qc = " + (c / (double) totalc) + " qd = " + (d / (double) totald));


            System.out.println("Run " + i + " P(XYe & YZe) = " + (c / (double) totalc) + " P(XYe & YZe | XZt) = " + (d / (double) totald));
        }
//        }

        System.out.println("p = " + p + " q = " + (c / (double) totalc));
    }

    private static void scenario4() {
        int c = 0;
        int total = 0;
        int sampleSize = 1000;
        int numRuns = 1;
        int numVars = 20;
        int avgDegree = 10;
        int numEdges = avgDegree * numVars / 2;
        double p = avgDegree / (double) (numVars - 1);

        System.out.println("p = " + p);

        for (int i = 0; i < numRuns; i++) {
            Node x = new ContinuousVariable("X");
            Node y = new ContinuousVariable("Y");
            Node z = new ContinuousVariable("Z");

            List<Node> nodes = new ArrayList<>();
            nodes.add(x);
            nodes.add(y);
            nodes.add(z);

            for (int n = 3; n <= numVars; n++) {
                nodes.add(new ContinuousVariable("V" + n));
            }

            Graph gt = GraphUtils.randomGraph(nodes, 0, numEdges, 100, 100, 100, false);

//            if (!gt.isAdjacentTo(x, y)) gt.addDirectedEdge(x, y);
//            if (!gt.isAdjacentTo(y, z)) gt.addDirectedEdge(y, z);
//            if (!gt.isAdjacentTo(x, z)) gt.addDirectedEdge(x, z);

//            gt.removeEdge(x, z);


//            if (gt.isAdjacentTo(x, y) && gt.isAdjacentTo(y, z)) {

            SemPm pm = new SemPm(gt);

            Parameters parameters = new Parameters();
            parameters.set("coefLow", 0.1);
            parameters.set("coefHigh", 0.5);

            SemIm im = new SemIm(pm);

            DataSet data = im.simulateData(sampleSize, false);

            edu.cmu.tetrad.search.Fges s = new edu.cmu.tetrad.search.Fges(new edu.cmu.tetrad.search.SemBicScore(data));
//                edu.cmu.tetrad.search.Pc s = new edu.cmu.tetrad.search.Pc(new edu.cmu.tetrad.search.IndTestFisherZ(data, 0.1));

            Graph ge = s.search();
            ge = GraphUtils.replaceNodes(ge, gt.getNodes());

//            if (!ge.isAdjacentTo(x, y)) ge.addDirectedEdge(x, y);
//            if (!ge.isAdjacentTo(y, z)) ge.addDirectedEdge(y, z);

            ChoiceGenerator gen = new ChoiceGenerator(numVars, 3);
            int[] choice;
            int t = 0;

            while ((choice = gen.next()) != null) {
                List<Node> v = GraphUtils.asList(choice, nodes);

                Node v1 = v.get(0);
                Node v2 = v.get(1);
                Node v3 = v.get(2);

                if (ge.isAdjacentTo(v1, v2) && ge.isAdjacentTo(v2, v3) && !ge.isAdjacentTo(v1, v3)) {
                    if (gt.isAdjacentTo(v1, v3)) {
                        c++;
                    }

                    total++;

                    System.out.println("Triple " + ++t + " p = " + p + " c = " + c + " total = " + total + " q = " + (c / (double) total));

                }

            }
        }

        System.out.println("p = " + p + " q = " + (c / (double) total));
    }

    // Make GT, simulate data, run FGES, yielding GE. Then find a nonredundant set of possible false negative shields ~XZ
    // touching all UTFP legs in GE and count for how many of these XZt is in GT.
    private static void scenario5() {
        int sampleSize = 1000;
        int[] numVars = new int[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        int[] avgDegree = new int[]{2, 4, 6, 8};

        double sumr = 0.0;
        int count = 0;

        NumberFormat nf = new DecimalFormat("0.00");

        for (int _numVars : numVars) {
            for (int _avgDegree : avgDegree) {

                double numEdges = (double) _avgDegree * _numVars / 2.;
                double p = _avgDegree / (double) (_numVars - 1);

//                System.out.println("\nProblem: numEdges = " + numEdges + " numVars = " + _numVars + " avgDegree = " + _avgDegree + " p = " + nf.format(p) + "\n");

                Graph gt = GraphUtils.randomGraph(_numVars, 0, (int) numEdges, 100, 100, 100, false);

                SemPm pm = new SemPm(gt);

                Parameters parameters = new Parameters();
                parameters.set("coefLow", 0.);
                parameters.set("coefHigh", 0.7);

                SemIm im = new SemIm(pm);

                DataSet data = im.simulateData(sampleSize, false);

//                edu.cmu.tetrad.search.Fges s = new edu.cmu.tetrad.search.Fges(new edu.cmu.tetrad.search.SemBicScore(data));

                PcAll s = new PcAll(new IndTestFisherZ(data, 0.001), null);
                s.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
                s.setConflictRule(PcAll.ConflictRule.PRIORITY);
                s.setFasType(PcAll.FasType.STABLE);
                s.setConcurrent(PcAll.Concurrent.NO);

                Graph ge = s.search();
                ge = GraphUtils.replaceNodes(ge, gt.getNodes());


                List<Node> nodes = ge.getNodes();

                ChoiceGenerator gen = new ChoiceGenerator(nodes.size(), 3);
                int[] choice;

                Set<Edge> L = new HashSet<>();
                Set<Edge> M = new HashSet<>();

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, nodes);

                    Node v1 = v.get(0);
                    Node v2 = v.get(1);
                    Node v3 = v.get(2);

                    count(ge, L, M, v1, v3, v2);
                    count(ge, L, M, v1, v2, v3);
                    count(ge, L, M, v2, v1, v3);
                }

                int P = 0;

                for (Edge e : ge.getEdges()) {
                    if (Edges.isDirectedEdge(e)) P++;
                }

                double alpha = L.size() / (double) M.size();
                double beta = M.size() / (double) P;
                double gamma = L.size() / (double) P;

                UtRStatistic utr = new UtRStatistic();
                double rhat = utr.getValue(gt, ge, data);

                if (!Double.isNaN(rhat)) {
                    sumr += rhat;
                    count++;
                }


//                System.out.println(ge);

                NumberFormat nf1 = new DecimalFormat("0.00");

//                System.out.println("\nSetting r := " + nf.format(r));
//                System.out.println("alpha = " + nf.format(alpha));
//                System.out.println("beta = " + nf.format(beta));
//                System.out.println("gamma = " + nf.format(gamma));
//                System.out.println("r = " + nf.format(r));
//                System.out.println("r2 = " + nf.format(r2));
//                System.out.println("Gamma Bound = 1 - gamma * r = " + nf.format((1 - gamma * r)));
//                System.out.println("Alpha Beta bound = 1 - alpha * beta * r = " + nf.format((1 - alpha * beta * r)));
//                System.out.println("Density bound = 1 - p  = " + nf.format((1 - p)));
//                System.out.println("Density Bound = 1 - 2 * r * p = " + nf.format((1 - 2 * r * p)));


                Statistic ahpc = new ArrowheadPrecision();
//                System.out.println("AHPC = " + nf.format(ahpc.getValue(gt, gc, data)));

                double _ahpc = ahpc.getValue(gt, getCommonGraph(gt, ge), data);

                System.out.print("L = " + L.size() + " P = " + P + " numVars = " + _numVars + " avgDegree = " + _avgDegree);

                System.out.println(
                        " density = " + nf1.format(p)
                                + " gamma = " + nf1.format(gamma)
                                + " r = " + nf1.format(rhat)
                                + " 1 - p = " + nf1.format(1. - p)
                                + " 1 - gamma * r = " + nf1.format((1 - gamma * rhat))
                                + "\t" + " AHPC = " + nf1.format(_ahpc));

                //                System.out.println("\n---\n");

//                UtRStatistic utr = new UtRStatistic();
//                double rhat = utr.getValue(gt, ge, data);
//
//                printDensityAndR(p, rhat);
            }
        }

        System.out.println("E(r) = " + sumr / (double) count + " sumr = " + sumr + " count = " + count);
    }

    private static Graph getCommonGraph(Graph gt, Graph ge1) {
        Graph g2 = new EdgeListGraph(ge1.getNodes());

        for (Edge e : ge1.getEdges()) {
            if (gt.isAdjacentTo(e.getNode1(), e.getNode2())) {
                g2.addEdge(e);
            }
        }
        return g2;
    }

    private static void count(Graph ge, Set<Edge> l, Set<Edge> m, Node v1, Node v2, Node v3) {

        if (ge.isAdjacentTo(v1, v2) && ge.isAdjacentTo(v2, v3) && !ge.isAdjacentTo(v1, v3)) {
            m.add(Edges.undirectedEdge(v1, v2));

            List<Node> adj = ge.getAdjacentNodes(v1);
            adj.retainAll(ge.getAdjacentNodes(v2));

            for (Node w : adj) {
                l.add(Edges.undirectedEdge(w, v1));
                l.add(Edges.undirectedEdge(w, v2));
            }
        }
    }

    private static void scenario6() {
        int c = 0;
        int total = 0;
        int sampleSize = 1000;
        int numRuns = 1;
        int numVars = 20;
        double avgDegree = 4;//2 * 25 / (double) (11);
        double numEdges = avgDegree * numVars / 2;
        double p = avgDegree / (double) (numVars - 1);

        System.out.println("p = " + p + " avgDegree = " + avgDegree);

        for (int i = 0; i < numRuns; i++) {
            Node x = new ContinuousVariable("X");
            Node y = new ContinuousVariable("Y");
            Node z = new ContinuousVariable("Z");

            List<Node> nodes = new ArrayList<>();
            nodes.add(x);
            nodes.add(y);
            nodes.add(z);

            for (int n = 3; n <= numVars; n++) {
                nodes.add(new ContinuousVariable("V" + n));
            }

            Graph ge = GraphUtils.randomGraph(nodes, 0, (int) numEdges, 100, 100, 100, false);
            Graph gt = new EdgeListGraph(ge.getNodes());

            for (int r = 0; r < nodes.size(); r++) {
                for (int s = r + 1; s < nodes.size(); s++) {
                    double p2 = p + RandomUtil.getInstance().nextUniform(-.1, .1);
                    if (p2 < 0) p2 = 0;
                    if (p2 > 1) p2 = 1;

                    if (RandomUtil.getInstance().nextDouble() < p2) {
                        gt.addUndirectedEdge(nodes.get(r), nodes.get(s));
                    }
                }
            }

            ChoiceGenerator gen = new ChoiceGenerator(numVars, 3);
            int[] choice;
            int t = 0;

            Set<Node> visited = new HashSet<>();

            while ((choice = gen.next()) != null) {
                List<Node> v = GraphUtils.asList(choice, nodes);

                Node v1 = v.get(0);
                Node v2 = v.get(1);
                Node v3 = v.get(2);

                if (ge.isAdjacentTo(v1, v2) && ge.isAdjacentTo(v2, v3) && !ge.isAdjacentTo(v1, v3)) {
                    if (visited.contains(v1) && visited.contains(v3)) continue;

                    if (gt.isAdjacentTo(v1, v3)) {
                        c++;
                    }

                    total++;

                    visited.add(v1);
                    visited.add(v3);

                    System.out.println("Triple " + ++t + " p = " + p + " c = " + c + " total = " + total + " Q = " + (c / (double) total));

                }
            }
        }

        System.out.println("p = " + p + " q = " + (c / (double) total));
    }
}
