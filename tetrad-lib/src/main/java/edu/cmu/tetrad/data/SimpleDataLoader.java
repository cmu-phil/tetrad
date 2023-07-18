package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.data.reader.ContinuousData;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class SimpleDataLoader {

    /**
     * Loads a continuous dataset from a file.
     *
     * @param file               The text file to load the data from.
     * @param commentMarker      The comment marker as a string--e.g., "//".
     * @param quoteCharacter     The quote character, e.g., '\"'.
     * @param missingValueMarker The missing value marker as a string--e.g., "NA".
     * @param hasHeader          True if the first row of the data contains variable names.
     * @param delimiter          One of the options in the Delimiter enum--e.g., Delimiter.TAB.
     * @return The loaded DataSet.
     * @throws IOException If an error occurred in reading the file.
     */
    @NotNull
    public static DataSet loadContinuousData(File file, String commentMarker, char quoteCharacter,
                                             String missingValueMarker, boolean hasHeader, Delimiter delimiter)
            throws IOException {
        ContinuousTabularDatasetFileReader dataReader
                = new ContinuousTabularDatasetFileReader(file.toPath(), delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setHasHeader(hasHeader);
        ContinuousData data = (ContinuousData) dataReader.readInData();
        return (DataSet) DataConvertUtils.toContinuousDataModel(data);
    }

    /**
     * Loads a discrete dataset from a file.
     *
     * @param file               The text file to load the data from.
     * @param commentMarker      The comment marker as a string--e.g., "//".
     * @param quoteCharacter     The quote character, e.g., '\"'.
     * @param missingValueMarker The missing value marker as a string--e.g., "NA".
     * @param hasHeader          True if the first row of the data contains variable names.
     * @param delimiter          One of the options in the Delimiter enum--e.g., Delimiter.TAB.
     * @return The loaded DataSet.
     * @throws IOException If an error occurred in reading the file.
     */
    @NotNull
    public static DataSet loadDiscreteData(File file, String commentMarker, char quoteCharacter,
                                           String missingValueMarker, boolean hasHeader, Delimiter delimiter)
            throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(file.toPath(), delimiter);
        DataColumn[] dataColumns = columnReader.readInDataColumns(new int[]{1}, true);

        columnReader.setCommentMarker(commentMarker);

        TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);

        // Need to specify commentMarker, .... again to the TabularDataFileReader
        dataReader.setCommentMarker(commentMarker);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setQuoteCharacter(quoteCharacter);

        Data data = dataReader.read(dataColumns, hasHeader);
        DataModel dataModel = DataConvertUtils.toDataModel(data);

        return (DataSet) dataModel;
    }

    /**
     * Loads a mixed dataset from a file.
     *
     * @param file               The text file to load the data from.
     * @param commentMarker      The comment marker as a string--e.g., "//".
     * @param quoteCharacter     The quote character, e.g., '\"'.
     * @param missingValueMarker The missing value marker as a string--e.g., "NA".
     * @param hasHeader          True if the first row of the data contains variable names.
     * @param delimiter          One of the options in the Delimiter enum--e.g., Delimiter.TAB.
     * @param maxNumCategories   The maximum number of distinct entries in a columns alloed in order for the column to
     *                           be parsed as discrete.
     * @return The loaded DataSet.
     * @throws IOException If an error occurred in reading the file.
     */
    @NotNull
    public static DataSet loadMixedData(File file, String commentMarker, char quoteCharacter,
                                        String missingValueMarker, boolean hasHeader, int maxNumCategories, Delimiter delimiter)
            throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(file.toPath(), delimiter);
        DataColumn[] dataColumns = columnReader.readInDataColumns(new int[]{1}, false);

        columnReader.setCommentMarker(commentMarker);

        TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);

        // Need to specify commentMarker, .... again to the TabularDataFileReader
        dataReader.setCommentMarker(commentMarker);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.determineDiscreteDataColumns(dataColumns, maxNumCategories, hasHeader);

        Data data = dataReader.read(dataColumns, hasHeader);
        DataModel dataModel = DataConvertUtils.toDataModel(data);

        return (DataSet) dataModel;
    }

    /**
     * Parses a covariance matrix from a char[] array. The format is as follows.
     * <pre>
     * /covariance
     * 100
     * X1   X2   X3   X4
     * 1.4
     * 3.2  2.3
     * 2.5  3.2  5.3
     * 3.2  2.5  3.2  4.2
     * </pre>
     * <pre>
     * CovarianceMatrix dataSet = DataLoader.loadCovMatrix(
     *                           new FileReader(file), " \t", "//");
     * </pre> The initial "/covariance" is optional.
     */
    public static ICovarianceMatrix loadCovarianceMatrix(char[] chars, String commentMarker,
                                                         DelimiterType delimiterType,
                                                         char quoteChar,
                                                         String missingValueMarker) {

        // Do first pass to get a description of the file.
        CharArrayReader reader = new CharArrayReader(chars);

        // Close the reader and re-open for a second pass to load the data.
        reader.close();
        CharArrayReader reader2 = new CharArrayReader(chars);
        ICovarianceMatrix covarianceMatrix = doCovariancePass(reader2, commentMarker,
                delimiterType, quoteChar, missingValueMarker);

        TetradLogger.getInstance().log("info", "\nData set loaded!");
        return covarianceMatrix;
    }

    /**
     * Parses the given files for a tabular data set, returning a RectangularDataSet if successful.
     *
     * @param file               The text file to load the data from.
     * @param commentMarker      The comment marker as a string--e.g., "//".
     * @param delimiter          One of the options in the Delimiter enum--e.g., Delimiter.TAB.
     * @param quoteCharacter     The quote character, e.g., '\"'.
     * @param missingValueMarker The missing value marker as a string--e.g., "NA".
     * @throws IOException if the file cannot be read.
     */
    public static ICovarianceMatrix loadCovarianceMatrix(File file, String commentMarker,
                                                         DelimiterType delimiter,
                                                         char quoteCharacter,
                                                         String missingValueMarker) throws IOException {
        FileReader reader = null;

        try {
            reader = new FileReader(file);
            ICovarianceMatrix covarianceMatrix = doCovariancePass(reader, commentMarker,
                    delimiter, quoteCharacter, missingValueMarker);

            TetradLogger.getInstance().log("info", "\nCovariance matrix loaded!");
            return covarianceMatrix;
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            if (reader != null) {
                reader.close();
            }

            throw new RuntimeException("Parsing failed.", e);
        }
    }

    private static ICovarianceMatrix doCovariancePass(Reader reader, String commentMarker, DelimiterType delimiterType,
                                                      char quoteChar, String missingValueMarker) {
        TetradLogger.getInstance().log("info", "\nDATA LOADING PARAMETERS:");
        TetradLogger.getInstance().log("info", "File type = COVARIANCE");
        TetradLogger.getInstance().log("info", "Comment marker = " + commentMarker);
        TetradLogger.getInstance().log("info", "Delimiter type = " + delimiterType);
        TetradLogger.getInstance().log("info", "Quote char = " + quoteChar);
        TetradLogger.getInstance().log("info", "Missing value marker = " + missingValueMarker);
        TetradLogger.getInstance().log("info", "--------------------");

        Lineizer lineizer = new Lineizer(reader, commentMarker);

        // Skip "/Covariance" if it is there.
        String line = lineizer.nextLine();

        if ("/Covariance".equalsIgnoreCase(line.trim())) {
            line = lineizer.nextLine();
        }

        // Read br sample size.
        RegexTokenizer st = new RegexTokenizer(line, delimiterType.getPattern(), quoteChar);
        String token = st.nextToken();

        int n;

        try {
            n = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Expected a sample size here, got \"" + token + "\".");
        }

        if (st.hasMoreTokens() && !"".equals(st.nextToken())) {
            throw new IllegalArgumentException(
                    "Line from file has more tokens than expected: \"" + st.nextToken() + "\"");
        }

        // Read br variable names and set up DataSet.
        line = lineizer.nextLine();

        // Variable lists can't have missing values, so we can excuse an extra tab at the end of the line.
        if (line.subSequence(line.length() - 1, line.length()).equals("\t")) {
            line = line.substring(0, line.length() - 1);
        }

        st = new RegexTokenizer(line, delimiterType.getPattern(), quoteChar);

        List<String> vars = new ArrayList<>();

        while (st.hasMoreTokens()) {
            String _token = st.nextToken();

            if ("".equals(_token)) {
                TetradLogger.getInstance().log("emptyToken", "Parsed an empty token for a variable name--ignoring.");
                continue;
            }

            vars.add(_token);
        }

        String[] varNames = vars.toArray(new String[0]);

        TetradLogger.getInstance().log("info", "Variables:");

        for (String varName : varNames) {
            TetradLogger.getInstance().log("info", varName + " --> Continuous");
        }

        // Read br covariances.
        Matrix c = new Matrix(vars.size(), vars.size());

        for (int i = 0; i < vars.size(); i++) {
            st = new RegexTokenizer(lineizer.nextLine(), delimiterType.getPattern(), quoteChar);

            for (int j = 0; j <= i; j++) {
                if (!st.hasMoreTokens()) {
                    throw new IllegalArgumentException("Expecting " + (i + 1)
                            + " numbers on line " + (i + 1)
                            + " of the covariance " + "matrix input.");
                }

                String literal = st.nextToken();

                if ("".equals(literal)) {
                    TetradLogger.getInstance().log("emptyToken", "Parsed an empty token for a "
                            + "covariance value--ignoring.");
                    continue;
                }

                if ("*".equals(literal)) {
                    c.set(i, j, Double.NaN);
                    c.set(j, i, Double.NaN);
                    continue;
                }

                double r = Double.parseDouble(literal);

                c.set(i, j, r);
                c.set(j, i, r);
            }
        }

        Knowledge knowledge = loadKnowledge(lineizer, delimiterType.getPattern());

        ICovarianceMatrix covarianceMatrix
                = new CovarianceMatrix(DataUtils.createContinuousVariables(varNames), c, n);

        covarianceMatrix.setKnowledge(knowledge);

        TetradLogger.getInstance().log("info", "\nData set loaded!");
        return covarianceMatrix;
    }

    /**
     * Returns the datamodel case to DataSet if it is discrete.
     */
    public static DataSet getDiscreteDataSet(DataModel dataSet) {
        if (!(dataSet instanceof DataSet) || !dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Sorry, I was expecting a discrete data set.");
        }

        return (DataSet) dataSet;
    }

    /**
     * Returns the datamodel case to DataSet if it is continuous.
     */
    public static DataSet getContinuousDataSet(DataModel dataSet) {
        if (!(dataSet instanceof DataSet) || !dataSet.isContinuous()) {
            throw new IllegalArgumentException("Sorry, I was expecting a (tabular) continuous data set.");
        }

        return (DataSet) dataSet;
    }

    /**
     * Returns the datamodel case to DataSet if it is mixed.
     */
    public static DataSet getMixedDataSet(DataModel dataSet) {
        if (!(dataSet instanceof DataSet)) {
            throw new IllegalArgumentException("Sorry, I was expecting a (tabular) mixed data set.");
        }

        return (DataSet) dataSet;
    }


    /**
     * Returns the model cast to ICovarianceMatrix if already a covariance matric, or else returns the covariance matrix
     * for a dataset.
     */
    public static ICovarianceMatrix getCovarianceMatrix(DataModel dataModel, boolean precomputeCovariances) {
        if (dataModel == null) {
            throw new IllegalArgumentException("Expecting either a tabular dataset or a covariance matrix.");
        }

        if (dataModel instanceof ICovarianceMatrix) {
            return (ICovarianceMatrix) dataModel;
        } else if (dataModel instanceof DataSet) {
            return getCovarianceMatrix((DataSet) dataModel, precomputeCovariances);
//            return new CovarianceMatrix((DataSet) dataModel);
        } else {
            throw new IllegalArgumentException("Sorry, I was expecting either a tabular dataset or a covariance matrix.");
        }
    }

    @NotNull
    public static ICovarianceMatrix getCovarianceMatrix(DataSet dataSet, boolean precomputeCovariances) {
        if (precomputeCovariances) {
            return new CovarianceMatrix(dataSet);
        } else {
            return new CovarianceMatrixOnTheFly(dataSet);
        }

//        return new CovarianceMatrix(dataSet, true);
    }

    @NotNull
    public static ICovarianceMatrix getCorrelationMatrix(DataSet dataSet) {
        return new CorrelationMatrix(dataSet);
    }

    /**
     * Loads knowledge from a file. Assumes knowledge is the only thing in the file. No jokes please. :)
     *
     * @param file          The text file to load the data from.
     * @param delimiter     One of the options in the Delimiter enum--e.g., Delimiter.TAB.
     * @param commentMarker The comment marker as a string--e.g., "//".
     */
    public static Knowledge loadKnowledge(File file, DelimiterType delimiter, String commentMarker) throws IOException {
        FileReader reader = new FileReader(file);
        Lineizer lineizer = new Lineizer(reader, commentMarker);
        Knowledge knowledge = loadKnowledge(lineizer, delimiter.getPattern());
        TetradLogger.getInstance().reset();
        return knowledge;
    }

    /**
     * Reads a knowledge file in tetrad2 format (almost--only does temporal tiers currently). Format is:
     * <pre>
     * /knowledge
     * addtemporal
     * 0 x1 x2
     * 1 x3 x4
     * 4 x5
     * </pre>
     */
    private static Knowledge loadKnowledge(Lineizer lineizer, Pattern delimiter) {
        Knowledge knowledge = new Knowledge();

        String line = lineizer.nextLine();
        String firstLine = line;

        if (line == null) {
            return new Knowledge();
        }

        if (line.startsWith("/knowledge")) {
            line = lineizer.nextLine();
            firstLine = line;
        }

        TetradLogger.getInstance().log("info", "\nLoading knowledge.");

        SECTIONS:
        while (lineizer.hasMoreLines()) {
            if (firstLine == null) {
                line = lineizer.nextLine();
            } else {
                line = firstLine;
            }

            // "addtemp" is the original in Tetrad 2.
            if ("addtemporal".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    int tier = -1;

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');
                    if (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        boolean forbiddenWithin = false;
                        if (token.endsWith("*")) {
                            forbiddenWithin = true;
                            token = token.substring(0, token.length() - 1);
                        }

                        tier = Integer.parseInt(token);
                        if (tier < 0) {
                            throw new IllegalArgumentException(
                                    lineizer.getLineNumber() + ": Tiers must be 0, 1, 2...");
                        }
                        if (forbiddenWithin) {
                            knowledge.setTierForbiddenWithin(tier, true);
                        }
                    }

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();

                        if (token.isEmpty()) {
                            continue;
                        }

                        String name = substitutePeriodsForSpaces(token);

                        addVariable(knowledge, name);

                        knowledge.addToTier(tier, name);

                        TetradLogger.getInstance().log("info", "Adding to tier " + (tier) + " " + name);
                    }
                }
            } else if ("forbiddengroup".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    Set<String> from = new HashSet<>();
                    Set<String> to = new HashSet<>();

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        String name = substitutePeriodsForSpaces(token);

                        addVariable(knowledge, name);

                        from.add(name);
                    }

                    line = lineizer.nextLine();

                    st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        String name = substitutePeriodsForSpaces(token);

                        addVariable(knowledge, name);

                        to.add(name);
                    }

                    KnowledgeGroup group = new KnowledgeGroup(KnowledgeGroup.FORBIDDEN, from, to);

                    knowledge.addKnowledgeGroup(group);
                }
            } else if ("requiredgroup".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    Set<String> from = new HashSet<>();
                    Set<String> to = new HashSet<>();

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        String name = substitutePeriodsForSpaces(token);

                        addVariable(knowledge, name);

                        from.add(name);
                    }

                    line = lineizer.nextLine();

                    st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        String name = substitutePeriodsForSpaces(token);

                        addVariable(knowledge, name);

                        to.add(name);
                    }

                    KnowledgeGroup group = new KnowledgeGroup(KnowledgeGroup.REQUIRED, from, to);

                    knowledge.addKnowledgeGroup(group);
                }
            } else if ("forbiddirect".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');
                    String from = null, to = null;

                    if (st.hasMoreTokens()) {
                        from = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        to = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Lines contains more than two elements.");
                    }

                    if (from == null || to == null) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Line contains fewer than two elements.");
                    }

                    addVariable(knowledge, from);

                    addVariable(knowledge, to);

                    knowledge.setForbidden(from, to);
                }
            } else if ("requiredirect".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');
                    String from = null, to = null;

                    if (st.hasMoreTokens()) {
                        from = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        to = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Lines contains more than two elements.");
                    }

                    if (from == null || to == null) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Line contains fewer than two elements.");
                    }

                    addVariable(knowledge, from);
                    addVariable(knowledge, to);

                    knowledge.removeForbidden(from, to);
                    knowledge.setRequired(from, to);
                }
            } else {
                throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                        + ": Expecting 'addtemporal', 'forbiddirect' or 'requiredirect'.");
            }
        }

        return knowledge;
    }

    private static void addVariable(Knowledge knowledge, String from) {
        if (!knowledge.getVariables().contains(from)) {
            knowledge.addVariable(from);
        }
    }

    private static String substitutePeriodsForSpaces(String s) {
        return s.replaceAll(" ", ".");
    }


}
