// Sanity check for EdgePriorFacade. Compiles against fakes; adapt or lift into JUnit.
package edu.cmu.tetrad.search.harness;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.harness.EdgePriorFacade;
import edu.cmu.tetrad.search.test.*;
import java.util.*;

public class EdgePriorFacadeCheck {
    static int fails = 0;
    static void ok(String w, boolean c) { System.out.printf("  [%s] %s%n", c?"PASS":"FAIL", w); if(!c) fails++; }

    static class SubTest implements IndependenceTest {
        final List<Node> vars; final double p; boolean verbose;
        SubTest(List<Node> vars, double p){this.vars=vars;this.p=p;}
        public IndependenceResult checkIndependence(Node x,Node y,Set<Node> z){
            return new IndependenceResult(new IndependenceFact(x,y,z), p>0.01, p, 0.01-p);}
        public List<Node> getVariables(){return vars;}
        public DataModel getData(){return null;}
        public boolean isVerbose(){return verbose;}
        public void setVerbose(boolean v){verbose=v;}
        public String toString(){return "sub";}
    }

    public static void main(String[] a) throws Exception {
        double alpha=0.01, P=0.012;
        int nSnp=200, nTr=2;
        String[] snp=new String[nSnp]; for(int j=0;j<nSnp;j++) snp[j]="rs"+j;
        String[] tr={"BMI","SBP"};

        // random (SNP,trait) annotation
        Random rng=new Random(0);
        double[][] ann=new double[nSnp][nTr];
        for(int j=0;j<nSnp;j++) for(int k=0;k<nTr;k++) ann[j][k]=rng.nextDouble();

        // full locus variable list
        List<Node> locus=new ArrayList<>();
        for(String s:snp) locus.add(new GraphNode(s));
        Node y1=new GraphNode("BMI"), y2=new GraphNode("SBP");
        locus.add(y1); locus.add(y2);

        System.out.println("1. tau = 0 is an exact no-op through the facade");
        EdgePriorFacade f0 = EdgePriorFacade.fromAnnotation(snp, tr, ann, 0.0, alpha);
        // sample a repeat of 50 SNPs + traits
        List<Node> sub = new ArrayList<>();
        Set<Integer> pick=new LinkedHashSet<>(); while(pick.size()<50) pick.add(rng.nextInt(nSnp));
        for(int i:pick) sub.add(locus.get(i)); sub.add(y1); sub.add(y2);
        IndependenceTest bare=new SubTest(sub,P);
        IndependenceTest wrapped=f0.wrap(new SubTest(sub,P));
        boolean same=true;
        for(Node x:sub) for(Node y:sub){ if(x==y) continue;
            if(bare.checkIndependence(x,y,Set.of()).isIndependent()
                    != wrapped.checkIndependence(x,y,Set.of()).isIndependent()) same=false; }
        ok("verdicts identical to bare test on all pairs", same);
        ok("meanAlpha == alpha at tau=0", Math.abs(f0.meanAlpha()-alpha)<1e-12);
        ok("numPriorPairs == 0 at tau=0", f0.numPriorPairs()==0);

        System.out.println("\n2. tau > 0: density drift is reported, and normalisation removes it");
        EdgePriorFacade f = EdgePriorFacade.fromAnnotation(snp, tr, ann, 0.5, alpha);
        ok("meanAlpha(pre-norm) > alpha (the Jensen drift)", f.meanAlpha() > alpha*1.05);
        ok("numPriorPairs == n_snp * n_trait", f.numPriorPairs()==nSnp*nTr);
        System.out.printf("     meanAlpha/alpha = %.3f  (drift the referee asks about)%n", f.meanAlpha()/alpha);

        System.out.println("\n3. wrap() restricts automatically -- no throw on a subset");
        IndependenceTest w = f.wrap(new SubTest(sub,P));
        EdgePriorTest ept=(EdgePriorTest)w;
        // a SNP in the subset that has a prior -> alpha_ij != alpha for its trait edges
        Node s0 = sub.get(0);
        boolean moved = Math.abs(ept.getAlpha(s0,y1)-alpha)>1e-9 || Math.abs(ept.getAlpha(s0,y2)-alpha)>1e-9;
        ok("a primed SNP-trait edge has alpha_ij != alpha", moved);
        ok("a SNP-SNP edge stays at alpha", Math.abs(ept.getAlpha(sub.get(0),sub.get(1))-alpha)<1e-9);

        System.out.println("\n4. NaN annotation entries contribute no prior");
        double[][] ann2=new double[nSnp][nTr];
        for(double[] row:ann2){ row[0]=Double.NaN; row[1]=Double.NaN; }
        ann2[5][0]=2.0; ann2[7][1]=-2.0;   // only two real entries
        EdgePriorFacade fn = EdgePriorFacade.fromAnnotation(snp, tr, ann2, 1.0, alpha);
        ok("only the 2 finite entries carry a prior", fn.numPriorPairs()==2);

        System.out.println(fails==0 ? "\nALL PASS" : "\n"+fails+" FAILURES");
        if(fails>0) System.exit(1);
    }
}
