package edu.cmu.tetrad.cli.json;

import java.util.ArrayList;

/**
 * Author : Jeremy Espino MD Created 6/6/16 4:25 PM
 */
// {"name":"foo","nodes":[{"name":"Node889"},{"name":"Node9728"}],"edgeSets":[{"name":"fooEdgeSet0","edges":[{"source":1,"target":0,"etype":"UNK"}]}]}
public class JsonGraph {

    public String name;

    public ArrayList<JsonNode> nodes = new ArrayList<>();

    public ArrayList<JsonEdgeSet> edgeSets = new ArrayList<>();

}
