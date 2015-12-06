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

        boolean isCaseMultipliersCollapsed = dataSet.isMulipliersCollapsed();

        if (!isCaseMultipliersCollapsed) {
            buf.append("MULT").append(separator);
        }

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

            if (!isCaseMultipliersCollapsed) {
                int multiplier = dataSet.getMultiplier(row);
                buf.append(multiplier).append(separator);
            }

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
//            String name = dataSet.getVariable(col).getName();
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
        int numVars = covMatrix.getVariableNames().size();
//        out.println("/Covariance");
        out.println(covMatrix.getSampleSize());

        for (int i = 0; i < numVars; i++) {
            String name = covMatrix.getVariableNames().get(i);
            out.print(name + "\t");
        }

        out.println();

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                if (Double.isNaN(covMatrix.getValue(i, j))) {
                    out.print("*" + "\t");
                } else {
                    out.print(nf.format(covMatrix.getValue(i, j)) + "\t");
                }
            }
            out.println();
        }
        out.flush();
        out.close();
    }
}





