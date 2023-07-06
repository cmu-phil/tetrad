package edu.cmu.tetrad.test;

import java.util.Random;

public class CausalDiscovery {
    private static void shuffle(int[] array) {
        Random rand = new Random();
        for (int i = array.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    private static int[][] erDAG(int p, double d, Double ad) {
        int npe = p * (p - 1) / 2;
        double ne;
        if (ad != null)
            ne = ad / (p - 1);
        else
            ne = d * npe;

        int[] edges = new int[npe];
        for (int i = 0; i < ne; i++) {
            edges[i] = 1;
        }
        shuffle(edges);

        int[][] g = new int[p][p];
        int index = 0;
        for (int i = 0; i < p - 1; i++) {
            for (int j = i + 1; j < p; j++) {
                g[i][j] = edges[index];
                index++;
            }
        }
        return g;
    }

    private static void sfColShuffle(int[][] g) {
        int p = g.length;

        for (int i = 1; i < p; i++) {
            int[] J = new int[i + 1];
            for (int j = 0; j < i; j++) {
                J[j] = j;
            }
            int index = i;
            for (int j = 0; j < i; j++) {
                int sum = 0;
                for (int k = 0; k < i; k++) {
                    sum += g[k][j];
                }
                for (int k = 0; k < sum; k++) {
                    J[index] = j;
                    index++;
                }
            }
            shuffle(J);

            int inDeg = g.length;
            for (int j : J) {
                if (inDeg != 0) {
                    if (g[i][j] == 0) {
                        inDeg--;
                        g[i][j] = 1;
                    }
                }
            }
        }
    }

    private static void sfRowShuffle(int[][] g) {
        flip(g);
        sfColShuffle(g);
        flip(g);
    }

    private static void flip(int[][] g) {
        int p = g.length;

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p - i; j++) {
                int x = g[i][j];
                g[i][j] = g[p - j - 1][p - i - 1];
                g[p - j - 1][p - i - 1] = x;
            }
        }
    }

    private static int[][] baDAG(int p, int m) {
        int[][] g = new int[p][p];
        g[1][0] = 1;

        for (int i = 2; i < p; i++) {
            int dne = 2 * countEdges(g);
            for (int j = 0; j < i; j++) {
                double rand = Math.random();
                double prob = (countEdges(g[j]) + countEdges(transpose(g)[j])) / (double) dne;
                g[i][j] = (rand < prob) ? 1 : 0;
            }
        }

        return g;
    }

    private static int[][] transpose(int[][] g) {
        int p = g.length;
        int[][] t = new int[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                t[i][j] = g[j][i];
            }
        }
        return t;
    }

    private static double[][] transpose(double[][] g) {
        int p = g.length;
        double[][] t = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                t[i][j] = g[j][i];
            }
        }
        return t;
    }

    private static int countEdges(int[][] g) {
        int p = g.length;
        int count = 0;
        for (int i = 0; i < p - 1; i++) {
            for (int j = i + 1; j < p; j++) {
                count += g[i][j];
            }
        }
        return count;
    }

    private static int countEdges(int[] g) {
        int p = g.length;
        int count = 0;
        for (int j = 0; j < p - 1; j++) {
            count += g[j];
        }
        return count;
    }

    private static CovarianceResult cov(int[][] g, double b, double s) {
        int p = g.length;
        int e = countEdges(g);

        double[] E = new double[p];
        for (int i = 0; i < p; i++) {
            E[i] = Math.random() * s + 1.0;
        }

        double[][] B = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                if (g[i][j] == 1) {
                    B[i][j] = Math.random() * 2 * b - b;
                }
            }
        }

        double[][] IB = inverse(subtract(identity(p), B));
        double[][] S = multiply(multiply(IB, diagonal(E)), transpose(IB));

        return new CovarianceResult(S, B, E);
    }

    private static double[][] diagonal(double[] e) {
        double[][] d = new double[e.length][e.length];
        for (int i = 0; i < e.length; i++) d[i][i] = e[i];
        return d;
    }

    private static double[][] inverse(double[][] matrix) {
        int n = matrix.length;
        double[][] inv = new double[n][n];
        double[][] augmented = new double[n][2 * n];

        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, augmented[i], 0, n);
            augmented[i][n + i] = 1.0;
        }

        for (int i = 0; i < n; i++) {
            int maxRow = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(augmented[j][i]) > Math.abs(augmented[maxRow][i])) {
                    maxRow = j;
                }
            }
            double[] temp = augmented[i];
            augmented[i] = augmented[maxRow];
            augmented[maxRow] = temp;

            double pivot = augmented[i][i];
            if (Math.abs(pivot) < 1e-12) {
                throw new ArithmeticException("Matrix is singular.");
            }

            for (int j = 0; j < 2 * n; j++) {
                augmented[i][j] /= pivot;
            }

            for (int j = 0; j < n; j++) {
                if (j != i) {
                    double factor = augmented[j][i];
                    for (int k = 0; k < 2 * n; k++) {
                        augmented[j][k] -= factor * augmented[i][k];
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            System.arraycopy(augmented[i], n, inv[i], 0, n);
        }

        return inv;
    }

    private static double[][] subtract(double[][] a, double[][] b) {
        int n = a.length;
        int m = a[0].length;
        double[][] result = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                result[i][j] = a[i][j] - b[i][j];
            }
        }
        return result;
    }

    private static double[][] identity(int n) {
        double[][] I = new double[n][n];
        for (int i = 0; i < n; i++) {
            I[i][i] = 1.0;
        }
        return I;
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        int n = a.length;
        int m = b[0].length;
        int p = b.length;
        double[][] result = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                for (int k = 0; k < p; k++) {
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        int p = 5;
        double d = 0.5;
        Double ad = null;

        int[][] g = erDAG(p, d, ad);

        for (int i = 0; i < p; i++) {
            System.out.print(countEdges(g[i]) + " ");
        }

        sfColShuffle(g);

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                System.out.print(g[i][j] + " ");
            }
            System.out.println();
        }
    }

    static class CovarianceResult {
        double[][] S;
        double[][] B;
        double[] E;

        CovarianceResult(double[][] S, double[][] B, double[] E) {
            this.S = S;
            this.B = B;
            this.E = E;
        }
    }
}
