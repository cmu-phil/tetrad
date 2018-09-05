package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.IndependenceFact;

public interface ScoreForFact {


    double getScoreForFact(IndependenceFact fact);

}
