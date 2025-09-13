package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Erich on 3/27/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimContinuous {
    //Dag mydag, Set<Node> simnodes, DataSet data
    private boolean verbose;
    private Dag mydag;
    private Set<Node> simnodes;
    private DataSet data;

    //************Constructors***************//

    /**
     * <p>Constructor for HsimContinuous.</p>
     *
     * @param thedag      a {@link edu.cmu.tetrad.graph.Dag} object
     * @param thesimnodes a {@link java.util.Set} object
     * @param thedata     a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public HsimContinuous(Dag thedag, Set<Node> thesimnodes, DataSet thedata) {
        if (thedata.isDiscrete()) {
            throw new IllegalArgumentException(
                    "HsimContinuous only accepts continuous data.");
        }
        if (thedag == null) {
            throw new IllegalArgumentException(
                    "Hsim needs a Dag.");
        }
        if (thesimnodes == null) {
            throw new IllegalArgumentException(
                    "Please specify the nodes Hsim will resimulate.");
        }
        // (Optional: Eventually want options for search methods for picking out the DAG)
        setVerbose();
        setDag(thedag);
        setData(thedata);
        setSimnodes(thesimnodes);
    }

    //**************Public methods***********************//

    // Calculates the Markov blanket of a node in a graph.
    private static Set<Node> mb(Graph graph, Node z) {
        Set<Node> mb = new HashSet<>(graph.getAdjacentNodes(z));

        for (Node c : graph.getChildren(z)) {
            for (Node p : graph.getParents(c)) {
                //make sure you don't add z itslef to the markov blanket
                if (p != z) {
                    mb.add(p);
                }
            }
        }

        return mb;
    }


//========================================PRIVATE METHODS====================================//

    /**
     * <p>hybridsimulate.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet hybridsimulate() {
        // Find Markov Blankets for resimulated variables**/
        // this needs to be made general, rather than only for two specific names nodes**/
        if (this.verbose) System.out.println("Finding a Markov blanket for resimulated nodes");
        Set<Node> mbAll = new HashSet<>(); //initialize an empty set of nodes;
        Set<Node> mbAdd = new HashSet<>(); //init set for adding
        for (Node node : this.simnodes) {
            mbAdd = HsimContinuous.mb(this.mydag, node); //find mb for that node
            mbAll.addAll(mbAdd); //use .addAll to add this mb to the set
        }
        //make sure all the simnodes are in mbAll! a disconnected node could cause errors later otherwise
        mbAll.addAll(this.simnodes);

        if (this.verbose) System.out.println("The Markov Blanket is " + mbAll);

        // Find the subgraph for the resimulated variables and their markov blanket**/
        if (this.verbose) System.out.println("Finding a subgraph over the Markov Blanket and Resimulated Nodes");

        //need a List as input for subgraph method, but mbAll is a Set
        List<Node> mbListAll = new ArrayList<>(mbAll);
        Graph subgraph = this.mydag.subgraph(mbListAll);

        // Learn an instantiated model over the subgraph**/
        if (this.verbose) System.out.println("Learning an instantiated model for the subgraph");

        //Do this step continuous instead of discrete:
        //learn a dirichlet IM for the subgraph using dataSet
        SemPm subgraphPM = new SemPm(subgraph);
        SemEstimator subgraphEstimator = new SemEstimator(this.data, subgraphPM);
        SemIm subgraphIM = subgraphEstimator.estimate();

        //if (verbose) System.out.println(fittedsubgraphIM.getVariable());

        // Use the learned instantiated subgraph model to create the resimulated data**/
        if (this.verbose) System.out.println("Starting resimulation loop");

        //Use the BayesIM to learn the conditional marginal distribution of X given mbAll
        //first construct the updater, using RowSummingExactUpdater(BayesIm bayesIm, Evidence evidence)
        //To use that, need to make an Evidence, which in this case is the values of the MB
        //will need to make a new Evidence, and perform the updater, for every row of data.
        //In order to make an Evidence, need to make a Proposition
        //then modify the proposition to fix the values of all conditioned variables using:
        //setCategory(int variable, int category)
        //since setCategory only takes the int values of variable and category, need to figure out what those are
        //can figure out those from the proposition's variable source using:
        //public int getNodeIndex(String name)
        //and
        //public int getCategoryIndex(String nodeName, String category)

        //want the causal ordering of the subgraph:
        //List<Node> subgraphOrdering = GraphUtils.getCausalOrdering(subgraph);

        //loop through each row of the data set, conditioning and drawing values each time.
        for (int row = 0; row < this.data.getNumRows(); row++) {
            //create a new evidence object
            SemEvidence evidence = new SemEvidence(subgraphIM);

            //need to define the set of variables being conditioned upon. Start with the outer set of MB
            //need to remove the whole set of starters, not just some X and Y... how do? loop a .remove?
            for (Node node : this.simnodes) {
                mbAll.remove(node);
            }

            //loop through all the nodes being conditioned upon, and set their values in the evidence prop
            for (Node i : mbAll) {
                //int nodeIndex = evidence.getNodeIndex(i.getName());
                int nodeColumn = this.data.getColumn(i);
                evidence.getProposition().setValue(i, this.data.getDouble(row, nodeColumn));
            }

            //use the new Evidence object to create the updater
            SemUpdater conditionUpdate = new SemUpdater(subgraphIM);
            conditionUpdate.setEvidence(evidence);
            SemIm updatedIM = conditionUpdate.getUpdatedSemIm();
            //draw values for the node we're resimming
            DataSet newValues = updatedIM.simulateData(1, false);
            //DataSet newValues = updatedIM.simulateDataRecursive(1,false);

            //take these new simnodes values and replace the old values in the data set with them
            for (Node node : this.simnodes) {
                //if (verbose) System.out.println(data.getInt(row,data.getColumn(nodeX)) + " old vs new " + newXvalue);
                this.data.setDouble(row, this.data.getColumn(node), newValues.getDouble(0, newValues.getColumn(node)));
                //if (verbose) System.out.println(" and again?: " + data.getInt(row,data.getColumn(nodeX)) + " old vs new " + newXvalue);
            }
        }
        return this.data;
    }

    /// *********Private methods for setting private variables***********//
    private void setVerbose() {
        this.verbose = false;
    }

    private void setDag(Dag thedag) {
        this.mydag = thedag;
    }

    private void setSimnodes(Set<Node> thenodes) {
        this.simnodes = thenodes;
    }

    private void setData(DataSet thedata) {
        this.data = thedata;
    }
}
