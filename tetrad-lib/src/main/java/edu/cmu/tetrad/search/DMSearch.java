package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * Implements the DM search.
 *
 * @author Alexander Murray-Watters
 */
public class DMSearch {
    private int[] inputs;
    private int[] outputs;

    //alpha value for sober's criterion.
    private double alphaSober = .05;

    //Alpha value for pc.
    private double alphaPC = .05;


    //If true, use GES, else use PC.
    private boolean useFges = true;

    //Lets the user select a subset of the inputs in the dataset to search over.
    //If not subseting, should be set to the entire input set.
    private int[] trueInputs;
    private DataSet data;
    private boolean verbose;

    public DMSearch() {
    }

    public void setTrueInputs(int[] trueInputs) {
        this.trueInputs = trueInputs;
    }

    public void setInputs(int[] inputs) {
        this.inputs = inputs;
    }

    public void setOutputs(int[] outputs) {
        this.outputs = outputs;
    }

    public void setData(DataSet data) {
        this.data = data;
    }

    public int[] getTrueInputs() {
        return (this.trueInputs);
    }

    public DataSet getData() {
        return (this.data);
    }

    public int[] getInputs() {
        return (this.inputs);
    }

    public int[] getOutputs() {
        return (this.outputs);
    }

    private CovarianceMatrix cov;
    private LatentStructure dmStructure;

    public LatentStructure getDmStructure() {
        return (this.dmStructure);
    }

    public void setDmStructure(LatentStructure structure) {
        this.dmStructure = structure;
    }

    public void setAlphaSober(double alpha) {
        this.alphaSober = alpha;
    }

    public void setAlphaPC(double alpha) {
        this.alphaPC = alpha;
    }

    public void setDiscount(

    ) {
        //Starting ges penalty penaltyDiscount.
    }

    public void setUseFges(boolean set) {
        this.useFges = set;
    }


    public Graph search() {

        int[] trueInputs = getTrueInputs();

        DataSet data = getData();

        //2DO: Break stuff below here into seperate fuct/classes.
        this.cov = new CovarianceMatrix(data);


        Knowledge2 knowledge = new Knowledge2(data.getVariableNames());

        //Forbids edges from outputs to inputs.
        for (int i : getInputs()) {
            knowledge.addToTier(0, data.getVariable(i).getName());
        }

        for (int i : getOutputs()) {
            knowledge.addToTier(1, data.getVariable(i).getName());
        }

        knowledge.setTierForbiddenWithin(0, true);
        knowledge.setTierForbiddenWithin(1, true);

        Set<String> inputString = new HashSet<>();

        HashSet<Integer> actualInputs = new HashSet<>();
        for (int trueInput : trueInputs) {
            actualInputs.add(trueInput);
        }


        for (int i : this.inputs) {
            if (actualInputs.contains(i)) {

                inputString.add(data.getVariable(i).getName());
            }
        }

        Graph CPDAG = new EdgeListGraph();

        if (!this.useFges) {
            this.cov = new CovarianceMatrix(data);
            if (this.verbose) {
                System.out.println("Running PC Search");
            }
            final double penalty = 2;

            IndTestFisherZ ind = new IndTestFisherZ(this.cov, this.alphaPC);
            for (int i = 0; i < getInputs().length; i++) {
                if (!CPDAG.containsNode(data.getVariable(i))) {
                    CPDAG.addNode(data.getVariable(i));
                }

                if (actualInputs.contains(i)) {
                    for (int j = getInputs().length; j < data.getNumColumns(); j++) {
                        if (!CPDAG.containsNode(data.getVariable(j))) {
                            CPDAG.addNode(data.getVariable(j));
                        }

                        if (ind.isDependent(data.getVariable(i), data.getVariable(j))) {
                            CPDAG.addDirectedEdge(data.getVariable(i), data.getVariable(j));
                        }
                    }
                }
            }

            if (this.verbose) {
                System.out.println("Running DM search");
            }
            applyDmSearch(CPDAG, inputString, penalty);
        }

        return (getDmStructure().latentStructToEdgeListGraph());

    }

