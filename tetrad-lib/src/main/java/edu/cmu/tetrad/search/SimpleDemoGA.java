package edu.cmu.tetrad.search;


import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

class Individual implements Comparable<Individual> {

    private final double fitness;
    private final List<Node> genes;
    private final TeyssierScorer scorer;

    public Individual(Score score, List<Node> genes) {
        scorer = new TeyssierScorer(null, score);

        //Set genes randomly for each individual
        this.genes = genes;
        fitness = scorer.score(this.genes);
    }

    //Calculate fitness
    public double getFitness() {
        return fitness;
    }

    public int getLength() {
        return genes.size();
    }

    public List<Node> getGenes() {
        return new ArrayList<>(genes);
    }

    @Override
    public int compareTo(@NotNull Individual o) {
        return Double.compare(o.getFitness(), getFitness());
    }

    public Graph getGraph(boolean cpdag) {
        return scorer.getGraph(cpdag);
    }
}

//Population class
class Population {

    private final List<Node> vars;
    private final Score score;
    private final int numIndividuals;
    private final ConcurrentSkipListSet<Individual> individuals = new ConcurrentSkipListSet<>();

    public Population(Score score, List<Node> vars, int numIndividuals) {
        this.score = score;
        this.vars = vars;
        this.numIndividuals = numIndividuals;
    }

    public void initializePopulation() {
        for (int i = 0; i < numIndividuals; i++) {
            List<Node> order = new ArrayList<>(vars);
            shuffle(order);
            individuals.add(new Individual(score, order));
        }
    }

    public Individual getFittestIndividual() {
        if (!individuals.isEmpty()) return individuals.first();
        else return null;
    }

    //Calculate fitness of each individual

    public void add(Individual individual) {
        individuals.add(individual);
//        if (individuals.size() > numIndividuals) {
//            individuals.remove(individuals.descendingIterator().next());
//        }
    }

    public int getNumGenes() {
        return vars.size();
    }
}