/**
 * 
 */
package edu.pitt.dbmi.cg;

import edu.cmu.tetrad.data.DiscreteVariable;

/**
 * Jun 14, 2019 3:16:35 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgVariableValues {

	private DiscreteVariable variable;
    private int value;

    public CgVariableValues(DiscreteVariable variable, int value) {
        this.variable = variable;
        this.value = value;
    }

    public DiscreteVariable getVariable() {
        return variable;
    }

    public int getValue() {
        return value;
    }

    public int hashCode() {
        return variable.hashCode() + value;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CgVariableValues)) {
            return false;
        }
        CgVariableValues v = (CgVariableValues) o;
        return v.variable.equals(this.variable) && v.value == this.value;
    }
}
