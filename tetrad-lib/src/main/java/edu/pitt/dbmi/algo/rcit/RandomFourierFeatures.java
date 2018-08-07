/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.algo.rcit;

import java.util.Date;

import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;

/**
 * 
 * Apr 10, 2018 5:23:04 PM
 * 
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class RandomFourierFeatures {

	public TetradMatrix feature;
	
	public TetradMatrix w;
	
	public TetradMatrix b;
	
	
	public TetradMatrix getFeature() {
		return feature;
	}


	public void setFeature(TetradMatrix feature) {
		this.feature = feature;
	}


	public TetradMatrix getW() {
		return w;
	}


	public void setW(TetradMatrix w) {
		this.w = w;
	}


	public TetradMatrix getB() {
		return b;
	}


	public void setB(TetradMatrix b) {
		this.b = b;
	}

	public static RandomFourierFeatures generate(TetradMatrix x, TetradMatrix w,TetradMatrix b,int num_f,double sigma){
		
		if(sigma == 0){
			sigma = 1;
		}
		
		if(num_f < 1){
			num_f = 25;
		}
		
		int row = x.rows();
		int col = x.columns();
		
		if(w == null){
			RandomUtil ru = RandomUtil.getInstance();
			w = new TetradMatrix(num_f, col);
			for(int i=0;i<num_f;i++){
				for(int j=0;j<col;j++){
					w.set(i, j, (1/sigma)*ru.nextNormal(0, 1));
				}
			}
			
			double[][] uniformFeat = new double[num_f][1];
			for(int i=0;i<num_f;i++){
				uniformFeat[i][0] = 2*Math.PI*ru.nextUniform(0, 1);
			}
			
			double[][] _b = RandomFourierFeatures.repMat(uniformFeat, 1, row);
			
			b = new TetradMatrix(_b);
		}
		
		// feat = sqrt(2)*t(cos(w[1:num_f,1:c]%*%t(x)+b[1:num_f,]));
		TetradMatrix feature = w.times(x.transpose()).plus(b);
		feature = feature.transpose();
		for(int i=0;i<feature.rows();i++) {
			for(int j=0;j<feature.columns();j++) {
				feature.set(i, j, Math.sqrt(2)*Math.cos(feature.get(i, j)));
			}
		}
		
		RandomFourierFeatures randomFourierFeatures = new RandomFourierFeatures();
		randomFourierFeatures.setFeature(feature);
		randomFourierFeatures.setW(w);
		randomFourierFeatures.setB(b);
		
		return randomFourierFeatures;
	}

	public static RandomFourierFeatures generate(double[][] x, double[][] w,double[][] b,int num_f,double sigma){
		
		if(sigma == 0){
			sigma = 1;
		}
		
		if(num_f < 1){
			num_f = 25;
		}
		
		int row = x.length;
		int col = x[0].length;
		
		if(w == null){
			RandomUtil ru = RandomUtil.getInstance();
			w = new double[num_f][col];
			for(int i=0;i<num_f;i++){
				for(int j=0;j<col;j++){
					w[i][j] = (1/sigma)*ru.nextNormal(0, 1);
				}
			}
			
			double[][] uniformFeat = new double[num_f][1];
			for(int i=0;i<num_f;i++){
				uniformFeat[i][0] = 2*Math.PI*ru.nextUniform(0, 1);
			}
			
			b = RandomFourierFeatures.repMat(uniformFeat, 1, row);
		}
		
		double[][] feature = new double[row][num_f];
		
		int w_row = w.length;
		int w_col = w[0].length;
		
		// w_row
		for(int i=0;i<w_row;i++){
			// row
			for(int j=0;j<row;j++){
				// w[1:num_f,1:c]%*%t(x)+b[1:num_f,]
				double wx_b = 0;
				
				// w[1:num_f,1:c]%*%t(x)
				// w_col
				for(int k=0;k<w_col;k++){
					// col
					for(int l=0;l<col;l++){
						double _w = w[i][k];
						double _x = x[j][l];
						wx_b += _w*_x;
					}
				}
				// w*x + b
				wx_b += b[i][j];
				
				// feat = sqrt(2)*t(cos(w[1:num_f,1:c]%*%t(x)+b[1:num_f,]));
				feature[j][i] = Math.sqrt(2)*Math.cos(wx_b);
			}
			
		}
		
		RandomFourierFeatures randomFourierFeatures = new RandomFourierFeatures();
		randomFourierFeatures.setFeature(new TetradMatrix(feature));
		randomFourierFeatures.setW(new TetradMatrix(w));
		randomFourierFeatures.setB(new TetradMatrix(b));
		
		return randomFourierFeatures;
	}
	
	public static double[][] repMat(double[][] x, int m, int n){
		int row = x.length;
		int col = x[0].length;
		double[][] repmat = new double[row*m][col*n];
		for(int i=0;i<m;i++){
			for(int j=0;j<n;j++){
				for(int k=0;k<row;k++){
					for(int l=0;l<col;l++){
						repmat[i*row + k][j*col + l] = x[k][l];
					}
				}
			}
		}
		
		return repmat;
	}
	
}
