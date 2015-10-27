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

import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.io.File;
import java.io.IOException;

/**
 * Useful for storing very large correlation matrices (space saver).
 *
 * @author Mike Freenor
 */
public class ShortTriangularMatrix implements TriangularMatrix{

    private short matrix[][];

    public ShortTriangularMatrix(){}

    public ShortTriangularMatrix(int size)
    {
        create(size);
    }

    public void becomeCorrelationMatrix(DataSet dataSet)
    {
        for(int i = 0; i < dataSet.getNumColumns(); i++)
            matrix[i][i] = 10000;

        TetradMatrix doubleData = dataSet.getDoubleData();
        TetradVector[] views = new TetradVector[dataSet.getNumColumns()];
        for(int i = 0; i < views.length; i++)
            views[i] = doubleData.getColumn(i);

        for(int i = 0; i < dataSet.getNumColumns() - 1; i++)
            for(int j = i + 1; j < dataSet.getNumColumns(); j++)
                matrix[j][i] = StatUtils.compressedCorrelation(views[i], views[j]);
    }

    public void create(int size)
    {
        matrix = new short[size][];
        for(int i = 0; i < size; i++)
		{
			matrix[i] = new short[i + 1];

			for(int j = 0; j < i + 1; j++)
				matrix[i][j] = (short)i;
		}
    }

    public short getShort(int row, int col)
    {
        if(col > row)
        {
            int temp = col;
            col = row;
            row = temp;
        }
        return matrix[row][col];    
    }

    public double getDouble(int row, int col)
    {
        if(col > row)
        {
            int temp = col;
            col = row;
            row = temp;
        }

        return ((double)matrix[row][col]) / 10000;
    }

    public boolean set(int row, int col, long value)
    {
        return set(row, col, (short)value);
    }

    public boolean set(int row, int col, int value)
    {
        return set(row, col, (short)value);
    }

    public boolean set(int row, int col, short value)
    {
        if(col > row)
        {
            int temp = col;
            col = row;
            row = temp;
        }

        matrix[row][col] = value;
        return true;
    }

    public boolean set(int row, int col, double value)
    {
        if(col > row)
        {
            int temp = col;
            col = row;
            row = temp;
        }

        matrix[row][col] = (short)(value * 10000);
        return true;
    }

    public String toString()
    {
        String out = "";
        for(int i = 0; i < matrix.length; i++)
		{
			for(int j = 0; j < matrix[i].length; j++)
			{
				out += (getDouble(i, j) + "\t\t");
			}
			out += "\n";
		}
        return out;
    }

    //testing sandbox
    public static void main(String [] args)
    {
        ShortTriangularMatrix test = new ShortTriangularMatrix();
        DataReader reader = new DataReader();
        File file = new File("C:/data1.txt");
        ColtDataSet data = null;
        try
        {
            data = (ColtDataSet)reader.parseTabular(file);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
        test.becomeCorrelationMatrix(data);
        System.out.println(test.toString());
        CorrelationMatrix m = new CorrelationMatrix(data);
        System.out.println(m.toString());
        System.out.println(test.getDouble(1, 3));
        System.out.println(test.getDouble(3, 1));
    }
}




