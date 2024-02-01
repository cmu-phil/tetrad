package edu.cmu.tetrad.data;

/**
 * Identifies a class that can read data from a file.
 *
 * @author josephramsey
 */
public interface IDataReader {

    /**
     * The delimiter between entries in a line, one of DelimiterType.WHITESPACE, DelimiterType.TAB, DelimiterType.COMMA,
     * DelimiterType.COLON
     */
    void setDelimiter(DelimiterType delimiterType);

    /**
     * The String identifier of the case ID column.
     *
     * @deprecated
     */
    void setIdLabel(String caseIdsLabel);

}
