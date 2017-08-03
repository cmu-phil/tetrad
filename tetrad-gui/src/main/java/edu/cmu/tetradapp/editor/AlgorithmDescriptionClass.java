package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.OracleType;

/**
 * Author : Jeremy Espino MD Created 6/30/17 4:15 PM
 */
public class AlgorithmDescriptionClass {

    private String algName;
    private AlgType algType;
    private OracleType oracleType;

    public AlgorithmDescriptionClass(String name, AlgType algType, OracleType oracleType) {
        this.algName = name;
        this.algType = algType;
        this.oracleType = oracleType;
    }

    public String getAlgName() {
        return algName;
    }

    public AlgType getAlgType() {
        return algType;
    }

    public OracleType getOracleType() {
        return oracleType;
    }
}
