///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NamingProtocol;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Parses a tabular data file or covariance matrix, with or without data file
 * sectioning, with possibly mixed continuous/discrete variables. With
 * sectioning, a /variables, a /data, and a /knowledge section may be specified.
 * (The /data section is required.) Without sectioning, it is assumed that no
 * variables will be defined in advance and that there will be no knowledge.
 *
 * @author Joseph Ramsey
 */
public final class DataReader {

    /**
     * A set of characters that in any combination makes up a delimiter.
     */
    private DelimiterType delimiterType = DelimiterType.WHITESPACE;

    /**
     * True iff variable names in the data section of the file are listed in the
     * first row.
     */
    private boolean varNamesSupplied = true;

    /**
     * True iff case IDs are provided in the file.
     */
    private boolean idsSupplied = false;

    /**
     * Assuming caseIdsPresent is true, this is null if case IDs are in an
     * unlabeled initial column; otherwise, they are assumed to be in a labeled
     * column by this name.
     */
    private String idLabel = null;

    /**
     * The initial segment of a line that is to be considered a comment line.
     */
    private String commentMarker = "//";

    /**
     * A character that sets off quoted strings.
     */
    private char quoteChar = '"';

    /**
     * In parsing data, missing values will be marked either by this string or
     * by an empty string.
     */
    private String missingValueMarker = "*";

    /**
     * In parsing integral columns, columns with up to this many distinct values
     * will be parsed as discrete; otherwise, continuous.
     */
    private int maxIntegralDiscrete = 0;

    /**
     * Known variable definitions. These will usurp any guessed variable
     * definitions by name.
     */
    private List<Node> knownVariables = new LinkedList<Node>();


    /**
     * The tetrad logger.
     */
    private TetradLogger logger = TetradLogger.getInstance();


    /**
     * Log empty token messages.
     */
    private boolean logEmptyTokens = false;

    /**
     * True if variable names should be read lowercase.
     */
    private boolean readVariablesLowercase = false;

    /**
     * True if variable names should be read uppercase.
     */
    private boolean readVariablesUppercase = false;

    /**
     * Constructs a new data parser.
     */
    public DataReader() {
    }

    //============================PUBLIC METHODS========================//


    public void setLogEmptyTokens(boolean log) {
        this.logEmptyTokens = log;
    }


    /**
     * Lines beginning with blanks or this marker will be skipped.
     */
    public void setCommentMarker(String commentMarker) {
        if (commentMarker == null) {
            throw new NullPointerException("Cannot be null.");
        }

        this.commentMarker = commentMarker;
    }

    /**
     * This is the delimiter used to parse the data. Default is whitespace.
     */
    public void setDelimiter(DelimiterType delimiterType) {
        if (delimiterType == null) {
            throw new NullPointerException("Cannot be null.");
        }

        this.delimiterType = delimiterType;
    }

    /**
     * Text between matched ones of these will treated as quoted text.
     */
    public void setQuoteChar(char quoteChar) {
        this.quoteChar = quoteChar;
    }

    /**
     * Will read variable names from the first row if this is true; otherwise,
     * will make make up variables in the series X1, x2, ... Xn.
     */
    public void setVariablesSupplied(boolean varNamesSupplied) {
        this.varNamesSupplied = varNamesSupplied;
    }

    /**
     * If true, a column of ID's is supplied; otherwise, not.
     */
    public void setIdsSupplied(boolean caseIdsPresent) {
        this.idsSupplied = caseIdsPresent;
    }

    /**
     * If null, ID's are in an unlabeled first column; otherwise, they are in
     * the column with the given label.
     */
    public void setIdLabel(String caseIdsLabel) {
        this.idLabel = caseIdsLabel;
    }

    /**
     * Tokens that are blank or equal to this value will be counted as missing
     * values.
     */
    public void setMissingValueMarker(String missingValueMarker) {
        if (missingValueMarker == null) {
            throw new NullPointerException("Cannot be null.");
        }

        this.missingValueMarker = missingValueMarker;
    }

    /**
     * Integral columns with up to this number of discrete values will be
     * treated as discrete.
     */
    public void setMaxIntegralDiscrete(int maxIntegralDiscrete) {
        if (maxIntegralDiscrete < -1) {
            throw new IllegalArgumentException(
                    "Must be >= -1: " + maxIntegralDiscrete);
        }

        this.maxIntegralDiscrete = maxIntegralDiscrete;
    }

