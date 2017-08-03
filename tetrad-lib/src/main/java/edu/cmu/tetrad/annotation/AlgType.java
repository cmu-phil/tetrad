package edu.cmu.tetrad.annotation;

/**
 * Author : Jeremy Espino MD
 * Created  6/30/17 10:36 AM
 */
public enum AlgType {
    ALL, forbid_latent_common_causes, allow_latent_common_causes, /*DAG, */
    search_for_Markov_blankets, produce_undirected_graphs, orient_pairwise,
    search_for_structure_over_latents, bootstrapping
}