    public LatentStructure applyDmSearch(Graph CPDAG, Set<String> inputString, double penalty) {

        List<Set<Node>> outputParentsList = new ArrayList<>();
        List<Node> CPDAGNodes = CPDAG.getNodes();

        CPDAGNodes.sort((node1, node2) -> {
            if (node1.getName().length() > node2.getName().length()) {
                return (1);
            } else if (node1.getName().length() < node2.getName().length()) {
                return (-1);
            } else {
                int n1 = Integer.parseInt(node1.getName().substring(1));
                int n2 = Integer.parseInt(node2.getName().substring(1));
                return (n1 - n2);
            }
        });

        if (this.verbose) {
            System.out.println("Sorted CPDAGNodes");
        }
        //constructing treeSet of output nodes.
        SortedSet<Node> outputNodes = new TreeSet<>();
        for (int i : getOutputs()) {
            outputNodes.add(CPDAGNodes.get(i));
        }

        if (this.verbose) {
            System.out.println("Got output nodes");
        }

//        System.out.println(outputNodes);

        //Constructing list of output node parents.
        for (Node node : outputNodes) {
            outputParentsList.add(new TreeSet<>(getInputParents(node, inputString, CPDAG)));
        }

        if (this.verbose) {
            System.out.println("Created list of output node parents");
        }
        int sublistStart = 1;
        int nLatents = 0;

        LatentStructure structure = new LatentStructure();

        //Creating set of nodes with same input sets.
        // And adding both inputs and outputs to their respective latents.
        for (Set<Node> set1 : outputParentsList) {

            TreeSet<Node> sameSetParents = new TreeSet<>((node1, node2) -> {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    int n1 = Integer.parseInt(node1.getName().substring(1));
                    int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            });

            List<Set<Node>> nextSet = outputParentsList.subList(sublistStart, outputParentsList.size());
            //If no next set, then just add var.
            if (nextSet.isEmpty()) {
//                for (int head = 0; head < (set1.size()); head++) {
                sameSetParents.addAll(set1);
//                }
            }
            for (Set<Node> set2 : nextSet) {
                if (!(set1.size() == 0 || set2.size() == 0) && set1.equals(set2)) {
//                    for (int head = 0; head < (set1.size()); head++) {
                    sameSetParents.addAll(set1);
//                    }
                } else if (set1.size() > 0) {
//                    for (int head = 0; head < (set1.size()); head++) {
                    sameSetParents.addAll(set1);
//                    }
                }
            }

            if (sameSetParents.size() > 0) {
                //Creates a new latent with a size 1 less than actually present.
                ContinuousVariable tempLatent = new ContinuousVariable("L" + nLatents);

                if (!setContained(structure, structure.inputs.keySet(), sameSetParents) || structure.inputs.isEmpty()) {
                    structure.latents.add(tempLatent);
                    structure.inputs.put(tempLatent, sameSetParents);
                    nLatents++;
                } else {
                    continue;
                }

                //Adding Outputs to their Map.
                for (Node node : outputNodes) {

                    if (new TreeSet<>(getInputParents(node, inputString, CPDAG)).equals(sameSetParents)) {

                        //If haven't created latent, then do so.
                        if (structure.outputs.get(tempLatent) == null) {
                            TreeSet<Node> outputNode = new TreeSet<>();
                            outputNode.add(node);
                            structure.outputs.put(tempLatent, outputNode);
                        }
                        //Otherwise, just add new node to set of output
                        // nodes for the given latent.
                        else {
                            structure.outputs.get(tempLatent).add(node);
                        }
                    }
                }
            }
            if (this.verbose) {
                System.out.println("Completed starting point: " + sublistStart + " out of #" + outputParentsList.size() + " sets, and is " + set1.size() + " units large.");
            }
            sublistStart++;
        }
        if (this.verbose) {
            System.out.println("created initial sets");
        }

        //Need to order latents by entryset value size (smallest to largest)
        //as Map only allows sorting by keyset size.
        TreeMap<TreeSet<Node>, Node> latentsSortedByInputSetSize = sortMapByValue(structure.inputs, structure.latents);


        if (this.verbose) {
            System.out.println("Finding initial latent-latent effects");
        }


//        System.out.println(latentsSortedByInputSetSize);


        TreeSet<Node> inputs1;

        TreeSet<Node> inputs2;

        HashSet<Node> alreadyLookedAt = new HashSet<>();

        //Finding initial latent-latent Effects.
        for (int i = 0; i <= latentsSortedByInputSetSize.keySet().size(); i++) {

//          2DO: Need to only perform this test if haven't already looked at latent. (for latent 1).


            TreeSet<TreeSet<Node>> sortedInputs = new TreeSet<>((o1, o2) -> {
                int size = o1.size() - o2.size();
                if (size == 0) {
                    if (o1.equals(o2)) {
                        return (0);
                    } else {
                        return (o1.hashCode() - o2.hashCode());
                    }
                } else {
                    return (size);
                }
            });

            sortedInputs.addAll(latentsSortedByInputSetSize.keySet());

            inputs1 = findFirstUnseenElement(sortedInputs, alreadyLookedAt, latentsSortedByInputSetSize);

            HashSet<Node> alreadyLookedAtInnerLoop = new HashSet<>();

            Node latent1 = latentsSortedByInputSetSize.get(inputs1);

            if (inputs1.first().getName().equals("alreadySeenEverything")) {
                continue;
            }

            for (int j = 0; j <= latentsSortedByInputSetSize.keySet().size(); j++) {


                inputs2 = findFirstUnseenElement(sortedInputs, alreadyLookedAtInnerLoop, latentsSortedByInputSetSize);


                Node latent2 = latentsSortedByInputSetSize.get(inputs2);

                if (inputs2.first().getName().equals("alreadySeenEverything")) {
                    continue;
                }

                alreadyLookedAtInnerLoop.add(latent2);


                if (latent1.equals(latent2) || structure.getInputs(latent2).equals(structure.getInputs(latent1))) {
                    continue;
                }


                //if latent1 is a subset of latent2...
                if (structure.getInputs(latent2).containsAll(structure.getInputs(latent1))) {
                    if (structure.latentEffects.get(latent1) == null) {
                        TreeSet<Node> latentEffects = new TreeSet<>((node1, node2) -> {

                            if (node1.getName().length() > node2.getName().length()) {
                                return (1);
                            } else if (node1.getName().length() < node2.getName().length()) {
                                return (-1);
                            } else {
                                int n1 = Integer.parseInt(node1.getName().substring(1));
                                int n2 = Integer.parseInt(node2.getName().substring(1));
                                return (n1 - n2);
                            }
                        });
                        latentEffects.add(latent2);
                        structure.latentEffects.put(latent1, latentEffects);
                    } else {
                        structure.latentEffects.get(latent1).add(latent2);
                    }
                    //Removes set of inputs from every other latent's input set.


                    removeSetInputs(structure, structure.getInputs(latent1),
                            structure.getInputs(latent2).size(), latent2, latentsSortedByInputSetSize);

                }
            }
            alreadyLookedAt.add(latent1);

        }


//        Ensuring no nulls in latenteffects map.
        SortedSet<Node> emptyTreeSet = new TreeSet<>((node1, node2) -> {

            if (node1.getName().length() > node2.getName().length()) {
                return (1);
            } else if (node1.getName().length() < node2.getName().length()) {
                return (-1);
            } else {
                int n1 = Integer.parseInt(node1.getName().substring(1));
                int n2 = Integer.parseInt(node2.getName().substring(1));
                return (n1 - n2);
            }
        });

        for (Node latent : structure.getLatents()) {
            structure.latentEffects.putIfAbsent(latent, emptyTreeSet);
        }

        if (this.verbose) {
            System.out.println("Structure prior to Sober's step:");
        }
//        System.out.println(structure);

        if (this.verbose) {
            System.out.println("Applying Sober's step ");
        }

        //Sober's step.
        for (Node latent : structure.getLatents()) {
            if (structure.latentEffects.containsKey(latent)) {
                for (Node latentEffect : structure.getLatentEffects(latent)) {
                    applySobersStep(structure.getInputs(latent),
                            structure.getOutputs(latent), structure.getOutputs(latentEffect),
                            structure, latentEffect);
                }
            }
        }

        setDmStructure(structure);


        //Saves DM output in case is needed.
        File file = new File("src/edu/cmu/tetradproj/amurrayw/DM_output_" + "GES_penalty" + penalty + "_.txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            PrintStream outStream = new PrintStream(out);
            outStream.println(structure.latentStructToEdgeListGraph());
        } catch (java.io.FileNotFoundException e) {
            if (this.verbose) {
                System.out.println("Can't write to file.");
            }

        }


        return (structure);

    }

