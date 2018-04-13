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

import edu.cmu.tetrad.util.RandomUtil;

/**
 * 
 * Apr 10, 2018 5:23:04 PM
 * 
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class RandomFourierFeatures {

	public double[][] feature;
	
	public double[][] w;
	
	public double[][] b;
	
	
	public double[][] getX() {
		return feature;
	}


	public void setFeature(double[][] feature) {
		this.feature = feature;
	}


	public double[][] getW() {
		return w;
	}


	public void setW(double[][] w) {
		this.w = w;
	}


	public double[][] getB() {
		return b;
	}


	public void setB(double[][] b) {
		this.b = b;
	}


	public static RandomFourierFeatures generate(double[][] x, double[][] w,double[][] b,int num_f,double sigma,long seed){
		
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
			ru.setSeed(seed);
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
		// feat = sqrt(2)*t(cos(w[1:num_f,1:c]%*%t(x)+b[1:num_f,]));
		for(int i=0;i<row;i++){
			for(int j=0;j<num_f;j++){
				// w[1:num_f,1:c]%*%t(x)+b[1:num_f,]
				double wx_b = 0;
				// w*x
				for(int k=0;k<col;k++){
					for(int l=0;l<row;l++){
						wx_b += w[i][k]*x[j][l];
					}
				}
				// w*x + b
				wx_b += b[row][num_f];
				
				feature[i][j] = Math.sqrt(2)*Math.cos(wx_b);
			}
			
		}
		
		RandomFourierFeatures randomFourierFeatures = new RandomFourierFeatures();
		randomFourierFeatures.setFeature(feature);
		randomFourierFeatures.setW(w);
		randomFourierFeatures.setB(b);
		
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
