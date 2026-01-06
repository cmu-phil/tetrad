In this directory:

/Users/josephramsey/IdeaProjects/tetrad8/tetrad-lib/target

java -cp classes:/Users/josephramsey/IdeaProjects/tetrad8/tetrad-gui/target/tetrad-gui-7.6.10-SNAPSHOT-launch.jar edu.cmu.tetrad.search.is.TestIGFCI_TCGA \
  -dir /Users/josephramsey/IdeaProjects/tetrad8/tetrad-lib/src/main/java/edu/cmu/tetrad/search/is \
  -data toy_discrete \
  -knowledge forbid_pairs_nodes2 \
  -th true -cutoff 0.5 -kappa 0.5 -bs 1