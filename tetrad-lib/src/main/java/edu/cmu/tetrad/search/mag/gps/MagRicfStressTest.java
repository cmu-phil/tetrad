// src/test/java/edu/cmu/tetrad/search/mag/gps/MagRicfStressTest.java
package edu.cmu.tetrad.search.mag.gps;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.Ricf;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MagRicfStressTest {

    private final Random rnd = new Random(42);
    private final Algebra A = new Algebra();

    @Test
    public void randomMag_populationSigma_likelihoodFiniteAndNearTruth() {
        int p = 3 + rnd.nextInt(4);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < p; i++) names.add("V" + i);

        // nodes for covariance (can be different instances; names must match graph)
        List<Node> covNodes = new ArrayList<>(p);
        for (String nm : names) covNodes.add(new GraphNode(nm));

        // graph + nodes
        Graph g = new EdgeListGraph();
        List<GraphNode> graphNodes = names.stream().map(GraphNode::new).collect(Collectors.toList());
        graphNodes.forEach(g::addNode);

        // pick UG set
        Set<Integer> U = randomUGSubset(p);
        Set<Integer> C = complementSet(p, U);

        // Directed part on C (acyclic)
        DoubleMatrix2D B = eye(p); // B is P = I - B_std
        int[] order = randomPermutation(p);
        for (int iPos = 0; iPos < p; iPos++) {
            int i = order[iPos];
            if (U.contains(i)) continue;
            for (int jPos = 0; jPos < iPos; jPos++) {
                int j = order[jPos];
                if (U.contains(j)) continue;
                if (rnd.nextDouble() < 0.35) {
                    g.addEdge(Edges.directedEdge(graphNodes.get(j), graphNodes.get(i)));
                    double beta = (rnd.nextDouble() * 0.6) - 0.3;
                    B.set(i, j, -beta);
                }
            }
        }

        // Undirected within U via Λ (precision)
        int[] ugIdx = U.stream().sorted().mapToInt(x -> x).toArray();
        DoubleMatrix2D Lambda = (ugIdx.length > 0) ? sparseSPD(ugIdx.length, 0.5, 0.6) : new DenseDoubleMatrix2D(0,0);
        for (int a = 0; a < ugIdx.length; a++)
            for (int b = a + 1; b < ugIdx.length; b++)
                if (Math.abs(Lambda.get(a, b)) > 1e-12)
                    g.addEdge(Edges.undirectedEdge(graphNodes.get(ugIdx[a]), graphNodes.get(ugIdx[b])));

        // Ω on C (covariance), spouses only within C where Ω_ij != 0
        int[] comp = C.stream().sorted().mapToInt(x -> x).toArray();
        DoubleMatrix2D OmegaC = (comp.length > 0) ? sparseSPD(comp.length, 0.4, 0.8) : new DenseDoubleMatrix2D(0,0);
        DoubleMatrix2D Omega = new DenseDoubleMatrix2D(p, p);
        for (int a = 0; a < comp.length; a++)
            for (int b = 0; b < comp.length; b++)
                Omega.set(comp[a], comp[b], OmegaC.get(a, b));
        for (int i = 0; i < comp.length; i++)
            for (int j = i + 1; j < comp.length; j++)
                if (Math.abs(OmegaC.get(i, j)) > 1e-12)
                    g.addEdge(Edges.bidirectedEdge(graphNodes.get(comp[i]), graphNodes.get(comp[j])));

        // D = blockdiag(Ω_C, Λ^{-1}_U)
        DoubleMatrix2D D = new DenseDoubleMatrix2D(p, p);
        for (int a = 0; a < comp.length; a++)
            for (int b = 0; b < comp.length; b++)
                D.set(comp[a], comp[b], OmegaC.get(a, b));
        if (ugIdx.length > 0) {
            DoubleMatrix2D LambdaInv = A.inverse(Lambda);
            for (int a = 0; a < ugIdx.length; a++)
                for (int b = 0; b < ugIdx.length; b++)
                    D.set(ugIdx[a], ugIdx[b], LambdaInv.get(a, b));
        }

        // Σ = B^{-1} D B^{-T}
        DoubleMatrix2D Binv = A.inverse(B);
        DoubleMatrix2D Sigma = A.mult(A.mult(Binv, D), A.inverse(B.viewDice()));
        int n = 1000;
        ICovarianceMatrix cov = new CovarianceMatrix(covNodes, Sigma.toArray(), n);

        double llTrue = Ricf.logLikMAG(B, Omega, liftLambdaToFull(Lambda, ugIdx, p), ugIdx, cov);
        double llFit  = MagRicfStub.likelihood(g, cov, 0.0, 0);

        assertFalse("fitted ll is NaN", Double.isNaN(llFit));
        assertFalse("fitted ll is infinite", Double.isInfinite(llFit));
        assertEquals("fitted ll should match truth on population covariance", llTrue, llFit, 1e-6);
    }

    @Test
    public void permutationInvariance_noNaN() {
        int p = 3;
        List<String> names = Arrays.asList("A","B","C");
        List<Node> nodes = new ArrayList<>(p);
        for (String nm : names) nodes.add(new GraphNode(nm));

        Graph g = new EdgeListGraph();
        nodes.forEach(g::addNode);

        int a=0,b=1,c=2;
        g.addEdge(Edges.directedEdge(nodes.get(b), nodes.get(c)));
        g.addEdge(Edges.bidirectedEdge(nodes.get(b), nodes.get(c)));

        DoubleMatrix2D Bm = eye(p);
        Bm.set(c, b, -0.25);

        DoubleMatrix2D Lambda = new DenseDoubleMatrix2D(1,1); Lambda.set(0,0,1.0);
        DoubleMatrix2D Omega = new DenseDoubleMatrix2D(p,p);
        Omega.set(b,b,1.0); Omega.set(c,c,1.0); Omega.set(b,c,0.3); Omega.set(c,b,0.3);

        DoubleMatrix2D D = new DenseDoubleMatrix2D(p,p);
        D.set(a,a,1.0);
        D.set(b,b,1.0); D.set(c,c,1.0); D.set(b,c,0.3); D.set(c,b,0.3);

        DoubleMatrix2D Sigma = A.mult(A.mult(A.inverse(Bm), D), A.inverse(Bm.viewDice()));
        ICovarianceMatrix cov = new CovarianceMatrix(nodes, Sigma.toArray(), 500);

        double ll1 = MagRicfStub.likelihood(g, cov, 0.0, 0);
        assertTrue(Double.isFinite(ll1));

        // permute
        int[] perm = {1,0,2};
        List<Node> nodes2 = Arrays.asList(new GraphNode("B"), new GraphNode("A"), new GraphNode("C"));
        double[][] Sperm = permute(Sigma.toArray(), perm);
        ICovarianceMatrix covPerm = new CovarianceMatrix(nodes2, Sperm, 500);
        double ll2 = MagRicfStub.likelihood(g, covPerm, 0.0, 0);
        assertTrue(Double.isFinite(ll2));
    }

    // helpers (safe versions)
    private static Set<Integer> complementSet(int p, Set<Integer> u) {
        Set<Integer> c = new HashSet<>();
        for (int i = 0; i < p; i++) if (!u.contains(i)) c.add(i);
        return c;
    }
    private static int[] randomPermutation(int n) { int[] a = new int[n]; for (int i=0;i<n;i++) a[i]=i; Random r=new Random(123); for (int i=n-1;i>0;i--){int j=r.nextInt(i+1); int t=a[i]; a[i]=a[j]; a[j]=t;} return a; }
    private Set<Integer> randomUGSubset(int p) { Set<Integer> u = new HashSet<>(); for (int i=0;i<p;i++) if (rnd.nextDouble()<0.3) u.add(i); if (u.isEmpty() && p>0 && rnd.nextBoolean()) u.add(0); if (u.size()==p) u.remove(0); return u; }
    private DoubleMatrix2D sparseSPD(int n, double edgeProb, double diagShift) { DoubleMatrix2D L = new DenseDoubleMatrix2D(n,n); for (int i=0;i<n;i++){ L.set(i,i,1.0+0.2*rnd.nextDouble()); for (int j=0;j<i;j++){ if (rnd.nextDouble()<edgeProb){ double v=rnd.nextDouble()*0.6-0.3; L.set(i,j,v); }}} DoubleMatrix2D Q = A.mult(L.viewDice(), L); for (int i=0;i<n;i++) Q.set(i,i, Q.get(i,i)+diagShift); return Q; }
    private static DoubleMatrix2D eye(int p) { DoubleMatrix2D I = new DenseDoubleMatrix2D(p,p); for (int i=0;i<p;i++) I.set(i,i,1.0); return I; }
    private static DoubleMatrix2D liftLambdaToFull(DoubleMatrix2D Lambda, int[] ug, int p) { DoubleMatrix2D Lfull = new DenseDoubleMatrix2D(p,p); for (int a=0;a<ug.length;a++) for (int b=0;b<ug.length;b++) Lfull.set(ug[a], ug[b], Lambda.get(a,b)); return Lfull; }
    private static double[][] permute(double[][] S, int[] perm) { int p=perm.length; double[][] out=new double[p][p]; for (int i=0;i<p;i++) for (int j=0;j<p;j++) out[i][j]=S[perm[i]][perm[j]]; return out; }
}