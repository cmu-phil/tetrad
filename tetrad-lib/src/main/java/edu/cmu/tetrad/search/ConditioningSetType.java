package edu.cmu.tetrad.search;

/**
 * The type of conditioning set to use for the Markov check. The default is PARENTS, which uses the parents of the
 * target variable to predict the separation set. DAG_MB uses the Markov blanket of the target variable in a DAG
 * setting, and PAG_MB uses a Markov blanket of the target variable in a PAG setting.
 */
public enum ConditioningSetType {
    LOCAL_MARKOV, ORDERED_LOCAL_MARKOV, MARKOV_BLANKET, GLOBAL_MARKOV
}
