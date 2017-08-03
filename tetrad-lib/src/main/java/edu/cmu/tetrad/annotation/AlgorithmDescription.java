package edu.cmu.tetrad.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Author : Jeremy Espino MD
 * Created  6/30/17 10:18 AM
 */

@Retention(RetentionPolicy.RUNTIME)
public @interface AlgorithmDescription {
    String name();
    AlgType algType();
    OracleType oracleType();
}
