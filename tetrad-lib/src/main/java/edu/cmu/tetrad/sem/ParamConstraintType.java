package edu.cmu.tetrad.sem;

/**
 * Enum for representing different types of parameter constraints.
 */
public enum ParamConstraintType {
    /**
     * Represents a parameter constraint type LT (less than).
     */
    LT("LT"),
    /**
     * Represents a parameter constraint type GT (greater than).
     */
    GT("GT"),
    /**
     * The EQ represents a parameter constraint type EQ (equal).
     * <p>
     * This enum value is used to represent the equality constraint on a parameter. It indicates that the parameter
     * value should be equal to a specific value.
     */
    EQ("EQ"),
    /**
     * Represents a parameter constraint type NONE.
     * <p>
     * This enum value is used to represent the absence of a constraint on a parameter. It indicates that there is no
     * specific constraint on the parameter value.
     */
    NONE("NONE");

    private final String name;

    ParamConstraintType(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }
}



