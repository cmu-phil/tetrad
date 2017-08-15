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
    private String description;

    public AlgorithmDescriptionClass(String name, AlgType algType, OracleType oracleType, String description) {
        this.algName = name;
        this.algType = algType;
        this.oracleType = oracleType;
        this.description = description;
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

    public String getDescription() {
        return description;
    }
}
