///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.pitt.dbmi.algo.bayesian.constraint.inference;

import java.util.Arrays;

import static java.lang.Math.log;

/**
 * Feb 26, 2014 8:07:20 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class BCInference {

    public enum OP {

        independent, dependent

    }
    private static final int MININUM_EXPONENT = -1022;

    private static final double PESS_VALUE = 1;

    private int[] countsTree;

    private int[] counts;

    private double[] logfact;

    private int[][] parents;

    /**
     * Maximum cases (samples) to read from a text file.
     */
    private int maxCases;

    /**
     * Maximum number of measured nodes.
     */
    private int maxNodes;

    private int maxParents;

    /**
     * Max value per node.
     */
    private int maxValues;

    private int maxCells;

    private int maxLogFact;

    private int countsTreePtr;

    private int countsPtr;

    private int numberOfNodes;

    private int numberOfCases;

    private int[][] cases;

    private int[] nodeDimension;

    private int scoreFn;

    private double[][] scores;

    private int numberOfScores;

    /**
     * Cases is a two-dimensional array dataset. If the dataset is M x N, the
     * size of the two-dimensional array is (M + 2) x (N + 2). In other words,
     * the size of the array is always 2 more of the number of data. Likewise,
     * if the data for nodeDimension is N then the size of the array is N + 2.
     * <p/>
     * The case array index starts from 1 (not zero) to numberOfCases. The
     * nodeDimension array index start from 1 (not zero) to numberOfNodes.
     * <p/>
     * nodeDimension array contains values denote the number of discrete values
     * that Node can have (e.g., 2 for a binary variable).
     *
     * @param cases is a two-dimensional integer array containing the data
     * @param nodeDimension one-dimensional integer array containing the
     * dimension of each variable
     */
    public BCInference(int[][] cases, int[] nodeDimension) {
        this.cases = cases;
        this.nodeDimension = nodeDimension;
        this.numberOfNodes = nodeDimension.length - 2;
        this.numberOfCases = cases.length - 1;

        this.maxCases = numberOfCases;
//        this.maxCases = max(numberOfCases, 1);
        this.maxNodes = numberOfNodes;
        this.maxValues = findMaxValue(nodeDimension);
        this.maxLogFact = (2 * maxCases) + maxValues;

        this.scoreFn = 1;

        this.logfact = new double[maxLogFact + 1];

        int[] _nodeDimension = Arrays.copyOf(nodeDimension, nodeDimension.length);
        Arrays.sort(_nodeDimension);
        int g1 = _nodeDimension[_nodeDimension.length - 1];
        int g2 = _nodeDimension[_nodeDimension.length - 2];


//            maxCells = maxParents * maxValues * maxCases ;
        maxCells = maxParents * g1*g2 * maxCases ;

        this.parents = new int[maxNodes + 2][maxParents + 1];
        this.countsTree = new int[maxCells + 1];
        this.counts = new int[maxCells + 1];
        this.scores = new double[maxCases + 1][4];
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
     * Z[0] = 1 represents the set Z of size 1. Z&lsqb;0&rsqb; = 0 represents an empty
     * set.
     * <p/>
     * Set Z with two elements: Z = &lcub;3, 2&rcub; Z&lsqb;0&rsqb; = 2 // set Z has two elements
     * (length of 2) Z[1] = 3 // first element Z[2] = 2 // second element.
     * <p/>
     * Empty set: Z = &lcub;&rcub; Z&lsqb;0&rsqb; = 0
     *
     * @param constraint has the value OP.independent or OP.dependent
     * @param x node x
     * @param y node y
     * @param z set of nodes
     * @return P&lpar; x dependent y given z &vert; data &rpar; or P&lpar;x independent y given z &vert;
     * data&rpar;
     */
    public double probConstraint(OP constraint, int x, int y, int[] z) {
//        if (true) return 0.5;

        double p = 0;

        logfact[0] = 0;
        for (int i = 1; i < logfact.length; i++) {
            logfact[i] = log(i) + logfact[i - 1];
        }
        logfact[0] = 0;

        if (z.length > maxParents) {
            int maxConditioningNodes = z.length;  // max size of set Z in ind(X, Y, | Z)
            maxParents = maxConditioningNodes;

            int[] _nodeDimension = Arrays.copyOf(nodeDimension, nodeDimension.length);
            Arrays.sort(_nodeDimension);
            int g1 = _nodeDimension[_nodeDimension.length - 1];
            int g2 = _nodeDimension[_nodeDimension.length - 2];


//            maxCells = maxParents * maxValues * maxCases ;
            maxCells = maxParents * g1*g2 * maxCases ;

            parents = new int[maxNodes + 2][maxParents + 1];
            countsTree = new int[maxCells + 1];
            counts = new int[maxCells + 1];
        }

        int n = z[0];
        parents[x][0] = n;
        for (int i = 1; i <= n; i++) {
            parents[x][i] = z[i];
        }
        double lnMarginalLikelihood_X = scoreNode(x, 1);  // the 1 indicates the scoring of X
        parents[y][0] = n;
        for (int i = 1; i <= n; i++) {
            parents[y][i] = z[i];
        }
        double lnMarginalLikelihood_Y = scoreNode(y, 2);  // the 2 indicates the scoring of Y
        double lnMarginalLikelihood_X_Y = lnMarginalLikelihood_X + lnMarginalLikelihood_Y;  // lnMarginalLikelihood_X_Y is the ln of the marginal likelihood, assuming X and Y are conditionally independence given Z.
        p = priorIndependent(x, y, z); // p should be in (0, 1), and thus, not 0 or 1.
        double lnPrior_X_Y = Math.log(p);
        double score_X_Y = lnMarginalLikelihood_X_Y + lnPrior_X_Y;

        numberOfNodes++;
        int xy = numberOfNodes;  // this is a constructed variable that represents the Cartesian product of X and Y.
        for (int casei = 1; casei <= numberOfCases; casei++) {  // derive and store values for the new variable XY.
            int xValue = cases[casei][x];
            int yValue = cases[casei][y];
//            cases[casei][xy] = (xValue - 1) * nodeDimension[x] + yValue;  // a value in the Cartesian product of X and Y
            cases[casei][xy] = (xValue - 1) * nodeDimension[y] + yValue;  // a value in the Cartesian product of X and Y
        }
        nodeDimension[xy] = nodeDimension[x] * nodeDimension[y];
        parents[xy][0] = n;
        for (int i = 1; i <= n; i++) {
            parents[xy][i] = z[i];
        }
        double lnMarginalLikelihood_XY = scoreNode(xy, 3);  // the 3 indicates the scoring of XY, which assumes X and Y are dependent given Z;
        //Note: lnMarginalLikelihood_XY is not used, but the above call to ScoreNode creates scores^[*, 3], which is used below
        numberOfNodes--;
        double lnTermPrior_X_Y = Math.log(p) / numberOfScores;  // this is equal to ln(p^(1/numberOfScores))
        double lnTermPrior_XY = Math.log(1 - Math.exp(lnTermPrior_X_Y));  // this is equal to ln(1 - p^(1/numberOfScores))
        double scoreAll = 0;  // will contain the sum over the scores of all hypotheses
        for (int i = 1; i <= numberOfScores; i++) {
            scoreAll += lnXpluslnY(lnTermPrior_X_Y + (scores[i][1] + scores[i][2]), lnTermPrior_XY + scores[i][3]);
        }
        double probInd = Math.exp(score_X_Y - scoreAll);

        if (constraint == OP.independent) {
            p = probInd;  // return P(X independent Y given Z | data)
        } else {
            p = 1.0 - probInd;  // return P(X dependent Y given Z | data)
        }

        return p;
    }

    /**
     * Takes ln(x) and ln(y) as input, and returns ln(x + y)
     *
     * @param lnX is natural log of x
     * @param lnY is natural log of y
     * @return natural log of x plus y
     */
    protected double lnXpluslnY(double lnX, double lnY) {
        double lnYminusLnX, temp;

        if (lnY > lnX) {
            temp = lnX;
            lnX = lnY;
            lnY = temp;
        }

        lnYminusLnX = lnY - lnX;

        if (lnYminusLnX < MININUM_EXPONENT) {
            return lnX;
        } else {
            return Math.log1p(Math.exp(lnYminusLnX)) + lnX;
        }
    }

    /**
     * This function returns the prior probability that X independent Y given Z.
     * It currently simply returns the uniform prior of 0.5. It can be revised
     * to return an informative prior. The code that calls priorIndependent()
     * currently assumes that it returns a value in (0, 1), and thus, does not
     * return 0 or 1.
     */
    private double priorIndependent(int x, int y, int[] z) {
        return 0.5;  // currently assumes uniform priors
    }

    private double scoreNode(int node, int whichList) {
        double totalScore = 0;

        if (parents[node][0] > 0) {
            int firstParentSize = nodeDimension[parents[node][1]];
            for (int i = 1; i <= firstParentSize; i++) {
                countsTree[i] = 0;
            }
            countsTreePtr = firstParentSize + 1;
            countsPtr = 1;
        } else {
            countsTreePtr = 1;
            countsPtr = nodeDimension[node] + 1;
            for (int i = 1; i <= nodeDimension[node]; i++) {
                counts[i] = 0;
            }
        }

        for (int casei = 1; casei <= numberOfCases; casei++) {
            fileCase(node, casei);
        }
        int instancePtr = 1;

        int q = 1;  // state space size of parent instantiations
        for (int i = 1; i <= parents[node][0]; i++) {
            q *= nodeDimension[parents[node][i]];
        }

        numberOfScores = 0;
        while (instancePtr < countsPtr) {
            double score;
            if (scoreFn == 1) {
                score = scoringFn1(node, instancePtr, q, PESS_VALUE);
            } else {
                score = scoringFn2(node, instancePtr);
            }
            numberOfScores++;
            scores[numberOfScores][whichList] = score;
            totalScore += score;
            instancePtr += nodeDimension[node];
        }

        return totalScore;
    }

    private double scoreNode(int node) {
        double score = 0;

        if (parents[node][0] > 0) {
            int firstParentSize = nodeDimension[parents[node][1]];
            for (int i = 1; i <= firstParentSize; i++) {
                countsTree[i] = 0;
            }
            countsTreePtr = firstParentSize + 1;
            countsPtr = 1;
        } else {
            countsTreePtr = 1;
            countsPtr = nodeDimension[node] + 1;
            for (int i = 1; i <= nodeDimension[node]; i++) {
                counts[i] = 0;
            }
        }
        for (int casei = 1; casei <= numberOfCases; casei++) {
            fileCase(node, casei);
        }
        int instancePtr = 1;
        score = 0;

        while (instancePtr < countsPtr) {
            score += scoringFn2(node, instancePtr);
            instancePtr += nodeDimension[node];
        }

        return score;
    }

    /**
     * @param q is the number of possible joint instantiation of the parents of
     * the parents of the node.
     * @param pess is the prior equivalent sample size
     */
    private double scoringFn1(int node, int instancePtr, double q, double pess) {
        int Nij = 0;
        double scoreOfSum = 0;
        int r = nodeDimension[node];
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

    /**
     * Computes the K2 score.
     */
    private double scoringFn2(int node, int instancePtr) {
        int hits = 0;
        double scoreNI = 0;
        for (int i = 0; i <= (nodeDimension[node] - 1); i++) {
            int count = counts[instancePtr + i];
            hits += count;
            scoreNI += logfact[count];
        }
        scoreNI += logfact[nodeDimension[node] - 1] - logfact[hits + nodeDimension[node] - 1];

        return scoreNI;
    }

    private void fileCase(int node, int casei) {
        int parent = 0;
        int parentValue = 0;
        int cPtr = 0;
        int parenti = 0;

        int nodeValue = cases[casei][node];
        if (nodeValue == 0) {
//            System.exit(1);
            throw new IllegalArgumentException();
        }
        int numberOfParents = parents[node][0];

        // Throws an exception if a missing value exists among the parents of node.
        boolean missingValue = false;
        for (int i = 1; i <= numberOfParents; i++) {
            parent = parents[node][i];
            parentValue = cases[casei][parent];
            if (parentValue == 0) {
                throw new IllegalArgumentException();
            }
        }

        int ctPtr = 1;
        int ptr = 1;
        for (int i = 1; i <= numberOfParents; i++) {
            parent = parents[node][i];
            parentValue = cases[casei][parent];
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
                parentValue = cases[casei][parent];

                if (i == numberOfParents) {
                    countsTree[ctPtr + parentValue - 1] = countsPtr;
                } else {
                    countsTree[ctPtr + parentValue - 1] = countsTreePtr;

                    for (int j = countsTreePtr; j <= (countsTreePtr + nodeDimension[parents[node][i + 1]] - 1); j++) {
                        countsTree[j] = 0;
                    }

                    ctPtr = countsTreePtr;
                    countsTreePtr += nodeDimension[parents[node][i + 1]];

                    if (countsPtr > maxCells) {
                        System.out.println("A");
                        System.out.println(maxCells);
                        System.out.println(countsTreePtr);
                        System.out.println(ctPtr);
                        System.out.println(countsPtr);
                        System.out.println(nodeDimension[parents[node][i+1]]);
                        throw new IllegalArgumentException();
                    }
                }
            }

            if (countsPtr > maxCells) {
                System.out.println("B");
                System.out.println(maxCells);
                System.out.println(countsTreePtr);
                System.out.println(ctPtr);
                System.out.println(countsPtr);
                System.out.println(node);
                System.out.println(nodeDimension[node]);
                throw new IllegalArgumentException();
            }

            for (int j = countsPtr; j <= (countsPtr + nodeDimension[node] - 1); j++) {
                counts[j] = 0;
            }

            cPtr = countsPtr;

            countsPtr += nodeDimension[node];
            if (countsPtr > maxCells) {
                System.out.println("C");
                System.out.println(maxCells);
                System.out.println(countsTreePtr);
                System.out.println(ctPtr);
                System.out.println("Max nodes = " + maxNodes);
                System.out.println("Node = " + node);
                System.out.println(nodeDimension[node]);
                throw new IllegalArgumentException();
            }
            // end of GrowBranch
            counts[cPtr + nodeValue - 1]++;
        } // end of else
    }

    private int findMaxValue(int[] nodeDimension) {
        int maxValue = 0;

        for (int i = 1; i < nodeDimension.length; i++) {
            if (maxValue < nodeDimension[i]) {
                maxValue = nodeDimension[i];
            }
        }

        return maxValue;
    }
}


