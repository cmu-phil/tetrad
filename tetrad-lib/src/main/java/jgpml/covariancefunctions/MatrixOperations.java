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

package jgpml.covariancefunctions;

import Jama.Matrix;

/**
 * Some useful operations defined over Matrices
 */
public class MatrixOperations {


    /**
     * Computes the exponential of the input <code>Matrix</code>
     * @param A  input <code>Matrix</code>
     * @return exp(A) result
     */
    public static Matrix exp(Matrix A){

        Matrix out = new Matrix(A.getRowDimension(),A.getColumnDimension());
        for(int i=0; i<A.getRowDimension(); i++)
            for(int j=0; j<A.getColumnDimension(); j++)
                out.set(i,j,Math.exp(A.get(i,j)));

        return out;
    }

    /**
     * Sums across the rows of the <code>Matrix</code> and return the result as a single column <code>MAtrix</code>
     * @param A  input <code>Matrix</code>
     * @return result
     */

    public static Matrix sumRows(Matrix A){
        Matrix sum = new Matrix(A.getRowDimension(),1);
        for(int i=0; i<A.getColumnDimension(); i++)
            sum.plusEquals(A.getMatrix(0,A.getRowDimension()-1,i,i));
        return sum;
    }


    /**
     * Adds a value to each elemnts of the <code>Matrix</code>
     * @param A  <code>Matrix</code>
     * @param val  value to be added
     * @return  result
     */
    public static Matrix addValue(Matrix A,double val){
        for(int i=0; i<A.getRowDimension(); i++)
            for(int j=0; j<A.getColumnDimension(); j++)
                A.set(i,j,A.get(i,j)+val);

        return A;
    }

    /**
     * Computes the arcsin of the input <code>Matrix</code> (element by element)
     * @param A input <code>Matrix</code>
     * @return asin(A)  result
     */
    public static Matrix asin(Matrix A){

        Matrix out = new Matrix(A.getRowDimension(),A.getColumnDimension());
        for(int i=0; i<A.getRowDimension(); i++)
            for(int j=0; j<A.getColumnDimension(); j++)
                out.set(i,j,Math.asin(A.get(i,j)));

        return out;
    }

    /**
     * Computes the square root of the input <code>Matrix</code> (element by element)
     * @param A input <code>Matrix</code>
     * @return sqrt(A)  result
     */
    public static Matrix sqrt(Matrix A){

        Matrix out = new Matrix(A.getRowDimension(),A.getColumnDimension());
        for(int i=0; i<A.getRowDimension(); i++)
            for(int j=0; j<A.getColumnDimension(); j++)
                out.set(i,j,Math.sqrt(A.get(i,j)));

        return out;
    }

    /**
     * If the argument is a row or column <code>Matrix</code> it returns a new diagonal <code>Matrix</code>
     * with the input as diagonal elements.
     * If the argument is a <code>Matrix</code> it returns the diagonal elements as a single column <code>Matrix</code>
     * Is a clone of the Matlab's function diag(A)
     * @param A input <code>Matrix</code>
     * @return diag(A) result
     */
    public static Matrix diag(Matrix A){
        Matrix diag =null;
        if(A.getColumnDimension()==1 || A.getRowDimension()==1){
            if(A.getColumnDimension()==1) {
                diag = new Matrix(A.getRowDimension(),A.getRowDimension());
                for(int i=0; i<diag.getColumnDimension(); i++)
                    diag.set(i,i,A.get(i,0));
            } else {
                diag = new Matrix(A.getColumnDimension(),A.getColumnDimension());
                for(int i=0; i<diag.getRowDimension(); i++)
                    diag.set(i,i,A.get(0,i));
            }
        } else {

            diag = new Matrix(A.getRowDimension(),1);
            for(int i=0; i<diag.getRowDimension(); i++)
                diag.set(i,0,A.get(i,i));
        }

        return diag;
    }


    public static Matrix mean(Matrix A){

        if(A.getRowDimension()==1) {
            double m = 0;
            for(int i=0; i<A.getColumnDimension(); i++) m+=A.get(0,i);

            Matrix M = new Matrix(1,1);
            M.set(0,0,m/A.getColumnDimension());
            return M;
        } else {
            Matrix M = new Matrix(1,A.getColumnDimension());
            for(int i=0; i<A.getColumnDimension(); i++){
                double m=0;
                for(int j=0; j<A.getRowDimension(); j++){
                    m+=A.get(j,i);
                }
                M.set(0,i,m/A.getRowDimension());
            }
            return M;
        }
    }

    public static Matrix std(Matrix A){

          if(A.getRowDimension()==1) {
              double m = 0; double var=0;
              for(int i=0; i<A.getColumnDimension(); i++){
                  m = (m*(i-1) + A.get(0,i))/i;
                  var = var*(i-1)/i + ((A.get(0,i)-m)*(A.get(0,i)-m))/(i-1);
              }
              Matrix M = new Matrix(1,1);
              M.set(0,0,Math.sqrt(var));
              return M;
          } else {
              Matrix M = new Matrix(1,A.getColumnDimension());
              for(int i=0; i<A.getColumnDimension(); i++){
                  double m=0; double var=0;
                  for(int j=0; j<A.getRowDimension(); j++){
                      m = (m*(j-1) + A.get(j,i))/j;
                      var = var*(j-1)/j + ((A.get(j,i)-m)*(A.get(j,i)-m))/(j-1);
                  }
                  M.set(0,i,Math.sqrt(var));
              }
              return M;
          }
      }



}

