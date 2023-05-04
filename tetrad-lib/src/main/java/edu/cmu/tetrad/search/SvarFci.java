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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in FCI.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p>
 * This class is based off a copy of FCI.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author Joseph Ramsey
 * @author Choh-Man Teng
 * @author Daniel Malinsky
 */
public final class SvarFci implements GraphSearch {

    /**
     * The PAG being constructed.
     */
    private Graph graph;

    /**
     * The SepsetMap being constructed.
     */
    private SepsetMap sepsets;

    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    private final IndependenceTest independenceTest;

    /**
     * flag for complete rule set, true if it should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed;

    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxPathLength = -1;

    /**
     * The depth for the fast adjacency search.
     */
    private int depth = -1;

    /**
     * The logger to use.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    private double penaltyDiscount = 2;


    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public SvarFci(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //========================PUBLIC METHODS==========================//

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    public Graph search() {
        getIndependenceTest().getVariables();
        return search(new TsFas(getIndependenceTest()));
//        return search(new Fas(getIndependenceTest()));
    }


    public Graph search(IFas fas) {
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        fas.setKnowledge(getKnowledge());
        fas.setDepth(this.depth);
        fas.setVerbose(this.verbose);
        this.graph = fas.search();
        this.sepsets = fas.getSepsets();

        this.graph.reorientAllWith(Endpoint.CIRCLE);

        SepsetProducer sp = new SepsetsPossibleDsep(this.graph, this.independenceTest, this.knowledge, this.depth, this.maxPathLength);
        sp.setVerbose(this.verbose);

        if (isPossibleDsepSearchDone()) {
            SvarFciOrient svarFciOrient = new SvarFciOrient(new SepsetsSet(this.sepsets, this.independenceTest), this.independenceTest);
            svarFciOrient.setKnowledge(this.knowledge);
            svarFciOrient.ruleR0(this.graph);

            for (Edge edge : new ArrayList<>(this.graph.getEdges())) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                List<Node> sepset = sp.getSepset(x, y);

                if (sepset != null) {
                    this.graph.removeEdge(x, y);
                    this.sepsets.set(x, y, sepset);


                    System.out.println("Possible DSEP Removed " + x + "--- " + y + " sepset = " + sepset);

                    // This is another added component to enforce repeating structure, specifically for possibleDsep
                    removeSimilarPairs(getIndependenceTest(), x, y, sepset); // added 4.27.2016
                }
            }

            // Reorient all edges as o-o.
            this.graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)
        long time5 = MillisecondTimes.timeMillis();
        //fciOrientbk(getKnowledge(), graph, independenceTest.getVariable());    - Robert Tillman 2008

        long time6 = MillisecondTimes.timeMillis();
        this.logger.log("info", "Step CI C: " + (time6 - time5) / 1000. + "s");

        SvarFciOrient fciOrient = new SvarFciOrient(new SepsetsSet(this.sepsets, this.independenceTest), this.independenceTest);

        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.ruleR0(this.graph);
        fciOrient.doFinalOrientation(this.graph);

        return this.graph;
    }

    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public boolean isPossibleDsepSearchDone() {
        return true;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of unlimited.
     */
    public int getMaxPathLength() {
        return this.maxPathLength == Integer.MAX_VALUE ? -1 : this.maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    //===========================PRIVATE METHODS=========================//

    // removeSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    // this version removes edges from graph instead of list of adjacencies
    private void removeSimilarPairs(IndependenceTest test, Node x, Node y, List<Node> condSet) {
        System.out.println("Entering removeSimilarPairs method...");
        System.out.println("original independence: " + x + " and " + y + " conditional on " + condSet);

        if (x.getName().equals("time") || y.getName().equals("time")) {
            System.out.println("Not removing similar pairs b/c variable pair includes time.");
            return;
        }

        for (Node tempNode : condSet) {
            if (tempNode.getName().equals("time")) {
                System.out.println("Not removing similar pairs b/c conditioning set includes time.");
                return;
            }
        }

        int ntiers = this.knowledge.getNumTiers();
        int indx_tier = this.knowledge.isInWhichTier(x);
        int indy_tier = this.knowledge.isInWhichTier(y);
        int tier_diff = FastMath.max(indx_tier, indy_tier) - FastMath.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List<String> tier_x = this.knowledge.getTier(indx_tier);
        List<String> tier_y = this.knowledge.getTier(indy_tier);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }
        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (this.knowledge.getTier(i).size() == 1) continue;

            String A;
            Node x1;
            String B;

            Node y1;
            if (indx_tier >= indy_tier) {
                List<String> tmp_tier1 = this.knowledge.getTier(i + tier_diff);
                List<String> tmp_tier2 = this.knowledge.getTier(i);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                this.graph.removeEdge(x1, y1);
                System.out.println("removed edge between " + x1 + " and " + y1 + " because of structure knowledge");
                List<Node> condSetAB = new ArrayList<>();
                for (Node tempNode : condSet) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    int ind_temptier = this.knowledge.isInWhichTier(tempNode);
                    List<String> temptier = this.knowledge.getTier(ind_temptier);
                    int ind_temp = -1;

                    for (int j = 0; j < temptier.size(); ++j) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(temptier.get(j)))) {
                            ind_temp = j;
                            break;
                        }
                    }

                    int cond_diff = indx_tier - ind_temptier;
                    int condAB_tier = this.knowledge.isInWhichTier(x1) - cond_diff;

                    if (condAB_tier < 0 || condAB_tier > (ntiers - 1)
                            || this.knowledge.getTier(condAB_tier).size() == 1) { // added condition for time tier 05.29.2016
                        System.out.println("Warning: For nodes " + x1 + "," + y1 + " the conditioning variable is outside "
                                + "of window, so not added to SepSet");
                        continue;
                    }
                    List<String> new_tier = this.knowledge.getTier(condAB_tier);
                    String tempNode1 = new_tier.get(ind_temp);
                    System.out.println("adding variable " + tempNode1 + " to SepSet");
                    condSetAB.add(test.getVariable(tempNode1));
                }
                System.out.println("done");
                getSepsets().set(x1, y1, condSetAB);
            } else {
                List<String> tmp_tier1 = this.knowledge.getTier(i);
                List<String> tmp_tier2 = this.knowledge.getTier(i + tier_diff);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = test.getVariable(A);
                y1 = test.getVariable(B);
                this.graph.removeEdge(x1, y1);
                System.out.println("removed edge between " + x1 + " and " + y1 + " because of structure knowledge");
                List<Node> condSetAB = new ArrayList<>();
                for (Node tempNode : condSet) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    int ind_temptier = this.knowledge.isInWhichTier(tempNode);
                    List<String> temptier = this.knowledge.getTier(ind_temptier);
                    int ind_temp = -1;

                    for (int j = 0; j < temptier.size(); ++j) {
                        if (getNameNoLag(tempNode.getName()).equals(getNameNoLag(temptier.get(j)))) {
                            ind_temp = j;
                            break;
                        }
                    }

                    int cond_diff = indx_tier - ind_temptier;
                    int condAB_tier = this.knowledge.isInWhichTier(x1) - cond_diff;

                    if (condAB_tier < 0 || condAB_tier > (ntiers - 1)
                            || this.knowledge.getTier(condAB_tier).size() == 1) { // added condition for time tier 05.29.2016
                        System.out.println("Warning: For nodes " + x1 + "," + y1 + " the conditioning variable is outside "
                                + "of window, so not added to SepSet");
                        continue;
                    }

                    List<String> new_tier = this.knowledge.getTier(condAB_tier);
                    String tempNode1 = new_tier.get(ind_temp);
                    System.out.println("adding variable " + tempNode1 + " to SepSet");
                    condSetAB.add(test.getVariable(tempNode1));
                }
                System.out.println("done");
                getSepsets().set(x1, y1, condSetAB);
            }
        }
    }

    public String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }
}




