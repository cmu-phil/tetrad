package edu.pitt.dbmi.algo.resampling;

/**
 * 
 * Sep 12, 2018 4:07:46 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public enum ResamplingEdgeEnsemble {
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
