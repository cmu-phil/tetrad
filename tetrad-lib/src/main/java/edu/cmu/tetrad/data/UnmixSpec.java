package edu.cmu.tetrad.data;

import java.io.Serializable;

/**
 * Minimal, UI-friendly spec for the unmixer. No search/PC/BOSS classes referenced here;
 * we keep it decoupled and translate elsewhere.
 */
public final class UnmixSpec implements Serializable {

    // ---- K handling ----
    // If autoSelectK == true, use Kmin..Kmax; otherwise use K (>=1).
    private boolean autoSelectK = true;
    private int K = 2;
    private int Kmin = 1;
    private int Kmax = 4;

    public boolean isLogIntermediate() {
        return logIntermediate;
    }

    public void setLogIntermediate(boolean logIntermediate) {
        this.logIntermediate = logIntermediate;
    }

    // ---- Graph learner ----
    public enum GraphLearner { BOSS, PC_MAX }
    private GraphLearner graphLearner = GraphLearner.BOSS;

    // PC-only knobs
    private double pcAlpha = 0.01;
    public enum ColliderStyle { SEPSETS, CONSERVATIVE, MAX_P }
    private ColliderStyle pcColliderStyle = ColliderStyle.MAX_P;

    // BOSS-only knobs (use what you actually wire; this is a common one)
    private double bossPenaltyDiscount = 2.0;

    // ---- Parent-superset residualization ----
    private boolean useParentSuperset = true;
    private int supersetTopM = 12;
    public enum SupersetScore { KENDALL, SPEARMAN }
    private SupersetScore supersetScore = SupersetScore.KENDALL;

    // ---- Residual scaling ----
    private boolean robustScaleResiduals = true;

    // ---- Covariance policy ----
    public enum CovarianceMode { AUTO, FULL, DIAGONAL }
    private CovarianceMode covarianceMode = CovarianceMode.AUTO;
    // Used only when AUTO: require n/K >= p + safetyMargin to use FULL
    private int fullSigmaSafetyMargin = 10;

    // ---- EM stability ----
    private int kmeansRestarts = 20;
    private int emMaxIters = 300;
    private double ridge = 1e-3;
    private double shrinkage = 0.10;  // optional; use if you wire it
    private int annealSteps = 15;     // optional
    private double annealStartT = 0.8;// optional
    private long randomSeed = 13L;

    // ---- Diagnostics ----
    private boolean saveDiagnostics = false;
    private boolean logIntermediate = false;

    // ===== Getters / Setters =====

    public boolean isAutoSelectK() { return autoSelectK; }
    public UnmixSpec setAutoSelectK(boolean v) { this.autoSelectK = v; return this; }

    public int getK() { return K; }
    public UnmixSpec setK(int k) {
        if (k < 1) throw new IllegalArgumentException("K must be >= 1");
        this.K = k; return this;
    }

    public int getKmin() { return Kmin; }
    public UnmixSpec setKmin(int kmin) { this.Kmin = Math.max(1, kmin); return this; }

    public int getKmax() { return Kmax; }
    public UnmixSpec setKmax(int kmax) { this.Kmax = Math.max(1, kmax); return this; }

    public GraphLearner getGraphLearner() { return graphLearner; }
    public UnmixSpec setGraphLearner(GraphLearner g) { this.graphLearner = g; return this; }

    public double getPcAlpha() { return pcAlpha; }
    public UnmixSpec setPcAlpha(double a) { this.pcAlpha = a; return this; }

    public ColliderStyle getPcColliderStyle() { return pcColliderStyle; }
    public UnmixSpec setPcColliderStyle(ColliderStyle s) { this.pcColliderStyle = s; return this; }

    public double getBossPenaltyDiscount() { return bossPenaltyDiscount; }
    public UnmixSpec setBossPenaltyDiscount(double d) { this.bossPenaltyDiscount = d; return this; }

    public boolean isUseParentSuperset() { return useParentSuperset; }
    public UnmixSpec setUseParentSuperset(boolean v) { this.useParentSuperset = v; return this; }

    public int getSupersetTopM() { return supersetTopM; }
    public UnmixSpec setSupersetTopM(int m) { this.supersetTopM = Math.max(1, m); return this; }

    public SupersetScore getSupersetScore() { return supersetScore; }
    public UnmixSpec setSupersetScore(SupersetScore s) { this.supersetScore = s; return this; }

    public boolean isRobustScaleResiduals() { return robustScaleResiduals; }
    public UnmixSpec setRobustScaleResiduals(boolean v) { this.robustScaleResiduals = v; return this; }

    public CovarianceMode getCovarianceMode() { return covarianceMode; }
    public UnmixSpec setCovarianceMode(CovarianceMode m) { this.covarianceMode = m; return this; }

    public int getFullSigmaSafetyMargin() { return fullSigmaSafetyMargin; }
    public UnmixSpec setFullSigmaSafetyMargin(int m) { this.fullSigmaSafetyMargin = Math.max(0, m); return this; }

    public int getKmeansRestarts() { return kmeansRestarts; }
    public UnmixSpec setKmeansRestarts(int r) { this.kmeansRestarts = Math.max(1, r); return this; }

    public int getEmMaxIters() { return emMaxIters; }
    public UnmixSpec setEmMaxIters(int it) { this.emMaxIters = Math.max(1, it); return this; }

    public double getRidge() { return ridge; }
    public UnmixSpec setRidge(double r) { this.ridge = Math.max(0.0, r); return this; }

    public double getShrinkage() { return shrinkage; }
    public UnmixSpec setShrinkage(double s) { this.shrinkage = Math.max(0.0, Math.min(1.0, s)); return this; }

    public int getAnnealSteps() { return annealSteps; }
    public UnmixSpec setAnnealSteps(int s) { this.annealSteps = Math.max(0, s); return this; }

    public double getAnnealStartT() { return annealStartT; }
    public UnmixSpec setAnnealStartT(double t) { this.annealStartT = Math.max(0.0, t); return this; }

    public long getRandomSeed() { return randomSeed; }
    public UnmixSpec setRandomSeed(long s) { this.randomSeed = s; return this; }

    public boolean isSaveDiagnostics() { return saveDiagnostics; }
    public UnmixSpec setSaveDiagnostics(boolean v) { this.saveDiagnostics = v; return this; }
}