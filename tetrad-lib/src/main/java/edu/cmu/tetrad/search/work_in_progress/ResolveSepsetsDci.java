///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodePair;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

/**
 * Utilities for resolving inconsistencies that arise between sepsets learned for overlapping datasets. This occurs
 * frequently when using the DCI and ION algorithm. f
 *
 * @author Robert Tillman
 * @version $Id: $Id
 */
public final class ResolveSepsetsDci {

    /**
     * Resolves the separation sets using the Decision Component Interface (DCI) algorithm. This method is responsible
     * for identifying and resolving the separation sets between different components in the system using the DCI
     * algorithm.
     * <p>
     * The DCI algorithm helps in determining the communication protocols and data dependencies between various
     * components in the system.
     * <p>
     * This method does not take any parameters and does not return any value.
     * <p>
     * Example usage:
     * <p>
     * ResolveSepsetsDci resolveSepsetsDci = new ResolveSepsetsDci(); resolveSepsetsDci.resolveSepsetsDci();
     *
     * @see <a href="https://www.example.com/dci-algorithm">DCI Algorithm</a>
     * @since 1.0.0
     */
    public ResolveSepsetsDci() {
    }

    /**
     * Resolves all inconsistencies between sepsets using a paricular method. Returns a sepsetMapDci with the resolved
     * separations and associations. resolvedIndependent and resolvedDependent keep up with the number resolved to check
     * later against the truth
     *
     * @param sepsets             a {@link java.util.List} object
     * @param independenceTests   a {@link java.util.List} object
     * @param method              a {@link edu.cmu.tetrad.search.work_in_progress.ResolveSepsetsDci.Method} object
     * @param resolvedIndependent a {@link edu.cmu.tetrad.search.work_in_progress.SepsetMapDci} object
     * @param resolvedDependent   a {@link edu.cmu.tetrad.search.work_in_progress.SepsetMapDci} object
     * @return a {@link edu.cmu.tetrad.search.work_in_progress.SepsetMapDci} object
     */
    public static SepsetMapDci resolveSepsets(List<SepsetMapDci> sepsets, List<IndependenceTest> independenceTests,
                                              Method method, SepsetMapDci resolvedIndependent, SepsetMapDci resolvedDependent) {
        SepsetMapDci resolvedSepset = new SepsetMapDci();
        // get all variables
        Set<Node> allVars = new HashSet<>();
        for (IndependenceTest independenceTest : independenceTests) {
            allVars.addAll(independenceTest.getVariables());
        }
        // checks each pair of nodes for inconsistencies across independenceTests
        for (NodePair pair : ResolveSepsetsDci.allNodePairs(new ArrayList<>(allVars))) {
            // gets independenceTests and sepsets for every dataset with the pair
            List<Set<Set<Node>>> pairSepsets = new ArrayList<>();
            List<IndependenceTest> testsWithPair = new ArrayList<>();
            for (int k = 0; k < independenceTests.size(); k++) {
                IndependenceTest independenceTest = independenceTests.get(k);
                if (new HashSet<>(independenceTest.getVariables()).containsAll(Arrays.asList(pair.getFirst(), pair.getSecond()))) {
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
                    for (Set<Node> sepset : pairSepsets.get(0)) {
                        resolvedSepset.set(pair.getFirst(), pair.getSecond(), sepset);
                    }
                }
                continue;
            }
            // check each conditioning set from a dataset
            Set<Set<Node>> allConditioningSets = new HashSet<>();
            for (Set<Set<Node>> conditioningSet : pairSepsets) {
                if (conditioningSet == null) {
                    continue;
                }
                allConditioningSets.addAll(conditioningSet);
            }
            for (Set<Node> conditioningSet : allConditioningSets) {
                List<IndependenceTest> testsWithSet = new ArrayList<>();
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
                        separated = testWithSet.checkIndependence(pair.getFirst(), pair.getSecond(), conditioningSet).isIndependent();
                        continue;
                    }
                    // checks to see if inconsistent
                    if (separated != testWithSet.checkIndependence(pair.getFirst(), pair.getSecond(), conditioningSet).isIndependent()) {
                        inconsistent = true;
                        break;
                    }
                }
                // if inconsistent then use pooling method
                if (inconsistent) {
                    // if using Fisher pooling
                    if (method == Method.fisher) {
                        if (ResolveSepsetsDci.isIndependentPooledFisher(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.fisher2) {
                        if (ResolveSepsetsDci.isIndependentPooledFisher2(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.tippett) {
                        if (ResolveSepsetsDci.isIndependentPooledTippett(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.worsleyfriston) {
                        if (ResolveSepsetsDci.isIndependentPooledWorsleyFriston(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.stouffer) {
                        if (ResolveSepsetsDci.isIndependentPooledStouffer(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.mudholkergeorge) {
                        if (ResolveSepsetsDci.isIndependentPooledMudholkerGeorge(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.mudholkergeorge2) {
                        if (ResolveSepsetsDci.isIndependentPooledMudholkerGeorge2(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.averagetest) {
                        if (ResolveSepsetsDci.isIndependentPooledAverageTest(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.average) {
                        if (ResolveSepsetsDci.isIndependentPooledAverage(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.random) {
                        if (ResolveSepsetsDci.isIndependentPooledRandom(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.fdr) {
                        if (ResolveSepsetsDci.isIndependentMajorityFdr(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
                            resolvedSepset.set(pair.getFirst(), pair.getFirst(), conditioningSet);
                            resolvedIndependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        } else {
                            resolvedDependent.set(pair.getFirst(), pair.getSecond(), conditioningSet);
                        }
                    } else if (method == Method.majority) {
                        if (ResolveSepsetsDci.isIndependentMajorityIndep(testsWithSet, pair.getFirst(), pair.getSecond(), conditioningSet)) {
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
     * @param method            a {@link edu.cmu.tetrad.search.work_in_progress.ResolveSepsetsDci.Method} object
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooled(Method method, List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        if (method == Method.fisher) {
            return ResolveSepsetsDci.isIndependentPooledFisher(independenceTests, x, y, condSet);
        } else if (method == Method.fisher2) {
            return ResolveSepsetsDci.isIndependentPooledFisher2(independenceTests, x, y, condSet);
        } else if (method == Method.tippett) {
            return ResolveSepsetsDci.isIndependentPooledTippett(independenceTests, x, y, condSet);
        } else if (method == Method.worsleyfriston) {
            return ResolveSepsetsDci.isIndependentPooledWorsleyFriston(independenceTests, x, y, condSet);
        } else if (method == Method.stouffer) {
            return ResolveSepsetsDci.isIndependentPooledStouffer(independenceTests, x, y, condSet);
        } else if (method == Method.mudholkergeorge) {
            return ResolveSepsetsDci.isIndependentPooledMudholkerGeorge(independenceTests, x, y, condSet);
        } else if (method == Method.mudholkergeorge2) {
            return ResolveSepsetsDci.isIndependentPooledMudholkerGeorge2(independenceTests, x, y, condSet);
        } else if (method == Method.averagetest) {
            return ResolveSepsetsDci.isIndependentPooledAverageTest(independenceTests, x, y, condSet);
        } else if (method == Method.average) {
            return ResolveSepsetsDci.isIndependentPooledAverage(independenceTests, x, y, condSet);
        } else if (method == Method.random) {
            return ResolveSepsetsDci.isIndependentPooledRandom(independenceTests, x, y, condSet);
        } else if (method == Method.fdr) {
            return ResolveSepsetsDci.isIndependentMajorityFdr(independenceTests, x, y, condSet);
        } else if (method == Method.majority) {
            return ResolveSepsetsDci.isIndependentMajorityIndep(independenceTests, x, y, condSet);
        } else {
            throw new RuntimeException("Invalid Test");
        }
    }

    /**
     * Checks independence from pooled samples using Fisher's method.
     * <p>
     * See R. A. Fisher. Statistical Methods for Research Workers. Oliver and Boyd, 11th edition, 1950.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledFisher(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double tf = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (ResolveSepsetsDci.missingVariable(x, y, condSet, independenceTest)) continue;
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            tf += -2.0 * FastMath.log(result.getPValue());
        }
        double p = 1.0 - ProbUtils.chisqCdf(tf, 2 * independenceTests.size());
        return (p > alpha);
    }

    /**
     * Eliminates from considerations independence tests that cannot be evaluated (due to missing variables mainly).
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledFisher2(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        List<Double> pValues = ResolveSepsetsDci.getAvailablePValues(independenceTests, x, y, condSet);

        double tf = 0.0;
        int numPValues = 0;

        for (double p : pValues) {
//            if (p > 0) {
            tf += -2.0 * FastMath.log(p);
            numPValues++;
//            }
        }

        double p = 1.0 - ProbUtils.chisqCdf(tf, 2 * numPValues);

        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples using Tippett's method
     * <p>
     * See L. H. C. Tippett. The Method of Statistics. Williams and Norgate, 1st edition, 1950.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledTippett(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double p = -1.0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (ResolveSepsetsDci.missingVariable(x, y, condSet, independenceTest)) continue;
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(
                    independenceTest.getVariable(x.getName()),
                    independenceTest.getVariable(y.getName()), localCondSet);
            if (Double.isNaN(result.getPValue())) {
                continue;
            }
            double newp = result.getPValue();
            if (newp < p) {
                p = newp;
            }
        }
        return (p > (1 - FastMath.pow(1 - alpha, (1 / (double) independenceTests.size()))));
    }

    /**
     * Checks independence from pooled samples using Wilkinson's method
     * <p>
     * I don't have a reference for this but its basically in between Tippett and Worsley and Friston.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @param r                 a int
     * @return a boolean
     */
    public static boolean isIndependentPooledWilkinson(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet, int r) {
        double alpha = independenceTests.get(0).getAlpha();
        double[] p = new double[independenceTests.size()];
        int k = 0;
        for (IndependenceTest independenceTest : independenceTests) {
            IndependenceResult result = independenceTest.checkIndependence(x, y, condSet);
            p[k] = result.getPValue();
            k++;
        }
        Arrays.sort(p);
        return (p[r] > (1 - FastMath.pow(1 - FastMath.pow(alpha, 1.0 / (double) r), (r / (double) independenceTests.size()))));
    }

    /**
     * Checks independence from pooled samples using Worsley and Friston's method
     * <p>
     * See K. J. Worsely and K. J. Friston. A test for conjunction. Statistics and Probability Letters 2000.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledWorsleyFriston(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double p = -1.0;
        for (IndependenceTest independenceTest : independenceTests) {
            Set<Node> localCondSet = new HashSet<>();
            if (ResolveSepsetsDci.missingVariable(x, y, condSet, independenceTest)) continue;
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            if (Double.isNaN(result.getPValue())) {
//                p = result.getPValue();
                continue;
            }
            double newp = result.getPValue();
            if (newp > p) {
                p = newp;
            }
        }
        return (p > FastMath.pow(alpha, (1 / (double) independenceTests.size())));
    }

    /**
     * Checks independence from pooled samples using Stouffer et al.'s method
     * <p>
     * See S. A. Stouffer, E. A. Suchman, L. C. Devinney, S. A. Star, and R. M. Williams. The American Soldier: Vol. 1.
     * Adjustment During Army Life. Princeton University Press, 1949.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledStouffer(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double ts = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            ts += ProbUtils.normalQuantile(result.getPValue()) / FastMath.sqrt(independenceTests.size());
        }
        double p = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, FastMath.abs(ts)));
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples using Mudholker and George's method
     * <p>
     * See G. S. Mudholkar and E. O. George. The logit method for combining probabilities. In J. Rustagi, editor,
     * Symposium on Optimizing Method in Statistics, pages 345-366. Academic Press, 1979.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledMudholkerGeorge(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double c = FastMath.sqrt(3 * (5 * independenceTests.size() + 4) / (independenceTests.size() * FastMath.pow(FastMath.PI, 2) * (5 * independenceTests.size() + 2)));
        double tm = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            double pk = result.getPValue();
            if (pk != 0 && pk != 1) {
                tm += -c * FastMath.log(pk / (1 - pk));
            }
        }
        double p = 2.0 * (1.0 - ProbUtils.tCdf(FastMath.abs(tm), 5 * independenceTests.size() + 4));
        return (p > alpha);
    }

    /**
     * The same as isIndepenentPooledMudholkerGeoerge, except that only available independence tests are used.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledMudholkerGeorge2(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        List<Double> pValues = ResolveSepsetsDci.getAvailablePValues(independenceTests, x, y, condSet);
        double c = FastMath.sqrt(3 * (5 * pValues.size() + 4) / (pValues.size() * FastMath.pow(FastMath.PI, 2) * (5 * pValues.size() + 2)));
        double tm = 0.0;
        for (double pk : pValues) {
            tm += -c * FastMath.log(pk / (1 - pk));
        }
        double p = 2.0 * (1.0 - ProbUtils.tCdf(FastMath.abs(tm), 5 * pValues.size() + 4));
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples by taking the average p value
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledAverage(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double sum = 0.0;
        int numTests = 0;

        for (IndependenceTest independenceTest : independenceTests) {
            if (ResolveSepsetsDci.missingVariable(x, y, condSet, independenceTest)) continue;

            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }

            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            double p = result.getPValue();

            if (Double.isNaN(p)) continue;

            sum += p;
            numTests++;
        }


        return (sum / numTests > alpha);
    }

    private static boolean missingVariable(Node x, Node y, Set<Node> condSet, IndependenceTest independenceTest) {
        DataSet dataSet = (DataSet) independenceTest.getData();

        if (ResolveSepsetsDci.isMissing(x, dataSet)) {
            return true;
        }

        if (ResolveSepsetsDci.isMissing(y, dataSet)) {
            return true;
        }

        for (Node z : condSet) {
            if (ResolveSepsetsDci.isMissing(z, dataSet)) {
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
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledAverageTest(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        double ts = 0.0;
        int df = 0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (!(independenceTest instanceof IndTestChiSquare)) {
                throw new RuntimeException("Must be ChiSquare Test");
            }
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            ts += ((IndTestChiSquare) independenceTest).getChiSquare() / independenceTests.size();
            df += ((IndTestChiSquare) independenceTest).getDf();
        }
        df = df / independenceTests.size();
        double p = 1.0 - ProbUtils.chisqCdf(ts, df);
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples by randomly selecting a p value
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledRandom(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        double alpha = independenceTests.get(0).getAlpha();
        int r = RandomUtil.getInstance().nextInt(independenceTests.size());
        IndependenceTest independenceTest = independenceTests.get(r);
        Set<Node> localCondSet = new HashSet<>();
        for (Node node : condSet) {
            localCondSet.add(independenceTest.getVariable(node.getName()));
        }
        IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
        double p = result.getPValue();
        return (p > alpha);
    }

    /**
     * Generates NodePairs of all possible pairs of nodes from given list of nodes.
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<NodePair> allNodePairs(List<Node> nodes) {
        List<NodePair> nodePairs = new ArrayList<>();
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
     */
    private static boolean isIndependentMajorityFdr(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        List<Double> allPValues = ResolveSepsetsDci.getAvailablePValues(independenceTests, x, y, condSet);

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
            String message = "***FDR judges " + LogUtilsSearch.independenceFact(x, y, condSet) + " independent";
            TetradLogger.getInstance().forceLogMessage(message);
        } else {
            String message = "###FDR judges " + LogUtilsSearch.independenceFact(x, y, condSet) + " dependent";
            TetradLogger.getInstance().forceLogMessage(message);
        }
        TetradLogger.getInstance().forceLogMessage("c = " + c);

        return independent;
    }

    private static List<Double> getAvailablePValues(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        List<Double> allPValues = new ArrayList<>();

        for (IndependenceTest test : independenceTests) {
            if (ResolveSepsetsDci.missingVariable(x, y, condSet, test)) continue;
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(test.getVariable(node.getName()));
            }

            try {
                IndependenceResult result = test.checkIndependence(test.getVariable(x.getName()), test.getVariable(y.getName()), localCondSet);
                allPValues.add(result.getPValue());
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
     */
    private static boolean isIndependentMajorityIndep(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        List<Double> allPValues = ResolveSepsetsDci.getAvailablePValues(independenceTests, x, y, condSet);

        Collections.sort(allPValues);
        int c = 0;
        while (c < allPValues.size() && allPValues.get(c) < independenceTests.get(0).getAlpha()) {
            c++;
        }


        // At least half of the judgments are for independence.
//        boolean independent = c < 40;
        boolean independent = c < allPValues.size() / 2;

        if (independent) {
            String message = "***Majority = " + LogUtilsSearch.independenceFact(x, y, condSet) + " independent";
            TetradLogger.getInstance().forceLogMessage(message);
        } else {
            String message = "###Majority = " + LogUtilsSearch.independenceFact(x, y, condSet) + " dependent";
            TetradLogger.getInstance().forceLogMessage(message);
        }
        TetradLogger.getInstance().forceLogMessage("c = " + c);

        return independent;
    }

    /**
     * A method for combining p values.
     */
    public enum Method {
        /**
         * The fisher method.
         */
        fisher,
        /**
         * The fisher method with only available p values.
         */
        fisher2,

        /**
         * The tippett method.
         */
        tippett,

        /**
         * The wilkinson method.
         */
        worsleyfriston,

        /**
         * The stouffer method.
         */
        stouffer,

        /**
         * The mudholkergeorge method.
         */
        mudholkergeorge,

        /**
         * The mudholkergeorge2 method.
         */
        mudholkergeorge2,

        /**
         * `The average method.
         */
        average,

        /**
         * The average test method.
         */
        averagetest,

        /**
         * The random method.
         */
        random,
        /**
         * The fdr method.
         */
        fdr,

        /**
         * The majority method.
         */
        majority
    }
}



