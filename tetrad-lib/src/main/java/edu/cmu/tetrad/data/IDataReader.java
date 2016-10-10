package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author jdramsey
 */
public interface IDataReader {

    /**
     * Sets the String used as a comment marker at the beginning of a line.
     * The entine will be ignored if it starts with this string.
     */
    void setCommentMarker(String commentMarker);

    /**
     * The delimiter between entries in a line, one of DelimiterType.WHITESPACE,
     * DelimiterType.TAB, DelimiterType.COMMA, DelimiterType.COLON
     */
    void setDelimiter(DelimiterType delimiterType);

    /**
     * The chactacter used to bound String entries in data, if they would not otherwise
     * be parsed together.
     */
    void setQuoteChar(char quoteChar);

    /**
     * True just in case a list of variable names for columns is supplied in the first
     * row of the data.
     */
    void setVariablesSupplied(boolean varNamesSupplied);

    /**
     * True if case IDs are provided in the first column of the data.
     * @deprecated
     */
    void setIdsSupplied(boolean caseIdsPresent);

    /**
     * The String identifier of the case ID column.
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
     * A list of variables antecedently known. If a variable the data set has the same name as
     * one of these varialbes, the variable from this list is used.
     */
    void setKnownVariables(List<Node> knownVariables);

    /**
     * Parses a tabular data set from the given file, whether it be continuous, discrete, or mixed.
     * @param file The file to parse.
     * @return The parsed dataset.
     * @throws IOException If the file cannot be read.
     */
    DataSet parseTabular(File file) throws IOException;

    /**
     * Parses a tabular data set from a char array, whether it be continuous, discrete, or mixed.
     * @param chars The chars array to parse
     * @return The parsed dataset.
     */
    DataSet parseTabular(char[] chars);

    /**
     * Parses a covariance matrix from the given file.
     * @param file The file containing the (text) covariance matrix.
     * @return The parsed covariance matrix.
     * @throws IOException If the file cannot be read.
     */
    ICovarianceMatrix parseCovariance(File file) throws IOException;

    /**
     * Parses knowledge from the given char array.
     * @param chars The file containing the (text) covariance matrix.
     * @return The parsed covariance matrix.
     */
    ICovarianceMatrix parseCovariance(char[] chars);

    /**
     * Parses knowledge from the given file.
     * @param file The file containing the (text) covariance matrix.
     * @return The parsed covariance matrix.
     * @throws IOException If the file cannot be read.
     */
    IKnowledge parseKnowledge(File file) throws IOException;

    /**
     * Parses knowledge from the given char array.
     * @param chars The file containing the (text) covariance matrix.
     * @return The parsed covariance matrix.
     */
    IKnowledge parseKnowledge(char[] chars);
}
