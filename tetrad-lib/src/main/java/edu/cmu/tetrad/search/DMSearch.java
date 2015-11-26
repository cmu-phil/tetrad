package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
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


    //Starting ges penalty discount.
    private double gesDiscount = 10;
    private int gesDepth = 0;

    //Minimum ges penalty discount to use in recursive search.
    private int minDiscount = 4;

    //If true, use GES, else use PC.
    private boolean useGES = true;

    //Lets the user select a subset of the inputs in the dataset to search over.
    //If not subseting, should be set to the entire input set.
    private int[] trueInputs;
    private DataSet data;

    public void setMinDiscount(int minDiscount) {
        this.minDiscount = minDiscount;
    }

    public int getMinDepth() {
        return (this.minDiscount);
    }

    public void setGesDepth(int gesDepth) {
        this.gesDepth = gesDepth;
    }

    public int getGesDepth() {
        return (gesDepth);
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
        return (inputs);
    }

    public int[] getOutputs() {
        return (outputs);
    }

    private CovarianceMatrixOnTheFly cov;
    private LatentStructure dmStructure;

    public LatentStructure getDmStructure() {
        return (dmStructure);
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

    public void setDiscount(double discount) {
        this.gesDiscount = discount;
    }

    public void setUseGES(boolean set) {
        this.useGES = set;
    }


    public Graph search() {

        int[] trueInputs = getTrueInputs();

        DataSet data = getData();

        //TODO: Break stuff below here into seperate fuct/classes.
        this.cov = new CovarianceMatrixOnTheFly(data);


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

        Set<String> inputString = new HashSet<String>();

        HashSet actualInputs = new HashSet<Integer>();
        for (int i = 0; i < trueInputs.length; i++) {
            actualInputs.add(trueInputs[i]);
        }


        for (int i : inputs) {
            if (actualInputs.contains(i)) {

                inputString.add(data.getVariable(i).getName());
            }
        }

        Graph pattern = new EdgeListGraph();

        if (useGES == true) {
            Fgs ges = new Fgs(cov);

            pattern = recursiveGES(pattern, knowledge, this.gesDiscount, getMinDepth(), data, inputString);
        } else {
            this.cov = new CovarianceMatrixOnTheFly(data);
//            Pc pc = new Pc(new IndTestFisherZ(cov, this.alphaPC));
//            pc.setKnowledge(knowledge);
//            pc.setDepth(0);
            System.out.println("Running PC Search");
//            pattern = pc.search();
            double penalty = 2;


//           TODO: Alternative to using built in PC. Needs a fix so that all nodes added to pattern are looked at in applyDmSearch
//            ExecutorService executorService = Executors.newFixedThreadPool(4); // number of threads

            IndTestFisherZ ind = new IndTestFisherZ(cov, this.alphaPC);
            for (int i = 0; i < getInputs().length; i++) {
                if (!pattern.containsNode(data.getVariable(i))) {
                    pattern.addNode(data.getVariable(i));
                }

                if (actualInputs.contains(i)) {
                    for (int j = getInputs().length; j < data.getNumColumns(); j++) {
                        if (!pattern.containsNode(data.getVariable(j))) {
                            pattern.addNode(data.getVariable(j));
                        }

//                    System.out.println(i);
//                    System.out.println(j);
                        if (ind.isDependent(data.getVariable(i), data.getVariable(j))) {
                            pattern.addDirectedEdge(data.getVariable(i), data.getVariable(j));
                        }
                    }
                }
            }

            System.out.println("Running DM search");
            applyDmSearch(pattern, inputString, penalty);
        }

        return (getDmStructure().latentStructToEdgeListGraph(getDmStructure()));

    }

    public LatentStructure applyDmSearch(Graph pattern, Set<String> inputString, double penalty) {

        List<Set<Node>> outputParentsList = new ArrayList<Set<Node>>();
        final List<Node> patternNodes = pattern.getNodes();

//        TODO: add testcase to see how sort compares 10, 11, 1, etc.
        java.util.Collections.sort(patternNodes, new Comparator<Node>() {
            public int compare(Node node1, Node node2) {
//TODO: string length error here. Fix.

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    int n1 = Integer.parseInt(node1.getName().substring(1));
                    int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        System.out.println("Sorted patternNodes");
        //constructing treeSet of output nodes.
        SortedSet<Node> outputNodes = new TreeSet<Node>();
        for (int i : getOutputs()) {

//            System.out.println("patternNodes");
//            System.out.println(patternNodes);

//            System.out.println("i");
//            System.out.println(i);
            outputNodes.add(patternNodes.get(i));
        }

        System.out.println("Got output nodes");

//        System.out.println(outputNodes);

        //Constructing list of output node parents.
        for (Node node : outputNodes) {
            outputParentsList.add(new TreeSet<Node>(getInputParents(node, inputString, pattern)));
        }

        System.out.println("Created list of output node parents");
        int sublistStart = 1;
        int nLatents = 0;

        LatentStructure structure = new LatentStructure();

        //Creating set of nodes with same input sets.
        // And adding both inputs and outputs to their respective latents.
        for (Set<Node> set1 : outputParentsList) {

            TreeSet<Node> sameSetParents = new TreeSet<Node>(new Comparator<Node>() {
                public int compare(Node node1, Node node2) {

                    if (node1.getName().length() > node2.getName().length()) {
                        return (1);
                    } else if (node1.getName().length() < node2.getName().length()) {
                        return (-1);
                    } else {
                        int n1 = Integer.parseInt(node1.getName().substring(1));
                        int n2 = Integer.parseInt(node2.getName().substring(1));
                        return (n1 - n2);
                    }
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
                GraphNode tempLatent = new GraphNode("L" + nLatents);

                if (!setContained(structure, structure.inputs.keySet(), sameSetParents) || structure.inputs.isEmpty()) {
                    structure.latents.add(tempLatent);
                    structure.inputs.put(tempLatent, sameSetParents);
                    nLatents++;
                } else {
                    continue;
                }

                // TODO: Spin off into own function, which adds the output nodes
                //Adding Outputs to their Map.
                for (Node node : outputNodes) {

                    if (new TreeSet<Node>(getInputParents(node, inputString, pattern)).equals(sameSetParents)) {

                        //If haven't created latent, then do so.
                        if (structure.outputs.get(tempLatent) == null) {
                            TreeSet<Node> outputNode = new TreeSet<Node>();
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
            System.out.println("Completed starting point: " + sublistStart + " out of #" + outputParentsList.size() + " sets, and is " + set1.size() + " units large.");
            sublistStart++;
        }
        System.out.println("created initial sets");

        //Need to order latents by entryset value size (smallest to largest)
        //as Map only allows sorting by keyset size.
        TreeMap<TreeSet<Node>, Node> latentsSortedByInputSetSize = sortMapByValue(structure.inputs, structure.latents, structure);


        System.out.println("Finding initial latent-latent effects");


//        System.out.println(latentsSortedByInputSetSize);


        TreeSet<Node> inputs1 = new TreeSet<Node>(new Comparator<Node>() {
            public int compare(Node node1, Node node2) {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    int n1 = Integer.parseInt(node1.getName().substring(1));
                    int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        TreeSet<Node> inputs2 = new TreeSet<Node>(new Comparator<Node>() {
            public int compare(Node node1, Node node2) {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    int n1 = Integer.parseInt(node1.getName().substring(1));
                    int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        HashSet alreadyLookedAt = new HashSet();

        //Finding initial latent-latent Effects.
        for (int i = 0; i <= latentsSortedByInputSetSize.keySet().size(); i++) {

//          TODO: Need to only perform this test if haven't already looked at latent. (for latent 1).


            TreeSet<TreeSet<Node>> sortedInputs = new TreeSet<TreeSet<Node>>(new Comparator<TreeSet<Node>>() {
                public int compare(TreeSet<Node> o1, TreeSet<Node> o2) {
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
                }
            });

            sortedInputs.addAll(latentsSortedByInputSetSize.keySet());

            inputs1 = findFirstUnseenElement(sortedInputs, alreadyLookedAt, latentsSortedByInputSetSize);

            HashSet alreadyLookedAtInnerLoop = new HashSet();

            Node latent1 = latentsSortedByInputSetSize.get(inputs1);

            if (inputs1.first().getName().equals("alreadySeenEverything")) {
                continue;
            }

            for (int j = 0; j <= latentsSortedByInputSetSize.keySet().size(); j++) {


                TreeSet temp2 = new TreeSet<TreeSet<Node>>(new Comparator<TreeSet<Node>>() {
                    public int compare(TreeSet<Node> o1, TreeSet<Node> o2) {
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
                    }
                });

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
                        TreeSet<Node> latentEffects = new TreeSet<Node>(new Comparator<Node>() {
                            public int compare(Node node1, Node node2) {

                                if (node1.getName().length() > node2.getName().length()) {
                                    return (1);
                                } else if (node1.getName().length() < node2.getName().length()) {
                                    return (-1);
                                } else {
                                    int n1 = Integer.parseInt(node1.getName().substring(1));
                                    int n2 = Integer.parseInt(node2.getName().substring(1));
                                    return (n1 - n2);
                                }
                            }
                        });
                        latentEffects.add(latent2);
                        structure.latentEffects.put(latent1, latentEffects);
                    } else {
                        structure.latentEffects.get(latent1).add(latent2);
                    }
                    //Removes set of inputs from every other latent's input set.


                    latentsSortedByInputSetSize = removeSetInputs(structure, structure.getInputs(latent1),
                            structure.getInputs(latent2).size(), latent2, latentsSortedByInputSetSize);

                }
            }
            alreadyLookedAt.add(latent1);

        }


//        Ensuring no nulls in latenteffects map.
        SortedSet<Node> emptyTreeSet = new TreeSet<Node>(new Comparator<Node>() {
            public int compare(Node node1, Node node2) {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    int n1 = Integer.parseInt(node1.getName().substring(1));
                    int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        for (Node latent : structure.getLatents()) {
            if (structure.latentEffects.get(latent) == null) {
                structure.latentEffects.put(latent, emptyTreeSet);
            }
        }

        System.out.println("Structure prior to Sober's step:");
//        System.out.println(structure);

        System.out.println("Applying Sober's step ");

        //Sober's step.
        for (Node latent : structure.getLatents()) {
            if (structure.latentEffects.keySet().contains(latent)) {
                for (Node latentEffect : structure.getLatentEffects(latent)) {
                    applySobersStep(structure.getInputs(latent), structure.getInputs(latentEffect),
                            structure.getOutputs(latent), structure.getOutputs(latentEffect),
                            pattern, structure, latent, latentEffect);
                }
            }
        }

        setDmStructure(structure);


        //Saves DM output in case is needed.
        File file = new File("src/edu/cmu/tetradproj/amurrayw/DM_output_" + "GES_penalty" + penalty + "_.txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            PrintStream outStream = new PrintStream(out);
            outStream.println(structure.latentStructToEdgeListGraph(structure));
        } catch (java.io.FileNotFoundException e) {
            System.out.println("Can't write to file.");

        }


        return (structure);

    }

    private TreeSet<Node> findFirstUnseenElement(TreeSet<TreeSet<Node>> set, HashSet alreadySeen, TreeMap map) {
        for (TreeSet<Node> currentSet : set) {
            if (!(alreadySeen.contains(map.get(currentSet))) && map.get(currentSet) != null) {
                return (currentSet);
            }
        }
        GraphNode end = new GraphNode("alreadySeenEverything");

        TreeSet seenEverything = new TreeSet();
        seenEverything.add(end);


        return (seenEverything);

    }


    private TreeSet nthElementOn(TreeSet set, int startingElementPos) {

        for (int i = 0; i < set.size() - startingElementPos; i++) {
            set = rest(set);
        }

        return (set);
    }

    //    Pulls head off of set and returns rest. (think cdr in lisp)
    private TreeSet<TreeSet<Node>> rest(TreeSet set) {
        set.remove(set.first());
        return (set);
    }

    //returns second set of nodes.(think cadr in lisp).
    private TreeSet<TreeSet<Node>> second(TreeSet<TreeSet<Node>> set) {

        TreeSet<TreeSet<Node>> secondNodeSet = new TreeSet<>();

        secondNodeSet = rest(set);

        secondNodeSet.first();

        return (secondNodeSet);

    }

    private boolean allEqual(SortedSet<Node> set1, SortedSet<Node> set2) {
        for (Node i : set1) {
            for (Node j : set2) {
                if (i.equals(j)) {
                    continue;
                } else {
                    return (false);
                }
            }
        }
        for (Node i : set2) {
            for (Node j : set1) {
                if (i.equals(j)) {
                    continue;
                } else {
                    return (false);
                }
            }
        }
        return (true);
    }

    // Uses previous runs of GES as new knowledge for a additional runs of GES with lower penalty discounts.
    private Graph recursiveGES(Graph previousGES, Knowledge2 knowledge, double penalty, double minPenalty, DataSet data, Set<String> inputString) {

        for (Edge edge : previousGES.getEdges()) {
            knowledge.setRequired(edge.getNode1().getName(), edge.getNode2().getName());
        }

        previousGES = null;

        this.cov = new CovarianceMatrixOnTheFly(data);


        Fgs ges = new Fgs((ICovarianceMatrix) cov);

        ges.setKnowledge(knowledge);
        ges.setDepth(this.gesDepth);
        ges.setPenaltyDiscount(penalty);

        ges.setIgnoreLinearDependent(true);


        Graph pattern = ges.search();


        //Saves GES output in case is needed.
        File file = new File("src/edu/cmu/tetradproj/amurrayw/ges_output_" + penalty + "_.txt");
        try {
            FileOutputStream out = new FileOutputStream(file);
            PrintStream outStream = new PrintStream(out);
            outStream.println(pattern);
        } catch (java.io.FileNotFoundException e) {
            System.out.println("Can't write to file.");

        }

        if (penalty > minPenalty) {
            applyDmSearch(pattern, inputString, penalty);
            return (recursiveGES(pattern, knowledge, penalty - 1, minPenalty, data, inputString));
        } else {
            applyDmSearch(pattern, inputString, penalty);
            return (pattern);
        }

    }


    //Finds any input set that dseps outputs for a pair of directly related latents, then adds input set to approp. set
    //Finally removes latent effect from list of latent effects.
    private void applySobersStep(SortedSet<Node> inputsLatent, SortedSet<Node> inputsLatentEffect,
                                 SortedSet<Node> outputsLatent, SortedSet<Node> outputsLatentEffect,
                                 Graph pattern, LatentStructure structure, Node latent, Node latentEffect) {

        List<Node> latentList = new ArrayList<Node>();

        latentList.addAll(inputsLatent);

        IndependenceTest test = new IndTestFisherZ(this.cov, this.alphaSober);

        boolean testResult = false;

        try {
            testResult = test.isIndependent(outputsLatent.first(), outputsLatentEffect.first(), latentList);
        } catch (SingularMatrixException error) {
            System.out.println(error);
            System.out.println("SingularMatrixException Error!!!!!! Evaluated as:");
            System.out.println(testResult);
            System.out.println("outputsLatent.first()");
            System.out.println(outputsLatent.first());
            System.out.println("outputsLatentEffect.first()");
            System.out.println(outputsLatentEffect.first());
        }
        if (testResult == true) {
            structure.latentEffects.get(latent).remove(latentEffect);
            structure.inputs.get(latentEffect).addAll(inputsLatent);
        }
    }

    // Removes subset of inputs from any latent's input set.
    //Inputs are latent structure, the set of inputs which are to be removed, the number of
    // inputs in the superset, and the identity of the latent they are a subset of,
    private TreeMap removeSetInputs(LatentStructure structure, SortedSet<Node> set, int sizeOfSuperset, Node latentForSuperset, TreeMap<TreeSet<Node>, Node> map) {
        for (Node latent : structure.latents) {
            if (structure.inputs.get(latent).equals(set)) {
                continue;
            } else {


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

    private SortedSet copy(SortedSet orig) {
        SortedSet newset = new TreeSet();

        for (Object o : orig) {
            newset.add(o);

        }
        return (newset);
    }


    private TreeMap<TreeSet<Node>, Node> sortMapByValue(Map<Node, SortedSet<Node>> inputMap, List<Node> latents, LatentStructure structure) {

        TreeMap<TreeSet<Node>, Node> sortedInputSets = new TreeMap<TreeSet<Node>, Node>(new Comparator<SortedSet<Node>>() {
            public int compare(SortedSet o1, SortedSet o2) {
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
            }
        });

        for (Node latent : latents) {
            TreeSet<Node> tempSet = new TreeSet<>(new Comparator<Node>() {
                public int compare(Node node1, Node node2) {

                    if (node1.getName().length() > node2.getName().length()) {
                        return (1);
                    } else if (node1.getName().length() < node2.getName().length()) {
                        return (-1);
                    } else {
                        int n1 = Integer.parseInt(node1.getName().substring(1));
                        int n2 = Integer.parseInt(node2.getName().substring(1));
                        return (n1 - n2);
                    }
                }
            });

            tempSet.addAll(inputMap.get(latent));

            sortedInputSets.put(tempSet, latent);
        }
        return (sortedInputSets);
    }


    //Making sure found nodes are actually inputs before adding as knowledge is now disabled.
    private Set<Node> getInputParents(Node node, Set inputString, Graph pattern) {
        Set<Node> actualInputs = new HashSet<Node>();
        for (Node posInput : pattern.getAdjacentNodes(node)) {
            if (inputString.contains(posInput.getName())) {
                actualInputs.add(posInput);
            }
        }
        return (actualInputs);
    }

    public class LatentStructure {
        List<Node> latents = new ArrayList<Node>();
        Map<Node, SortedSet<Node>> inputs = new TreeMap<Node, SortedSet<Node>>();
        Map<Node, SortedSet<Node>> outputs = new TreeMap<Node, SortedSet<Node>>();
        Map<Node, SortedSet<Node>> latentEffects = new TreeMap<Node, SortedSet<Node>>();


        public LatentStructure() {
        }

        public void addRecord(Node latent, SortedSet<Node> inputs, SortedSet<Node> outputs, SortedSet<Node> latentEffects) {
            if (latents.contains(latent)) throw new IllegalArgumentException();

            this.latents.add(latent);
            this.inputs.put(latent, inputs);
            this.outputs.put(latent, outputs);
            this.latentEffects.put(latent, latentEffects);
        }

        public void removeLatent(Node latent) {
            this.latents.remove(latent);
            this.inputs.remove(latent);
            this.outputs.remove(latent);
            this.latentEffects.remove(latent);
        }

        public List<Node> getLatents() {
            return new ArrayList<Node>(latents);
        }

        public boolean containsLatent(Node latent) {
            return latents.contains(latent);
        }

        public SortedSet<Node> getInputs(Node latent) {
            return new TreeSet<Node>(inputs.get(latent));
        }

        public SortedSet<Node> getOutputs(Node latent) {
            return new TreeSet<Node>(outputs.get(latent));
        }

        public SortedSet<Node> getLatentEffects(Node latent) {
            return new TreeSet<Node>(latentEffects.get(latent));
        }

        public String toString() {
            StringBuilder b = new StringBuilder();

            for (Node node : latents) {
                b.append("Latent:" + node + "\n "
                        + "Inputs:" + inputs.get(node) + "\n "
                        + "Outputs:" + outputs.get(node) + "\n "
                        + "Latent Effects:" + latentEffects.get(node) + "\t\n");
            }

            b.append("\n");
            return b.toString();
        }

        public Graph latentStructToEdgeListGraph(LatentStructure structure) {

            Graph structureGraph = new EdgeListGraphSingleConnections();


            for (Node latent : latents) {
                //Adding every node to graph.
                structureGraph.addNode(latent);

                for (Node input : inputs.get(latent)) {
                    structureGraph.addNode(input);
                }
                for (Node output : outputs.get(latent)) {
                    structureGraph.addNode(output);
                }

                //adding edges from inputs to latent.
                for (Node input : inputs.get(latent)) {
                    structureGraph.addDirectedEdge(input, latent);
                }

                //adding edges from latent to outputs.
                for (Node output : outputs.get(latent)) {
                    structureGraph.addDirectedEdge(latent, output);
                }

                //adding edges from latents to latents.
                if (latentEffects.get(latent) == null) {
                    continue;
                } else {
                    for (Node latentEff : latentEffects.get(latent)) {

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