    /**
     * The known variables for a given name will usurp guess the variable by
     * that name.
     */
    public void setKnownVariables(List<Node> knownVariables) {
        if (knownVariables == null) {
            throw new NullPointerException();
        }

        this.knownVariables = knownVariables;
    }

    /**
     * Parses the given files for a tabular data set, returning a
     * RectangularDataSet if successful.
     *
     * @throws IOException if the file cannot be read.
     */
    public DataSet parseTabular(File file) throws IOException {
        FileReader reader = null, reader2 = null;

        try {
            // Do first pass to get a description of the file.
            reader = new FileReader(file);
            DataSetDescription description = doFirstTabularPass(reader);

            // Close the reader and re-open for a second pass to load the data.
            reader.close();
            reader2 = new FileReader(file);
            DataSet dataSet = doSecondTabularPass(description, reader2);

            dataSet.setName(file.getName());

            this.logger.log("info", "\nData set loaded!");
            this.logger.reset();
            return dataSet;
        } catch (IOException e) {
            if (reader != null) {
                reader.close();
            }

            throw e;
        } catch (Exception e) {
            if (reader != null) {
                reader.close();
            }

            if (reader2 != null) {
                reader2.close();
            }

            throw new RuntimeException("Parsing failed.", e);
        }
    }

    /**
     * Parses the given character array for a tabular data set, returning a
     * RectangularDataSet if successful. Log messages are written to the
     * LogUtils log; to view them, add System.out to that.
     */
    public DataSet parseTabular(char[] chars) {

        // Do first pass to get a description of the file.
        CharArrayReader reader = new CharArrayReader(chars);
        DataSetDescription description = doFirstTabularPass(reader);

        // Close the reader and re-open for a second pass to load the data.
        reader.close();
        CharArrayReader reader2 = new CharArrayReader(chars);
        DataSet dataSet = doSecondTabularPass(description, reader2);

        this.logger.log("info", "\nData set loaded!");
        this.logger.reset();
        return dataSet;
    }

