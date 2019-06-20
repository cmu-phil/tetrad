/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.util.HashSet;
import java.util.Set;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.sem.Parameter;

/**
 * Jun 14, 2019 3:15:39 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgCombination {
	
	private Parameter parameter;
    private Set<CgVariableValues> paramValues;

    public CgCombination(Parameter parameter) {
        this.parameter = parameter;
        this.paramValues = new HashSet<>();
    }

    public void addParamValue(DiscreteVariable variable, int value) {
        this.paramValues.add(new CgVariableValues(variable, value));
    }

    public int hashCode() {
        return parameter.hashCode() + paramValues.hashCode();
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CgCombination)) {
            return false;
        }
        CgCombination v = (CgCombination) o;
        return v.parameter == this.parameter && v.paramValues.equals(this.paramValues);
    }

    public Parameter getParameter() {
        return parameter;
    }
}
