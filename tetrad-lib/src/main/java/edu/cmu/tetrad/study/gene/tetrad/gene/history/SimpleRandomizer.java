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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Randomizes a graph using existing factors by first removing all edges and then adding for each factor (a) an edge
 * from the same factor at time lag 1 and (b) a given number of factors chosen uniformly from all lagged factors with
 * lag &gt; 0. The number of factors added is chosen according to a strategy set in the constructor. If constant
 * indegree n is chosen, then n - 1 edges are added (in addition to the edge from the same factor at time lag 1) for
 * each factor. If max indegree n in chosen, then an integer is chosen uniformly for each factor from {1, ..., n - 1},
 * and that number of edges is added for that factor. If mean indegree n is chosen, then an integer is chosen uniformly
 * for each factor from {1, ..., 2n - 1}, and that number of edges is added for that factor. Notice that the number of
 * "extra" edges added take account of the fact that one edge has already been added in each case, so that the total
 * indegree for each factor is correctly distributed.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SimpleRandomizer implements GraphInitializer {
    /**
     * Indicates constant indegree.
     */
    public static final int CONSTANT = 0;
    /**
     * Indicates maximum indegree.
     */
    public static final int MAX = 1;
    /**
     * Indicates mean indegree.
     */
    public static final int MEAN = 2;
    private static final long serialVersionUID = 23L;
    /**
     * The indegree type of this randomizer.
     */
    private int indegreeType = SimpleRandomizer.CONSTANT;

    /**
     * The stored indegree for this randomizer (differently interpreted depending on the indegree type).
     */
    private int indegree = 1;

    /**
     * The stored maximum lag.
     */
    private int mlag = 1;

    /**
     * The stored percent houseekeeping. This percent of the genes should be initialized with no parents except for
     * themselves one time lag back.
     */
    private double percentHousekeeping = 80.0;

    /**
     * <p>Constructor for SimpleRandomizer.</p>
     *
     * @param indegree            a int
     * @param indegreeType        a int
     * @param mlag                a int
     * @param percentHousekeeping a double
     */
    public SimpleRandomizer(int indegree, int indegreeType, int mlag,
                            double percentHousekeeping) {

        // Set indegree.
        if (indegree >= 2) {
            this.indegree = indegree;
        } else {
            throw new IllegalArgumentException(
                    "Indegree must be at least 2: " + indegree);
        }

        // Set indegree type.
        switch (indegreeType) {

            case SimpleRandomizer.CONSTANT:

                // Falls through!
            case SimpleRandomizer.MAX:

                // Falls through!
            case SimpleRandomizer.MEAN:
                this.indegreeType = indegreeType;
                break;

            default:
                throw new IllegalArgumentException();
        }

        // Set mlag.
        if (mlag > 0) {
            this.mlag = mlag;
        } else {
            throw new IllegalArgumentException();
        }

        // Set percent housekeeping.
        if ((percentHousekeeping >= 0.0) && (percentHousekeeping <= 100.0)) {
            this.percentHousekeeping = percentHousekeeping;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Randomizes the graph.
     */
    public void initialize(LagGraph lagGraph) {
        lagGraph.clearEdges();

        List factors = new ArrayList(lagGraph.getFactors());

        // Add edges one time step back.
        for (Object value : factors) {
            String factor = (String) value;
            LaggedFactor laggedFactor = new LaggedFactor(factor, 1);

            lagGraph.addEdge(factor, laggedFactor);
        }

        //        System.out.println("Indegree = " + indegree);

        // Add remaining edges for each factor.
        for (Object o : factors) {
            String factor = (String) o;

            // Pick an indegree for this variable
            int extraEdges = 1;

            // Decide whether this is a housekeeping gene and if so
            // don't add any more edges.
            boolean isHousekeeping =
                    RandomUtil.getInstance().nextDouble() * 100 < this.percentHousekeeping;

            if (isHousekeeping) {
                continue;
            }

            // This is not a housekeeping gene, so add more edges.
            switch (this.indegreeType) {

                case SimpleRandomizer.CONSTANT:
                    extraEdges = this.indegree - 1;
                    break;

                case SimpleRandomizer.MAX:
                    extraEdges = RandomUtil.getInstance().nextInt(
                            this.indegree - 1) + 1;
                    break;

                case SimpleRandomizer.MEAN:
                    extraEdges = RandomUtil.getInstance().nextInt(
                            2 * (this.indegree - 1) - 1) + 1;

                    break;

                default:
                    throw new IllegalStateException();
            }

            // Add that number of edges for this factor, randomly chosen.
            int i = 0;

            while (i < extraEdges) {

                // Pick a lag uniformly from {1, ..., mlag}.
                int lag = RandomUtil.getInstance().nextInt(
                        this.mlag) + 1;

                // Pick a factor uniformly from {0, ..., numfactors}.
                int factorIndex = RandomUtil.getInstance().nextInt(
                        lagGraph.getNumFactors());

                // If that edge has not already been added, add it.
                String otherFactor = (String) factors.get(factorIndex);
                LaggedFactor laggedFactor = new LaggedFactor(otherFactor, lag);

                if (!lagGraph.existsEdge(factor, laggedFactor)) {
                    lagGraph.addEdge(factor, laggedFactor);
                    ++i;
                }
            }
        }
    }
}





