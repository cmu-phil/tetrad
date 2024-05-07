package edu.cmu.tetrad.simulation;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Erich on 8/6/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GdistanceRandom {
    private static DataSet locationMap;
    double xdist = 2.4;
    double ydist = 2.4;
    double zdist = 2;
    private int numEdges1;
    private int numEdges2;
    private boolean verbose;

    //**************CONSTRUCTORS*********************//

    /**
     * <p>Constructor for GdistanceRandom.</p>
     *
     * @param inMap a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public GdistanceRandom(DataSet inMap) {
        setLocationMap(inMap);
    }

    //*************PUBLIC METHODS*******************//

    /**
     * <p>randomSimulation.</p>
     *
     * @param repeat a int
     * @return a {@link java.util.List} object
     */
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
        Graph dag1 = RandomGraph.randomGraphRandomForwardEdges(GdistanceRandom.locationMap.getVariables(), 0, this.numEdges1, numVars, numVars, numVars, false, false);
        if (this.verbose) System.out.println(dag1);
        Graph dag2 = RandomGraph.randomGraphRandomForwardEdges(GdistanceRandom.locationMap.getVariables(), 0, this.numEdges2, numVars, numVars, numVars, false, false);

        //convert those dags to CPDAGs
        if (this.verbose) System.out.println("converting dags to CPDAGs");
        Graph graph1 = GraphTransforms.dagToCpdag(dag1);
        Graph graph2 = GraphTransforms.dagToCpdag(dag2);

        //run Gdistance on these two graphs
        if (this.verbose) System.out.println("running Gdistance on the CPDAGs");
        Gdistance gdist = new Gdistance(GdistanceRandom.locationMap, this.xdist, this.ydist, this.zdist);
        return gdist.distances(graph1, graph2);
    }

    //**********Methods for setting values of private variables**************//

    /**
     * <p>Setter for the field <code>locationMap</code>.</p>
     *
     * @param map a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public void setLocationMap(DataSet map) {
        GdistanceRandom.locationMap = map;
    }

    /**
     * <p>Setter for the field <code>numEdges1</code>.</p>
     *
     * @param edges a int
     */
    public void setNumEdges1(int edges) {
        this.numEdges1 = edges;
    }

    /**
     * <p>Setter for the field <code>numEdges2</code>.</p>
     *
     * @param edges a int
     */
    public void setNumEdges2(int edges) {
        this.numEdges2 = edges;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param wantverbose a boolean
     */
    public void setVerbose(boolean wantverbose) {
        this.verbose = wantverbose;
    }
}
