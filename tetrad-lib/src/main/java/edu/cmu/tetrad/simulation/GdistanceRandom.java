package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.DagToPattern;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erich on 8/6/2016.
 */
public class GdistanceRandom {
    private static DataSet locationMap;
    private int numEdges1;
    private int numEdges2;
    private boolean verbose = false;

    double xdist = 2.4;
    double ydist = 2.4;
    double zdist = 2;

    //**************CONSTRUCTORS*********************//
    public GdistanceRandom(DataSet inMap) {
        setLocationMap(inMap);
    }

    //*************PUBLIC METHODS*******************//

    public List<List<Double>> randomSimulation(int repeat){
        List<List<Double>> simdata = new ArrayList<>();
        if (verbose) System.out.println("starting simulation loop");
        for (int counter =0; counter < repeat; counter++) {
            if (verbose) System.out.println("counter = "+counter);
            List<Double> distance = randomPairSimulation();
            if (verbose) System.out.println("adding distance to simdata");
            simdata.add(distance);
        }
        return simdata;
    }


    //************Private Methods*******************//

    private List<Double> randomPairSimulation(){
        //make 2 random dags over the vars in locationMap
        int numVars=locationMap.getNumColumns();
        if (verbose) System.out.println("generating pair of random dags");
        Graph dag1 = GraphUtils.randomGraphRandomForwardEdges(locationMap.getVariables(),0, numEdges1, numVars,numVars,numVars, false, false);
        if (verbose) System.out.println(dag1);
        Graph dag2 = GraphUtils.randomGraphRandomForwardEdges(locationMap.getVariables(),0, numEdges2, numVars,numVars,numVars, false, false);

        //convert those dags to patterns
        if (verbose) System.out.println("converting dags to patterns");
        Graph graph1 = SearchGraphUtils.patternFromDag(dag1);
        Graph graph2 = SearchGraphUtils.patternFromDag(dag2);

        //run Gdistance on these two graphs
        if (verbose) System.out.println("running Gdistance on the patterns");
        Gdistance gdist = new Gdistance(locationMap,xdist,ydist,zdist);
        return gdist.distances(graph1, graph2);
    }

    //**********Methods for setting values of private variables**************//
    public void setLocationMap(DataSet map){
        locationMap=map;
    }

    public void setNumEdges1(int edges){
        numEdges1=edges;
    }

    public void setNumEdges2(int edges){
        numEdges2=edges;
    }

    public void setVerbose(boolean wantverbose){
        verbose=wantverbose;
    }
}
