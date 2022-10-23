package edu.cmu.tetrad.search;


import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;

import static java.util.Collections.shuffle;

/**
 * @author jdramsey@andrew.cmu.edu
 */
public class SimpleDemoGA {

    private final Score score;
    private final Population population;
    private int numIterations = 40;

    public SimpleDemoGA(Score score, int numIndividuals) {
        this.score = score;
        population = new Population(score, score.getVariables(), numIndividuals);
    }

    public Graph search() {
        population.initializePopulation();

        class MyTask implements Callable<Boolean> {

            private final Population population;

            private final int j;
            private final int chunk;

            MyTask(Population population, int j, int chunk) {
                this.population = population;
                this.j = j;
                this.chunk = chunk;
            }

            @Override
            public Boolean call() {
                Individual ind = bossContiguous(population.getFittestIndividual(), j, chunk);
                population.add(ind);
                return true;
            }
        }

        int chunk = Math.min(25, population.getNumGenes() / 2);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int k = 0; k < numIterations; k++) {
            for (int i = 0; i < chunk; i++) {
                for (int j = i; j < population.getNumGenes() - chunk; j += chunk) {
                    tasks.add(new MyTask(population, j, chunk));
                }
            }
        }

        ForkJoinPool.commonPool().invokeAll(tasks);

        System.out.println("Fitness: " + population.getFittestIndividual().getFitness());
        return population.getFittestIndividual().getGraph(true);
    }

    private Individual bossContiguous(Individual individual, int start, int chunk) {
        List<Node> pi = individual.getGenes();

        List<Node> pi2 = new ArrayList<>();

        for (int j = 0; j < chunk; j++) {
            pi2.add(pi.get(start + j));
        }

        Score score2 = ((SemBicScore) score).subset(pi2);

        // Run BOSS on pi2.
        Boss boss = new Boss(score2);
        boss.setAlgType(Boss.AlgType.BOSS);
        boss.setVerbose(true);
        List<Node> pi3 = boss.bestOrder(pi2);

        List<Node> pi4 = new ArrayList<>(pi);

        for (int j = 0; j < chunk; j++) {
            pi4.set(start + j, pi3.get(j));
        }

        return new Individual(score, pi4);
    }
}

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