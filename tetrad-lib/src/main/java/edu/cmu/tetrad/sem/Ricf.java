/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Mult;
import cern.jet.math.PlusMult;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.MatrixUtils;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Implements ICF as specified in Drton and Richardson (2003), Iterative Conditional Fitting for Gaussian Ancestral
 * Graph Models, using hints from previous implementations by Drton in the ggm package in R and by Silva in the Purify
 * class. The reason for reimplementing in this case is to take advantage of linear algebra optimizations in the COLT
 * library.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Ricf {

    /**
     * Represents the Ricf class. This class provides methods for calculating the Restricted Information Criterion
     * Fusion (RICF) for a given SemGraph.
     */
    public Ricf() {
    }

    // Compute constant-free log-likelihood for a fitted (B, Omega, Lambda) given S and n.
// ug: indices of the undirected-component nodes (consistent with your ricf()).
    public static double logLikMAG(
            DoubleMatrix2D B, DoubleMatrix2D Omega, DoubleMatrix2D Lambda,
            int[] ug, ICovarianceMatrix covMatrix) {

        final Algebra A = new Algebra();
        final int p = covMatrix.getDimension();
        final int n = covMatrix.getSampleSize();

        // IMPORTANT: In your Ricf, sigmahat = inv(B) * omega * inv(B^T).
        // That means 'B' here IS the P matrix (I - B_standard). So use P = B.
        DoubleMatrix2D P = B;  // NOT I - B

        // D^{-1} = blockdiag(Omega^{-1} on non-UG, Lambda on UG)
        DoubleMatrix2D Dinv = new DenseDoubleMatrix2D(p, p);
        int[] ugComp = complement(p, ug);

        if (ugComp.length > 0) {
            DoubleMatrix2D O = Omega.viewSelection(ugComp, ugComp);
            DoubleMatrix2D Oinv = A.inverse(O);
            Dinv.viewSelection(ugComp, ugComp).assign(Oinv);
        }
        if (ug.length > 0) {
            DoubleMatrix2D L = Lambda.viewSelection(ug, ug);
            Dinv.viewSelection(ug, ug).assign(L); // Lambda (not inverse)
        }

        // Precision: K = P^T * Dinv * P
        DoubleMatrix2D K = A.mult(A.transpose(P), A.mult(Dinv, P));

        // Use S from covMatrix
        DoubleMatrix2D S = new DenseDoubleMatrix2D(covMatrix.getMatrix().toArray());

        // trace(K S)
//        double trKS = A.trace(A.mult(K, S));

        // tr(K S) == sum_{ij} K_ij * S_ji == sum elementwise of (K .* S^T)
        double trKS = 0.0;
        DoubleMatrix2D ST = A.transpose(S);
        for (int r = 0; r < p; r++) {
            for (int c = 0; c < p; c++) trKS += K.get(r, c) * ST.get(r, c);
        }
// (Your current A.trace(A.mult(K,S)) is also fine; keep it if you prefer clarity.)

        // log|K| via Cholesky with SPD guard and tiny ridge fallback
        double logdetK;
        try {
            cern.colt.matrix.linalg.CholeskyDecomposition chol =
                    new cern.colt.matrix.linalg.CholeskyDecomposition(K);
            // Colt's Cholesky doesn't expose isSPD() in all versions; we defensively try again if diag <= 0
            DoubleMatrix2D L = chol.getL();
            double sumLogDiag = 0.0;
            for (int i = 0; i < p; i++) {
                double d = L.get(i, i);
                if (!(d > 0.0) || Double.isNaN(d)) throw new RuntimeException("non-SPD");
                sumLogDiag += Math.log(d);
            }
            logdetK = 2.0 * sumLogDiag;
        } catch (Exception e) {
            // Symmetrize + tiny ridge, then try again; final fallback to det()
            // Symmetrize: Ks = 0.5 * (K + K^T)
            DoubleMatrix2D Ks = K.copy();
            Ks.assign(K.viewDice(), PlusMult.plusMult(1.0)); // Ks = K + K^T
            Ks.assign(Mult.mult(0.5));                       // Ks = 0.5 * Ks
            for (int i = 0; i < p; i++) Ks.set(i, i, Ks.get(i, i) + 1e-8);
            try {
                cern.colt.matrix.linalg.CholeskyDecomposition chol2 =
                        new cern.colt.matrix.linalg.CholeskyDecomposition(Ks);
                DoubleMatrix2D L2 = chol2.getL();
                double sumLogDiag2 = 0.0;
                for (int i = 0; i < p; i++) sumLogDiag2 += Math.log(Math.max(1e-300, L2.get(i, i)));
                logdetK = 2.0 * sumLogDiag2;
            } catch (Exception e2) {
                logdetK = Math.log(Math.max(1e-300, Math.abs(A.det(Ks))));
            }
        }

        return -0.5 * n * (trKS - logdetK);
    }

    private static int[] complement(int p, int[] idx) {
        boolean[] mark = new boolean[p];
        if (idx != null) {
            for (int v : idx) if (0 <= v && v < p) mark[v] = true;
        }
        int count = 0;
        for (int i = 0; i < p; i++) if (!mark[i]) count++;
        int[] out = new int[count];
        int k = 0;
        for (int i = 0; i < p; i++) if (!mark[i]) out[k++] = i;
        return out;
    }

    private static DoubleMatrix2D symmetrize(DoubleMatrix2D M) {
        DoubleMatrix2D Ms = M.copy();
        Ms.assign(M.viewDice(), PlusMult.plusMult(1.0)); // Ms = M + M^T
        Ms.assign(Mult.mult(0.5));                        // Ms = 0.5*(M + M^T)
        return Ms;
    }

    private static void addRidgeInPlace(DoubleMatrix2D M, double eps) {
        for (int i = 0; i < M.rows(); i++) {
            M.set(i, i, M.get(i, i) + eps);
        }
    }

    /** Robust inverse for (near-)SPD: symmetrize + ridge then inverse. */
    private static DoubleMatrix2D invSPD(DoubleMatrix2D M, double eps, Algebra A) {
        DoubleMatrix2D Ms = symmetrize(M);
        addRidgeInPlace(Ms, eps);
        return A.inverse(Ms);
    }

    /** Generic safe inverse: tiny Tikhonov on diagonal. Use when matrix may not be SPD. */
    private static DoubleMatrix2D invSafe(DoubleMatrix2D M, double eps, Algebra A) {
        DoubleMatrix2D Ms = M.copy();
        addRidgeInPlace(Ms, eps);
        return A.inverse(Ms);
    }

    /**
     * Calculates the Restricted Information Criterion Fusion (RICF) for a given SemGraph.
     *
     * @param mag       The SemGraph object representing the graph to calculate RICF for.
     * @param covMatrix The ICovarianceMatrix object representing the covariance matrix.
     * @param tolerance The tolerance value for convergence.
     * @return The RicfResult object containing the results of the RICF calculation.
     */
    public RicfResult ricf(SemGraph mag, ICovarianceMatrix covMatrix, double tolerance) {
        mag.setShowErrorTerms(false);

        DoubleFactory2D factory = DoubleFactory2D.dense;
        Algebra algebra = new Algebra();

        DoubleMatrix2D S = new DenseDoubleMatrix2D(covMatrix.getMatrix().toArray());
        int p = covMatrix.getDimension();

        if (p == 1) {
            return new RicfResult(S, S, null, null, 1, Double.NaN, covMatrix);
        }

//        List<Node> nodes = new ArrayList<>();
//
//        for (String name : covMatrix.getVariableNames()) {
//            nodes.add(mag.getNode(name));
//        }

        // Build nodes list in cov order, but validate existence in MAG
        List<Node> nodes = new ArrayList<>(p);
        List<String> missing = new ArrayList<>();

        for (String name : covMatrix.getVariableNames()) {
            Node v = mag.getNode(name);
            if (v == null) missing.add(name);
            nodes.add(v);
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "RICF: Graph is missing variables from covariance matrix: " + missing);
        }

        DoubleMatrix2D omega = factory.diagonal(factory.diagonal(S));
        DoubleMatrix2D B = factory.identity(p);

        int[] ug = ugNodes(mag, nodes);
        int[] ugComp = complement(p, ug);

        if (ug.length > 0) {
            List<Node> _ugNodes = new LinkedList<>();
            for (int i : ug) _ugNodes.add(nodes.get(i));

            Graph ugGraph = mag.subgraph(_ugNodes);
            ICovarianceMatrix ugCov = covMatrix.getSubmatrix(ug);

            // FitConGraph returns Σ_hat (covariance). For UG block we actually need Λ⁻¹,
            // since Ricf keeps Omega as a *precision* on non-UG nodes. So invert here.
            DoubleMatrix2D lambdaInv = invSPD(fitConGraph(ugGraph, ugCov, p + 1, tolerance).shat, 1e-10, algebra);

            omega.viewSelection(ug, ug).assign(lambdaInv);
        }

        // Prepare lists of parents and spouses.
        int[][] pars = parentIndices(p, mag, nodes);
        int[][] spo = spouseIndices(p, mag, nodes);

        int i = 0;
        double _diff;

        while (true) {
            i++;

            DoubleMatrix2D omegaOld = omega.copy();
            DoubleMatrix2D bOld = B.copy();

            for (int _v = 0; _v < p; _v++) { // Need to exclude the UG part.

                // Exclude the UG part.
                if (Arrays.binarySearch(ug, _v) >= 0) {
                    continue;
                }

                int[] v = {_v};
                int[] vcomp = complement(p, v);
                int[] all = range(0, p - 1);
                int[] parv = pars[_v];
                int[] spov = spo[_v];

                DoubleMatrix2D bview = B.viewSelection(v, parv);

//                System.out.println("v = " + Arrays.toString(v));
//                System.out.println("parv = " + Arrays.toString(parv));
//                System.out.println("bview = " + bview);

//                System.out.println("B = " + B);

                if (spov.length == 0) {
                    if (parv.length != 0) {
                        if (i == 1) {
                            DoubleMatrix2D a1 = S.viewSelection(parv, parv);
                            DoubleMatrix2D a2 = S.viewSelection(v, parv);
                            DoubleMatrix2D a3 = invSPD(a1, 1e-10, algebra);//  algebra.inverse(a1);
                            DoubleMatrix2D a4 = algebra.mult(a2, a3);
                            a4.assign(Mult.mult(-1));
                            bview.assign(a4);

                            DoubleMatrix2D a7 = S.viewSelection(parv, v);
                            DoubleMatrix2D a9 = algebra.mult(bview, a7);
                            DoubleMatrix2D a8 = S.viewSelection(v, v);
                            DoubleMatrix2D a8b = omega.viewSelection(v, v);
                            a8b.assign(a8);
                            omega.viewSelection(v, v).assign(a9, PlusMult.plusMult(1));
                        }
                    }
                } else {
                    if (parv.length != 0) {
                        DoubleMatrix2D oInv = new DenseDoubleMatrix2D(p, p);
                        DoubleMatrix2D a2 = omega.viewSelection(vcomp, vcomp);
                        DoubleMatrix2D a3 = invSPD(a2, 1e-10, algebra);
                        ;//algebra.inverse(a2);
                        oInv.viewSelection(vcomp, vcomp).assign(a3);

                        DoubleMatrix2D Z = algebra.mult(oInv.viewSelection(spov, vcomp),
                                B.viewSelection(vcomp, all));

                        int lpa = parv.length;
                        int lspo = spov.length;

                        // Build XX
                        DoubleMatrix2D XX = new DenseDoubleMatrix2D(lpa + lspo, lpa + lspo);
                        int[] range1 = range(0, lpa - 1);
                        int[] range2 = range(lpa, lpa + lspo - 1);

                        // Upper left quadrant
                        XX.viewSelection(range1, range1).assign(S.viewSelection(parv, parv));

                        // Upper right quadrant
                        DoubleMatrix2D a11 = algebra.mult(S.viewSelection(parv, all),
                                algebra.transpose(Z));
                        XX.viewSelection(range1, range2).assign(a11);

                        // Lower left quadrant
                        DoubleMatrix2D a12 = XX.viewSelection(range2, range1);
                        DoubleMatrix2D a13 = algebra.transpose(XX.viewSelection(range1, range2));
                        a12.assign(a13);

                        // Lower right quadrant
                        DoubleMatrix2D a14 = XX.viewSelection(range2, range2);
                        DoubleMatrix2D a15 = algebra.mult(Z, S);
                        DoubleMatrix2D a16 = algebra.mult(a15, algebra.transpose(Z));
                        a14.assign(a16);

                        // Build XY
                        DoubleMatrix1D YX = new DenseDoubleMatrix1D(lpa + lspo);
                        DoubleMatrix1D a17 = YX.viewSelection(range1);
                        DoubleMatrix1D a18 = S.viewSelection(v, parv).viewRow(0);
                        a17.assign(a18);

                        DoubleMatrix1D a19 = YX.viewSelection(range2);
                        DoubleMatrix2D a20 = S.viewSelection(v, all);
                        DoubleMatrix2D mult = algebra.mult(a20, algebra.transpose(Z));
                        DoubleMatrix1D a21 = mult.viewRow(0);
                        a19.assign(a21);

                        // Temp
                        DoubleMatrix2D a22 = invSPD(XX, 1e-10, algebra);// algebra.inverse(XX);
                        DoubleMatrix1D temp = algebra.mult(algebra.transpose(a22), YX);

                        // Assign to b.
                        DoubleMatrix1D a23 = bview.viewRow(0);
                        DoubleMatrix1D a24 = temp.viewSelection(range1);

//                        System.out.println("B = " + B);

                        a23.assign(a24);
                        a23.assign(Mult.mult(-1));

                        // Assign to omega.
                        omega.viewSelection(v, spov).viewRow(0).assign(temp.viewSelection(range2));
                        omega.viewSelection(spov, v).viewColumn(0).assign(temp.viewSelection(range2));

                        // Variance.
                        double tempVar = S.get(_v, _v) - algebra.mult(temp, YX);
                        DoubleMatrix2D a27 = omega.viewSelection(v, spov);
                        DoubleMatrix2D a28 = oInv.viewSelection(spov, spov);
                        DoubleMatrix2D a29 = omega.viewSelection(spov, v).copy();
                        DoubleMatrix2D a30 = algebra.mult(a27, a28);
                        DoubleMatrix2D a31 = algebra.mult(a30, a29);
                        omega.viewSelection(v, v).assign(tempVar);
                        omega.viewSelection(v, v).assign(a31, PlusMult.plusMult(1));


                    } else {
                        DoubleMatrix2D oInv = new DenseDoubleMatrix2D(p, p);
                        DoubleMatrix2D a2 = omega.viewSelection(vcomp, vcomp);
                        DoubleMatrix2D a3 = invSPD(a2, 1e-10, algebra);// algebra.inverse(a2);
                        oInv.viewSelection(vcomp, vcomp).assign(a3);

                        DoubleMatrix2D a4 = oInv.viewSelection(spov, vcomp);
                        DoubleMatrix2D a5 = B.viewSelection(vcomp, all);
                        DoubleMatrix2D Z = algebra.mult(a4, a5);

                        // Build XX
                        DoubleMatrix2D XX = algebra.mult(algebra.mult(Z, S), Z.viewDice());

                        // Build XY
                        DoubleMatrix2D a20 = S.viewSelection(v, all);
                        DoubleMatrix2D doubleMatrix2D1 = Z.viewDice();
                        DoubleMatrix1D YX = algebra.mult(a20, doubleMatrix2D1).viewRow(0);

                        // Temp
                        DoubleMatrix2D a22 = invSPD(XX, 1e-10, algebra);
                        ;// algebra.inverse(XX);
                        DoubleMatrix1D a23 = algebra.mult(algebra.transpose(a22), YX);

                        // Assign to omega.
                        DoubleMatrix1D a24 = omega.viewSelection(v, spov).viewRow(0);
                        a24.assign(a23);
                        DoubleMatrix1D a25 = omega.viewSelection(spov, v).viewColumn(0);
                        a25.assign(a23);

                        // Variance.
                        double tempVar = S.get(_v, _v) - algebra.mult(a24, YX);

                        DoubleMatrix2D a27 = omega.viewSelection(v, spov);
                        DoubleMatrix2D a28 = oInv.viewSelection(spov, spov);
                        DoubleMatrix2D a29 = omega.viewSelection(spov, v).copy();
                        DoubleMatrix2D a30 = algebra.mult(a27, a28);
                        DoubleMatrix2D a31 = algebra.mult(a30, a29);
                        omega.set(_v, _v, tempVar + a31.get(0, 0));
                    }
                }
            }

            DoubleMatrix2D a32 = omega.copy();
            a32.assign(omegaOld, PlusMult.plusMult(-1));

            double diff1 = algebra.norm1(a32);
            DoubleMatrix2D a33 = B.copy();
            a33.assign(bOld, PlusMult.plusMult(-1));
            double diff2 = algebra.norm1(a33);

            double diff = diff1 + diff2;
            _diff = diff;

            if (diff < tolerance) break;
        }

        DoubleMatrix2D a34 = invSPD(B, 1e-10, algebra); // algebra.inverse(B);
        DoubleMatrix2D a35 = invSPD(B.viewDice(), 1e-10, algebra);// algebra.inverse(B.viewDice());

        // ensure symmetry + tiny positive diagonal
        DoubleMatrix2D sym = omega.copy();
        sym.assign(omega.viewDice(), PlusMult.plusMult(1));
        sym.assign(Mult.mult(0.5));
        for (int ii = 0; ii < sym.rows(); ii++) {
            double d = sym.get(ii, ii);
            if (!(d > 0.0)) sym.set(ii, ii, 1e-8);
        }
        omega = sym;

        DoubleMatrix2D sigmahat = algebra.mult(algebra.mult(a34, omega), a35);

        // Build Λ̂ as the UG *precision* block and zeros elsewhere.
        // Recall omega[ug,ug] stores Λ^{-1} (UG covariance). We invert it here to get Λ on UG,
        // and leave the complement zero to indicate “no Λ outside UG”.
        DoubleMatrix2D lambdahat = new DenseDoubleMatrix2D(p, p);
        if (ug.length > 0) {
            DoubleMatrix2D omegaUG = omega.viewSelection(ug, ug).copy(); // = Λ^{-1}
            DoubleMatrix2D LambdaUG = invSPD(omegaUG, 1e-10, algebra);    // -> Λ
            lambdahat.viewSelection(ug, ug).assign(LambdaUG);
        }

        // Omega_hat = omega with UG block zeroed (as you already intended)
        DoubleMatrix2D omegahat = omega.copy();
        omegahat.viewSelection(ug, ug).assign(factory.make(ug.length, ug.length, 0.0));

        // B_hat unchanged
        DoubleMatrix2D bhat = B.copy();

        return new RicfResult(sigmahat, lambdahat, bhat, omegahat, i, _diff, covMatrix);
    }

    /**
     * Same as above but takes a Graph instead of a SemGraph
     *
     * @param mag       a {@link Graph} object
     * @param covMatrix a {@link ICovarianceMatrix} object
     * @param tolerance a double
     * @return a {@link Ricf.RicfResult} object
     */
    public RicfResult ricf2(Graph mag, ICovarianceMatrix covMatrix, double tolerance) {
//        mag.setShowErrorTerms(false);

        DoubleFactory2D factory = DoubleFactory2D.dense;
        Algebra algebra = new Algebra();

        DoubleMatrix2D S = new DenseDoubleMatrix2D(covMatrix.getMatrix().toArray());
        int p = covMatrix.getDimension();

        if (p == 1) {
            return new RicfResult(S, S, null, null, 1, Double.NaN, covMatrix);
        }

//        List<Node> nodes = new ArrayList<>();

//        for (String name : covMatrix.getVariableNames()) {
//            nodes.add(mag.getNode(name));
//        }

        // Build nodes list in cov order, but validate existence in MAG
        List<Node> nodes = new ArrayList<>(p);
        List<String> missing = new ArrayList<>();

        for (String name : covMatrix.getVariableNames()) {
            Node v = mag.getNode(name);
            if (v == null) missing.add(name);
            nodes.add(v);
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "RICF: Graph is missing variables from covariance matrix: " + missing);
        }

        DoubleMatrix2D omega = factory.diagonal(factory.diagonal(S));
        DoubleMatrix2D B = factory.identity(p);

        int[] ug = ugNodes(mag, nodes);

        if (ug.length > 0) {
            List<Node> _ugNodes = new LinkedList<>();

            for (int i : ug) {
                _ugNodes.add(nodes.get(i));
            }

            Graph ugGraph = mag.subgraph(_ugNodes);
            ICovarianceMatrix ugCov = covMatrix.getSubmatrix(ug);

            // FitConGraph returns Σ_hat (covariance). For UG block we actually need Λ⁻¹,
            // since Ricf keeps Omega as a *precision* on non-UG nodes. So invert here.
            DoubleMatrix2D lambdaInv = fitConGraph(ugGraph, ugCov, p + 1, tolerance).shat;
            omega.viewSelection(ug, ug).assign(lambdaInv);
        }

        // Prepare lists of parents and spouses.
        int[][] pars = parentIndices(p, mag, nodes);
        int[][] spo = spouseIndices(p, mag, nodes);

        int i = 0;
        double _diff;

        while (true) {
            i++;

            DoubleMatrix2D omegaOld = omega.copy();
            DoubleMatrix2D bOld = B.copy();

            for (int _v = 0; _v < p; _v++) { // Need to exclude the UG part.

                // Exclude the UG part.
                if (Arrays.binarySearch(ug, _v) >= 0) {
                    continue;
                }

                int[] v = {_v};
                int[] vcomp = complement(p, v);
                int[] all = range(0, p - 1);
                int[] parv = pars[_v];
                int[] spov = spo[_v];

                DoubleMatrix2D a6 = B.viewSelection(v, parv);
                if (spov.length == 0) {
                    if (parv.length != 0) {
                        if (i == 1) {
                            DoubleMatrix2D a1 = S.viewSelection(parv, parv);
                            DoubleMatrix2D a2 = S.viewSelection(v, parv);
                            DoubleMatrix2D a3 = invSPD(a1, 1e-10, algebra); // algebra.inverse(a1);
                            DoubleMatrix2D a4 = algebra.mult(a2, a3);
                            a4.assign(Mult.mult(-1));
                            a6.assign(a4);

                            DoubleMatrix2D a7 = S.viewSelection(parv, v);
                            DoubleMatrix2D a9 = algebra.mult(a6, a7);
                            DoubleMatrix2D a8 = S.viewSelection(v, v);
                            DoubleMatrix2D a8b = omega.viewSelection(v, v);
                            a8b.assign(a8);
                            omega.viewSelection(v, v).assign(a9, PlusMult.plusMult(1));
                        }
                    }
                } else {
                    if (parv.length != 0) {
                        DoubleMatrix2D oInv = new DenseDoubleMatrix2D(p, p);
                        DoubleMatrix2D a2 = omega.viewSelection(vcomp, vcomp);
                        DoubleMatrix2D a3 = invSPD(a2, 1e-10, algebra); //algebra.inverse(a2);
                        oInv.viewSelection(vcomp, vcomp).assign(a3);

                        DoubleMatrix2D Z = algebra.mult(oInv.viewSelection(spov, vcomp),
                                B.viewSelection(vcomp, all));

                        int lpa = parv.length;
                        int lspo = spov.length;

                        // Build XX
                        DoubleMatrix2D XX = new DenseDoubleMatrix2D(lpa + lspo, lpa + lspo);
                        int[] range1 = range(0, lpa - 1);
                        int[] range2 = range(lpa, lpa + lspo - 1);

                        // Upper left quadrant
                        XX.viewSelection(range1, range1).assign(S.viewSelection(parv, parv));

                        // Upper right quadrant
                        DoubleMatrix2D a11 = algebra.mult(S.viewSelection(parv, all),
                                algebra.transpose(Z));
                        XX.viewSelection(range1, range2).assign(a11);

                        // Lower left quadrant
                        DoubleMatrix2D a12 = XX.viewSelection(range2, range1);
                        DoubleMatrix2D a13 = algebra.transpose(XX.viewSelection(range1, range2));
                        a12.assign(a13);

                        // Lower right quadrant
                        DoubleMatrix2D a14 = XX.viewSelection(range2, range2);
                        DoubleMatrix2D a15 = algebra.mult(Z, S);
                        DoubleMatrix2D a16 = algebra.mult(a15, algebra.transpose(Z));
                        a14.assign(a16);

                        // Build XY
                        DoubleMatrix1D YX = new DenseDoubleMatrix1D(lpa + lspo);
                        DoubleMatrix1D a17 = YX.viewSelection(range1);
                        DoubleMatrix1D a18 = S.viewSelection(v, parv).viewRow(0);
                        a17.assign(a18);

                        DoubleMatrix1D a19 = YX.viewSelection(range2);
                        DoubleMatrix2D a20 = S.viewSelection(v, all);
                        DoubleMatrix1D a21 = algebra.mult(a20, algebra.transpose(Z)).viewRow(0);
                        a19.assign(a21);

                        // Temp
                        DoubleMatrix2D a22 = invSPD(XX, 1e-10, algebra); // algebra.inverse(XX);
                        DoubleMatrix1D temp = algebra.mult(algebra.transpose(a22), YX);

                        // Assign to b.
                        DoubleMatrix1D a23 = a6.viewRow(0);
                        DoubleMatrix1D a24 = temp.viewSelection(range1);
                        a23.assign(a24);
                        a23.assign(Mult.mult(-1));

                        // Assign to omega.
                        omega.viewSelection(v, spov).viewRow(0).assign(temp.viewSelection(range2));
                        omega.viewSelection(spov, v).viewColumn(0).assign(temp.viewSelection(range2));

                        // Variance.
                        double tempVar = S.get(_v, _v) - algebra.mult(temp, YX);
                        DoubleMatrix2D a27 = omega.viewSelection(v, spov);
                        DoubleMatrix2D a28 = oInv.viewSelection(spov, spov);
                        DoubleMatrix2D a29 = omega.viewSelection(spov, v).copy();
                        DoubleMatrix2D a30 = algebra.mult(a27, a28);
                        DoubleMatrix2D a31 = algebra.mult(a30, a29);
                        omega.viewSelection(v, v).assign(tempVar);
                        omega.viewSelection(v, v).assign(a31, PlusMult.plusMult(1));
                    } else {
                        DoubleMatrix2D oInv = new DenseDoubleMatrix2D(p, p);
                        DoubleMatrix2D a2 = omega.viewSelection(vcomp, vcomp);
                        DoubleMatrix2D a3 = invSPD(a2, 1e-10, algebra); // algebra.inverse(a2);
                        oInv.viewSelection(vcomp, vcomp).assign(a3);

                        DoubleMatrix2D a4 = oInv.viewSelection(spov, vcomp);
                        DoubleMatrix2D a5 = B.viewSelection(vcomp, all);
                        DoubleMatrix2D Z = algebra.mult(a4, a5);

                        // Build XX
                        DoubleMatrix2D XX = algebra.mult(algebra.mult(Z, S), Z.viewDice());

                        // Build XY
                        DoubleMatrix2D a20 = S.viewSelection(v, all);
                        DoubleMatrix1D YX = algebra.mult(a20, Z.viewDice()).viewRow(0);

                        // Temp
                        DoubleMatrix2D a22 = invSPD(XX, 1e-10, algebra); // algebra.inverse(XX);
                        DoubleMatrix1D a23 = algebra.mult(algebra.transpose(a22), YX);

                        // Assign to omega.
                        DoubleMatrix1D a24 = omega.viewSelection(v, spov).viewRow(0);

                        a24.assign(a23);
                        DoubleMatrix1D a25 = omega.viewSelection(spov, v).viewColumn(0);
                        a25.assign(a23);

                        // Variance.
                        double tempVar = S.get(_v, _v) - algebra.mult(a24, YX);
                        DoubleMatrix2D a27 = omega.viewSelection(v, spov);
                        DoubleMatrix2D a28 = oInv.viewSelection(spov, spov);
                        DoubleMatrix2D a29 = omega.viewSelection(spov, v).copy();
                        DoubleMatrix2D a30 = algebra.mult(a27, a28);
                        DoubleMatrix2D a31 = algebra.mult(a30, a29);
                        omega.set(_v, _v, tempVar + a31.get(0, 0));
                    }
                }
            }

            DoubleMatrix2D a32 = omega.copy();
            a32.assign(omegaOld, PlusMult.plusMult(-1));
            double diff1 = algebra.norm1(a32);

            DoubleMatrix2D a33 = B.copy();
            a33.assign(bOld, PlusMult.plusMult(-1));
            double diff2 = algebra.norm1(a33);

            double diff = diff1 + diff2;
            _diff = diff;

            if (diff < tolerance) break;
        }

        DoubleMatrix2D a34 = invSPD(B, 1e-10, algebra); // algebra.inverse(B);
        DoubleMatrix2D a35 = invSPD(B.viewDice(), 1e-10, algebra); // algebra.inverse(B.viewDice());

        DoubleMatrix2D sym = omega.copy();
        sym.assign(omega.viewDice(), PlusMult.plusMult(1));
        sym.assign(Mult.mult(0.5));
        for (int ii = 0; ii < sym.rows(); ii++) {
            double d = sym.get(ii, ii);
            if (!(d > 0.0)) sym.set(ii, ii, 1e-8);
        }
        omega = sym;

        DoubleMatrix2D sigmahat = algebra.mult(algebra.mult(a34, omega), a35);

        // Build Lambda_hat = precision on UG; zeros elsewhere
        DoubleMatrix2D lambdahat = new DenseDoubleMatrix2D(p, p);
        if (ug.length > 0) {
            // omega[ug,ug] currently stores Lambda^{-1} (UG covariance)
            DoubleMatrix2D omegaUG = omega.viewSelection(ug, ug).copy();
            // robust inverse to get precision
            DoubleMatrix2D LambdaUG = invSPD(omegaUG, 1e-10, algebra);
            lambdahat.viewSelection(ug, ug).assign(LambdaUG);
        }

        // Omega_hat = omega with UG block zeroed (as you already intended)
        DoubleMatrix2D omegahat = omega.copy();
        omegahat.viewSelection(ug, ug).assign(factory.make(ug.length, ug.length, 0.0));

        // B_hat unchanged
        DoubleMatrix2D bhat = B.copy();

        return new RicfResult(sigmahat, lambdahat, bhat, omegahat, i, _diff, covMatrix);
    }

    // Returns the indices in [0, p) that are NOT listed in `a`.
