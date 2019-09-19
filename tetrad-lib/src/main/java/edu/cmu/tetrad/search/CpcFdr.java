///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;

/**
 * Finds the graph using PC or some variant. This can be merged with PC-All.
 *
 * @author Joseph Ramsey.
 */
public class CpcFdr implements GraphSearch {

    /**
     * The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
     * x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
     */
    private Graph graph;

    /**
     * The independence test. This should be appropriate to the types
     */
    private IndependenceTest test;

    /**
     * Knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The logger, by default the empty logger.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;

    /**
     * Verbose output is sent here.
     */
    private PrintStream out = System.out;

    /**
     * The elapsed time in milliseconds.
     */
    private long elapsedtime = 0;

    /**
     * The FDR q to use for the orientation search.
     */
    private double fdrQ = 0.05;


    //==========================CONSTRUCTORS=============================//

    public CpcFdr(IndependenceTest test) {
        this.test = test;
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Finds the graph using PC or some variant.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");
        return main();
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0.");
        }

        if (depth == -1) depth = 1000;
        this.depth = depth;
    }

    /**
     * The FDR q to use for the orientation search.
     */
    public void setFdrQ(double fdrQ) {
        if (fdrQ < 0 || fdrQ > 1) {
            throw new IllegalArgumentException("FDR q must be in [0, 1]: " + fdrQ);
        }

        this.fdrQ = fdrQ;
    }

    /**
     * Specification of which edges are forbidden or required.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    @Override
    public long getElapsedTime() {
        return elapsedtime;
    }

    //==============================PRIVATE METHODS======================//

    private Graph main() {
        long start = System.currentTimeMillis();

        findAdjacencies();
        orientTriples();
        applyMeekRules();
        removeUnnecessaryMarks();

        long stop = System.currentTimeMillis();
        this.elapsedtime = stop - start;

        return graph;
    }

    private void findAdjacencies() {
        FasStable fas = new FasStable(test);
        fas.setKnowledge(knowledge);
        fas.setVerbose(verbose);
        this.graph = fas.search();
    }


    private void orientTriples() {
        OrientColliders orientColliders = new OrientColliders(test, OrientColliders.ColliderMethod.CPC);
        orientColliders.setConflictRule(OrientColliders.ConflictRule.PRIORITY);
        orientColliders.setIndependenceDetectionMethod(OrientColliders.IndependenceDetectionMethod.FDR);
        orientColliders.setDepth(depth);
        orientColliders.setFdrQ(fdrQ);
        orientColliders.setVerbose(verbose);
        orientColliders.setOut(out);
        orientColliders.orientTriples(graph);
    }

    private void applyMeekRules() {
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.orientImplied(graph);
    }

    private void removeUnnecessaryMarks() {

        // Remove unnecessary marks.
        for (Triple triple : graph.getUnderLines()) {
            graph.removeUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        for (Triple triple : graph.getAmbiguousTriples()) {
            if (graph.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getX())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (graph.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getZ())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (graph.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getY())
                    && graph.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getY())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }
    }
}

