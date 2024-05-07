package edu.cmu.tetrad.search;

/**
 * The type of conditioning set to use for the Markov check. The default is LOCAL_MARKOV, which uses the parents of the
 * target variable to predict the separation set.
 * <p>
 * All of these options are available for DAG models as well as latent variable models. M-separation is used to
 * determine if two variables are independent given a conditioning set or dependent given a conditioning set, which is a
 * correct procedure in both cases. The conditioning set is the set of variables that are conditioned on in the
 * independence test.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MarkovCheck
 */
public enum ConditioningSetType {

    /**
     * Testing all possible independence facts implied by the graph.  Some independence facts obtained in this way may
     * be for implied dependencies.
     */
    GLOBAL_MARKOV,

    /**
     * Testing independence facts implied by the graph, conditioning on the parents of each variable in the graph. Some
     * independence facts obtained in this way may be for implied dependencies.
     */
    LOCAL_MARKOV,

    /**
     * Conditioning on the Markov blanket of each variable in the graph. These are all conditional independence facts,
     * so no conditional dependence facts will be listed if this option is selected.
     */
    MARKOV_BLANKET,

    /**
     * Testing independence facts implied by the graph, conditioning on the parents of each variable in the graph, in a
     * causal order of the graph. Some independence facts obtained in this way may be for implied dependencies.
     */
    ORDERED_LOCAL_MARKOV

}
