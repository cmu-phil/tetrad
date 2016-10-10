package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author jdramsey
 */
public interface IDataReader {
    void setCommentMarker(String commentMarker);

    void setDelimiter(DelimiterType delimiterType);

    void setQuoteChar(char quoteChar);

    void setVariablesSupplied(boolean varNamesSupplied);

    void setIdsSupplied(boolean caseIdsPresent);

    void setIdLabel(String caseIdsLabel);

    void setMissingValueMarker(String missingValueMarker);

    void setMaxIntegralDiscrete(int maxIntegralDiscrete);

    void setKnownVariables(List<Node> knownVariables);

    DataSet parseTabular(File file) throws IOException;

    DataSet parseTabular(char[] chars);

    ICovarianceMatrix parseCovariance(File file) throws IOException;

    ICovarianceMatrix parseCovariance(char[] chars);

    IKnowledge parseKnowledge(File file) throws IOException;

    IKnowledge parseKnowledge(char[] chars);
}
