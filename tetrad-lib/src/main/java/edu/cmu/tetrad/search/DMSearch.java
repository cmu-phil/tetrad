package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Edge;
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


    //Starting ges penalty penaltyDiscount.
    private double gesDiscount = 10;
    private int gesDepth = 0;

    //Minimum ges penalty penaltyDiscount to use in recursive search.
    private int minDiscount = 4;

    //If true, use GES, else use PC.
    private boolean useFges = true;

    //Lets the user select a subset of the inputs in the dataset to search over.
    //If not subseting, should be set to the entire input set.
    private int[] trueInputs;
    private DataSet data;
    private boolean verbose = false;

    public void setMinDiscount(final int minDiscount) {
        this.minDiscount = minDiscount;
    }

    public int getMinDepth() {
        return (this.minDiscount);
    }

    public void setGesDepth(final int gesDepth) {
        this.gesDepth = gesDepth;
    }

    public int getGesDepth() {
        return (this.gesDepth);
    }

    public void setTrueInputs(final int[] trueInputs) {
        this.trueInputs = trueInputs;
    }

    public void setInputs(final int[] inputs) {
        this.inputs = inputs;
    }

    public void setOutputs(final int[] outputs) {
        this.outputs = outputs;
    }

    public void setData(final DataSet data) {
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

    public void setDmStructure(final LatentStructure structure) {
        this.dmStructure = structure;
    }

    public void setAlphaSober(final double alpha) {
        this.alphaSober = alpha;
    }

    public void setAlphaPC(final double alpha) {
        this.alphaPC = alpha;
    }

    public void setDiscount(final double discount) {
        this.gesDiscount = discount;
    }

    public void setUseFges(final boolean set) {
        this.useFges = set;
    }


    public Graph search() {

        final int[] trueInputs = getTrueInputs();

        final DataSet data = getData();

        //2DO: Break stuff below here into seperate fuct/classes.
        this.cov = new CovarianceMatrix(data);


        final Knowledge2 knowledge = new Knowledge2(data.getVariableNames());

        //Forbids edges from outputs to inputs.
        for (final int i : getInputs()) {
            knowledge.addToTier(0, data.getVariable(i).getName());
        }

        for (final int i : getOutputs()) {
            knowledge.addToTier(1, data.getVariable(i).getName());
        }

        knowledge.setTierForbiddenWithin(0, true);
        knowledge.setTierForbiddenWithin(1, true);

        final Set<String> inputString = new HashSet<>();

        final HashSet actualInputs = new HashSet<>();
        for (int i = 0; i < trueInputs.length; i++) {
            actualInputs.add(trueInputs[i]);
        }


        for (final int i : this.inputs) {
            if (actualInputs.contains(i)) {

                inputString.add(data.getVariable(i).getName());
            }
        }

        Graph CPDAG = new EdgeListGraph();

        if (this.useFges) {
            final Score score = new SemBicScore(this.cov);
            final Fges fges = new Fges(score);

            CPDAG = recursiveFges(CPDAG, knowledge, this.gesDiscount, getMinDepth(), data, inputString);
        } else {
            this.cov = new CovarianceMatrix(data);
//            PC pc = new PC(new IndTestFisherZ(cov, this.alphaPC));
//            pc.setKnowledge(knowledge);
//            pc.setMaxIndegree(0);
            if (this.verbose) {
                if (this.verbose) {
                    System.out.println("Running PC Search");
                }
            }
//            CPDAG = pc.search();
            final double penalty = 2;


//           2DO: Alternative to using built in PC. Needs a fix so that all nodes added to CPDAG are looked at in applyDmSearch
//            ExecutorService executorService = Executors.newFixedThreadPool(4); // number of threads

            final IndTestFisherZ ind = new IndTestFisherZ(this.cov, this.alphaPC);
            for (int i = 0; i < getInputs().length; i++) {
                if (!CPDAG.containsNode(data.getVariable(i))) {
                    CPDAG.addNode(data.getVariable(i));
                }

                if (actualInputs.contains(i)) {
                    for (int j = getInputs().length; j < data.getNumColumns(); j++) {
                        if (!CPDAG.containsNode(data.getVariable(j))) {
                            CPDAG.addNode(data.getVariable(j));
                        }

//                    System.out.println(i);
//                    System.out.println(j);
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

        return (getDmStructure().latentStructToEdgeListGraph(getDmStructure()));

    }

    public LatentStructure applyDmSearch(final Graph CPDAG, final Set<String> inputString, final double penalty) {

        final List<Set<Node>> outputParentsList = new ArrayList<>();
        final List<Node> CPDAGNodes = CPDAG.getNodes();

//        2DO: add testcase to see how sort compares 10, 11, 1, etc.
        java.util.Collections.sort(CPDAGNodes, new Comparator<Node>() {
            public int compare(final Node node1, final Node node2) {
//2DO: string length error here. Fix.

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    final int n1 = Integer.parseInt(node1.getName().substring(1));
                    final int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        if (this.verbose) {
            System.out.println("Sorted CPDAGNodes");
        }
        //constructing treeSet of output nodes.
        final SortedSet<Node> outputNodes = new TreeSet<>();
        for (final int i : getOutputs()) {

//            System.out.println("CPDAGNodes");
//            System.out.println(CPDAGNodes);

//            System.out.println("i");
//            System.out.println(i);
            outputNodes.add(CPDAGNodes.get(i));
        }

        if (this.verbose) {
            System.out.println("Got output nodes");
        }

//        System.out.println(outputNodes);

        //Constructing list of output node parents.
        for (final Node node : outputNodes) {
            outputParentsList.add(new TreeSet<>(getInputParents(node, inputString, CPDAG)));
        }

        if (this.verbose) {
            System.out.println("Created list of output node parents");
        }
        int sublistStart = 1;
        int nLatents = 0;

        final LatentStructure structure = new LatentStructure();

        //Creating set of nodes with same input sets.
        // And adding both inputs and outputs to their respective latents.
        for (final Set<Node> set1 : outputParentsList) {

            final TreeSet<Node> sameSetParents = new TreeSet<>(new Comparator<Node>() {
                public int compare(final Node node1, final Node node2) {

                    if (node1.getName().length() > node2.getName().length()) {
                        return (1);
                    } else if (node1.getName().length() < node2.getName().length()) {
                        return (-1);
                    } else {
                        final int n1 = Integer.parseInt(node1.getName().substring(1));
                        final int n2 = Integer.parseInt(node2.getName().substring(1));
                        return (n1 - n2);
                    }
                }
            });

            final List<Set<Node>> nextSet = outputParentsList.subList(sublistStart, outputParentsList.size());
            //If no next set, then just add var.
            if (nextSet.isEmpty()) {
//                for (int head = 0; head < (set1.size()); head++) {
                sameSetParents.addAll(set1);
//                }
            }
            for (final Set<Node> set2 : nextSet) {
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
                final ContinuousVariable tempLatent = new ContinuousVariable("L" + nLatents);

                if (!setContained(structure, structure.inputs.keySet(), sameSetParents) || structure.inputs.isEmpty()) {
                    structure.latents.add(tempLatent);
                    structure.inputs.put(tempLatent, sameSetParents);
                    nLatents++;
                } else {
                    continue;
                }

                //Adding Outputs to their Map.
                for (final Node node : outputNodes) {

                    if (new TreeSet<>(getInputParents(node, inputString, CPDAG)).equals(sameSetParents)) {

                        //If haven't created latent, then do so.
                        if (structure.outputs.get(tempLatent) == null) {
                            final TreeSet<Node> outputNode = new TreeSet<>();
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
        TreeMap<TreeSet<Node>, Node> latentsSortedByInputSetSize = sortMapByValue(structure.inputs, structure.latents, structure);


        if (this.verbose) {
            System.out.println("Finding initial latent-latent effects");
        }


//        System.out.println(latentsSortedByInputSetSize);


        TreeSet<Node> inputs1 = new TreeSet<>(new Comparator<Node>() {
            public int compare(final Node node1, final Node node2) {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    final int n1 = Integer.parseInt(node1.getName().substring(1));
                    final int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        TreeSet<Node> inputs2 = new TreeSet<>(new Comparator<Node>() {
            public int compare(final Node node1, final Node node2) {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    final int n1 = Integer.parseInt(node1.getName().substring(1));
                    final int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        final HashSet alreadyLookedAt = new HashSet();

        //Finding initial latent-latent Effects.
        for (int i = 0; i <= latentsSortedByInputSetSize.keySet().size(); i++) {

//          2DO: Need to only perform this test if haven't already looked at latent. (for latent 1).


            final TreeSet<TreeSet<Node>> sortedInputs = new TreeSet<>(new Comparator<TreeSet<Node>>() {
                public int compare(final TreeSet<Node> o1, final TreeSet<Node> o2) {
                    final int size = o1.size() - o2.size();
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

            final HashSet alreadyLookedAtInnerLoop = new HashSet();

            final Node latent1 = latentsSortedByInputSetSize.get(inputs1);

            if (inputs1.first().getName().equals("alreadySeenEverything")) {
                continue;
            }

            for (int j = 0; j <= latentsSortedByInputSetSize.keySet().size(); j++) {


                final TreeSet temp2 = new TreeSet<>(new Comparator<TreeSet<Node>>() {
                    public int compare(final TreeSet<Node> o1, final TreeSet<Node> o2) {
                        final int size = o1.size() - o2.size();
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


                final Node latent2 = latentsSortedByInputSetSize.get(inputs2);

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
                        final TreeSet<Node> latentEffects = new TreeSet<>(new Comparator<Node>() {
                            public int compare(final Node node1, final Node node2) {

                                if (node1.getName().length() > node2.getName().length()) {
                                    return (1);
                                } else if (node1.getName().length() < node2.getName().length()) {
                                    return (-1);
                                } else {
                                    final int n1 = Integer.parseInt(node1.getName().substring(1));
                                    final int n2 = Integer.parseInt(node2.getName().substring(1));
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
        final SortedSet<Node> emptyTreeSet = new TreeSet<>(new Comparator<Node>() {
            public int compare(final Node node1, final Node node2) {

                if (node1.getName().length() > node2.getName().length()) {
                    return (1);
                } else if (node1.getName().length() < node2.getName().length()) {
                    return (-1);
                } else {
                    final int n1 = Integer.parseInt(node1.getName().substring(1));
                    final int n2 = Integer.parseInt(node2.getName().substring(1));
                    return (n1 - n2);
                }
            }
        });

        for (final Node latent : structure.getLatents()) {
            if (structure.latentEffects.get(latent) == null) {
                structure.latentEffects.put(latent, emptyTreeSet);
            }
        }

        if (this.verbose) {
            System.out.println("Structure prior to Sober's step:");
        }
//        System.out.println(structure);

        if (this.verbose) {
            System.out.println("Applying Sober's step ");
        }

        //Sober's step.
        for (final Node latent : structure.getLatents()) {
            if (structure.latentEffects.keySet().contains(latent)) {
                for (final Node latentEffect : structure.getLatentEffects(latent)) {
                    applySobersStep(structure.getInputs(latent), structure.getInputs(latentEffect),
                            structure.getOutputs(latent), structure.getOutputs(latentEffect),
                            CPDAG, structure, latent, latentEffect);
                }
            }
        }

        setDmStructure(structure);


        //Saves DM output in case is needed.
        final File file = new File("src/edu/cmu/tetradproj/amurrayw/DM_output_" + "GES_penalty" + penalty + "_.txt");
        try {
            final FileOutputStream out = new FileOutputStream(file);
            final PrintStream outStream = new PrintStream(out);
            outStream.println(structure.latentStructToEdgeListGraph(structure));
        } catch (final java.io.FileNotFoundException e) {
            if (this.verbose) {
                System.out.println("Can't write to file.");
            }

        }


        return (structure);

    }

    private TreeSet<Node> findFirstUnseenElement(final TreeSet<TreeSet<Node>> set, final HashSet alreadySeen, final TreeMap map) {
        for (final TreeSet<Node> currentSet : set) {
            if (!(alreadySeen.contains(map.get(currentSet))) && map.get(currentSet) != null) {
                return (currentSet);
            }
        }
        final ContinuousVariable end = new ContinuousVariable("alreadySeenEverything");

        final TreeSet seenEverything = new TreeSet();
        seenEverything.add(end);


        return (seenEverything);

    }


    private TreeSet nthElementOn(TreeSet set, final int startingElementPos) {

        for (int i = 0; i < set.size() - startingElementPos; i++) {
            set = rest(set);
        }

        return (set);
    }

    //    Pulls head off of set and returns rest. (think cdr in lisp)
    private TreeSet<TreeSet<Node>> rest(final TreeSet set) {
        set.remove(set.first());
        return (set);
    }

    //returns second set of nodes.(think cadr in lisp).
    private TreeSet<TreeSet<Node>> second(final TreeSet<TreeSet<Node>> set) {

        TreeSet<TreeSet<Node>> secondNodeSet = new TreeSet<>();

        secondNodeSet = rest(set);

        secondNodeSet.first();

        return (secondNodeSet);

    }

    private boolean allEqual(final SortedSet<Node> set1, final SortedSet<Node> set2) {
        for (final Node i : set1) {
            for (final Node j : set2) {
                if (i.equals(j)) {
                    continue;
                } else {
                    return (false);
                }
            }
        }
        for (final Node i : set2) {
            for (final Node j : set1) {
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
    private Graph recursiveFges(final Graph previousGES, final Knowledge2 knowledge, final double penalty, final double minPenalty, final DataSet data, final Set<String> inputString) {

        for (final Edge edge : previousGES.getEdges()) {
            knowledge.setRequired(edge.getNode1().getName(), edge.getNode2().getName());
        }

        this.cov = new CovarianceMatrix(data);

        final SemBicScore score = new SemBicScore(this.cov);
        score.setPenaltyDiscount(penalty);
        final Fges fges = new Fges(score);
        fges.setKnowledge(knowledge);
//        fges.setMaxIndegree(this.gesDepth);
//        fges.setIgnoreLinearDependent(true);

        final Graph CPDAG = fges.search();

        //Saves GES output in case is needed.
        final File file = new File("src/edu/cmu/tetradproj/amurrayw/ges_output_" + penalty + "_.txt");
        try {
            final FileOutputStream out = new FileOutputStream(file);
            final PrintStream outStream = new PrintStream(out);
            outStream.println(CPDAG);
        } catch (final java.io.FileNotFoundException e) {
            if (this.verbose) {
                System.out.println("Can't write to file.");
            }

        }

        if (penalty > minPenalty) {
            applyDmSearch(CPDAG, inputString, penalty);
            return (recursiveFges(CPDAG, knowledge, penalty - 1, minPenalty, data, inputString));
        } else {
            applyDmSearch(CPDAG, inputString, penalty);
            return (CPDAG);
        }

    }


    //Finds any input set that dseps outputs for a pair of directly related latents, then adds input set to approp. set
    //Finally removes latent effect from list of latent effects.
    private void applySobersStep(final SortedSet<Node> inputsLatent, final SortedSet<Node> inputsLatentEffect,
                                 final SortedSet<Node> outputsLatent, final SortedSet<Node> outputsLatentEffect,
                                 final Graph CPDAG, final LatentStructure structure, final Node latent, final Node latentEffect) {

        final List<Node> latentList = new ArrayList<>();

        latentList.addAll(inputsLatent);

        final IndependenceTest test = new IndTestFisherZ(this.cov, this.alphaSober);

        boolean testResult = false;

        try {
            testResult = test.isIndependent(outputsLatent.first(), outputsLatentEffect.first(), latentList);
        } catch (final SingularMatrixException error) {
            if (this.verbose) {
                System.out.println(error);
                System.out.println("SingularMatrixException Error!!!!!! Evaluated as:");
                System.out.println(testResult);
                System.out.println("outputsLatent.first()");
                System.out.println(outputsLatent.first());
                System.out.println("outputsLatentEffect.first()");
                System.out.println(outputsLatentEffect.first());
            }
        }
        if (testResult == true) {
            structure.latentEffects.get(latent).remove(latentEffect);
            structure.inputs.get(latentEffect).addAll(inputsLatent);
        }
    }

    // Removes subset of inputs from any latent's input set.
    //Inputs are latent structure, the set of inputs which are to be removed, the number of
    // inputs in the superset, and the identity of the latent they are a subset of,
    private TreeMap removeSetInputs(final LatentStructure structure, final SortedSet<Node> set, final int sizeOfSuperset, final Node latentForSuperset, final TreeMap<TreeSet<Node>, Node> map) {
        for (final Node latent : structure.latents) {
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
    private boolean setContained(final LatentStructure structure, final Set<Node> latentSet, final Set<Node> inputSet) {
        for (final Node latent : latentSet) {
            if (structure.getInputs(latent).equals(inputSet)) {
                return (true);
            }
        }
        return (false);
    }

    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj);
    }

    private SortedSet copy(final SortedSet orig) {
        final SortedSet newset = new TreeSet();

        for (final Object o : orig) {
            newset.add(o);

        }
        return (newset);
    }


    private TreeMap<TreeSet<Node>, Node> sortMapByValue(final Map<Node, SortedSet<Node>> inputMap, final List<Node> latents, final LatentStructure structure) {

        final TreeMap<TreeSet<Node>, Node> sortedInputSets = new TreeMap<>(new Comparator<SortedSet<Node>>() {
            public int compare(final SortedSet o1, final SortedSet o2) {
                final int size = o1.size() - o2.size();
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

        for (final Node latent : latents) {
            final TreeSet<Node> tempSet = new TreeSet<>(new Comparator<Node>() {
                public int compare(final Node node1, final Node node2) {

                    if (node1.getName().length() > node2.getName().length()) {
                        return (1);
                    } else if (node1.getName().length() < node2.getName().length()) {
                        return (-1);
                    } else {
                        final int n1 = Integer.parseInt(node1.getName().substring(1));
                        final int n2 = Integer.parseInt(node2.getName().substring(1));
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
    private Set<Node> getInputParents(final Node node, final Set inputString, final Graph CPDAG) {
        final Set<Node> actualInputs = new HashSet<>();
        for (final Node posInput : CPDAG.getAdjacentNodes(node)) {
            if (inputString.contains(posInput.getName())) {
                actualInputs.add(posInput);
            }
        }
        return (actualInputs);
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public class LatentStructure {
        List<Node> latents = new ArrayList<>();
        Map<Node, SortedSet<Node>> inputs = new TreeMap<>();
        Map<Node, SortedSet<Node>> outputs = new TreeMap<>();
        Map<Node, SortedSet<Node>> latentEffects = new TreeMap<>();


        public LatentStructure() {
        }

        public void addRecord(final Node latent, final SortedSet<Node> inputs, final SortedSet<Node> outputs, final SortedSet<Node> latentEffects) {
            if (this.latents.contains(latent)) throw new IllegalArgumentException();

            this.latents.add(latent);
            this.inputs.put(latent, inputs);
            this.outputs.put(latent, outputs);
            this.latentEffects.put(latent, latentEffects);
        }

        public void removeLatent(final Node latent) {
            this.latents.remove(latent);
            this.inputs.remove(latent);
            this.outputs.remove(latent);
            this.latentEffects.remove(latent);
        }

        public List<Node> getLatents() {
            return new ArrayList<>(this.latents);
        }

        public boolean containsLatent(final Node latent) {
            return this.latents.contains(latent);
        }

        public SortedSet<Node> getInputs(final Node latent) {
            return new TreeSet<>(this.inputs.get(latent));
        }

        public SortedSet<Node> getOutputs(final Node latent) {
            return new TreeSet<>(this.outputs.get(latent));
        }

        public SortedSet<Node> getLatentEffects(final Node latent) {
            return new TreeSet<>(this.latentEffects.get(latent));
        }

        public String toString() {
            final StringBuilder b = new StringBuilder();

            for (final Node node : this.latents) {
                b.append("Latent:" + node + "\n "
                        + "Inputs:" + this.inputs.get(node) + "\n "
                        + "Outputs:" + this.outputs.get(node) + "\n "
                        + "Latent Effects:" + this.latentEffects.get(node) + "\t\n");
            }

            b.append("\n");
            return b.toString();
        }

        public Graph latentStructToEdgeListGraph(final LatentStructure structure) {

            final Graph structureGraph = new EdgeListGraph();


            for (final Node latent : this.latents) {
                //Adding every node to graph.
                structureGraph.addNode(latent);

                for (final Node input : this.inputs.get(latent)) {
                    structureGraph.addNode(input);
                }
                for (final Node output : this.outputs.get(latent)) {
                    structureGraph.addNode(output);
                }

                //adding edges from inputs to latent.
                for (final Node input : this.inputs.get(latent)) {
                    structureGraph.addDirectedEdge(input, latent);
                }

                //adding edges from latent to outputs.
                for (final Node output : this.outputs.get(latent)) {
                    structureGraph.addDirectedEdge(latent, output);
                }

                //adding edges from latents to latents.
                if (this.latentEffects.get(latent) == null) {
                    continue;
                } else {
                    for (final Node latentEff : this.latentEffects.get(latent)) {

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
