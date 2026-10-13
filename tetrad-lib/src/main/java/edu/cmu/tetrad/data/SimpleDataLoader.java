///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p>SimpleDataLoader class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SimpleDataLoader {

    /**
     * Prevent instantiation.
     */
    private SimpleDataLoader() {
    }

    /**
     * Loads a continuous dataset from a file.
     *
     * @param file               The text file to load the data from.
     * @param commentMarker      The comment marker as a string--e.g., "//".
     * @param quoteCharacter     The quote character, e.g., '\"'.
     * @param missingValueMarker The missing value marker as a string--e.g., "NA".
     * @param hasHeader          True if the first row of the data contains variable names.
     * @param delimiter          One of the options in the Delimiter enum--e.g., Delimiter.TAB.
     * @param excludeFirstColumn If the first column should be excluded from the data.
     * @return The loaded DataSet.
     * @throws java.io.IOException If an error occurred in reading the file.
     */
    // From SimpleDataLoader
    @NotNull
    public static DataSet loadContinuousData(File file, String commentMarker, char quoteCharacter,
                                             String missingValueMarker, boolean hasHeader, Delimiter delimiter,
                                             boolean excludeFirstColumn)
            throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(file.toPath(), delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteCharacter);

        DataColumn[] dataColumns = columnReader.readInDataColumns(excludeFirstColumn ?
                new int[]{1} : new int[]{}, false);

        TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);

        // Need to specify commentMarker, .... again to the TabularDataFileReader
        dataReader.setCommentMarker(commentMarker);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setQuoteCharacter(quoteCharacter);

        Data data = dataReader.read(dataColumns, hasHeader);
        DataModel dataModel = DataConvertUtils.toDataModel(data);
        dataModel.setName(file.getName());

        return (DataSet) dataModel;
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
     * @param excludeFirstColumn If the first columns should be excluded from the data.
     * @return The loaded DataSet.
     * @throws java.io.IOException If an error occurred in reading the file.
     */
    // From SimpleDataLoader
    @NotNull
    public static DataSet loadDiscreteData(File file, String commentMarker, char quoteCharacter,
                                           String missingValueMarker, boolean hasHeader, Delimiter delimiter,
                                           boolean excludeFirstColumn)
            throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(file.toPath(), delimiter);
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludeFirstColumn ?
                new int[]{1} : new int[]{}, true);

        columnReader.setCommentMarker(commentMarker);

        TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);

        // Need to specify commentMarker, .... again to the TabularDataFileReader
        dataReader.setCommentMarker(commentMarker);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setQuoteCharacter(quoteCharacter);

        Data data = dataReader.read(dataColumns, hasHeader);
        DataModel dataModel = DataConvertUtils.toDataModel(data);
        dataModel.setName(file.getName());

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
     * @param maxNumCategories   The maximum number of distinct entries in a columns alloed in order for the column to
     *                           be parsed as discrete.
     * @param delimiter          One of the options in the Delimiter enum--e.g., Delimiter.TAB.
     * @param excludeFirstColumn If the first columns should be excluded from the data set.
     * @return The loaded DataSet.
     * @throws java.io.IOException If an error occurred in reading the file.
     */
    // From SimpleDataLoader
    @NotNull
    public static DataSet loadMixedData(File file, String commentMarker, char quoteCharacter,
                                        String missingValueMarker, boolean hasHeader, int maxNumCategories,
                                        Delimiter delimiter, boolean excludeFirstColumn)
            throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(file.toPath(), delimiter);
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludeFirstColumn ?
                new int[]{1} : new int[]{}, false);

        columnReader.setCommentMarker(commentMarker);

        TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);

        // Need to specify commentMarker, .... again to the TabularDataFileReader
        dataReader.setCommentMarker(commentMarker);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.determineDiscreteDataColumns(dataColumns, maxNumCategories, hasHeader);

        Data data = dataReader.read(dataColumns, hasHeader);

        if (data != null) {
            DataModel dataModel = DataConvertUtils.toDataModel(data);
            dataModel.setName(file.getName());
            return (DataSet) dataModel;
        }

        return null;
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
     *
     * @param chars              an array of  objects
     * @param commentMarker      a {@link java.lang.String} object
     * @param delimiterType      a {@link edu.cmu.tetrad.data.DelimiterType} object
     * @param quoteChar          a char
     * @param missingValueMarker a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
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

        TetradLogger.getInstance().log("\nData set loaded!");
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
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @throws java.io.IOException if the file cannot be read.
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

            TetradLogger.getInstance().log("\nCovariance matrix loaded!");
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
        TetradLogger.getInstance().log("\nDATA LOADING PARAMETERS:");
        TetradLogger.getInstance().log("File type = COVARIANCE");
        TetradLogger.getInstance().log("Comment marker = " + commentMarker);
        TetradLogger.getInstance().log("Delimiter type = " + delimiterType);
        TetradLogger.getInstance().log("Quote char = " + quoteChar);
        TetradLogger.getInstance().log("Missing value marker = " + missingValueMarker);
        TetradLogger.getInstance().log("--------------------");

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
                TetradLogger.getInstance().warn("Parsed an empty token for a variable name--ignoring.");
                continue;
            }

            vars.add(_token);
        }

        String[] varNames = vars.toArray(new String[0]);

        TetradLogger.getInstance().log("Variables:");

        for (String varName : varNames) {
            TetradLogger.getInstance().log(varName + " --> Continuous");
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
                    TetradLogger.getInstance().warn("Parsed an empty token for a "
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

        TetradLogger.getInstance().log("\nData set loaded!");
        return covarianceMatrix;
    }

    /**
     * Returns the datamodel case to DataSet if it is discrete.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataModel} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet getDiscreteDataSet(DataModel dataSet) {
        if (!(dataSet instanceof DataSet) || !dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Sorry, I was expecting a discrete data set.");
        }

        return (DataSet) dataSet;
    }

    /**
     * Returns the datamodel case to DataSet if it is continuous.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataModel} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet getContinuousDataSet(DataModel dataSet) {
        if (!(dataSet instanceof DataSet) || !dataSet.isContinuous()) {
            throw new IllegalArgumentException("Sorry, I was expecting a (tabular) continuous data set.");
        }

        return (DataSet) dataSet;
    }

    /**
     * Returns the datamodel case to DataSet if it is mixed.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataModel} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
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
     *
     * @param dataModel             a {@link edu.cmu.tetrad.data.DataModel} object
     * @param precomputeCovariances a boolean
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
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

    /**
     * <p>getCovarianceMatrix.</p>
     *
     * @param dataSet               a {@link edu.cmu.tetrad.data.DataSet} object
     * @param precomputeCovariances a boolean
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    @NotNull
    public static ICovarianceMatrix getCovarianceMatrix(DataSet dataSet, boolean precomputeCovariances) {
        if (precomputeCovariances) {
            return new CovarianceMatrix(dataSet);
        } else {
            return new CovarianceMatrixOnTheFly(dataSet);
        }
    }

    /**
     * <p>getCorrelationMatrix.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
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
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     * @throws java.io.IOException if any.
     */
    public static Knowledge loadKnowledge(File file, DelimiterType delimiter, String commentMarker) throws IOException {
        FileReader reader = new FileReader(file);
        return loadKnowledge(reader, delimiter, commentMarker);
    }

    /**
     * Loads knowledge from an arbitrary character stream, in the same tetrad2 format
     * {@link #loadKnowledge(File, DelimiterType, String)} reads and {@code DataWriter.saveKnowledge}
     * writes. Added 2026-8-13 so that callers holding the specification as text -- in particular the
     * editable "Text" tab of the knowledge editor -- can parse it through exactly the same path as
     * the file loader, rather than round-tripping through a temporary file or reimplementing the
     * parse.
     *
     * @param reader        the character stream to read the specification from
     * @param delimiter     one of the options in the DelimiterType enum
     * @param commentMarker the comment marker as a string -- e.g., "//"
     * @return the parsed knowledge
     * @throws java.io.IOException if any.
     */
    public static Knowledge loadKnowledge(Reader reader, DelimiterType delimiter, String commentMarker) throws IOException {
        Lineizer lineizer = new Lineizer(reader, commentMarker);
        Knowledge knowledge = loadKnowledge(lineizer, delimiter.getPattern());
        TetradLogger.getInstance().reset();
        return knowledge;
    }

    /**
     * Reads a knowledge file in tetrad2 format. The sections are addtemporal (the main tiers),
     * tierstructure (additional named tier structures, one section per structure), forbiddengroup
     * and requiredgroup (one from-line and one to-line per group), and forbiddirect and
     * requiredirect (one from/to pair per line). For example:
     * <pre>
     * /knowledge
     * addtemporal
     * 0 x1 x2
     * 1 x3 x4
     * 4 x5
     * </pre>
     * A header line of {@code /knowledge comma} switches the file to the comma-delimited variant,
     * in which names in tier and group lines are separated by commas (the tier index remains the
     * first whitespace-delimited token of its line), the from- and to-names of a forbiddirect or
     * requiredirect line are separated by a comma, and a name containing a comma is written in
     * double quotes. This is the variant that represents variable names containing spaces; in the
     * whitespace-delimited variant, spaces in tier and group names are replaced by periods, as
     * they always were.
     */
    private static Knowledge loadKnowledge(Lineizer lineizer, Pattern delimiter) {
        Knowledge knowledge = new Knowledge();

        String line = lineizer.nextLine();
        String firstLine = line;

        if (line == null) {
            return new Knowledge();
        }

        boolean commaMode = false;

        if (line.startsWith("/knowledge")) {
            commaMode = line.substring("/knowledge".length()).toLowerCase().contains("comma");
            line = lineizer.nextLine();
            firstLine = line;
        }

        if (commaMode) {

            // Spaces around the commas are eaten with the delimiter, so that a quoted name is
            // recognized after ", " and tokens come back without surrounding spaces.
            delimiter = Pattern.compile("\\s*,\\s*");
        }

        TetradLogger.getInstance().log("\nLoading knowledge.");

        // "Can cause only next tier" rules are applied after the whole file is read, since they
        // forbid edges into tiers that may not have been read yet when their line is.
        List<Integer> onlyCauseNextTiers = new ArrayList<>();

        SECTIONS:
        while (lineizer.hasMoreLines()) {
            if (firstLine == null) {
                line = lineizer.nextLine();
            } else {
                line = firstLine;
            }

            String trimmed = line.trim();

            // "addtemp" is the original in Tetrad 2.
            if ("addtemporal".equalsIgnoreCase(trimmed)) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (isSectionHeader(line)) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    TierLine tierLine = parseTierLine(line, delimiter, commaMode,
                            lineizer.getLineNumber());

                    if (tierLine.forbiddenWithin) {
                        knowledge.setTierForbiddenWithin(tierLine.tier, true);
                    }

                    if (tierLine.onlyCauseNext) {
                        onlyCauseNextTiers.add(tierLine.tier);
                    }

                    for (String name : tierLine.names) {
                        addVariable(knowledge, name);
                        knowledge.addToTier(tierLine.tier, name);
                        TetradLogger.getInstance().log("Adding to tier " + tierLine.tier + " " + name);
                    }
                }
            } else if (trimmed.toLowerCase().startsWith("tierstructure")) {
                String name = trimmed.substring("tierstructure".length()).trim();

                if (name.isEmpty()) {
                    name = "Structure " + (knowledge.getNumTierStructures() + 1);
                }

                KnowledgeTierStructure structure = new KnowledgeTierStructure(name);
                knowledge.addTierStructure(structure);

                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (isSectionHeader(line)) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    TierLine tierLine = parseTierLine(line, delimiter, commaMode,
                            lineizer.getLineNumber());

                    if (tierLine.onlyCauseNext) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": The '-' (can cause only next tier) flag is not supported in "
                                + "a tier structure.");
                    }

                    if (tierLine.forbiddenWithin) {
                        structure.setTierForbiddenWithin(tierLine.tier, true);
                    }

                    for (String varName : tierLine.names) {
                        addVariable(knowledge, varName);
                        structure.addToTier(tierLine.tier, varName);
                        TetradLogger.getInstance().log("Adding to tier " + tierLine.tier
                                + " of structure " + name + " " + varName);
                    }
                }
            } else if ("forbiddengroup".equalsIgnoreCase(trimmed)) {
                firstLine = readGroupSection(lineizer, delimiter, commaMode, knowledge,
                        KnowledgeGroup.FORBIDDEN);
                if (firstLine != null) continue SECTIONS;
            } else if ("requiredgroup".equalsIgnoreCase(trimmed)) {
                firstLine = readGroupSection(lineizer, delimiter, commaMode, knowledge,
                        KnowledgeGroup.REQUIRED);
                if (firstLine != null) continue SECTIONS;
            } else if ("forbiddirect".equalsIgnoreCase(trimmed)) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (isSectionHeader(line)) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    String[] pair = readEdgeLine(line, delimiter, lineizer.getLineNumber());

                    addVariable(knowledge, pair[0]);
                    addVariable(knowledge, pair[1]);

                    knowledge.setForbidden(pair[0], pair[1]);
                }
            } else if ("requiredirect".equalsIgnoreCase(trimmed)) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (isSectionHeader(line)) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    String[] pair = readEdgeLine(line, delimiter, lineizer.getLineNumber());

                    addVariable(knowledge, pair[0]);
                    addVariable(knowledge, pair[1]);

                    knowledge.removeForbidden(pair[0], pair[1]);
                    knowledge.setRequired(pair[0], pair[1]);
                }
            } else {
                throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                                   + ": Expecting 'addtemporal', 'tierstructure', "
                                                   + "'forbiddengroup', 'requiredgroup', "
                                                   + "'forbiddirect' or 'requiredirect'.");
            }
        }

        for (int tier : onlyCauseNextTiers) {
            knowledge.setOnlyCanCauseNextTier(tier, true);
        }

        return knowledge;
    }

    /**
     * True iff the line opens one of the knowledge sections. Matched by prefix on the trimmed
     * line, as the original per-section checks were.
     */
    private static boolean isSectionHeader(String line) {
        String trimmed = line.trim().toLowerCase();
        return trimmed.startsWith("addtemporal")
               || trimmed.startsWith("tierstructure")
               || trimmed.startsWith("forbiddengroup")
               || trimmed.startsWith("requiredgroup")
               || trimmed.startsWith("forbiddirect")
               || trimmed.startsWith("requiredirect");
    }

    /**
     * The parse of one tier line: the tier index, its flags, and the names on the line.
     */
    private static final class TierLine {
        int tier;
        boolean forbiddenWithin;
        boolean onlyCauseNext;
        final List<String> names = new ArrayList<>();
    }

    /**
     * Parses one tier line. The first whitespace-delimited token is the tier index, optionally
     * suffixed by '*' (edges forbidden within the tier) and/or '-' (the tier can cause only the
     * next tier); the remaining names are delimited by the given pattern. In the
     * whitespace-delimited variant, spaces within names are replaced by periods, as the loader
     * always did; in the comma-delimited variant names are taken as written.
     */
    private static TierLine parseTierLine(String line, Pattern delimiter, boolean commaMode,
                                          int lineNumber) {
        TierLine result = new TierLine();
        String spec;
        RegexTokenizer st;

        if (commaMode) {

            // The tier index is the first whitespace-delimited token of the line; the names
            // after it are comma-delimited.
            String trimmed = line.trim();
            int space = indexOfWhitespace(trimmed);
            String rest;

            if (space < 0) {
                spec = trimmed;
                rest = "";
            } else {
                spec = trimmed.substring(0, space);
                rest = trimmed.substring(space + 1);
            }

            st = new RegexTokenizer(rest, delimiter, '"');
        } else {

            // The whole line is tokenized by the file's delimiter, and the first token is the
            // tier index, as the loader always did.
            st = new RegexTokenizer(line, delimiter, '"');
            spec = st.hasMoreTokens() ? st.nextToken().trim() : "";
        }

        while (spec.endsWith("*") || spec.endsWith("-")) {
            if (spec.endsWith("*")) {
                result.forbiddenWithin = true;
            } else {
                result.onlyCauseNext = true;
            }

            spec = spec.substring(0, spec.length() - 1);
        }

        try {
            result.tier = Integer.parseInt(spec);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(lineNumber
                    + ": Expecting a tier index (0, 1, 2...) at the start of the line, possibly "
                    + "followed by '*' or '-': " + line);
        }

        if (result.tier < 0) {
            throw new IllegalArgumentException(lineNumber + ": Tiers must be 0, 1, 2...");
        }

        while (st.hasMoreTokens()) {
            String token = st.nextToken().trim();

            if (token.isEmpty()) {
                continue;
            }

            result.names.add(commaMode ? token : substitutePeriodsForSpaces(token));
        }

        return result;
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Reads a forbiddengroup or requiredgroup section: one from-line and one to-line per group,
     * repeated until another section begins or the file ends. Returns the header line of the next
     * section, or null at the end of the file.
     */
    private static String readGroupSection(Lineizer lineizer, Pattern delimiter, boolean commaMode,
                                           Knowledge knowledge, int type) {
        while (lineizer.hasMoreLines()) {
            String line = lineizer.nextLine();

            if (isSectionHeader(line)) {
                return line;
            }

            Set<String> from = readNameLine(line, delimiter, commaMode, knowledge);

            if (!lineizer.hasMoreLines()) {
                throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                        + ": A group needs a from-line and a to-line; the to-line is missing.");
            }

            String toLine = lineizer.nextLine();

            if (isSectionHeader(toLine)) {
                throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                        + ": A group needs a from-line and a to-line; the to-line is missing.");
            }

            Set<String> to = readNameLine(toLine, delimiter, commaMode, knowledge);

            knowledge.addKnowledgeGroup(new KnowledgeGroup(type, from, to));
        }

        return null;
    }

    private static Set<String> readNameLine(String line, Pattern delimiter, boolean commaMode,
                                            Knowledge knowledge) {
        Set<String> names = new HashSet<>();
        RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');

        while (st.hasMoreTokens()) {
            String token = st.nextToken().trim();

            if (token.isEmpty()) {
                continue;
            }

            String name = commaMode ? token : substitutePeriodsForSpaces(token);
            addVariable(knowledge, name);
            names.add(name);
        }

        return names;
    }

    /**
     * Reads a forbiddirect or requiredirect line: exactly a from-name and a to-name, delimited by
     * the given pattern.
     */
    private static String[] readEdgeLine(String line, Pattern delimiter, int lineNumber) {
        RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');
        String from = null, to = null;

        if (st.hasMoreTokens()) {
            from = st.nextToken().trim();
        }

        if (st.hasMoreTokens()) {
            to = st.nextToken().trim();
        }

        if (st.hasMoreTokens()) {
            throw new IllegalArgumentException("Line " + lineNumber
                                               + ": Lines contains more than two elements.");
        }

        if (from == null || to == null || from.isEmpty() || to.isEmpty()) {
            throw new IllegalArgumentException("Line " + lineNumber
                                               + ": Line contains fewer than two elements.");
        }

        return new String[]{from, to};
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
