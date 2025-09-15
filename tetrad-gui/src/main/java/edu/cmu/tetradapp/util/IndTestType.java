package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataType;

public enum IndTestType {
    /**
     * Default test.
     */
    DEFAULT("Default", null),
    /**
     * T-test for correlation.
     */
    CORRELATION_T("Correlation T Test", DataType.Continuous),
    /**
     * Pearson's correlation coefficient.
     */
    FISHER_Z("Fisher's Z", DataType.Continuous),
    /**
     * Zero linear coefficient
     */
    LINEAR_REGRESSION("Linear Regression", DataType.Continuous),
    /**
     * Conditional correlation.
     */
    CONDITIONAL_CORRELATION("Conditional Correlation Test", DataType.Continuous),
    /**
     * SEM BIC used as a test.
     */
    SEM_BIC("SEM BIC used as a Test", DataType.Continuous),
    /**
     * Logistic regression.
     */
    LOGISTIC_REGRESSION("Logistic Regression", DataType.Continuous),
    /**
     * Multinomial logistic regression.
     */
    MIXED_MLR("Multinomial Logistic Regression", DataType.Mixed),
    /**
     * G Square.
     */
    G_SQUARE("G Square", DataType.Discrete),
    /**
     * Chi Square.
     */
    CHI_SQUARE("Chi Square", DataType.Discrete),
    /**
     * M-separation.
     */
    M_SEPARATION("M-Separation", DataType.Graph),
    /**
     * Time series.
     */
    TIME_SERIES("Time Series", DataType.Continuous),
    /**
     * Independence facts.
     */
    INDEPENDENCE_FACTS("Independence Facts", DataType.Graph),
    /**
     * Fisher's Z pooled residuals.
     */
    POOL_RESIDUALS_FISHER_Z("Fisher Z Pooled Residuals", DataType.Continuous),
    /**
     * Fisher's pooled p-values.
     */
    FISHER("Fisher (Fisher Z)", DataType.Continuous),
    /**
     * Tippett's pooled p-values.
     */
    TIPPETT("Tippett (Fisher Z)", DataType.Continuous);

    private final String name;
    private final DataType dataType;

    IndTestType(String name, DataType dataType) {
        this.name = name;
        this.dataType = dataType;
    }

    public String toString() {
        return this.name;
    }

    public DataType getDataType() {
        return this.dataType;
    }
}




