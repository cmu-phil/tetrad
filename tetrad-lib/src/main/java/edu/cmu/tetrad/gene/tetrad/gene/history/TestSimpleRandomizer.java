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

package edu.cmu.tetrad.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Iterator;
import java.util.SortedSet;

/**
 * Tests the SimpleRandomizer class by constructing graphs with randomly chosen
 * parameters and seeing if they have the required properties.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TestSimpleRandomizer extends TestCase {
    LagGraph lagGraph = new BasicLagGraph();

    /**
     * Change the name of this constructor to match the name of the test class.
     */
    public TestSimpleRandomizer(String name) {
        super(name);
    }

    /**
     * Sets up a graph to randomize with 100 variables in it.
     */
    public void setUp() {

        int numVars = 100;

        for (int i = 0; i < numVars; i++) {
            lagGraph.addFactor("G" + i);
        }
    }

    /**
     * Tests whether the randomizer can randomly make a graph where all of the
     * factors have the same indegree.
     */
    public void testConstantIndegree() {

        // Pick an indegree.
        int indegree = RandomUtil.getInstance().nextInt(8) + 2;

        // Pick mlag.
        int mlag = RandomUtil.getInstance().nextInt(9) + 1;

        // Pick percent housekeeping.
        double percentHousekeeping =
                RandomUtil.getInstance().nextDouble() * 20.0;

        // Randomize
        SimpleRandomizer simpleRandomizer = new SimpleRandomizer(indegree,
                SimpleRandomizer.CONSTANT, mlag, percentHousekeeping);

        simpleRandomizer.initialize(this.lagGraph);

        // Make sure all nonhousekeeping genes have indegree edges.
        SortedSet factors = lagGraph.getFactors();

        for (Iterator it = factors.iterator(); it.hasNext();) {
            String factor = (String) it.next();
            SortedSet parents = lagGraph.getParents(factor);

            // Make sure it's not a housekeeping gene.
            if (1 != parents.size()) {
                assertEquals(indegree, parents.size());
            }
        }
    }

    /**
     * Tests whether the randomizer can randomly make a graph where the mean
     * ofindegree across factors is the given number.
     */
    public void testMeanIndegree() {

        // Pick an indegree.
        int indegree = RandomUtil.getInstance().nextInt(8) + 2;

        // Pick mlag.
        int mlag = RandomUtil.getInstance().nextInt(9) + 1;

        // Pick percent housekeeping.
        double percentHousekeeping =
                RandomUtil.getInstance().nextDouble() * 20.0;

        // Randomize
        SimpleRandomizer simpleRandomizer = new SimpleRandomizer(indegree,
                SimpleRandomizer.MEAN, mlag, percentHousekeeping);

        simpleRandomizer.initialize(this.lagGraph);

        // Go through graph and check that the nonhousekeeping genes
        // have a mean of indegree edges.
        int sum = 0;
        int numNonHousekeeping = 0;
        SortedSet factors = lagGraph.getFactors();

        for (Iterator it = factors.iterator(); it.hasNext();) {
            String factor = (String) it.next();
            SortedSet parents = lagGraph.getParents(factor);

            if (parents.size() > 1) {
                numNonHousekeeping++;

                sum += parents.size();

                System.out.println("# Nonhousekeeping = " + numNonHousekeeping +
                        " This num parents = " + parents.size() + " sum = " +
                        sum);
            }
        }

        if (numNonHousekeeping > 0) {
            double mean = (double) sum / (double) numNonHousekeeping;

            // The mean of the nonhousekeeping genes should be the
            // specified indegree, to within 0.5.
            assertEquals((double) indegree, mean, 1.2);
        }
    }

    /**
     * Tests whether the randomizer can randomly make a graph where the maximum
     * indegree across factors is the given factor.
     */
    public void testMaxIndegree() {

        // Pick an indegree.
        int indegree = RandomUtil.getInstance().nextInt(8) + 2;

        // Pick mlag.
        int mlag = RandomUtil.getInstance().nextInt(9) + 1;

        // Pick percent housekeeping.
        double percentHousekeeping =
                RandomUtil.getInstance().nextDouble() * 20.0;

        // Randomize
        SimpleRandomizer simpleRandomizer = new SimpleRandomizer(indegree,
                SimpleRandomizer.MAX, mlag, percentHousekeeping);

        simpleRandomizer.initialize(this.lagGraph);

        // Make sure that the maximum number of edges is indegree.
        int max = 0;
        SortedSet factors = lagGraph.getFactors();

        for (Iterator it = factors.iterator(); it.hasNext();) {
            String factor = (String) it.next();
            SortedSet parents = lagGraph.getParents(factor);

            if (parents.size() > max) {
                max = parents.size();
            }
        }

        assertTrue(indegree <= max);
    }

    public void tearDown() {
        lagGraph = null;
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSimpleRandomizer.class);
    }
}