    private DataSetDescription doFirstTabularPass(Reader reader) {
        DataSetDescription description;
        Lineizer lineizer = new Lineizer(reader, commentMarker);

        if (!lineizer.hasMoreLines()) {
            throw new IllegalArgumentException("Data source is empty.");
        }

        try {
            this.logger.log("info", "\nDATA LOADING PARAMETERS:");
            this.logger.log("info", "File type = TABULAR");
            this.logger.log("info", "Comment marker = " + commentMarker);
            this.logger.log("info", "Delimiter chars = " + delimiterType);
            this.logger.log("info", "Quote char = " + quoteChar);
            this.logger.log("info", "Var names first row = " + varNamesSupplied);
            this.logger.log("info", "IDs supplied = " + idsSupplied);
            this.logger.log("info", "ID label = " + idLabel);
            this.logger.log("info", "Missing value marker = " + missingValueMarker);
            this.logger.log("info", "Max discrete = " + maxIntegralDiscrete);
            this.logger.log("info", "--------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // The delimiter comes from the delimiter type.
        Pattern delimiter = delimiterType.getPattern();

        // Read in variable definitions.
        String line = lineizer.nextLine();

        // If the last character of the list of variables is a tab, drop the tab. Variable lists
        // can't have missing values.
        if (line.subSequence(line.length() - 1, line.length()).equals("\t")) {
            line = line.substring(0, line.length() - 1);
        }

        boolean variableSectionIncluded = false;

        if (line.startsWith("/variables")) {
            variableSectionIncluded = true;

            // Read lines one at a time. The part to the left of ":" is
            // the variable name. Divide the part to the right of ":" using
            // commas. Each token should be of the form n=name. n should
            // start with 0 and increment by 1. Build a variable with this
            // information and store it on the knownVariables list. (If
            // there's already a variable by that name in the list, throw
            // an exception.)
            LINES:
            while (lineizer.hasMoreLines()) {
                line = lineizer.nextLine();

                if (line.startsWith("/data")) {
                    break;
                }

                RegexTokenizer tokenizer = new RegexTokenizer(line,
                        DelimiterType.COLON.getPattern(), quoteChar);
                String name = tokenizer.nextToken().trim();

                if ("".equals(name)) {
                    throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                            + ": Expected a variable name, got an empty token.");
                }

                // Skip any definitions for known variables.
                for (Node node : knownVariables) {
                    if (name.equals(node.getName())) {
                        continue LINES;
                    }
                }

                String values = tokenizer.nextToken();

                if ("".equals(values.trim())) {
                    throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                            + ": Empty variable specification for variable " + name + ".");
                } else if ("Continuous".equalsIgnoreCase(values.trim())) {
                    ContinuousVariable variable = new ContinuousVariable(name);
                    knownVariables.add(variable);
                } else {
                    List<String> categories = new LinkedList<String>();
                    tokenizer = new RegexTokenizer(values,
                            delimiterType.getPattern(), quoteChar);

                    while (tokenizer.hasMoreTokens()) {
                        String token = tokenizer.nextToken().trim();

                        if ("".equals(token)) {
                            throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                    + ": Expected a category name, got an empty token, " +
                                    "for variable " + name + ".");
                        }

                        if (categories.contains(token)) {
                            throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                    + ": Duplicate category (" + token + ") for variable "
                                    + name + ".");
                        }

                        categories.add(token);
                    }

                    DiscreteVariable variable = new DiscreteVariable(name, categories);
                    variable.setAccommodateNewCategories(false);
                    knownVariables.add(variable);
                }
            }
        }

        if (variableSectionIncluded && !line.startsWith("/data")) {
            throw new IllegalArgumentException(
                    "If a /variables section is included, a /data section must follow.");
        }

        // Construct list of variable names.
        String dataFirstLine = line;

        if (line.startsWith("/data")) {
            dataFirstLine = lineizer.nextLine();
        }

        List<String> varNames;

        if (varNamesSupplied) {
            varNames = new ArrayList<String>();
            RegexTokenizer tokenizer =
                    new RegexTokenizer(dataFirstLine, delimiter, quoteChar);

            while (tokenizer.hasMoreTokens()) {
                String name = tokenizer.nextToken().trim();

                if ("".equals(name)) {
                    throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                            + ": Expected variable name, got empty token: " + line);
                }

                if (varNames.contains(name)) {
                    throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                            + ": Duplicate variable name (" + name + ").");
                }

                if (readVariablesLowercase) {
                    varNames.add(name.toLowerCase());
                } else if (readVariablesUppercase) {
                    varNames.add(name.toUpperCase());
                } else {
                    varNames.add(name);
                }
            }

            dataFirstLine = null;
        } else {
            varNames = new LinkedList<String>();
            RegexTokenizer tokenizer =
                    new RegexTokenizer(dataFirstLine, delimiter, quoteChar);

            if (idsSupplied && idLabel == null) {
                if (tokenizer.hasMoreTokens()) {

                    // Eat first token, which is supposed to be a case ID.
                    tokenizer.nextToken();
                }
            }

            int i = 0;

            while (tokenizer.hasMoreTokens()) {
                tokenizer.nextToken();
                varNames.add("X" + (++i));
            }
        }

        // Adjust variable names for id, returning the index of id.
        int idIndex = adjustForId(varNames, lineizer);

        // Scan for variable types.
        description = scanForDescription(varNames, lineizer,
                delimiter, dataFirstLine, idIndex, variableSectionIncluded);
        return description;
    }

    private DataSet doSecondTabularPass(DataSetDescription description, Reader reader2) {
        Lineizer lineizer;
        String dataFirstLine;
        lineizer = new Lineizer(reader2, commentMarker);
        String line2;

        // Skip through /variables.
        if (description.isVariablesSectionIncluded()) {
            while (lineizer.hasMoreLines()) {
                line2 = lineizer.nextLine();

                if (line2.startsWith("/data")) {
                    break;
                }
            }
        }

        line2 = lineizer.nextLine();

        if (line2.startsWith("/data")) {
            line2 = lineizer.nextLine();
        }

        // Note that now line2 is either the first line of the file or the
        // first line after the /data. Either way it's either the variable line
        // or the first line of the data itself.
        dataFirstLine = line2;

        if (varNamesSupplied) {
            line2 = lineizer.nextLine();
            dataFirstLine = line2;
        }

        // Read in the data.
        final List<Node> variables = description.getVariables();
        DataSet dataSet = new ColtDataSet(description.getNumRows(),
                variables);
//        DataSet dataSet = new BoxDataSet(new DoubleDataBox(description.getNumRows(),
//                variables.size()), variables);
//        DataSet dataSet = new NumberObjectDataSet(description.getNumRows(),
//                description.getVariables());

//        ShortDataBox box = new ShortDataBox(description.getNumRows(), description.getVariables().size());
//        BoxDataSet dataSet = new BoxDataSet(box, description.getVariables());

        int row = -1;

        while (lineizer.hasMoreLines()) {
            if (dataFirstLine == null) {
                line2 = lineizer.nextLine();
            } else {
                line2 = dataFirstLine;
                dataFirstLine = null;
            }

            if (line2.startsWith("/knowledge")) {
                break;
            }

            ++row;

            RegexTokenizer tokenizer1 = new RegexTokenizer(line2, description.getDelimiter(),
                    quoteChar);

            if (description.isMultColumnIncluded() && tokenizer1.hasMoreTokens()) {
                String token = tokenizer1.nextToken().trim();
                int multiplier = Integer.parseInt(token);
                dataSet.setMultiplier(row, multiplier);
            }

            int col = -1;

            while (tokenizer1.hasMoreTokens()) {
                String token = tokenizer1.nextToken().trim();
                setValue(dataSet, row, ++col, token);
            }
        }

        // Copy ids into the data set and remove the id column.
        if (description.getIdIndex() != -1) {
            DiscreteVariable idVar =
                    (DiscreteVariable) dataSet.getVariable(description.getIdIndex());

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                int index = dataSet.getInt(i, description.getIdIndex());

                if (index == -99) {
                    continue;
                }

                String id = idVar.getCategories().get(index);
                dataSet.setCaseId(i, id);
            }

            dataSet.removeColumn(idVar);
        }

        IKnowledge knowledge = parseKnowledge(lineizer, delimiterType.getPattern());

        if (knowledge != null) {
            dataSet.setKnowledge(knowledge);
        }

        return dataSet;
    }

    /**
     * Parses the given files for a tabular data set, returning a
     * RectangularDataSet if successful.
     *
     * @throws IOException if the file cannot be read.
     */
    public ICovarianceMatrix parseCovariance(File file) throws IOException {
        FileReader reader = null;

        try {
            reader = new FileReader(file);
            ICovarianceMatrix covarianceMatrix = doCovariancePass(reader);

            this.logger.log("info", "\nCovariance matrix loaded!");
            this.logger.reset();
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

    /**
     * Reads in a covariance matrix. The format is as follows. </p>
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
     * </pre>
     * The initial "/covariance" is optional.
     */
    public ICovarianceMatrix parseCovariance(char[] chars) {

        // Do first pass to get a description of the file.
        CharArrayReader reader = new CharArrayReader(chars);
        DataSetDescription description = doFirstTabularPass(reader);

        // Close the reader and re-open for a second pass to load the data.
        reader.close();
        CharArrayReader reader2 = new CharArrayReader(chars);
        ICovarianceMatrix covarianceMatrix = doCovariancePass(reader2);

        this.logger.log("info", "\nData set loaded!");
        this.logger.reset();
        return covarianceMatrix;
    }


    public ICovarianceMatrix doCovariancePass(Reader reader) {
        this.logger.log("info", "\nDATA LOADING PARAMETERS:");
        this.logger.log("info", "File type = COVARIANCE");
        this.logger.log("info", "Comment marker = " + commentMarker);
        this.logger.log("info", "Delimiter type = " + delimiterType);
        this.logger.log("info", "Quote char = " + quoteChar);
        //        LogUtils.getInstance().info("Var names first row = " + varNamesSupplied);
//        LogUtils.getInstance().info("IDs supplied = " + idsSupplied);
//        LogUtils.getInstance().info("ID label = " + idLabel);
        this.logger.log("info", "Missing value marker = " + missingValueMarker);
        //        LogUtils.getInstance().info("Max discrete = " + maxIntegralDiscrete);
        this.logger.log("info", "--------------------");

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

        List<String> vars = new ArrayList<String>();

        while (st.hasMoreTokens()) {
            String _token = st.nextToken();

            if ("".equals(_token)) {
                TetradLogger.getInstance().log("emptyToken", "Parsed an empty token for a variable name--ignoring.");
                continue;
            }

            vars.add(_token);
        }

        String[] varNames = vars.toArray(new String[vars.size()]);

        this.logger.log("info", "Variables:");

        for (String varName : varNames) {
            this.logger.log("info", varName + " --> Continuous");
        }

        // Read br covariances.
        TetradMatrix c = new TetradMatrix(vars.size(), vars.size());

        for (int i = 0; i < vars.size(); i++) {
            st = new RegexTokenizer(lineizer.nextLine(), delimiterType.getPattern(), quoteChar);

            for (int j = 0; j <= i; j++) {
                if (!st.hasMoreTokens()) {
                    throw new IllegalArgumentException("Expecting " + (i + 1) +
                            " numbers on line " + (i + 1) +
                            " of the covariance " + "matrix input.");
                }

                String literal = st.nextToken();

                if ("".equals(literal)) {
                    TetradLogger.getInstance().log("emptyToken", "Parsed an empty token for a " +
                            "covariance value--ignoring.");
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

        IKnowledge knowledge = parseKnowledge(lineizer, delimiterType.getPattern());

        ICovarianceMatrix covarianceMatrix =
                new CovarianceMatrix(DataUtils.createContinuousVariables(varNames), c, n);

        if (knowledge != null) {
            covarianceMatrix.setKnowledge(knowledge);
        }

        this.logger.log("info", "\nData set loaded!");
        this.logger.reset();
        return covarianceMatrix;
    }

    /**
     * Loads knowledge from a file. Assumes knowledge is the only thing in
     * the file. No jokes please. :)
     */
    public IKnowledge parseKnowledge(File file) throws IOException {
        FileReader reader = new FileReader(file);
        Lineizer lineizer = new Lineizer(reader, commentMarker);
        IKnowledge knowledge = parseKnowledge(lineizer, delimiterType.getPattern());
        this.logger.reset();
        return knowledge;
    }

    /**
     * Parses knowledge from the char array, assuming that's all there is in
     * the char array.
     */
    public IKnowledge parseKnowledge(char[] chars) {
        CharArrayReader reader = new CharArrayReader(chars);
        Lineizer lineizer = new Lineizer(reader, commentMarker);
        IKnowledge knowledge = parseKnowledge(lineizer, delimiterType.getPattern());
        this.logger.reset();
        return knowledge;
    }

    //============================PRIVATE METHODS========================//

    private int adjustForId(List<String> varNames, Lineizer lineizer) {
        int idIndex = -1;

        if (idsSupplied) {
            if (idLabel == null) {
                idIndex = 0;
                varNames.add(0, "");
            } else {
                idIndex = varNames.indexOf(idLabel);

                if (idIndex == -1) {
                    throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                            + ": The given ID column label (" + idLabel + ") was not among " +
                            "the list of variables.");
                }
            }
        }
        return idIndex;
    }

    private void setValue(DataSet dataSet, int row, int col, String s) {
        if (s == null || s.equals("") || s.trim().equals(missingValueMarker)) {
            return;
        }

        if (col >= dataSet.getNumColumns()) {
            return;
        }

        Node node = dataSet.getVariable(col);

        if (node instanceof ContinuousVariable) {
            try {
                double value = Double.parseDouble(s);
                dataSet.setDouble(row, col, value);
            } catch (NumberFormatException e) {
                dataSet.setDouble(row, col, Double.NaN);
            }
        } else if (node instanceof DiscreteVariable) {
            DiscreteVariable var = (DiscreteVariable) node;
            int value = var.getCategories().indexOf(s.trim());

            if (value == -1) {
                dataSet.setInt(row, col, -99);
            } else {
                dataSet.setInt(row, col, value);
            }
        }
    }

    /**
     * Reads a knowledge file in tetrad2 format (almost--only does temporal
     * tiers currently). Format is:
     * <pre>
     * /knowledge
     * addtemporal
     * 0 x1 x2
     * 1 x3 x4
     * 4 x5
     * </pre>
     */
    private IKnowledge parseKnowledge(Lineizer lineizer, Pattern delimiter) {
        IKnowledge knowledge = new Knowledge2();

        String line = lineizer.nextLine();
        String firstLine = line;

        if (line == null) {
            return new Knowledge2();
        }

        if (line.startsWith("/knowledge")) {
            line = lineizer.nextLine();
            firstLine = line;
        }

        this.logger.log("info", "\nLoading knowledge.");

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

                    int tier = -1;

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, quoteChar);
                    if (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        boolean forbiddenWithin = false;
                        if (token.endsWith("*")) {
                            forbiddenWithin = true;
                            token = token.substring(0, token.length() - 1);
                        }

                        tier = Integer.parseInt(token);
                        if (tier < 1) {
                            throw new IllegalArgumentException(
                                    lineizer.getLineNumber() + ": Tiers must be 1, 2...");
                        }
                        if (forbiddenWithin) {
                            knowledge.setTierForbiddenWithin(tier - 1, true);
                        }
                    }

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();

                        if (token.length() == 0) {
                            continue;
                        }

                        String name = substitutePeriodsForSpaces(token);
                        knowledge.addToTier(tier - 1, name);

                        this.logger.log("info", "Adding to tier " + (tier - 1) + " " + name);
                    }
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

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, quoteChar);
                    String from = null, to = null;

                    if (st.hasMoreTokens()) {
                        from = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        to = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber() +
                                ": Lines contains more than two elements.");
                    }

                    if (from == null || to == null) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber() +
                                ": Line contains fewer than two elements.");
                    }

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

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, quoteChar);
                    String from = null, to = null;

                    if (st.hasMoreTokens()) {
                        from = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        to = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber() +
                                ": Lines contains more than two elements.");
                    }

                    if (from == null || to == null) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber() +
                                ": Line contains fewer than two elements.");
                    }

                    knowledge.setRequired(from, to);
                }
            } else {
                throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                        + ": Expecting 'addtemporal', 'forbiddirect' or 'requiredirect'.");
            }
        }

        return knowledge;
    }

    private static String substitutePeriodsForSpaces(String s) {
        return s.replaceAll(" ", ".");
    }

    public void setReadVariablesLowercase(boolean readVariablesLowercase) {
        this.readVariablesLowercase = true;
    }

    public void setReadVariablesUppercase(boolean readVariablesUppercase) {
        this.readVariablesLowercase = true;
    }

    private static class DataSetDescription {
        private List<Node> variables;
        private int numRows;
        private int idIndex;
        private boolean variablesSectionIncluded;
        private Pattern delimiter;
        private boolean multColumnIncluded;

        public DataSetDescription(List<Node> variables, int numRows, int idIndex,
                                  boolean variablesSectionIncluded, Pattern delimiter,
                                  boolean multColumnIncluded) {
            this.variables = variables;
            this.numRows = numRows;
            this.idIndex = idIndex;
            this.variablesSectionIncluded = variablesSectionIncluded;
            this.delimiter = delimiter;
            this.multColumnIncluded = multColumnIncluded;
        }

        public List<Node> getVariables() {
            return variables;
        }

        public int getNumRows() {
            return numRows;
        }

        public int getIdIndex() {
            return idIndex;
        }

        public boolean isVariablesSectionIncluded() {
            return variablesSectionIncluded;
        }

        public Pattern getDelimiter() {
            return delimiter;
        }

        public boolean isMultColumnIncluded() {
            return multColumnIncluded;
        }
    }

    /**
     * Scans the file for variable definitions and number of cases.
     *
     * @param varNames                Names of variables, if known. Otherwise, if null,
     *                                variables in the series X1, X2, ..., Xn will be made up,
     *                                one for each token in the first row.
     * @param lineizer                Parses lines, skipping comments.
     * @param delimiter               Delimiter to tokenize tokens in each row.
     * @param firstLine               Non-null if a non-variable first line had to be
     *                                lineized
     * @param idIndex                 The index of the ID column.
     * @param variableSectionIncluded
     */
    private DataSetDescription scanForDescription(List<String> varNames,
                                                  Lineizer lineizer, Pattern delimiter,
                                                  String firstLine, int idIndex,
                                                  boolean variableSectionIncluded) {

        // Scan file, collecting up the set of range values for each variables.
        List<Set<String>> dataStrings = new ArrayList<Set<String>>();

        for (int i = 0; i < varNames.size(); i++) {
            dataStrings.add(new HashSet<String>(varNames.size()));
        }

        int row = -1;

        while (lineizer.hasMoreLines()) {
            String line;

            if (firstLine == null) {
                line = lineizer.nextLine();
            } else {
                line = firstLine;
                firstLine = null;
            }

            if (line.startsWith("/knowledge")) {
                break;
            }

            ++row;

            RegexTokenizer tokenizer =
                    new RegexTokenizer(line, delimiter, quoteChar);

            int col = -1;

            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                ++col;

                if (col >= dataStrings.size()) {
                    continue;
                }

                if ("".equals(token) || missingValueMarker.equals(token)) {
                    continue;
                }

                dataStrings.get(col).add(token);
            }

            if (col < varNames.size() - 1) {
                this.logger.log("info", "Line " + lineizer.getLineNumber()
                        + ": Too few tokens; expected " + varNames.size() +
                        " tokens but got " + (col + 1) + " tokens.");
            }

            if (col > varNames.size() - 1) {
                this.logger.log("info", "Line " + lineizer.getLineNumber()
                        + ": Too many tokens; expected " + varNames.size() +
                        " tokens but got " + (col + 1) + " tokens.");
            }
        }

        this.logger.log("info", "\nNumber of data rows = " + (row + 1));
        int numRows = row + 1;

        // Convert these range values into variable definitions.
        List<Node> variables = new ArrayList<Node>();

        VARNAMES:
        for (int i = 0; i < varNames.size(); i++) {
            Set<String> strings = dataStrings.get(i);

            // Use known variables if they exist for the corresponding name.
            for (Node variable : knownVariables) {
                if (variable.getName().equals(varNames.get(i))) {
                    variables.add(variable);
                    continue VARNAMES;
                }
            }

            if (isDouble(strings) && !isIntegral(strings) && i != idIndex) {
                variables.add(new ContinuousVariable(varNames.get(i)));
            } else if (isIntegral(strings) && tooManyDiscreteValues(strings) &&
                    i != idIndex) {
                String name = varNames.get(i);

                if (name.contains(" ")) {
                    name = name.replaceAll(" ", "_");
                    varNames.set(i, name);
                }

                if (!NamingProtocol.isLegalName(name)) {
                    throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                            + ": This cannot be used as a variable name: " + name + ".");
                }

                variables.add(new ContinuousVariable(name));
            } else {
                List<String> categories = new LinkedList<String>(strings);
                categories.remove(null);
                categories.remove("");
                categories.remove(missingValueMarker);

                Collections.sort(categories, new Comparator<String>() {
                    public int compare(String o1, String o2) {
                        return o1.compareTo(o2);
//                        try {
//                            int i1 = Integer.parseInt(o1);
//                            int i2 = Integer.parseInt(o2);
//                            return i1 - i2;
//                            return i2 < i1 ? -1 : i2 == i1 ? 0 : 1;
//                        }
//                        catch (NumberFormatException e) {
//                            return o1.compareTo(o2);
//                        }
                    }
                });

                String name = varNames.get(i);

                if (name.contains(" ")) {
                    name = name.replaceAll(" ", "_");
                    varNames.set(i, name);
                }

                if (!NamingProtocol.isLegalName(name)) {
                    throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                            + ": This cannot be used as a variable name: " + name + ".");
                }

                variables.add(new DiscreteVariable(name, categories));
            }
        }

        boolean multColumnIncluded = false;

        if (variables.get(0).getName().equals("MULT")) {
            multColumnIncluded = true;
            variables.remove(0);
            varNames.remove(0);
        }

        // Print out a report of the variable definitions guessed at (or
        // read in through the /variables section or specified as known
        // variables.
        for (int i = 0; i < varNames.size(); i++) {
            if (i == idIndex) {
                continue;
            }

            Node node = variables.get(i);

            if (node instanceof ContinuousVariable) {
                this.logger.log("info", node + " --> Continuous");
            } else if (node instanceof DiscreteVariable) {
                StringBuilder buf = new StringBuilder();
                buf.append(node).append(" --> <");
                List<String> categories =
                        ((DiscreteVariable) node).getCategories();

                for (int j = 0; j < categories.size(); j++) {
                    buf.append(categories.get(j));

                    if (j < categories.size() - 1) {
                        buf.append(", ");
                    }
                }

                buf.append(">");
                this.logger.log("info", buf.toString());
            }
        }

        return new DataSetDescription(variables, numRows, idIndex, variableSectionIncluded,
                delimiter, multColumnIncluded);
    }

    private boolean tooManyDiscreteValues(Set<String> strings) {
        return strings.size() > maxIntegralDiscrete;
    }

    private static boolean isIntegral(Set<String> strings) {
        for (String s : strings) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    private static boolean isDouble(Set<String> strings) {
        for (String s : strings) {
            try {
                Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    /**
     * Loads text from the given file in the form of a char[] array.
     */
    private static char[] loadChars(File file) throws IOException {
        FileReader reader = new FileReader(file);
        CharArrayWriter writer = new CharArrayWriter();
        int c;

        while ((c = reader.read()) != -1) {
            writer.write(c);
        }

        return writer.toCharArray();
    }
}



