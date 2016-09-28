/* This file is part of the jgpml Project.
 * http://github.com/renzodenardi/jgpml
 *
 * Copyright (c) 2011 Renzo De Nardi and Hugo Gravato-Marques
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package jgpml;

import Jama.Matrix;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * Simple Class to load the example data from files straight into Matrices.
 */
public class CSVtoMatrix {

    /**
     * Load data
     * @param filename  data file
     * @param sizeofInputs
     * @param sizeofOutputs
     * @return  [X, Y]
     */
    public static Matrix[] load(String filename,int sizeofInputs, int sizeofOutputs){

        ArrayList<double[]> inputsList = new ArrayList<>();
        ArrayList<double[]> outputsList = new ArrayList<>();
        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException e) {
            System.out.println("error: file " + filename + " not found.");
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        boolean eof;
        int datasize = 0;

        do {
            eof = true;

            String readLine = null;

            try {
                readLine = br.readLine();
            } catch (IOException e) {
                System.out.println("error: reading from " + filename + ".");
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            if (readLine != null && !readLine.equals("")) {
                eof = false;

                try {
                    double[] in = new double[sizeofInputs];
                    double[] out = new double[sizeofOutputs];
                    StringTokenizer st = new StringTokenizer(readLine, ", ");

                    // parse inputs
                    int index = 0;
                    int currentVariable = 0;
                    for (int i = 0; i < sizeofInputs; i++) {
                        in[index] = Double.parseDouble(st.nextToken());
                        index++;
                        currentVariable++;
                    }

                    // parse outputs
                    index = 0;
                    for (int i = 0; i < sizeofOutputs; i++) {
                        out[index] = Double.parseDouble(st.nextToken());
                        index++;
                        currentVariable++;
                    }

                    inputsList.add(in);
                    outputsList.add(out);
                }
                catch (Exception e) {
                    System.out.println(e + "\nerror: this line in the logfile does not agree with the configuration provided... it will be skipped");
                    datasize--;
                }
            }
            datasize++;
        } while (!eof);

        double[][] inmat = new double[inputsList.size()][sizeofInputs];
        double[][] outmat = new double[inputsList.size()][sizeofOutputs];
        inputsList.toArray(inmat);
        outputsList.toArray(outmat);

        return new Matrix[]{new Matrix(inmat), new Matrix(outmat)};
    }

    /**
     * Simple example of how to use this class.
     * @param args
     */
    public static void main(String[] args) {


       Matrix[] data = CSVtoMatrix.load("../machinelearning/src/machinelearning/gaussianprocess/armdata.csv",6,1);

       
     }


}
