package edu.cmu.tetrad.cli.json;

/**
 * Author : Jeremy Espino MD Created 6/6/16 4:51 PM
 */
//// {"name":"foo","nodes":[{"name":"Node889"},{"name":"Node9728"}],"edgeSets":[{"name":"fooEdgeSet0","edges":[{"source":1,"target":0,"etype":"UNK"}]}]}
public class JsonNode {

    public JsonNode(String name) {
        this.name = name;
    }

    public String name;

}
