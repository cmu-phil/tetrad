package edu.cmu.tetrad.sem;

/**
 * An enum of the types of the various comparisons a parameter may have with respect to one another for SEM estimation.
 */
public enum ParamComparison {
    /**
     * Represents the "Non-comparable" comparison type for a parameter in SEM estimation.
     * <p>
     * This type of comparison indicates that the parameter is not comparable to any other parameter in the structural
     * equation model.
     */
    NC("NC"),
    /**
     * An enum representing the "EQ" comparison type for a parameter in SEM estimation.
     * <p>
     * This type of comparison indicates that the parameter is equal to another parameter in the structural equation
     * model.
     */
    EQ("EQ"),
    /**
     * Represents the "LT" comparison type for a parameter in SEM estimation.
     * <p>
     * This type of comparison indicates that the parameter is less than another parameter in the structural equation
     * model.
     */
    LT("LT"),
    /**
     * An enum value representing the "LE" comparison type for a parameter in SEM estimation.
     * <p>
     * This type of comparison indicates that the parameter is less than or equal to another parameter in the structural
     * equation model.
     */
    LE("LE");

    private final String name;

    ParamComparison(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }
}




