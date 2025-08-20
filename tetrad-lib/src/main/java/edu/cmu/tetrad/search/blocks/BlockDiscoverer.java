package edu.cmu.tetrad.search.blocks;

/** Unifies FOFC, BPC, TSC under one return type. */
public interface BlockDiscoverer {
    BlockSpec discover();
}