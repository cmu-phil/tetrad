package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Erich on 3/27/2016.
 */
public class Hsim {
    //Dag mydag, Set<Node> simnodes, DataSet data
    private boolean verbose;
    private Dag mydag;
    private Set<Node> simnodes;
    private DataSet data;

    //************Constructors***************//

    public Hsim(Dag thedag, Set<Node> thesimnodes, DataSet thedata) {
        if (thedata.isContinuous()) {
            throw new IllegalArgumentException(
                    "Hsim currently only accepts discrete data.");
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
        setVerbose(false);
        setDag(thedag);
        setData(thedata);
        setSimnodes(thesimnodes);
    }

    //**************Public methods***********************//

    public DataSet hybridsimulate() {
        /**Find Markov Blankets for resimulated variables**/
        /**this needs to be made general, rather than only for two specific names nodes**/
        if (verbose) System.out.println("Finding a Markov blanket for resimulated nodes");
        Set<Node> mbAll = new HashSet<Node>(); //initialize an empty set of nodes;
        Set<Node> mbAdd = new HashSet<Node>(); //init set for adding
        for (Node node : simnodes) {
            mbAdd = mb(mydag, node); //find mb for that node
            mbAll.addAll(mbAdd); //use .addAll to add this mb to the set
        }
        //make sure all the simnodes are in mbAll! a disconnected node could cause errors later otherwise
        mbAll.addAll(simnodes);

        if (verbose) System.out.println("The Markov Blanket is " + mbAll);

        /**Find the subgraph for the resimulated variables and their markov blanket**/
        if (verbose) System.out.println("Finding a subgraph over the Markov Blanket and Resimulated Nodes");

        //need a List as input for subgraph method, but mbAll is a Set
        List<Node> mbListAll = new ArrayList<Node>(mbAll);
        Graph subgraph = mydag.subgraph(mbListAll);

        /**Learn an instantiated model over the subgraph**/
        if (verbose) System.out.println("Learning an instantiated model for the subgraph");

        //learn a dirichlet IM for the subgraph using dataSet
        BayesPm subgraphPM = new BayesPm(subgraph);
        DirichletBayesIm subgraphIM = DirichletBayesIm.symmetricDirichletIm(subgraphPM, 1.0);
        DirichletEstimator estimator = new DirichletEstimator();
        DirichletBayesIm fittedsubgraphIM = estimator.estimate(subgraphIM, data);
        //if (verbose) System.out.println(fittedsubgraphIM.getVariable());

        /**Use the learned instantiated subgraph model to create the resimulated data**/
        if (verbose) System.out.println("Starting resimulation loop");

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

        //loop through each row of the data set, conditioning and drawing values each time.
        for (int row = 0; row < data.getNumRows(); row++) {
            //create a new evidence object
            Evidence evidence = Evidence.tautology(fittedsubgraphIM);

            //need to define the set of variables being conditioned upon. Start with the outer set of MB
            Set<Node> mbOuter = mbAll;
            //need to remove the whole set of starters, not just some X and Y... how do? loop a .remove?
            for (Node node : simnodes) {
                mbOuter.remove(node);
            }
            //THIS SHOULD ALL BE INSIDE ANOTHER LOOP THROUGH THE RESIM VARS:
            //this actually needs to be more careful than a for each. I think a causal ordering of resim should be used?
            Set<Node> conditionNodes = mbOuter;
            for (Node node : simnodes) {
                //identify the conditioning set for this node, which is mbAll plus the previously resimmed nodes

                //loop through all the nodes being conditioned upon, and set their values in the evidence prop
                for (Node i : conditionNodes) {
                    int nodeIndex = evidence.getNodeIndex(i.getName());
                    //how do i get the category index from a value in the data?
                    //int catIndex =
                    int nodeColumn = data.getColumn(i);
                    //Pray to whoever you can think of that the CategoryIndex is just the int in the data
                    //According to this comment in the DataSet class, for the getInt method, we can do this:
                    //"For discrete variables, this returns the category index of the datum for the variable at that column."
                    evidence.getProposition().setCategory(nodeIndex, data.getInt(row, nodeColumn));
                }


                //use the new Evidence object to create the updater
                RowSummingExactUpdater conditionUpdate = new RowSummingExactUpdater(fittedsubgraphIM, evidence);

                //NEED THIS TO WORK FOR MORE THAN NODEX, needs to be arbitrary and looping
                //use the updater to create the marginal distribution for nodeX:
                //need to get nodeX's int index first

                int nodeIndex = evidence.getNodeIndex(node.getName());

                //===complain if no node of that name is found, which makes nodeIndex = -1
                if (nodeIndex == -1) {
                    throw new IllegalArgumentException(
                            "Variable " + node.getName() + " was not found.");
                }

                //===for bug checking====
                //if (verbose) System.out.println(node.getName());
                //if (verbose) System.out.println(nodeIndex);

                //then need to identify all of nodeX's categories so we can iterate through them
                //I can't figure out a nice way to identify this generally, so we're going to cross out fingers
                //and hope that it's just 0, 1, 2, 3... n-1, with n-1 being the largest int category
                //that would correspond to n different categories
                //so, we're gonna see how many categories there are, call that n, and iterate from 0 to n-1

                int numCat = evidence.getNumCategories(nodeIndex);

                //we generate a random number between 0 and 1, and then count prob mass for each cat until we hit it
                RandomUtil random = RandomUtil.getInstance();
                double cutoff = random.nextDouble();
                //if (verbose) System.out.println(cutoff);

                //****** turns out, this needs to be generalized outside of just X and Y as well. doh!*******//
                //for (resimvars) {do the next stuff} //how to iterate through them? order matters, need causal ordering

                double sum = 0.0;
                //initialize the int for the new value of nodeX
                int newValue = -99;
                //now iterate through the categories to see which one owns that probability mass real estate
                for (int i = 0; i < numCat; i++) {
                    //for each category, calc the marginal conditional prob of nodeX having that value
                    double probability = conditionUpdate.getMarginal(nodeIndex, i);
                    //if (verbose) System.out.println("cat " + i + " prob " + probability);
                    sum += probability;

                    if (sum >= cutoff) {
                        newValue = i;
                        break;
                    }
                }
                //then set the value of nodeX to newXvalue for this row
                //if (verbose) System.out.println(data.getInt(row,data.getColumn(nodeX)) + " old vs new " + newXvalue);
                data.setInt(row, data.getColumn(node), newValue);
                //if (verbose) System.out.println(" and again?: " + data.getInt(row,data.getColumn(nodeX)) + " old vs new " + newXvalue);

                //at the end, at this node to the conditioning set
                conditionNodes.add(node);
            }
        }

        //right now output is just a dataset
        //in future, might want output to include more info

        return data;
    }


//========================================PRIVATE METHODS====================================//

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

    //***********Private methods for setting private variables***********//
    private void setVerbose(boolean verbosity) {
        verbose = verbosity;
    }

    private void setDag(Dag thedag){
        mydag=thedag;
    }
    private void setSimnodes(Set<Node> thenodes){
        simnodes=thenodes;
    }
    private void setData(DataSet thedata){
        data=thedata;
    }
}