package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.BossOrig;
import edu.cmu.tetrad.search.PermutationSearch;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TestAnneAnalysis3 {


    public static void main(String... args) {
        new TestAnneAnalysis3().run1();
    }

    private void run1() {
        double penalty = 2.0;

        for (int p : new int[]{5, 10, 20}) {
            for (int n : new int[]{50, 50, 100, 500, 1000, 5000, 10000, 50000}) {

                String nString;

                switch (n) {
                    case 50:
                        nString = "50";
                        break;
                    case 100:
                        nString = "100";
                        break;
                    case 500:
                        nString = "500";
                        break;
                    case 1000:
                        nString = "1K";
                        break;
                    case 5000:
                        nString = "5K";
                        break;
                    case 10000:
                        nString = "10K";
                        break;
                    case 50000:
                        nString = "50K";
                        break;
                    default:
                        throw new IllegalArgumentException("Don't have that sample size.");
                }

                try {
                    File filecor = new File("/Users/josephramsey/Downloads/sldisco/sldisco_cormats_b5K/" +
                            "cormats_p" + p + "_n" + nString + "_b5K.txt");
                    File adjout = new File("/Users/josephramsey/Downloads/sldisco/sldisco_adjout_b5K/" +
                            "adjmatsout_p" + p + "_n" + nString + "_b5K.txt");

                    filecor.mkdirs();
                    adjout.getParentFile().mkdirs();

                    BufferedReader incor = new BufferedReader(new FileReader(filecor));
                    incor.readLine();

                    PrintStream out = new PrintStream(adjout);

                    int d = 1;

                    for (int j = 0; j < p; j++) {
                        for (int i = 0; i < p; i++) {
                            out.print("X" + d++);

                            if (!(i == p - 1 && j == p - 1)) {
                                out.print(" ");
                            }
                        }
                    }

                    out.println();

                    for (int k = 0; k < 5000; k++) {
                        System.out.println("k = " + (k + 1));
                        String linecor = incor.readLine();

                        List<Node> vars = new ArrayList<>();
                        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("x" + (i + 1)));
                        CovarianceMatrix cov = getCov1(linecor, vars, n);

//                        edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(cov);
                        edu.cmu.tetrad.search.PoissonPriorScore score = new edu.cmu.tetrad.search.PoissonPriorScore(cov);
//                        score.setPenaltyDiscount(penalty);
                        score.setLambda(vars.size() / 2.);

//                        Grasp alg = new Grasp(score);
                        PermutationSearch alg = new PermutationSearch(new Boss(score));
                        Graph estCpdag = alg.search();

                        for (int j = 0; j < vars.size(); j++) {
                            for (int i = 0; i < vars.size(); i++) {
                                if (estCpdag.isAdjacentTo(vars.get(j), vars.get(i)) &&
                                        (Edges.isUndirectedEdge(estCpdag.getEdge(vars.get(j), vars.get(i)))
                                                || estCpdag.isParentOf(vars.get(j), vars.get(i)))) {
                                    out.print("1");
                                } else {
                                    out.print("0");
                                }

                                if (!(i == vars.size() - 1 && j == vars.size() - 1)) {
                                    out.print(" ");
                                }
                            }
                        }

                        out.println();
                    }

                    incor.close();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Test
    public void testSample() {
        List<Node> vars = new ArrayList<>();
        Node x1 = new ContinuousVariable("1");
        Node x2 = new ContinuousVariable("2");
        Node x3 = new ContinuousVariable("3");
        Node x4 = new ContinuousVariable("4");
        Node x5 = new ContinuousVariable("5");

        vars.add(x1);
        vars.add(x2);
        vars.add(x3);
        vars.add(x4);
        vars.add(x5);

        Graph estCpdag = new EdgeListGraph(vars);
        estCpdag.addDirectedEdge(x2, x1);
        estCpdag.addDirectedEdge(x3, x1);
        estCpdag.addDirectedEdge(x4, x1);
        estCpdag.addDirectedEdge(x5, x1);
        estCpdag.addUndirectedEdge(x2, x4);
        estCpdag.addUndirectedEdge(x3, x4);
        estCpdag.addUndirectedEdge(x2, x3);
        estCpdag.addUndirectedEdge(x2, x5);
        estCpdag.addUndirectedEdge(x3, x5);

        PrintStream out = System.out;

        System.out.println(estCpdag);

        for (int j = 0; j < vars.size(); j++) {
            for (int i = 0; i < vars.size(); i++) {
                if (estCpdag.isAdjacentTo(vars.get(j), vars.get(i)) &&
                        (Edges.isUndirectedEdge(estCpdag.getEdge(vars.get(j), vars.get(i)))
                                || estCpdag.isParentOf(vars.get(j), vars.get(i)))) {
                    out.print("1");
                } else {
                    out.print("0");
                }

                if (!(i == vars.size() - 1 && j == vars.size() - 1)) {
                    out.print(" ");
                }
            }
        }
    }

    @NotNull
    private CovarianceMatrix getCov1(String linecor, List<Node> vars, int n) {
        int v = vars.size();
        double[][] matrix = new double[v][v];
        String[] tokenscor = linecor.split(" ");
        int colcor = 2;

        for (int j = 0; j < v; j++) {
            for (int k = 0; k < v; k++) {
                matrix[j][k] = Double.parseDouble(tokenscor[colcor++]);
            }
        }

        return new CovarianceMatrix(vars, matrix, n);
    }

    @NotNull
    private Graph getTrueG1(String lineadj, List<Node> vars) {
        int v = vars.size();
        Graph trueG = new EdgeListGraph(vars);
        int[][] adjmat = new int[v][v];

        String[] tokensadj = lineadj.split(" ");
        int coladj = 2;

        for (int j = 0; j < v; j++) {
            for (int k = 0; k < v; k++) {
                adjmat[j][k] = Integer.parseInt(tokensadj[coladj++]);
            }
        }

        for (int j = 0; j < v; j++) {
            for (int k = 0; k < v; k++) {
                if (adjmat[j][k] == 1) {
                    trueG.addUndirectedEdge(vars.get(j), vars.get(k));
                }
            }
        }

        return trueG;
    }
}

