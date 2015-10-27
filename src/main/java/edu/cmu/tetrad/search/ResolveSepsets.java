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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodePair;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Utilities for resolving inconsistencies that arise between sepsets learned for overlapping datasets. This occurs
 * frequently when using the DCI and ION algorithms. f
 *
 * @author Robert Tillman
 */
public final class ResolveSepsets {

    public enum Method {
        fisher, fisher2, tippett, worsleyfriston, stouffer, mudholkergeorge,
        mudholkergeorge2, average, averagetest, random, fdr, majority
    }

    /**
     * Resolves all inconsistencies between sepsets using a paricular method. Returns a sepsetMapDci with the resolved
     * separations and associations. resolvedIndependent and resolvedDependent keep up with the number resolved to check
     * later against the truth
     *
     * @param sepsets
     * @param independenceTests
     * @param method
     * @param resolvedIndependent
     * @param resolvedDependent
     * @return
     */
    public static SepsetMapDci ResolveSepsets(List<SepsetMapDci> sepsets, List<IndependenceTest> independenceTests,
                                              Method method, SepsetMapDci resolvedIndependent, SepsetMapDci resolvedDependent) {
        SepsetMapDci resolvedSepset = new SepsetMapDci();
        // get all variables
        Set<Node> allVars = new HashSet<Node>();
        for (IndependenceTest independenceTest : independenceTests) {
            allVars.addAll(independenceTest.getVariables());
        }
        // checks each pair of nodes for inconsistencies across independenceTests
        for (NodePair pair : allNodePairs(new ArrayList<Node>(allVars))) {
            // gets independenceTests and sepsets for every dataset with the pair
            List<List<List<Node>>> pairSepsets = new ArrayList<List<List<Node>>>();
            List<IndependenceTest> testsWithPair = new ArrayList<IndependenceTest>();
            for (int k = 0; k < independenceTests.size(); k++) {
                IndependenceTest independenceTest = independenceTests.get(k);
                if (independenceTest.getVariables().containsAll(Arrays.asList(pair.getFirst(), pair.getSecond()))) {
                    pairSepsets.add(sepsets.get(k).getSet(pair.getFirst(), pair.getSecond()));
                    testsWithPair.add(independenceTest);
                }
            }
            // only check if pair is included in more than one dataset
            if (testsWithPair.size() < 2) {
                // if pair only in one dataset then add all to resolvedSepset
                if (testsWithPair.size() == 1) {
                    if (pairSepsets.get(0) == null) {
                        continue;
                    }
                    for (List<Node> sepset : pairSepsets.get(0)) {
                        resolvedSepset.set(pair.getFirst(), pair.getSecond(), sepset);
                    }
                }
                continue;
            }
            // check each conditioning set from a dataset
            List<List<Node>> allConditioningSets = new ArrayList<List<Node>>();
            for (List<List<Node>> conditioningSet : pairSepsets) {
                if (conditioningSet == null) {
                    continue;
                }
                allConditioningSets.addAll(conditioningSet);
            }
            for (List<Node> conditioningSet : allConditioningSets) {
                List<IndependenceTest> testsWithSet = new ArrayList<IndependenceTest>();
                for (IndependenceTest independenceTest : testsWithPair) {
                    if (independenceTest.getVariables().containsAll(conditioningSet) || conditioningSet.isEmpty()) {
                        testsWithSet.add(independenceTest);
                    }
                }
                // only check if more than one dataset have test
                if (testsWithSet.size() < 2) {
                    // if conditioning set only in one dataset then add to resolvedSepset
                    if (testsWithPair.size() == 1) {
                        resolvedSepset.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                    }
                    continue;
                }
                boolean separated = false;
                boolean inconsistent = false;
                for (int k = 0; k < testsWithSet.size(); k++) {
                    IndependenceTest testWithSet = testsWithSet.get(k);
                    if (k == 0) {
                        separated = testWithSet.isIndependent(pair.getFirst(), pair.getSecond(), conditioningSet);
                        continue;
                    }
                    // checks to see if inconsistent
                    if (separated != testWithSet.isIndependent(pair.getFirst(), pair.getSecond(), conditioningSet)) {
                        inconsistent = true;
                        break;
                    }
                }
                // if inconsistent then use pooling method
                if (inconsistent) {
                    // if using Fisher pooling
                    if (method == Method.fisher) {
                        if (isIndependentPooledFisher(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.fisher2) {
                        if (isIndependentPooledFisher2(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.tippett) {
                        if (isIndependentPooledTippett(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.worsleyfriston) {
                        if (isIndependentPooledWorsleyFriston(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.stouffer) {
                        if (isIndependentPooledStouffer(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.mudholkergeorge) {
                        if (isIndependentPooledMudholkerGeorge(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.mudholkergeorge2) {
                        if (isIndependentPooledMudholkerGeorge2(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.averagetest) {
                        if (isIndependentPooledAverageTest(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.average) {
                        if (isIndependentPooledAverage(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.random) {
                        if (isIndependentPooledRandom(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.fdr) {
                        if (isIndependentMajorityFdr(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.majority) {
                        if (isIndependentMajorityIndep(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else {
                        throw new RuntimeException("Invalid Test");
                    }

                } else {
                    resolvedSepset.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                }
            }
        }
        return resolvedSepset;
    }

    /**
     * Tests for independence using one of the pooled methods
     *
     * @param method
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooled(Method method, List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        if (method == Method.fisher) {
            return isIndependentPooledFisher(independenceTests, x, y, condSet);
        } else if (method == Method.fisher2) {
            return isIndependentPooledFisher2(independenceTests, x, y, condSet);
        } else if (method == Method.tippett) {
            return isIndependentPooledTippett(independenceTests, x, y, condSet);
        } else if (method == Method.worsleyfriston) {
            return isIndependentPooledWorsleyFriston(independenceTests, x, y, condSet);
        } else if (method == Method.stouffer) {
            return isIndependentPooledStouffer(independenceTests, x, y, condSet);
        } else if (method == Method.mudholkergeorge) {
            return isIndependentPooledMudholkerGeorge(independenceTests, x, y, condSet);
        } else if (method == Method.mudholkergeorge2) {
            return isIndependentPooledMudholkerGeorge2(independenceTests, x, y, condSet);
        } else if (method == Method.averagetest) {
            return isIndependentPooledAverageTest(independenceTests, x, y, condSet);
        } else if (method == Method.average) {
            return isIndependentPooledAverage(independenceTests, x, y, condSet);
        } else if (method == Method.random) {
            return isIndependentPooledRandom(independenceTests, x, y, condSet);
        } else if (method == Method.fdr) {
            return isIndependentMajorityFdr(independenceTests, x, y, condSet);
        } else if (method == Method.majority) {
            return isIndependentMajorityIndep(independenceTests, x, y, condSet);
        } else {
            throw new RuntimeException("Invalid Test");
        }
    }

    /**
     * Checks independence from pooled samples using Fisher's method.
     * <p/>
     * See R. A. Fisher. Statistical Methods for Research Workers. Oliver and Boyd, 11th edition, 1950.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledFisher(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double tf = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (missingVariable(x, y, condSet, independenceTest)) continue;
            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            tf += -2.0 * Math.log(independenceTest.getPValue());
        }
        double p = 1.0 - ProbUtils.chisqCdf(tf, 2 * independenceTests.size());
        return (p > alpha);
    }

    /**
     * Eliminates from considerations independence tests that cannot be evaluated (due to missing variables mainly).
     */
    public static boolean isIndependentPooledFisher2(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        List<Double> pValues = getAvailablePValues(independenceTests, x, y, condSet);

        double tf = 0.0;
        int numPValues = 0;

        for (double p : pValues) {
//            if (p > 0) {
                tf += -2.0 * Math.log(p);
                numPValues++;
//            }
        }

        double p = 1.0 - ProbUtils.chisqCdf(tf, 2 * numPValues);

        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples using Tippett's method
     * <p/>
     * See L. H. C. Tippett. The Method of Statistics. Williams and Norgate, 1st edition, 1950.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledTippett(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double p = -1.0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (missingVariable(x, y, condSet, independenceTest)) continue;
            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            if (p == -1.0) {
                p = independenceTest.getPValue();
                continue;
            }
            double newp = independenceTest.getPValue();
            if (newp < p) {
                p = newp;
            }
        }
        return (p > (1 - Math.pow(1 - alpha, (1 / (double) independenceTests.size()))));
    }

    /**
     * Checks independence from pooled samples using Wilkinson's method
     * <p/>
     * I don't have a reference for this but its basically in between Tippett and Worsley and Friston.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @param r
     * @return
     */
    public static boolean isIndependentPooledWilkinson(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet, int r) {
        double alpha = independenceTests.get(0).getAlpha();
        double p[] = new double[independenceTests.size()];
        int k = 0;
        for (IndependenceTest independenceTest : independenceTests) {
            p[k] = independenceTest.getPValue();
            k++;
        }
        java.util.Arrays.sort(p);
        return (p[r] > (1 - Math.pow(1 - Math.pow(alpha, 1.0 / (double) r), (r / (double) independenceTests.size()))));
    }

    /**
     * Checks independence from pooled samples using Worsley and Friston's method
     * <p/>
     * See K. J. Worsely and K. J. Friston. A test for conjunction. Statistics and Probability Letters
     * 2000.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledWorsleyFriston(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double p = -1.0;
        for (IndependenceTest independenceTest : independenceTests) {
            List<Node> localCondSet = new ArrayList<Node>();
            if (missingVariable(x, y, condSet, independenceTest)) continue;
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            if (p == -1.0) {
                p = independenceTest.getPValue();
                continue;
            }
            double newp = independenceTest.getPValue();
            if (newp > p) {
                p = newp;
            }
        }
        return (p > Math.pow(alpha, (1 / (double) independenceTests.size())));
    }

    /**
     * Checks independence from pooled samples using Stouffer et al.'s method
     * <p/>
     * See S. A. Stouffer, E. A. Suchman, L. C. Devinney, S. A. Star, and R. M. Williams. The American Soldier: Vol. 1.
     * Adjustment During Army Life. Princeton University Press, 1949.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledStouffer(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double ts = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            ts += ProbUtils.normalQuantile(independenceTest.getPValue()) / Math.sqrt(independenceTests.size());
        }
        double p = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(ts)));
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples using Mudholker and George's method
     * <p/>
     * See G. S. Mudholkar and E. O. George. The logit method for combining probabilities. In J. Rustagi, editor,
     * Symposium on Optimizing Method in Statistics, pages 345-366. Academic Press, 1979.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledMudholkerGeorge(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double c = Math.sqrt(3 * (5 * independenceTests.size() + 4) / (double) (independenceTests.size() * Math.pow(Math.PI, 2) * (5 * independenceTests.size() + 2)));
        double tm = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            double pk = independenceTest.getPValue();
            if (pk != 0 && pk != 1) {
                tm += -c * Math.log(pk / (1 - pk));
            }
        }
        double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tm), 5 * independenceTests.size() + 4));
        return (p > alpha);
    }

    /**
     * The same as isIndepenentPooledMudholkerGeoerge, except that only available independence tests are used.
     */
    public static boolean isIndependentPooledMudholkerGeorge2(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        List<Double> pValues = getAvailablePValues(independenceTests, x, y, condSet);
        double c = Math.sqrt(3 * (5 * pValues.size() + 4) / (double) (pValues.size() * Math.pow(Math.PI, 2) * (5 * pValues.size() + 2)));
        double tm = 0.0;
        for (double pk : pValues) {
            tm += -c * Math.log(pk / (1 - pk));
        }
        double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tm), 5 * pValues.size() + 4));
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples by taking the average p value
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledAverage(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double sum = 0.0;
        int numTests = 0;

        for (IndependenceTest independenceTest : independenceTests) {
            if (missingVariable(x, y, condSet, independenceTest)) continue;

            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }

            independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            double p = independenceTest.getPValue();

            if (Double.isNaN(p)) continue;

            sum += p;
            numTests++;
        }

//        if (p > alpha) {
//            System.out.println("Independent: " + SearchLogUtils.independenceFact(x, y, condSet) + " " + p);
//        }
//        else {
//            System.out.println("Dependent: " + SearchLogUtils.independenceFact(x, y, condSet) + " " + p);
//        }


        return (sum / numTests > alpha);
    }

    private static boolean missingVariable(Node x, Node y, List<Node> condSet, IndependenceTest independenceTest) {
        DataSet dataSet = (DataSet) independenceTest.getData();

        if (isMissing(x, dataSet)) {
            return true;
        }

        if (isMissing(y, dataSet)) {
            return true;
        }

        for (Node z : condSet) {
            if (isMissing(z, dataSet)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMissing(Node node, DataSet dataSet) {
        Node _node = dataSet.getVariable(node.getName());

        int col = dataSet.getColumn(_node);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            if (Double.isNaN(dataSet.getDouble(i, col))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks independence from pooled samples by taking the average test statistic CURRENTLY ONLY WORKS FOR CHISQUARE
     * TEST
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledAverageTest(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double ts = 0.0;
        int df = 0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (!(independenceTest instanceof IndTestChiSquare)) {
                throw new RuntimeException("Must be ChiSquare Test");
            }
            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            ts += ((IndTestChiSquare) independenceTest).getXSquare() / independenceTests.size();
            df += ((IndTestChiSquare) independenceTest).getDf();
        }
        df = df / independenceTests.size();
        double p = 1.0 - ProbUtils.chisqCdf(ts, df);
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples by randomly selecting a p value
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    public static boolean isIndependentPooledRandom(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        int r = RandomUtil.getInstance().nextInt(independenceTests.size());
        IndependenceTest independenceTest = independenceTests.get(r);
        List<Node> localCondSet = new ArrayList<Node>();
        for (Node node : condSet) {
            localCondSet.add(independenceTest.getVariable(node.getName()));
        }
        independenceTest.isIndependent(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
        double p = independenceTest.getPValue();
        return (p > alpha);
    }

    /**
     * Generates NodePairs of all possible pairs of nodes from given list of nodes.
     */
    public static List<NodePair> allNodePairs(List<Node> nodes) {
        List<NodePair> nodePairs = new ArrayList<NodePair>();
        for (int j = 0; j < nodes.size() - 1; j++) {
            for (int k = j + 1; k < nodes.size(); k++) {
                nodePairs.add(new NodePair(nodes.get(j), nodes.get(k)));
            }
        }
        return nodePairs;
    }

    /**
     * Judges x to be independent of y conditional on condSet if the false discovery rate of the p values for the
     * separate judgements for their collective alpha level identifies no more than # p values / 2 values below
     * threshold.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    private static boolean isIndependentMajorityFdr(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        List<Double> allPValues = getAvailablePValues(independenceTests, x, y, condSet);

        Collections.sort(allPValues);
        int c = 0;
        while (c < allPValues.size() &&
                allPValues.get(c) < independenceTests.get(0).getAlpha() * (c + 1.) / allPValues.size()) {
            c++;
        }


        // At least half of the judgments are for independence.
        boolean independent = c < allPValues.size() / 2;
//        boolean independent = c < allPValues.size() - 2;

        if (independent) {
            TetradLogger.getInstance().log("independence", "***FDR judges " + SearchLogUtils.independenceFact(x, y, condSet) + " independent");
            TetradLogger.getInstance().log("independence", "c = " + c);
        } else {
            TetradLogger.getInstance().log("independence", "###FDR judges " + SearchLogUtils.independenceFact(x, y, condSet) + " dependent");
            TetradLogger.getInstance().log("independence", "c = " + c);
        }

        return independent;
    }

    private static List<Double> getAvailablePValues(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        List<Double> allPValues = new ArrayList<Double>();

        for (IndependenceTest test : independenceTests) {
            if (missingVariable(x, y, condSet, test)) continue;
            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(test.getVariable(node.getName()));
            }

            try {
                test.isIndependent(test.getVariable(x.getName()), test.getVariable(y.getName()), localCondSet);
                allPValues.add(test.getPValue());
            } catch (Exception e) {
                // Skip that test.
            }
        }

        return allPValues;
    }

    /**
     * Judges x to be independent of y conditional on condSet if the false discovery rate of the p values for the
     * separate judgements for their collective alpha level identifies no more than # p values / 2 values below
     * threshold.
     *
     * @param independenceTests
     * @param x
     * @param y
     * @param condSet
     * @return
     */
    private static boolean isIndependentMajorityIndep(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        List<Double> allPValues = getAvailablePValues(independenceTests, x, y, condSet);

        Collections.sort(allPValues);
        int c = 0;
        while (c < allPValues.size() && allPValues.get(c) < independenceTests.get(0).getAlpha()) {
            c++;
        }


        // At least half of the judgments are for independence.
//        boolean independent = c < 40;
        boolean independent = c < allPValues.size() / 2;

        if (independent) {
            TetradLogger.getInstance().log("independence", "***Majority = " + SearchLogUtils.independenceFact(x, y, condSet) + " independent");
            TetradLogger.getInstance().log("independence", "c = " + c);
        } else {
            TetradLogger.getInstance().log("independence", "###Majority = " + SearchLogUtils.independenceFact(x, y, condSet) + " dependent");
            TetradLogger.getInstance().log("independence", "c = " + c);
        }

        return independent;
    }
}



