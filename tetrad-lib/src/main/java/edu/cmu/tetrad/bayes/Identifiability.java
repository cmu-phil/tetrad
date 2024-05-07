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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Identifiability, based on RowSummingExactUpdater
 * <p>
 * Jin Tian and Judea Pearl.  On the Identification of Causal Effects. Technical Report R-290-L, Department of Computer
 * Science, University of California, Los Angeles, 2002.
 *
 * @author Choh Man Teng
 * @version $Id: $Id
 */
public final class Identifiability implements ManipulatingBayesUpdater {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The BayesIm which this updater modifies.
     */
    private final BayesIm bayesIm;

    /**
     * The last manipulated BayesIm.
     */
    private final boolean debug = false;
    /**
     * Stores evidence for all variables.
     */
    private Evidence evidence;
    /**
     * The last manipulated BayesIm.
     */
    private BayesIm manipulatedBayesIm;


    //==============================CONSTRUCTORS===========================//

    /**
     * Constructs a new updater for the given Bayes net.
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public Identifiability(BayesIm bayesIm) {
        this(bayesIm, Evidence.tautology(bayesIm));
    }

    /////////////////////////////////////////////////////////////////
    // Constructs a new updater with misc tests.
    // debug indicates debug information is to be generated
    //

    /**
     * <p>Constructor for Identifiability.</p>
     *
     * @param bayesIm  a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param evidence a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public Identifiability(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(evidence);
        //setTargetProp(targetProp);

        // misc tests for private methods
        if (this.debug) {
            int[] cComponents = getCComponents(bayesIm);
            printCComponents(cComponents);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.bayes.Identifiability} object
     */
    public static Identifiability serializableInstance() {
        return new Identifiability(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /////////////////////////////////////////////////////////////////

    /**
     * The BayesIm that this updater bases its update on. This BayesIm is not modified; rather, a new BayesIm is created
     * and updated.
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    /////////////////////////////////////////////////////////////////

    /**
     * <p>Getter for the field <code>manipulatedBayesIm</code>.</p>
     *
     * @return the updated BayesIm.
     */
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    /////////////////////////////////////////////////////////////////

    /**
     * <p>getManipulatedGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    /////////////////////////////////////////////////////////////////

    /**
     * The updated BayesIm. This is a different object from the source BayesIm.
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @see #getBayesIm
     */
    public BayesIm getUpdatedBayesIm() {
        return null;
    }

    /////////////////////////////////////////////////////////////////

    /**
     * <p>Getter for the field <code>evidence</code>.</p>
     *
     * @return a defensive copy of the evidence.
     */
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    /////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     */
    public void setEvidence(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.isIncompatibleWith(this.bayesIm)) {
            throw new IllegalArgumentException("The variable list for the " +
                                               "given bayesIm must be compatible with the variable list " +
                                               "for this evidence.");
        }

        this.evidence = evidence;

        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);

        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);
        /*
      The BayesIm after update, if this was calculated.

      */
    }

    /////////////////////////////////////////////////////////////////

    /**
     * <p>isJointMarginalSupported.</p>
     *
     * @return a boolean
     */
    public boolean isJointMarginalSupported() {
        return true;
    }

    ////////////////////////////////////////////////////////////////
    // compute P_t(s):
    // s is given by the variable-value pairs in the argument
    // t is given by the evidence
    //
    // t and s should each be only one combination of variable values
    // (i.e., no disjunctions of values of a variable even those they
    // are allowed in Proposition)
    //

    /**
     * <p>getJointMarginal.</p>
     *
     * @param sVariables an array of {@link int} objects
     * @param sValues    an array of {@link int} objects
     * @return a double
     */
    public double getJointMarginal(int[] sVariables, int[] sValues) {
        if (sVariables.length != sValues.length) {
            throw new IllegalArgumentException("Values must match variables.");
        }

        //////////////////////////////////////
        // collect s and t variables

        // the s variables
        List<Node> sNodes = new ArrayList<>();
        for (int sVariable : sVariables) {
            sNodes.add(this.bayesIm.getNode(sVariable));
        }

        if (this.debug) {
            System.out.println("\nsVariables: " + sNodes);
        }

        List<Node> tNodesTmp = this.evidence.getVariablesInEvidence();

        // no evidence (tNodes is empty):
        // P_t(s) is identifiable and can be computed directly using
        //    any other regular updater (here: RowSummingExactUpdater)
        if (tNodesTmp.size() == 0) {

            Proposition prop = Proposition.tautology(this.bayesIm);
            for (int i = 0; i < sVariables.length; i++) {
                prop.setCategory(sVariables[i], sValues[i]);
            }

            // restrict the proposition to only observed variables
            Proposition propObs =
                    new Proposition(((MlBayesImObs) this.bayesIm).getBayesImObs(), prop);

            return ((MlBayesImObs) this.bayesIm).getJPD().getProb(propObs);
        }

        // recast the t variables
        List<Node> tNodes = new ArrayList<>();
        for (Node node1 : tNodesTmp) {
            tNodes.add(this.bayesIm.getNode(node1.getName()));
        }

        if (this.debug) {
            System.out.println("\ntVariables: " + tNodes);
        }

        //////////////////////////////////////
        // collect variable values that are set in S and T

        int nNodes = this.bayesIm.getNumNodes();

        int[] fixedVarValues = new int[nNodes];

        for (int i = 0; i < nNodes; i++) {
            fixedVarValues[i] = -1;   // value not set
        }

        // incorporate values of S
        for (int i = 0; i < sVariables.length; i++) {
            fixedVarValues[sVariables[i]] = sValues[i];
        }

        // incorporate values of T
        // assume all the variables are manipulated
        for (int i = 0; i < this.evidence.getNumNodes(); i++) {
            int tNodeValue = this.evidence.getProposition().getSingleCategory(i);
            if (tNodeValue != -1)  // only consider variables with a single value
            {
                Node tNode = this.evidence.getNode(i);
                String tNodeStr = this.evidence.getNode(i).getName();

                Node tNodeInBayesIm = this.bayesIm.getNode(tNodeStr);
                int tIndexInBayesIm = this.bayesIm.getNodeIndex(tNodeInBayesIm);

                String tCategoryStr =
                        this.evidence.getCategory(tNode, tNodeValue);

                int tValueInBayesIm = this.bayesIm.getBayesPm().getCategoryIndex(
                        tNodeInBayesIm,
                        tCategoryStr
                );

                int oldValue = fixedVarValues[tIndexInBayesIm];
                if (oldValue != -1)   // S has a value for this variable
                {
                    if (oldValue == tValueInBayesIm)
                    // remove the variable from S (S and T should be disjoint)
                    {
                        sNodes.remove(tNodeInBayesIm);
                        if (this.debug) {
                            System.out.println("sNode removed: index: "
                                               + tNodeInBayesIm
                                               + "; name: " + tNodeStr);
                        }
                    } else
                    // S and T have different values for this same variable
                    {
                        return 0.0;    // S and T inconsistent: Prob = 0
                    }

                } else   // no value for this variable yet
                {
                    fixedVarValues[tIndexInBayesIm] = tValueInBayesIm;
                }
            }
        }

        if (this.debug) {
            System.out.print("fixedVarValues: ");
            for (int i = 0; i < nNodes; i++) {
                System.out.print(fixedVarValues[i] + "  ");
            }
            System.out.println();
        }

        // if all nodes in S are removed
        if (sNodes.size() == 0) {
            return 1.0; // Prob = 1
        }

        //////////////////////////////////////
        // get c-components

        int[] cComponents = getCComponents(this.bayesIm);
        int nCComponents = nCComponents(cComponents);

        // store the nodes in each c-component

        List<List<Node>> cComponentNodes = new LinkedList<>();

        for (int i = 0; i < nCComponents; i++) {
            cComponentNodes.add(getCComponentNodes(this.bayesIm, cComponents, i));
        }

        if (this.debug) {
            for (int i = 0; i < nCComponents; i++) {
                System.out.println("c-component " + i + ": " +
                                   cComponentNodes.get(i));
            }
        }

        //////////////////////////////////////

        // allowUnfaithfulness joint probability of all the measured variables
        int[] probTermV = new int[nNodes];
        for (int i = 0; i < nNodes; i++) {
            if (this.bayesIm.getNode(i).getNodeType() == NodeType.MEASURED) {
                probTermV[i] = 1;
            }
        }

        if (this.debug) {
            System.out.print("probTermV: ");
            for (int i = 0; i < nNodes; i++) {
                System.out.print(probTermV[i] + "   ");
            }
            System.out.println();
        }

        // get C-factors
        QList[] cFactors = new QList[nCComponents];

        for (int i = 0; i < nCComponents; i++) {
            // Q[V]: allowUnfaithfulness joint probTermV
            QList qV = new QList(nNodes, probTermV);

            if (this.debug) {
                System.out.println("cFactors " + i + "   " +
                                   this.bayesIm.getDag().getNodes() + "   " +
                                   cComponentNodes.get(i)
                );
                System.out.println("============== QList: qV ==============");
                qV.printQList(0, 0);
            }

            cFactors[i] = qDecomposition(
                    this.bayesIm,
                    this.bayesIm.getDag().getNodes(),
                    cComponentNodes.get(i),
                    qV
            );
            if (this.debug) {
                System.out.println("============== QList: cFactors[" + i +
                                   "] ==============");
                cFactors[i].printQList(0, 0);
            }
        }

        //////////////////////////////////////

        // get D
        // Note: "dag" is a new copy of the dag; otherwise modifications
        //			would be made to the one in the bayesIm
        // Note: the ordering of the nodes may not be the same as in
        //			the original graph
        Dag dag = new Dag(this.bayesIm.getDag());

        if (this.debug) {
            System.out.println("------ here1 -------------");
            System.out.println(this.bayesIm.getDag());
        }

        dag.removeNodes(tNodes);

        if (this.debug) {
            System.out.println("------ here2 -------------");
            System.out.println(this.bayesIm.getDag());
        }

        List<Node> dNodes = dag.paths().getAncestors(sNodes);

        // create a Bayes IM with the dag G_dNodes
        Dag gD = new Dag(this.bayesIm.getDag().subgraph(dNodes));

        BayesPm bayesPmD = new BayesPm(gD, this.bayesIm.getBayesPm());
        BayesIm bayesImD = new MlBayesIm(bayesPmD, this.bayesIm, MlBayesIm.InitializationMethod.RANDOM);

        if (this.debug) {
            System.out.println("------ bayeIm.getDag() -------------");
            System.out.println(this.bayesIm.getDag());
            System.out.println("------ gD -------------");
            System.out.println(gD);
            System.out.println("------ bayeImD.getDag() -------------");
            System.out.println(bayesImD.getDag());
            System.out.println("bayesIm node 0: " + this.bayesIm.getNode(0));
            System.out.println("bayesImD node 0: " + bayesImD.getNode(0));
        }

        // get c-components of gD
        int[] cComponentsD = getCComponents(bayesImD);
        int nCComponentsD = nCComponents(cComponentsD);

        // Q[Di]
        QList[] qD = new QList[nCComponentsD];

        for (int i = 0; i < nCComponentsD; i++) {
            // Di
            List<Node> cComponentNodesDi
                    = getCComponentNodes(bayesImD, cComponentsD, i);

            // Sj
            // Find the index j of the c-component Sj in cComponentNodes
            // which is a superset of cComponentNodesDi
            //
            int j = 0;
            boolean flag = false;
            while ((j < nCComponents) && !flag) {
                List<Node> cComponentNodesSj = cComponentNodes.get(j);
                if (cComponentNodesSj.containsAll(cComponentNodesDi)) {
                    if (this.debug) {
                        System.out.println("----- Di   Sj --------");
                        System.out.println(i + "   " + cComponentNodesDi
                                           + "    " + cComponentNodesSj);
                    }

                    flag = true;
                    qD[i] = identify(cComponentNodesDi,
                            cComponentNodesSj,
                            cFactors[j]
                    );

                    // fail: qD[i] not identifiable with this algorithm
                    if (qD[i] == null) {
                        if (this.debug) {
                            System.out.println("----- FAIL qD[" + i
                                               + "] --------");
                        }
                        // fail: P_t(s) not identifiable with this algorithm
                        return -1.0;
                    }

                    if (this.debug) {
                        System.out.println("======================== QList: qD["
                                           + i + "] =================");
                        qD[i].printQList(0, 0);
                    }
                }
                j++;
            }

            if (!flag)  // something is wrong
            {
                throw
                        new RuntimeException("getJointMarginal: Sj not found");
            }
        }

        //////////////////////////////////////

        // multiply the Q[Di]'s
        QList qDProducts = new QList(nNodes);

        int[] sumOverVariables = new int[nNodes];
        for (int i = 0; i < nNodes; i++) {
            sumOverVariables[i] = 0;
        }

        for (int i = 0; i < nCComponentsD; i++) {
            qDProducts.add(qD[i], sumOverVariables, true);
        }

        // P_t(s)
        QList qPTS = new QList(nNodes);
        qPTS.add(qDProducts, sumList(nNodes, dNodes, sNodes), true);


        if (this.debug) {
            System.out.println("***************************** QList: qPTS *******************");
            qPTS.printQList(0, 0);
        }

        // compute numeric value from the algebraic expression qPTS
        if (this.debug) {
            System.out.println("***************************** computeValue *******************");
        }
        return qPTS.computeValue(this.bayesIm, fixedVarValues);
    }

    /////////////////////////////////////////////////////////////////
    // The following methods are for here for compatibility with ManipulatingBayesUpdater
    //

    /**
     * {@inheritDoc}
     */
    public double getMarginal(int variable, int value) {
        int[] sVariables = {variable};
        int[] sValues = {value};
        return getJointMarginal(sVariables, sValues);
    }

    /////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     */
    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence evidence = getEvidence();
        setEvidence(Evidence.tautology(evidence.getVariableSource()));

        //double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        // nodeIndex might be for another bayesIm if we have made changes
        // to the graph upstream

        double[] marginals = new double[getBayesIm().getNumColumns(nodeIndex)];

        for (int i = 0; i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        setEvidence(evidence);
        return marginals;
    }

    /////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     */
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        //double[] marginals = new double[evidence.getNumCategories(nodeIndex)];
        double[] marginals = new double[getBayesIm().getNumColumns(nodeIndex)];

        for (int i = 0; i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        return marginals;
    }


    /////////////////////////////////////////////////////////////////

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Identifiability";
    }

    /////////////////////////////////////////////////////////////////

    /**
     * Set the target proposition.
     */

    //==============================PRIVATE METHODS=======================//


    /////////////////////////////////////////////////////////////////
    //  Get the c-components (Huang and Valtorta 2006)
    //
    private int[] getCComponents(BayesIm bayesIm) {
        int nNodes = bayesIm.getNumNodes();

        int[] cComponents1 = new int[nNodes];
        int[] cComponents2 = new int[nNodes];

        // initialize so that each node is in a separate component
        for (int i = 0; i < nNodes; i++) {
            cComponents1[i] = i;
        }

        // merge nodes into c-components
        for (int nodeIndex = 0; nodeIndex < nNodes; nodeIndex++) {
            int cComponentIndexNew = cComponents1[nodeIndex];

            int nParents = bayesIm.getNumParents(nodeIndex);
            for (int i = 0; i < nParents; i++) {
                int parentIndex = bayesIm.getParent(nodeIndex, i);
                if (bayesIm.getNode(parentIndex).getNodeType() ==
                    NodeType.LATENT
                ) {
                    int cComponentIndexOld = cComponents1[parentIndex];
                    if (cComponentIndexOld != cComponentIndexNew) {
                        for (int j = 0; j < nNodes; j++) {
                            if (cComponents1[j] == cComponentIndexOld) {
                                cComponents1[j] = cComponentIndexNew;
                            }
                        }
                    }
                }
            }
        }

        // renumber the c-components
        int cComponentIndexNew = 0;
        for (int i = 0; i < nNodes; i++) {
            boolean hasValue = false;
            int cComponentIndexOld = cComponents1[i];

            int j = 0;
            while ((j < i) && !hasValue) {
                // check if node i belongs to a c-component already encountered
                if (cComponents1[j] == cComponentIndexOld) {
                    cComponents2[i] = cComponents2[j];
                    hasValue = true;
                }

                j++;
            }

            // start a new c-component
            if (!hasValue) {
                cComponents2[i] = cComponentIndexNew++;
            }
        }

        return cComponents2;
    }

    /////////////////////////////////////////////////////////////////
    // Return the number of c-components
    //
    private int nCComponents(int[] cComponents) {
        int currentMax = -1;
        for (int cComponent : cComponents) {
            if (cComponent > currentMax) {
                currentMax = cComponent;
            }
        }

        return currentMax + 1;
    }

    /////////////////////////////////////////////////////////////////
    //  Return the index-th c-component as a list of nodes
    //
    private List<Node> getCComponentNodes(
            BayesIm bayesIm,
            int[] cComponents,
            int index) {
        List<Node> nodeList = new ArrayList<>();
        for (int i = 0; i < cComponents.length; i++) {
            if (cComponents[i] == index) {
                nodeList.add(bayesIm.getNode(i));
            }
        }

        return nodeList;
    }

    /////////////////////////////////////////////////////////////////
    //  Print c-components
    //
    private void printCComponents(int[] cComponents) {
        System.out.println("----- printCComponents: total " +
                           nCComponents(cComponents) + " -----");

        for (int i = 0; i < cComponents.length; i++) {
            System.out.println(i + ": " + cComponents[i]);

        }
    }

    /////////////////////////////////////////////////////////////////
    // Compute generalized Q-decomposition (Tian and Pearl 2002, Lemma 4)
    //
    // hj is a c-component in subgraph graphWhole_h
    // qH is the q-factor for h
    //
    // return q-factor of hj
    //

    // ??? may not need to pass graphWhole as argument ???

    private QList qDecomposition(BayesIm graphWhole,
                                 List<Node> h,
                                 List<Node> hj,
                                 QList qH) {
        Dag graphH = new Dag(graphWhole.getDag().subgraph(h));

        // tier ordering
        Paths paths = graphH.paths();
        List<Node> initialOrder = graphH.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);

        // convert to the indices of the original graph
        // (from which the subgraph was obtained)
        int tierSize = tierOrdering.size();
        int[] tiers = new int[tierSize];
        for (int i = 0; i < tierSize; i++) {
            tiers[i] =
                    graphWhole.getNodeIndex(tierOrdering.get(i));
        }

        if (this.debug) {
            System.out.print("************************* QDecomposition: Tier ordering: ");
            for (int i = 0; i < tierSize; i++) {
                System.out.print(graphWhole.getNode(tiers[i]) + "  ");
            }
            System.out.println();
        }

        int nNodes = graphWhole.getNumNodes();
        QList qHj = new QList(nNodes);

        for (Node nodeHj : hj) {
            // index of node hj in the original graph
            int nodeHjIndex = graphWhole.getNodeIndex(nodeHj);

            if (graphWhole.getNode(nodeHjIndex).getNodeType() ==
                NodeType.MEASURED)  // skip latent variables
            {
                // get index of node hj in the tier ordering of the nodes
                //of the original graph
                int nodeHjTierIndex;

                for (nodeHjTierIndex = 0;
                     nodeHjTierIndex < tierSize;
                     nodeHjTierIndex++
                ) {
                    if (tiers[nodeHjTierIndex] == nodeHjIndex) {
                        break;
                    }
                }

                if (nodeHjTierIndex == tierSize) {
                    throw
                            new RuntimeException("qDecomposition: index out of bound");
                }

                // Q[H^i]
                int[] sumOverVariables = new int[nNodes];
                for (int i = 0; i < nNodes; i++) {
                    sumOverVariables[i] = 0;
                }
                for (int i = nodeHjTierIndex + 1; i < tierSize; i++) {
                    if (graphWhole.getNode(tiers[i]).getNodeType() ==
                        NodeType.MEASURED
                    ) {
                        sumOverVariables[tiers[i]] = 1;
                    }
                }

                qHj.add(qH, sumOverVariables, true);

                if (this.debug) {
                    System.out.println("************* QDecomposition: Q[H^i], sumOverVariables: ");
                    for (int i = 0; i < nNodes; i++) {
                        System.out.print(sumOverVariables[i] + "   ");
                    }
                    System.out.println();
                }

                // Q[H^{i-1}]
                sumOverVariables[tiers[nodeHjTierIndex]] = 1;
                qHj.add(qH, sumOverVariables, false);

                if (this.debug) {
                    System.out.println("************* QDecomposition: Q[H^{i-1}], sumOverVariables: ");
                    for (int i = 0; i < nNodes; i++) {
                        System.out.print(sumOverVariables[i] + "   ");
                    }
                    System.out.println();
                }
            }
        }

        return qHj;
    }

    /////////////////////////////////////////////////////////////////
    // construct the variables to be summed over in the Q-factor
    // (bigSet minus smallSet)
    //
    private int[] sumList(int nNodes, List<Node> bigSet, List<Node> smallSet) {
        int[] sumOverVariables = new int[nNodes];
        for (int i = 0; i < nNodes; i++) {
            sumOverVariables[i] = 0;
        }

        for (Node node1 : bigSet) {
            if (!smallSet.contains(node1) &&
                node1.getNodeType() == NodeType.MEASURED) {
                sumOverVariables[this.bayesIm.getNodeIndex(node1)] = 1;
            }
        }

        return sumOverVariables;
    }

    /////////////////////////////////////////////////////////////////
    // identify
    //
    private QList identify(List<Node> nodesC,
                           List<Node> nodesT,
                           QList qT) {
        Dag graphT = new Dag(this.bayesIm.getDag().subgraph(nodesT));

        List<Node> nodesA = graphT.paths().getAncestors(nodesC);

        int nNodes = this.bayesIm.getNumNodes();
        QList qC = new QList(nNodes);


        if (this.debug) {
            System.out.println("-------------- identify -------------");
            System.out.println("----- bayesIm.getDag() -----");
            System.out.println(this.bayesIm.getDag());
            System.out.println("----- graphT -----");
            System.out.println(graphT);
            System.out.println("nodesC: " + nodesC);
            System.out.println("nodesT: " + nodesT);
            System.out.println("nodesA: " + nodesA);
            System.out.println("nodesC containsAll nodesA: " +
                               nodesC.containsAll(nodesA));
            System.out.println("nodesA containsAll nodesT: " +
                               nodesA.containsAll(nodesT));
        }

        /////////////////////////////////
        // Should be: "if (nodesA.equals(nodesC))"
        // but equals returns false when nodesA and nodesC contain
        // the nodes but in different orders
        //
        // equivalent to
        // (nodesC.containsAll(nodesA)) && (nodesA.containsAll(nodesC))
        // but the second conjunct is trivially satisified
        //
        // We do not have to worry about the order of the variables
        // when checking for the subset instead of equality relation
        if (nodesC.containsAll(nodesA)) {
            qC.add(qT, sumList(nNodes, nodesT, nodesC), true);

            if (this.debug) {
                System.out.println("***************** identify: QList: qC *****************");
                qC.printQList(0, 0);
            }

            return qC;
        }

        /////////////////////////////////
        // else if (nodesA.equals(nodesT))
        // (see comments for the first "if" branch above)
        else if (nodesA.containsAll(nodesT)) {
            if (this.debug) {
                System.out.println("----- FAIL: identify -----");
            }

            return null;   // fail
        }

        /////////////////////////////////
        // must be: nodesC subset A subset T
        Dag graphA = new Dag(this.bayesIm.getDag().subgraph(nodesA));

        // construct an IM with the dag graphA
        BayesPm bayesPmA = new BayesPm(graphA, this.bayesIm.getBayesPm());
        BayesIm bayesImA = new MlBayesIm(bayesPmA, this.bayesIm, MlBayesIm.InitializationMethod.RANDOM);

        // get c-components of graphA
        int[] cComponentsA = getCComponents(bayesImA);
        int nCComponentsA = nCComponents(cComponentsA);

        // get Q[A]
        QList qA = new QList(nNodes);
        qA.add(qT, sumList(nNodes, nodesT, nodesA), true);

        if (this.debug) {
            System.out.println("***************** identify: QList: qA *****************");
            qC.printQList(0, 0);
        }

        int i = 0;

        while ((i < nCComponentsA)) {
            List<Node> cComponentNodesT2 =
                    getCComponentNodes(bayesImA, cComponentsA, i);

            if (this.debug) {
                System.out.println("identify Q[A]: i: " + i);
                System.out.println("cComponentNodesT2: " + cComponentNodesT2);
                System.out.println("cComponentNodesT2.containsAll(nodesC): " +
                                   cComponentNodesT2.containsAll(nodesC));
            }

            if (cComponentNodesT2.containsAll(nodesC)) {

                // get Q[T2]
                QList qT2 =
                        qDecomposition(this.bayesIm, nodesA, cComponentNodesT2, qA);

                // recursive call to "identify"
                return identify(nodesC, cComponentNodesT2, qT2);
            }

            i++;
        }

        throw new RuntimeException("identify: T2 not found");
    }


    /////////////////////////////////////////////////////////////////

    private BayesIm createdUpdatedBayesIm(BayesPm updatedBayesPm) {
        return new MlBayesIm(updatedBayesPm, this.bayesIm, MlBayesIm.InitializationMethod.RANDOM);
    }

    private BayesPm createUpdatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, this.bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        // alters graph for manipulated evidenceItems
        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = updatedGraph.getNode(this.evidence.getNode(i).getName());
                List<Node> parents = updatedGraph.getParents(node);

                for (Node parent1 : parents) {
                    updatedGraph.removeEdge(node, parent1);
                }
            }
        }

        return updatedGraph;
    }

    /////////////////////////////////////////////////////////////////

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The input stream to read from.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.bayesIm == null) {
            throw new NullPointerException();
        }

        if (this.evidence == null) {
            throw new NullPointerException();
        }
    }
}




