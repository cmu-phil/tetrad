package edu.cmu.tetrad.data;

import java.io.File;
import java.io.IOException;

/**
 * @author jdramsey
 */
public interface IDataReader {

    /**
     * The delimiter between entries in a line, one of DelimiterType.WHITESPACE,
     * DelimiterType.TAB, DelimiterType.COMMA, DelimiterType.COLON
     */
    void setDelimiter(DelimiterType delimiterType);

    /**
     * True just in case a list of variable names for columns is supplied in the first
     * row of the data.
     */
    void setVariablesSupplied(boolean varNamesSupplied);

    /**
     * True if case IDs are provided in the first column of the data.
     *
     * @deprecated
     */
    void setIdsSupplied(boolean caseIdsPresent);

    /**
     * The String identifier of the case ID column.
     *
     * @deprecated
     */
    void setIdLabel(String caseIdsLabel);

    /**
     * The String used to mark missing values.
     */
    void setMissingValueMarker(String missingValueMarker);

    /**
     * The maximum number of values a column can have before the variable for the
     * column is considered continuous.
     */
    void setMaxIntegralDiscrete(int maxIntegralDiscrete);

    /**
     * Parses a tabular data set from the given file, whether it be continuous, discrete, or mixed.
     *
     * @param file The file to parse.
     * @return The parsed dataset.
     * @throws IOException If the file cannot be read.
     */
    DataSet parseTabular(File file) throws IOException;
}
