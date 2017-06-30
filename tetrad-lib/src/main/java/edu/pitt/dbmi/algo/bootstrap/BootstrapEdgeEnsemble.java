package edu.pitt.dbmi.algo.bootstrap;

/**
 * 
 * Apr 20, 2017 11:44:46 AM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public enum BootstrapEdgeEnsemble {
    // Choose an edge iff its prob. is the highest (even it's nil, which means
    // that there is no edge).
    Highest,
    // Choose an edge iff there is an edge that its prob. is the highest and
    // it's not nil otherwise choose nil,
    // The default choice
    Preserved,
    // Choose an edge iff its prob. > .5 even it's nil.
    Majority
}
