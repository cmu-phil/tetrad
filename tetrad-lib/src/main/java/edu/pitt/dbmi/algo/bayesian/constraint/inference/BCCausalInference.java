/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.algo.bayesian.constraint.inference;

import java.util.Arrays;

/**
 * This is a thread-safe version of BCInference.
 *
 * Jan 30, 2019 5:42:50 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class BCCausalInference {

    public enum OP {
        DEPENDENT,
        INDEPENDENT
    }

    private static final double PESS_VALUE = 1.0;

    private final int numberOfNodes;
    private final int numberOfCases;
    private final int maximumNodes;
    private final int maximumCases;
    private final int maximumValues; // maximum value per node;
    private final int maximumParents;
    private final int maximumCells;
    private final double[] logFactorial;

    private final int scoreFn;  // score function

    private final int[] nodeDimension;
    private final int[][] cases;

    public BCCausalInference(int[] nodeDimension, int[][] cases) {
        this.nodeDimension = nodeDimension;
        this.cases = cases;
        this.numberOfNodes = nodeDimension.length - 2;
        this.numberOfCases = cases.length - 1;
        this.maximumNodes = numberOfNodes;
        this.maximumCases = cases.length - 1;;
        this.maximumValues = Arrays.stream(nodeDimension).max().getAsInt();
        this.maximumParents = maximumNodes - 2;
        this.maximumCells = maximumParents * maximumValues * maximumValues * maximumCases;
        this.logFactorial = computeLogFactorial(maximumCases, maximumValues);
        this.scoreFn = 1;  // right now we just fixed it to 1
    }

    /**
     * This function takes a constraint, which has a value of either
     * OP.dependent or OP.independent, of the form "X independent Y given Z" or
     * "X dependent Y given Z" and returns a probability for that constraint
     * given the data in cases and assumed prior probability for that constraint
     * given the data in cases and assumed prior probabilities. Currently, it
     * assumes uniform parameter priors and a structure prior of 0.5. A
     * structure prior of 0.5 means taht a priori we have that P(X independent Y
     * given Z) = P(X dependent Y given Z) = 0.5.
     * <p/>
     * Z[0] is the length of the set represented by array Z. For an example,
     * Z[0] = 1 represents the set Z of size 1. Z[0] = 0 represents an empty
     * set.
     * <p/>
     * Set Z with two elements: Z = {3, 2} Z[0] = 2 // set Z has two elements
     * (length of 2) Z[1] = 3 // first element Z[2] = 2 // second element.
     * <p/>
     * Empty set: Z = {} Z[0] = 0
     *
     * @param constraint has the value OP.independent or OP.dependent
     * @param x node x
     * @param y node y
     * @param z set of nodes
     * @return P(x dependent y given z | data) or P(x independent y given z |
     * data)
     */
    public double probConstraint(OP constraint, int x, int y, int[] z) {
        double probability = 0;

        CountsTracker countsTracker = createCountsTracker(z);

        int[][] parents = countsTracker.parents;
        int[] countsTree = countsTracker.countsTree;
        int[] counts = countsTracker.counts;
        double[][] scores = countsTracker.scores;
        int[] xyProducts = countsTracker.xyProducts;

        int n = z[0];
        parents[x][0] = n;
        for (int i = 1; i <= n; i++) {
            parents[x][i] = z[i];
        }
        double lnMarginalLikelihood_X = scoreNode(x, 1, countsTracker);  // the 1 indicates the scoring of X
        parents[y][0] = n;
        for (int i = 1; i <= n; i++) {
            parents[y][i] = z[i];
        }
        double lnMarginalLikelihood_Y = scoreNode(y, 2, countsTracker);  // the 2 indicates the scoring of Y
        double lnMarginalLikelihood_X_Y = lnMarginalLikelihood_X + lnMarginalLikelihood_Y;  // lnMarginalLikelihood_X_Y is the ln of the marginal likelihood, assuming X and Y are conditionally independence given Z.
        probability = priorIndependent(x, y, z); // p should be in (0, 1), and thus, not 0 or 1.
        double lnPrior_X_Y = Math.log(probability);
        double score_X_Y = lnMarginalLikelihood_X_Y + lnPrior_X_Y;

        countsTracker.numOfNodes++;
        int xy = countsTracker.numOfNodes;  // this is a constructed variable that represents the Cartesian product of X and Y.
        for (int casei = 1; casei <= numberOfCases; casei++) {  // derive and store values for the new variable XY.
            int xValue = cases[casei][x];
            int yValue = cases[casei][y];
            xyProducts[casei] = (xValue - 1) * nodeDimension[x] + yValue;  // a value in the Cartesian product of X and Y
        }
        countsTracker.xyDim = nodeDimension[x] * nodeDimension[y];
        parents[xy][0] = n;
        for (int i = 1; i <= n; i++) {
            parents[xy][i] = z[i];
        }
        double lnMarginalLikelihood_XY = scoreNode(xy, 3, countsTracker);  // the 3 indicates the scoring of XY, which assumes X and Y are dependent given Z;
        //Note: lnMarginalLikelihood_XY is not used, but the above call to ScoreNode creates scores^[*, 3], which is used below
        countsTracker.numOfNodes--;
        double lnTermPrior_X_Y = Math.log(probability) / countsTracker.numOfScores;  // this is equal to ln(p^(1/numberOfScores))
        double lnTermPrior_XY = Math.log(1 - Math.exp(lnTermPrior_X_Y));  // this is equal to ln(1 - p^(1/numberOfScores))
        double scoreAll = 0;  // will contain the sum over the scores of all hypotheses
        for (int i = 1; i <= countsTracker.numOfScores; i++) {
            scoreAll += lnXpluslnY(lnTermPrior_X_Y + (scores[i][1] + scores[i][2]), lnTermPrior_XY + scores[i][3]);
        }
        double probInd = Math.exp(score_X_Y - scoreAll);

        if (constraint == OP.INDEPENDENT) {
            probability = probInd;  // return P(X independent Y given Z | data)
        } else {
            probability = 1.0 - probInd;  // return P(X dependent Y given Z | data)
        }

        return probability;
    }

    private double scoreNode(int node, int whichList, CountsTracker countsTracker) {
        double totalScore = 0;

        int[][] parents = countsTracker.parents;
        int[] counts = countsTracker.counts;
        int[] countsTree = countsTracker.countsTree;
        double[][] scores = countsTracker.scores;

        int nodeDim = (node > numberOfNodes) ? countsTracker.xyDim : nodeDimension[node];

        if (parents[node][0] > 0) {
            int firstParentSize = nodeDimension[parents[node][1]];
            for (int i = 1; i <= firstParentSize; i++) {
                countsTree[i] = 0;
            }
            countsTracker.countsTreePtr = firstParentSize + 1;
            countsTracker.countsPtr = 1;
        } else {
            countsTracker.countsTreePtr = 1;
            countsTracker.countsPtr = nodeDim + 1;
            for (int i = 1; i <= nodeDim; i++) {
                counts[i] = 0;
            }
        }

        for (int casei = 1; casei <= numberOfCases; casei++) {
            fileCase(node, casei, countsTracker);
        }
        int instancePtr = 1;

        int q = 1;  // state space size of parent instantiations
        for (int i = 1; i <= parents[node][0]; i++) {
            q *= nodeDimension[parents[node][i]];
        }

        countsTracker.numOfScores = 0;
        while (instancePtr < countsTracker.countsPtr) {
            double score;
            if (scoreFn == 1) {
                score = scoringFn1(node, instancePtr, q, PESS_VALUE, countsTracker);
            } else {
                score = scoringFn2(node, instancePtr, countsTracker);
            }
            countsTracker.numOfScores++;
            scores[countsTracker.numOfScores][whichList] = score;
            totalScore += score;
            instancePtr += nodeDim;
        }

        return totalScore;
    }

    /**
     *
     * @param node
     * @param instancePtr
     * @param q is the number of possible joint instantiation of the parents of
     * the parents of the node.
     * @param pess is the prior equivalent sample size
     * @return
     */
    private double scoringFn1(int node, int instancePtr, double q, double pess, CountsTracker countsTracker) {
        int[] counts = countsTracker.counts;

        int Nij = 0;
        double scoreOfSum = 0;
        int r = (node > numberOfNodes) ? countsTracker.xyDim : nodeDimension[node];
        double rr = r;
        double pessDivQR = pess / (q * rr);
        double pessDivQ = pess / q;
        double lngammPessDivQR = gammln(pessDivQR);
        for (int k = 0; k <= (r - 1); k++) {
            int Nijk = counts[instancePtr + k];
            Nij += Nijk;
            scoreOfSum += gammln(Nijk + pessDivQR) - lngammPessDivQR;
        }

        return gammln(pessDivQ) - gammln(Nij + pessDivQ) + scoreOfSum;
    }

    /**
     * Computes the K2 score.
     *
     * @param node
     * @param instancePtr
     * @return
     */
    private double scoringFn2(int node, int instancePtr, CountsTracker countsTracker) {
        int[] counts = countsTracker.counts;
        int nodeDim = (node > numberOfNodes) ? countsTracker.xyDim : nodeDimension[node];

        int hits = 0;
        double scoreNI = 0;
        for (int i = 0; i <= (nodeDim - 1); i++) {
            int count = counts[instancePtr + i];
            hits += count;
            scoreNI += logFactorial[count];
        }
        scoreNI += logFactorial[nodeDim - 1] - logFactorial[hits + nodeDim - 1];

        return scoreNI;
    }

    private void fileCase(int node, int casei, CountsTracker countsTracker) {
        int nodeDim = (node > numberOfNodes) ? countsTracker.xyDim : nodeDimension[node];

        int parent = 0;
        int parentValue = 0;
        int cPtr = 0;
        int parenti = 0;

        int[][] parents = countsTracker.parents;
        int[] counts = countsTracker.counts;
        int[] countsTree = countsTracker.countsTree;
        int[] xyProducts = countsTracker.xyProducts;

        int nodeValue = (node > numberOfNodes) ? xyProducts[casei] : cases[casei][node];
        if (nodeValue == 0) {
//            System.exit(1);
            throw new IllegalArgumentException();
        }
        int numberOfParents = parents[node][0];
        boolean missingValue = false;
        for (int i = 1; i <= numberOfParents; i++) {
            parent = parents[node][i];
            parentValue = (parent > numberOfNodes) ? xyProducts[casei] : cases[casei][parent];
            if (parentValue == 0) {
//                System.exit(1);
                throw new IllegalArgumentException();
            }
        }
        int ctPtr = 1;
        int ptr = 1;
        for (int i = 1; i <= numberOfParents; i++) {
            parent = parents[node][i];
            parentValue = (parent > numberOfNodes) ? xyProducts[casei] : cases[casei][parent];
            ptr = countsTree[ctPtr + parentValue - 1];

            if (ptr > 0) {
                ctPtr = ptr;
            } else {
                parenti = i;
                break;
            }
        }

        if (ptr > 0) {
            cPtr = ctPtr;
            counts[cPtr + nodeValue - 1]++;
        } else {
            // GrowBranch
            for (int i = parenti; i <= numberOfParents; i++) {
                parent = parents[node][i];
                parentValue = (parent > numberOfNodes) ? xyProducts[casei] : cases[casei][parent];

                if (i == numberOfParents) {
                    countsTree[ctPtr + parentValue - 1] = countsTracker.countsPtr;
                } else {
                    countsTree[ctPtr + parentValue - 1] = countsTracker.countsTreePtr;

                    for (int j = countsTracker.countsTreePtr; j <= (countsTracker.countsTreePtr + nodeDimension[parents[node][i + 1]] - 1); j++) {
                        countsTree[j] = 0;
                    }

                    ctPtr = countsTracker.countsTreePtr;
                    countsTracker.countsTreePtr += nodeDimension[parents[node][i + 1]];

                    if (countsTracker.countsPtr > countsTracker.maxCells) {
//                        System.exit(0);
                        throw new IllegalArgumentException();
                    }
                }
            }

            for (int j = countsTracker.countsPtr; j <= (countsTracker.countsPtr + nodeDim - 1); j++) {
                counts[j] = 0;
            }

            cPtr = countsTracker.countsPtr;

            countsTracker.countsPtr += nodeDim;
            if (countsTracker.countsPtr > countsTracker.maxCells) {
//                System.exit(0);
                throw new IllegalArgumentException();
            }
            // end of GrowBranch
            counts[cPtr + nodeValue - 1]++;
        } // end of else
    }

    private CountsTracker createCountsTracker(int[] z) {
        CountsTracker tracker = new CountsTracker();
        tracker.numOfNodes = numberOfNodes;
        tracker.numOfCases = numberOfCases;
        tracker.maxNodes = maximumNodes;
        tracker.maxCases = maximumCases;
        tracker.maxValues = maximumValues;
        tracker.maxParents = maximumParents;
        tracker.maxCells = maximumCells;

        if (z.length > tracker.maxParents) {
            tracker.maxParents = z.length;
            tracker.maxCells = tracker.maxParents * tracker.maxValues * tracker.maxCases;
        }

        tracker.parents = new int[tracker.maxNodes + 2][tracker.maxParents + 1];
        tracker.countsTree = new int[tracker.maxCells + 1];
        tracker.counts = new int[tracker.maxCells + 1];
        tracker.scores = new double[tracker.maxCases + 1][4];
        tracker.xyProducts = new int[tracker.numOfCases + 1];

        return tracker;
    }

    /**
     * This function returns the prior probability that X independent Y given Z.
     * It currently simply returns the uniform prior of 0.5. It can be revised
     * to return an informative prior. The code that calls priorIndependent()
     * currently assumes that it returns a value in (0, 1), and thus, does not
     * return 0 or 1.
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    private double priorIndependent(int x, int y, int[] z) {
        return 0.5;  // currently assumes uniform priors
    }

    private double[] computeLogFactorial(int maxCases, int maxValues) {
        int size = (2 * maxCases) + maxValues;
        double[] logFact = new double[size + 1];
        for (int i = 1; i < logFact.length; i++) {
            logFact[i] = Math.log(i) + logFact[i - 1];
        }

        return logFact;
    }

    /**
     * Takes ln(x) and ln(y) as input, and returns ln(x + y)
     *
     * @param lnX is natural log of x
     * @param lnY is natural log of y
     * @return natural log of x plus y
     */
    private static double lnXpluslnY(double lnX, double lnY) {
        if (lnY > lnX) {
            double temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        double lnYminusLnX = lnY - lnX;

        return (lnYminusLnX < Double.MIN_EXPONENT)
                ? lnX
                : Math.log1p(Math.exp(lnYminusLnX)) + lnX;
    }

    private double gammln(double xx) {
        if (xx == 1) {
            return 0;  // this is a correction to a bug that used to be here
        } else {
            if (xx > 1) {
                return gammlnCore(xx);
            } else {
                double z = 1 - xx;
                return Math.log(Math.PI * z) - gammlnCore(1 + z) - Math.log(Math.sin(Math.PI * z));
            }
        }
    }

    private double gammlnCore(double xx) {
        double stp = 2.50662827465;
        double half = 0.5;
        double one = 1.0;
        double fpf = 5.5;
        double[] cof = {
            0,
            76.18009173,
            -86.50532033,
            24.01409822,
            -1.231739516,
            0.120858003E-2,
            -0.536382E-5
        };

        double x = xx - one;
        double tmp = x + fpf;
        tmp = (x + half) * Math.log(tmp) - tmp;
        double ser = one;
        for (int j = 1; j <= 6; j++) {
            x += one;
            ser += cof[j] / x;
        }

        return tmp + Math.log(stp * ser);
    }

    private class CountsTracker {

        int numOfNodes;
        int numOfCases;
        int maxNodes;
        int maxCases;
        int maxValues; // max value per node;
        int maxParents;
        int maxCells;

        int numOfScores;

        int countsTreePtr;
        int countsPtr;

        int xyDim;

        int[][] parents;
        int[] countsTree;
        int[] counts;
        double[][] scores;
        int[] xyProducts; // represents a vector of the Cartesian product of X and Y

    }

}
