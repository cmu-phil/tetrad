package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
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
    private boolean verbose;

    double xdist = 2.4;
    double ydist = 2.4;
    double zdist = 2;

    //**************CONSTRUCTORS*********************//
    public GdistanceRandom(DataSet inMap) {
        setLocationMap(inMap);
    }

    //*************PUBLIC METHODS*******************//

    public List<List<Double>> randomSimulation(int repeat) {
        List<List<Double>> simdata = new ArrayList<>();
        if (this.verbose) System.out.println("starting simulation loop");
        for (int counter = 0; counter < repeat; counter++) {
            if (this.verbose) System.out.println("counter = " + counter);
            List<Double> distance = randomPairSimulation();
            if (this.verbose) System.out.println("adding distance to simdata");
            simdata.add(distance);
        }
        return simdata;
    }


    //************Private Methods*******************//

    private List<Double> randomPairSimulation() {
        //make 2 random dags over the vars in locationMap
        int numVars = GdistanceRandom.locationMap.getNumColumns();
        if (this.verbose) System.out.println("generating pair of random dags");
        Graph dag1 = GraphUtils.randomGraphRandomForwardEdges(GdistanceRandom.locationMap.getVariables(), 0, this.numEdges1, numVars, numVars, numVars, false, false);
        if (this.verbose) System.out.println(dag1);
        Graph dag2 = GraphUtils.randomGraphRandomForwardEdges(GdistanceRandom.locationMap.getVariables(), 0, this.numEdges2, numVars, numVars, numVars, false, false);

        //convert those dags to CPDAGs
        if (this.verbose) System.out.println("converting dags to CPDAGs");
        Graph graph1 = SearchGraphUtils.cpdagFromDag(dag1);
        Graph graph2 = SearchGraphUtils.cpdagFromDag(dag2);

        //run Gdistance on these two graphs
        if (this.verbose) System.out.println("running Gdistance on the CPDAGs");
        Gdistance gdist = new Gdistance(GdistanceRandom.locationMap, this.xdist, this.ydist, this.zdist);
        return gdist.distances(graph1, graph2);
    }

    //**********Methods for setting values of private variables**************//
    public void setLocationMap(DataSet map) {
        GdistanceRandom.locationMap = map;
    }

    public void setNumEdges1(int edges) {
        this.numEdges1 = edges;
    }

    public void setNumEdges2(int edges) {
        this.numEdges2 = edges;
    }

    public void setVerbose(boolean wantverbose) {
        this.verbose = wantverbose;
    }
}
