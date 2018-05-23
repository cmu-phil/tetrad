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
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides static methods for saving data to files.
 *
 * @author Joseph Ramsey
 */
public final class DataWriter {

    /**
     * Writes a dataset to file. The dataset may have continuous and/or discrete
     * columns. Note that <code>out</code> is not closed by this method, so
     * the close method on <code>out</code> will need to be called externally.
     *
     * @param dataSet   The data set to save.
     * @param out       The writer to write the output to.
     * @param separator The character separating fields, usually '\t' or ','.
     * @throws IOException If there is some problem dealing with the writer.
     */
    public static void writeRectangularData(DataSet dataSet,
                                            Writer out, char separator) throws IOException {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        StringBuilder buf = new StringBuilder();

//        boolean isCaseMultipliersCollapsed = dataSet.isMulipliersCollapsed();

//        if (false) {
//            buf.append("MULT").append(separator);
//        }

        for (int col = 0; col < dataSet.getNumColumns(); col++) {
            String name = dataSet.getVariable(col).getName();

            if (name.trim().equals("")) {
                name = "C" + (col - 1);
            }

            buf.append(name);

            if (col < dataSet.getNumColumns() - 1) {
                buf.append(separator);
            }
        }

        for (int row = 0; row < dataSet.getNumRows(); row++) {
            buf.append("\n");

//            if (isCaseMultipliersCollapsed) {
//                int multiplier = dataSet.getMultiplier(row);
//                buf.append(multiplier).append(separator);
//            }

            for (int col = 0; col < dataSet.getNumColumns(); col++) {
                Node variable = dataSet.getVariable(col);

                if (variable instanceof ContinuousVariable) {
                    double value = dataSet.getDouble(row, col);

                    if (ContinuousVariable.isDoubleMissingValue(value)) {
                        buf.append("*");
                    } else {
                        buf.append(nf.format(value));
                    }

                    if (col < dataSet.getNumColumns() - 1) {
                        buf.append(separator);
                    }
                } else if (variable instanceof DiscreteVariable) {
                    Object obj = dataSet.getObject(row, col);
                    String val = ((obj == null) ? "" : obj.toString());

                    buf.append(val);

                    if (col < dataSet.getNumColumns() - 1) {
                        buf.append(separator);
                    }
                }
            }
        }

        buf.append("\n");
        out.write(buf.toString());
        out.close();
    }

//    /**
//     * Writes a dataset to file. The dataset may have continuous and/or discrete
//     * columns. Note that <code>out</code> is not closed by this method, so
//     * the close method on <code>out</code> will need to be called externally.
//     *
//     * @param dataSet   The data set to save.
//     * @param out       The writer to write the output to.
//     * @param separator The character separating fields, usually '\t' or ','.
//     */
//    public static void writeRectangularDataALittleFaster(DataSet dataSet,
//                                                         PrintWriter out, char separator) {
//        NumberFormat nf = new DecimalFormat("0.0000");
////        StringBuilder buf = new StringBuilder();
//
//        for (int col = 0; col < dataSet.getNumColumns(); col++) {
//            String name = dataSet.getVariable(col).getNode();
//
//            if (name.trim().equals("")) {
//                name = "C" + (col - 1);
//            }
//
//            out.append(name);
//
//            if (col < dataSet.getNumColumns() - 1) {
//                out.append(separator);
//            }
//        }
//
//        for (int row = 0; row < dataSet.getNumRows(); row++) {
//            out.append("\n");
//
//            for (int col = 0; col < dataSet.getNumColumns(); col++) {
//                Node variable = dataSet.getVariable(col);
//
//                if (variable instanceof ContinuousVariable) {
//                    double value = dataSet.getDouble(row, col);
//
//                    if (ContinuousVariable.isDoubleMissingValue(value)) {
//                        out.print("*");
//                    } else {
//                        out.print(nf.format(value));
////                        out.print(value);
//                    }
//
//                    if (col < dataSet.getNumColumns() - 1) {
//                        out.print(separator);
//                    }
//                } else if (variable instanceof DiscreteVariable) {
//                    Object obj = dataSet.getObject(row, col);
//                    String val = ((obj == null) ? "" : obj.toString());
//
//                    out.print(val);
//
//                    if (col < dataSet.getNumColumns() - 1) {
//                        out.print(separator);
//                    }
//                }
//            }
//        }
//
//        out.print("\n");
//        out.close();
//    }


    /**
     * Writes the lower triangle of a covariance matrix to file.  Note that
     * <code>out</code> is not closed by this method, so the close method on
     * <code>out</code> will need to be called externally.
     *
     * @param out The writer to write the output to.
     */
    public static void writeCovMatrix(ICovarianceMatrix covMatrix,
            PrintWriter out, NumberFormat nf) {
//        out.println("/Covariance");
        out.println(covMatrix.getSampleSize());

        List<String> variables = covMatrix.getVariableNames();
        int numVars = variables.size();

        int varCount = 0;
        for (String variable : variables) {
            varCount++;
            if (varCount < numVars) {
                out.print(variable);
                out.print("\t");
            } else {
                out.println(variable);
            }
        }

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                double value = covMatrix.getValue(i, j);
                if (Double.isNaN(value)) {
                    out.print("*");
                } else {
                    out.print(nf.format(value));
                }
                
                out.print((i < j)  ? "\t" : "\n");
            }
        }
        out.flush();
        out.close();
    }

    public static void saveKnowledge(IKnowledge knowledge, Writer out) throws IOException {
        StringBuilder buf = new StringBuilder();
        buf.append("/knowledge");

        buf.append("\naddtemporal\n");

        for (int i = 0; i < knowledge.getNumTiers(); i++) {

            String forbiddenWithin = knowledge.isTierForbiddenWithin(i) ? "*" : "";
            String onlyCanCauseNextTier = knowledge.isOnlyCanCauseNextTier(i) ? "-" : "";
            buf.append("\n").append(i+1).append(forbiddenWithin).append(onlyCanCauseNextTier).append(" ");


            List<String> tier = knowledge.getTier(i);
            if (!(tier == null || tier.isEmpty())) {
                buf.append(" ");
                buf.append(tier.stream().collect(Collectors.joining(" ")));
            }
        }

        buf.append("\n\nforbiddirect");

        for (Iterator<KnowledgeEdge> i
             = knowledge.forbiddenEdgesIterator(); i.hasNext();) {
            KnowledgeEdge pair = i.next();
            String from = pair.getFrom();
            String to = pair.getTo();

            if (knowledge.isForbiddenByTiers(from, to)) {
                continue;
            }

            buf.append("\n").append(from).append(" ").append(to);
        }

        buf.append("\n\nrequiredirect");

        for (Iterator<KnowledgeEdge> i
                = knowledge.requiredEdgesIterator(); i.hasNext();) {
            KnowledgeEdge pair = i.next();
            String from = pair.getFrom();
            String to = pair.getTo();
            buf.append("\n").append(from).append(" ").append(to);
        }

        out.write(buf.toString());
        out.flush();
    }
}





