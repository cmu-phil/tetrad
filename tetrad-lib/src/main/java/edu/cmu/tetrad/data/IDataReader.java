package edu.cmu.tetrad.data;

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

}