    private TreeSet<Node> findFirstUnseenElement(TreeSet<TreeSet<Node>> set, HashSet<Node> alreadySeen, TreeMap<TreeSet<Node>, Node> map) {
        for (TreeSet<Node> currentSet : set) {
            if (!(alreadySeen.contains(map.get(currentSet))) && map.get(currentSet) != null) {
                return (currentSet);
            }
        }
        ContinuousVariable end = new ContinuousVariable("alreadySeenEverything");

        TreeSet<Node> seenEverything = new TreeSet<>();
        seenEverything.add(end);


        return (seenEverything);

    }

    //Finds any input set that dseps outputs for a pair of directly related latents, then adds input set to approp. set
    //Finally removes latent effect from list of latent effects.
    private void applySobersStep(SortedSet<Node> inputsLatent,
                                 SortedSet<Node> outputsLatent, SortedSet<Node> outputsLatentEffect,
                                 LatentStructure structure, Node latentEffect) {

        List<Node> latentList = new ArrayList<>(inputsLatent);

        IndependenceTest test = new IndTestFisherZ(this.cov, this.alphaSober);

        boolean testResult = false;

        try {
            testResult = test.isIndependent(outputsLatent.first(), outputsLatentEffect.first(), latentList);
        } catch (SingularMatrixException error) {
            if (this.verbose) {
                System.out.println(error.toString());
                System.out.println("SingularMatrixException Error!!!!!! Evaluated as:");
                System.out.println("outputsLatent.first()");
                System.out.println(outputsLatent.first());
                System.out.println("outputsLatentEffect.first()");
                System.out.println(outputsLatentEffect.first());
            }
        }
        if (testResult) {
            structure.inputs.get(latentEffect).addAll(inputsLatent);
        }
    }

