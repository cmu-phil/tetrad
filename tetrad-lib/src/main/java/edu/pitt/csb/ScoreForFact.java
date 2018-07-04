package edu.pitt.csb;

import edu.cmu.tetrad.graph.IndependenceFact;

public interface ScoreForFact {


    double getScoreForFact(IndependenceFact fact);

}
