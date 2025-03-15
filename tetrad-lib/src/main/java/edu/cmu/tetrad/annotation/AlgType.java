package edu.cmu.tetrad.annotation;

/**
 * Author : Jeremy Espino MD Created 6/30/17 10:36 AM
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public enum AlgType {

    /**
     * If an algorithm forbids latent common causes.
     */
    forbid_latent_common_causes, // PC_All, PcStableMax, FGES, IMaGES_Discrete, IMaGES_Continuous, FANG, EFANG

    /**
     * If an algorithm allows latent common causes.
     */
    allow_latent_common_causes, // FCI, RFCI, FGES-FCI, SVARFCI, SvarGFCI

    /**
     * If an algorithm searches for Markov blanekts.
     */
    search_for_Markov_blankets, // FGES-MB, PC-MB

    /**
     * If an algorithm produces undirected graphs.
     */
    produce_undirected_graphs, // FAS, MGM, GLASSO

    /**
     * If algorithm orients edges pairwise.
     */
    orient_pairwise, // R3, RSkew, Skew

    /**
     * If an algorithm searches for structure over latents.
     */
    search_for_structure_over_latents // BPC, FOFC, FTFC
}