    // Removes subset of inputs from any latent's input set.
    //Inputs are latent structure, the set of inputs which are to be removed, the number of
    // inputs in the superset, and the identity of the latent they are a subset of,
    private TreeMap<? extends TreeSet<Node>, ? extends Node> removeSetInputs(LatentStructure structure, SortedSet<Node> set, int sizeOfSuperset, Node latentForSuperset, TreeMap<TreeSet<Node>, Node> map) {
        for (Node latent : structure.latents) {
            if (!structure.inputs.get(latent).equals(set)) {
                //Want to remove input set only if the target set is greater than the discovered superset or is superset.
                if (structure.inputs.get(latent).size() > sizeOfSuperset || latent.equals(latentForSuperset)) {
                    if (structure.inputs.get(latent).containsAll(set)) {

                        structure.inputs.get(latent).removeAll(set);

                    }

                }
            }
        }
        return (map);
    }

    //Returns true if a latent already contains the given input set.
    private boolean setContained(LatentStructure structure, Set<Node> latentSet, Set<Node> inputSet) {
        for (Node latent : latentSet) {
            if (structure.getInputs(latent).equals(inputSet)) {
                return (true);
            }
        }
        return (false);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    private TreeMap<TreeSet<Node>, Node> sortMapByValue(Map<Node, SortedSet<Node>> inputMap, List<Node> latents) {

        TreeMap<TreeSet<Node>, Node> sortedInputSets = new TreeMap<>((Comparator<SortedSet<Node>>) (o1, o2) -> {
            int size = o1.size() - o2.size();
            if (size == 0) {
                if (o1.equals(o2)) {
                    return (0);
                } else {
                    return (o1.hashCode() - o2.hashCode());
                }
            } else {
                return (size);
            }
        });

        for (Node latent : latents) {
            TreeSet<Node> tempSet = new TreeSet<>((node1, node2) -> {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    int n1 = Integer.parseInt(node1.getName().substring(1));
                    int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            });

            tempSet.addAll(inputMap.get(latent));

            sortedInputSets.put(tempSet, latent);
        }
        return (sortedInputSets);
    }


    //Making sure found nodes are actually inputs before adding as knowledge is now disabled.
    private Set<Node> getInputParents(Node node, Set<? extends String> inputString, Graph CPDAG) {
        Set<Node> actualInputs = new HashSet<>();
        for (Node posInput : CPDAG.getAdjacentNodes(node)) {
            if (inputString.contains(posInput.getName())) {
                actualInputs.add(posInput);
            }
        }
        return (actualInputs);
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public static class LatentStructure {
        List<Node> latents = new ArrayList<>();
        Map<Node, SortedSet<Node>> inputs = new TreeMap<>();
        Map<Node, SortedSet<Node>> outputs = new TreeMap<>();
        Map<Node, SortedSet<Node>> latentEffects = new TreeMap<>();


        public LatentStructure() {
        }

        public void removeLatent(Node latent) {
            this.latents.remove(latent);
            this.inputs.remove(latent);
            this.outputs.remove(latent);
            this.latentEffects.remove(latent);
        }

        public List<Node> getLatents() {
            return new ArrayList<>(this.latents);
        }

        public SortedSet<Node> getInputs(Node latent) {
            return new TreeSet<>(this.inputs.get(latent));
        }

        public SortedSet<Node> getOutputs(Node latent) {
            return new TreeSet<>(this.outputs.get(latent));
        }

        public SortedSet<Node> getLatentEffects(Node latent) {
            return new TreeSet<>(this.latentEffects.get(latent));
        }

        public String toString() {
            StringBuilder b = new StringBuilder();

            for (Node node : this.latents) {
                b.append("Latent:").append(node).append("\n ").append("Inputs:")
                        .append(this.inputs.get(node)).append("\n ").append("Outputs:")
                        .append(this.outputs.get(node)).append("\n ").append("Latent Effects:")
                        .append(this.latentEffects.get(node)).append("\t\n");
            }

            b.append("\n");
            return b.toString();
        }

        public Graph latentStructToEdgeListGraph() {

            Graph structureGraph = new EdgeListGraph();


            for (Node latent : this.latents) {
                //Adding every node to graph.
                structureGraph.addNode(latent);

                for (Node input : this.inputs.get(latent)) {
                    structureGraph.addNode(input);
                }
                for (Node output : this.outputs.get(latent)) {
                    structureGraph.addNode(output);
                }

                //adding edges from inputs to latent.
                for (Node input : this.inputs.get(latent)) {
                    structureGraph.addDirectedEdge(input, latent);
                }

                //adding edges from latent to outputs.
                for (Node output : this.outputs.get(latent)) {
                    structureGraph.addDirectedEdge(latent, output);
                }

                //adding edges from latents to latents.
                if (this.latentEffects.get(latent) != null) {
                    for (Node latentEff : this.latentEffects.get(latent)) {

                        if (!structureGraph.containsNode(latentEff)) {
                            structureGraph.addNode(latentEff);
                        }
                        structureGraph.addDirectedEdge(latent, latentEff);
                    }
                }
            }
            return (structureGraph);
        }


    }

}