// Ignores duplicates and any out-of-range entries in `a`.
//    private static int[] complement(int p, int[] a) {
//        if (p < 0) throw new IllegalArgumentException("p must be >= 0");
//        boolean[] excluded = new boolean[p];
//
//        if (a != null) {
//            for (int v : a) {
//                if (v >= 0 && v < p) {
//                    excluded[v] = true; // duplicates harmless
//                }
//            }
//        }
//
//        int count = 0;
//        for (int i = 0; i < p; i++) {
//            if (!excluded[i]) count++;
//        }
//
//        int[] res = new int[count];
//        int k = 0;
//        for (int i = 0; i < p; i++) {
//            if (!excluded[i]) res[k++] = i;
//        }
//        return res;
//    }

    /**
     * <p>cliques.</p>
     *
     * @param graph a {@link Graph} object
     * @return an enumeration of the cliques of the given graph considered as undirected.
     */
    public List<List<Node>> cliques(Graph graph) {
        List<Node> nodes = graph.getNodes();
        List<List<Node>> cliques = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            List<Node> adj = graph.getAdjacentNodes(nodes.get(i));

            SortedSet<Integer> L1 = new TreeSet<>();
            L1.add(i);

            SortedSet<Integer> L2 = new TreeSet<>();

            for (Node _adj : adj) {
                L2.add(nodes.indexOf(_adj));
            }

            int moved = -1;

            do {
                addNodesToRight(L1, L2, graph, nodes, moved);

                if (isMaximal(L1, L2, graph, nodes)) {
                    record(L1, cliques, nodes);
                }

                moved = moveLastBack(L1, L2);

            } while (moved != -1);
        }

        return cliques;
    }

    /**
     * Fits a concentration graph. Coding algorithm #2 only.
     */
    private FitConGraphResult fitConGraph(Graph graph, ICovarianceMatrix cov, int n, double tol) {
        DoubleFactory2D factory = DoubleFactory2D.dense;
        Algebra algebra = new Algebra();

        List<Node> nodes = graph.getNodes();
        String[] nodeNames = new String[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (!cov.getVariableNames().contains(node.getName())) {
                throw new IllegalArgumentException("Node in graph not in cov matrix: " + node);
            }

            nodeNames[i] = node.getName();
        }

        DoubleMatrix2D S = new DenseDoubleMatrix2D(cov.getSubmatrix(nodeNames).getMatrix().toArray());
        graph = graph.subgraph(nodes);

        List<List<Node>> cli = cliques(graph);

        int nc = cli.size();

        if (nc == 1) {
            return new FitConGraphResult(S, 0, 0, 1);
        }

        int k = S.rows();
        int it = 0;

        // Only coding alg #2 here.
        DoubleMatrix2D diagonal = factory.diagonal(factory.diagonal(S));
        DoubleMatrix2D K = invSPD(diagonal, 1e-10, algebra); // algebra.inverse(diagonal);

        int[] all = range(0, k - 1);

        while (true) {
            DoubleMatrix2D KOld = K.copy();
            it++;

            for (List<Node> aCli : cli) {
                int[] a = asIndices(aCli, nodes);
                int[] b = complement(all, a);
                DoubleMatrix2D a1 = S.viewSelection(a, a);
                DoubleMatrix2D a2 = invSPD(a1, 1e-10, algebra);;//algebra.inverse(a1);
                DoubleMatrix2D a3 = K.viewSelection(a, b);
                DoubleMatrix2D a4 = K.viewSelection(b, b);
                DoubleMatrix2D a5 = invSPD(a4, 1e-10, algebra);//algebra.inverse(a4);
                DoubleMatrix2D a6 = K.viewSelection(b, a).copy();
                DoubleMatrix2D a7 = algebra.mult(a3, a5);
                DoubleMatrix2D a8 = algebra.mult(a7, a6);
                a2.assign(a8, PlusMult.plusMult(1));
                DoubleMatrix2D a9 = K.viewSelection(a, a);
                a9.assign(a2);
            }

            DoubleMatrix2D a32 = K.copy();
            a32.assign(KOld, PlusMult.plusMult(-1));
            double diff = algebra.norm1(a32);

            if (diff < tol) break;
        }

        DoubleMatrix2D V = invSPD(K, 1e-10, algebra);// algebra.inverse(K);

        int numNodes = graph.getNumNodes();
        int df = numNodes * (numNodes - 1) / 2 - graph.getNumEdges();
//        DoubleMatrix2D inverse = algebra.inverse(V);
//        double dev = lik(invSPD(inverse, 1e-10, algebra), S, n, k);
        double dev = lik(invSPD(algebra.inverse(V), 1e-10, algebra), S, n, k);

        invSPD(diagonal, 1e-10, algebra);

        return new FitConGraphResult(V, dev, df, it);
    }

    private int[] asIndices(List<Node> clique, List<Node> nodes) {
        int[] a = new int[clique.size()];

        for (int j = 0; j < clique.size(); j++) {
            a[j] = nodes.indexOf(clique.get(j));
        }

        return a;
    }

    private double lik(DoubleMatrix2D K, DoubleMatrix2D S, int n, int k) {
        Algebra algebra = new Algebra();
        DoubleMatrix2D SK = algebra.mult(S, K);
        return (algebra.trace(SK) - FastMath.log(algebra.det(SK)) - k) * n;
    }

    private int[] range(int from, int to) {
        if (from < 0 || to < 0 || from > to) {
            throw new IllegalArgumentException();
        }

        int[] range = new int[to - from + 1];

        for (int k = from; k <= to; k++) {
            range[k - from] = k;
        }

        return range;
    }

    private int[] complement(int[] all, int[] remove) {
        Arrays.sort(remove);
        int[] vcomp = new int[all.length - remove.length];

        int k = -1;

        for (int j = 0; j < all.length; j++) {
            if (Arrays.binarySearch(remove, j) >= 0) continue;
            vcomp[++k] = j;
        }

        return vcomp;
    }

    private int[] ugNodes(Graph mag, List<Node> nodes) {
        List<Node> ugNodes = new LinkedList<>();

        for (Node node : nodes) {
            if (mag.getNodesInTo(node, Endpoint.ARROW).isEmpty()) {
                ugNodes.add(node);
            }
        }

        int[] indices = new int[ugNodes.size()];

        for (int j = 0; j < ugNodes.size(); j++) {
            indices[j] = nodes.indexOf(ugNodes.get(j));
        }

        return indices;
    }

    private int[][] parentIndices(int p, Graph mag, List<Node> nodes) {
        int[][] pars = new int[p][];

        for (int i = 0; i < p; i++) {
            List<Node> parents = new ArrayList<>(mag.getParents(nodes.get(i)));
            int[] indices = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                indices[j] = nodes.indexOf(parents.get(j));
            }

            pars[i] = indices;
        }

        return pars;
    }

    private int[][] spouseIndices(int p, Graph mag, List<Node> nodes) {
        int[][] spo = new int[p][];

        for (int i = 0; i < p; i++) {
            List<Node> list1 = mag.getNodesOutTo(nodes.get(i), Endpoint.ARROW);
            List<Node> list2 = mag.getNodesInTo(nodes.get(i), Endpoint.ARROW);
            list1.retainAll(list2);

            int[] indices = new int[list1.size()];

            for (int j = 0; j < list1.size(); j++) {
                indices[j] = nodes.indexOf(list1.get(j));
            }

            spo[i] = indices;
        }

        return spo;
    }

    private int moveLastBack(SortedSet<Integer> L1, SortedSet<Integer> L2) {
        if (L1.size() == 1) {
            return -1;
        }

        int moved = L1.last();
        L1.remove(moved);
        L2.add(moved);

        return moved;
    }

    /**
     * If L2 is nonempty, moves nodes from L2 to L1 that can be added to L1. Nodes less than max(L1) are not
     * considered--i.e. L1 is being extended to the right. Nodes not greater than the most recently moved node are not
     * considered--this is a mechanism for
     */
    private void addNodesToRight(SortedSet<Integer> L1, SortedSet<Integer> L2,
                                 Graph graph, List<Node> nodes, int moved) {
        for (int j : new TreeSet<>(L2)) {
            if (j > max(L1) && j > moved && addable(j, L1, graph, nodes)) {
                L1.add(j);
                L2.remove(j);
            }
        }
    }

    private void record(SortedSet<Integer> L1, List<List<Node>> cliques,
                        List<Node> nodes) {
        List<Node> clique = new LinkedList<>();

        for (int i : L1) {
            clique.add(nodes.get(i));
        }

        cliques.add(clique);
    }

    private boolean isMaximal(SortedSet<Integer> L1, SortedSet<Integer> L2, Graph graph, List<Node> nodes) {
        for (int j : L2) {
            if (addable(j, L1, graph, nodes)) {
                return false;
            }
        }

        return true;
    }

    private int max(SortedSet<Integer> L1) {
        int max = Integer.MIN_VALUE;

        for (int i : L1) {
            if (i > max) {
                max = i;
            }
        }

        return max;
    }

    /**
     * Determines if a node j can be added to a set L1 while maintaining adjacency with all nodes in L1.
     *
     * @param j     The index of the node to be added.
     * @param L1    The set of indices representing the current set of nodes.
     * @param graph The graph containing the nodes.
     * @param nodes The list of nodes.
     * @return Returns true if node j can be added to L1 while maintaining adjacency with all nodes in L1, false
     * otherwise.
     */
    private boolean addable(int j, SortedSet<Integer> L1, Graph graph, List<Node> nodes) {
        for (int k : L1) {
            if (!graph.isAdjacentTo(nodes.get(j), nodes.get(k))) {
                return false;
            }
        }

        return true;
    }

    /**
     * RICF result.
     */
    public static class RicfResult {

        /**
         * The covariance matrix.
         */
        private final ICovarianceMatrix covMatrix;

        /**
         * The shat matrix.
         */
        private final DoubleMatrix2D shat;

        /**
         * The lhat matrix.
         */
        private final DoubleMatrix2D lhat;

        /**
         * The bhat matrix.
         */
        private final DoubleMatrix2D bhat;

        /**
         * The ohat matrix.
         */
        private final DoubleMatrix2D ohat;

        /**
         * The number of iterations.
         */
        private final int iterations;

        /**
         * The diff.
         */
        private final double diff;

        /**
         * The result.
         *
         * @param shat       The shat matrix.
         * @param lhat       The laht matrix.
         * @param bhat       The bhat matrix.
         * @param ohat       The ohat matrix.
         * @param iterations The number of iterations.
         * @param diff       The diff.
         * @param covMatrix  The covariance matrix.
         */
        public RicfResult(DoubleMatrix2D shat, DoubleMatrix2D lhat, DoubleMatrix2D bhat,
                          DoubleMatrix2D ohat, int iterations, double diff, ICovarianceMatrix covMatrix) {
            this.shat = shat;
            this.lhat = lhat;
            this.bhat = bhat;
            this.ohat = ohat;
            this.iterations = iterations;
            this.diff = diff;
            this.covMatrix = covMatrix;
        }

        /**
         * Returns a string representation of the RicfResult object.
         *
         * @return The string representation of the RicfResult object.
         */
        public String toString() {

            return "\nSigma hat\n" +
                   MatrixUtils.toStringSquare(getShat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nLambda hat\n" +
                   MatrixUtils.toStringSquare(getLhat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nBeta hat\n" +
                   MatrixUtils.toStringSquare(getBhat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nOmega hat\n" +
                   MatrixUtils.toStringSquare(getOhat().toArray(), new DecimalFormat("0.0000"), this.covMatrix.getVariableNames()) +
                   "\n\nIterations\n" +
                   getIterations() +
                   "\n\ndiff = " + this.diff;
        }

        /**
         * Retrieves the shat matrix.
         *
         * @return The shat matrix.
         */
        public DoubleMatrix2D getShat() {
            return this.shat;
        }

        /**
         * Returns the "lhat" matrix.
         *
         * @return The "lhat" matrix.
         */
        public DoubleMatrix2D getLhat() {
            return this.lhat;
        }

        /**
         * Returns the bhat matrix.
         *
         * @return The bhat matrix.
         */
        public DoubleMatrix2D getBhat() {
            return this.bhat;
        }

        /**
         * Returns the ohat matrix.
         *
         * @return The ohat matrix.
         */
        public DoubleMatrix2D getOhat() {
            return this.ohat;
        }

        /**
         * Returns the number of iterations.
         *
         * @return The number of iterations.
         */
        public int getIterations() {
            return this.iterations;
        }
    }

    /**
     * The fit con graph result.
     */
    public static class FitConGraphResult {

        /**
         * The shat matrix
         */
        private final DoubleMatrix2D shat;

        /**
         * The deviance
         */
        double deviance;

        /**
         * The degrees of freedom.
         */
        int df;

        /**
         * The number of iterations.
         */
        int iterations;

        /**
         * The result.
         *
         * @param shat       The shat matrix.
         * @param deviance   The deviance.
         * @param df         The degrees of freedom.
         * @param iterations The iterations.
         */
        public FitConGraphResult(DoubleMatrix2D shat, double deviance,
                                 int df, int iterations) {
            this.shat = shat;
            this.deviance = deviance;
            this.df = df;
            this.iterations = iterations;
        }

        /**
         * Returns a string representation of the FitConGraphResult object. The string includes the Sigma hat matrix,
         * deviance value, degrees of freedom, and number of iterations.
         *
         * @return a string representation of the FitConGraphResult object.
         */
        public String toString() {

            return "\nSigma hat\n" +
                   this.shat +
                   "\nDeviance\n" +
                   this.deviance +
                   "\nDf\n" +
                   this.df +
                   "\nIterations\n" +
                   this.iterations;
        }
    }
}




