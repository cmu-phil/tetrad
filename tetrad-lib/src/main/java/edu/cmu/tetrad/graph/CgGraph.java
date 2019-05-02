/**
 * 
 */
package edu.cmu.tetrad.graph;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Apr 15, 2019 5:41:18 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public final class CgGraph implements Graph, TetradSerializable {

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.TripleClassifier#getTriplesClassificationTypes()
	 */
	@Override
	public List<String> getTriplesClassificationTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.TripleClassifier#getTriplesLists(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public List<List<Triple>> getTriplesLists(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addBidirectedEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean addBidirectedEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addDirectedEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean addDirectedEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addUndirectedEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean addUndirectedEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addNondirectedEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean addNondirectedEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addPartiallyOrientedEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addEdge(edu.cmu.tetrad.graph.Edge)
	 */
	@Override
	public boolean addEdge(Edge edge) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addNode(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean addNode(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addPropertyChangeListener(java.beans.PropertyChangeListener)
	 */
	@Override
	public void addPropertyChangeListener(PropertyChangeListener e) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#clear()
	 */
	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#containsEdge(edu.cmu.tetrad.graph.Edge)
	 */
	@Override
	public boolean containsEdge(Edge edge) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#containsNode(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean containsNode(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#existsDirectedCycle()
	 */
	@Override
	public boolean existsDirectedCycle() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#existsDirectedPathFromTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean existsDirectedPathFromTo(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#existsUndirectedPathFromTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#existsSemiDirectedPathFromTo(edu.cmu.tetrad.graph.Node, java.util.Set)
	 */
	@Override
	public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#existsInducingPath(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean existsInducingPath(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#existsTrek(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean existsTrek(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#fullyConnect(edu.cmu.tetrad.graph.Endpoint)
	 */
	@Override
	public void fullyConnect(Endpoint endpoint) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#reorientAllWith(edu.cmu.tetrad.graph.Endpoint)
	 */
	@Override
	public void reorientAllWith(Endpoint endpoint) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getAdjacentNodes(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public List<Node> getAdjacentNodes(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getAncestors(java.util.List)
	 */
	@Override
	public List<Node> getAncestors(List<Node> nodes) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getChildren(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public List<Node> getChildren(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getConnectivity()
	 */
	@Override
	public int getConnectivity() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getDescendants(java.util.List)
	 */
	@Override
	public List<Node> getDescendants(List<Node> nodes) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public Edge getEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getDirectedEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public Edge getDirectedEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getEdges(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public List<Edge> getEdges(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getEdges(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public List<Edge> getEdges(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getEdges()
	 */
	@Override
	public Set<Edge> getEdges() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getEndpoint(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public Endpoint getEndpoint(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getEndpointMatrix()
	 */
	@Override
	public Endpoint[][] getEndpointMatrix() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getIndegree(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public int getIndegree(Node node) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getDegree(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public int getDegree(Node node) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNode(java.lang.String)
	 */
	@Override
	public Node getNode(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNodes()
	 */
	@Override
	public List<Node> getNodes() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNodeNames()
	 */
	@Override
	public List<String> getNodeNames() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNumEdges()
	 */
	@Override
	public int getNumEdges() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNumEdges(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public int getNumEdges(Node node) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNumNodes()
	 */
	@Override
	public int getNumNodes() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getOutdegree(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public int getOutdegree(Node node) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getParents(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public List<Node> getParents(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isAdjacentTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isAdjacentTo(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isAncestorOf(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isAncestorOf(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#possibleAncestor(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean possibleAncestor(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isChildOf(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isChildOf(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isParentOf(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isParentOf(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isProperAncestorOf(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isProperAncestorOf(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isProperDescendentOf(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isProperDescendentOf(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isDescendentOf(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isDescendentOf(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#defNonDescendent(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean defNonDescendent(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isDefNoncollider(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isDefCollider(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isDefCollider(Node node1, Node node2, Node node3) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isDConnectedTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, java.util.List)
	 */
	@Override
	public boolean isDConnectedTo(Node node1, Node node2, List<Node> z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isPattern()
	 */
	@Override
	public boolean isPattern() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setPattern(boolean)
	 */
	@Override
	public void setPattern(boolean pattern) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isPag()
	 */
	@Override
	public boolean isPag() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setPag(boolean)
	 */
	@Override
	public void setPag(boolean pag) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isDSeparatedFrom(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, java.util.List)
	 */
	@Override
	public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#possDConnectedTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, java.util.List)
	 */
	@Override
	public boolean possDConnectedTo(Node node1, Node node2, List<Node> z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isDirectedFromTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isDirectedFromTo(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isUndirectedFromTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isUndirectedFromTo(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#defVisible(edu.cmu.tetrad.graph.Edge)
	 */
	@Override
	public boolean defVisible(Edge edge) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isExogenous(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isExogenous(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNodesInTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Endpoint)
	 */
	@Override
	public List<Node> getNodesInTo(Node node, Endpoint n) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getNodesOutTo(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Endpoint)
	 */
	@Override
	public List<Node> getNodesOutTo(Node node, Endpoint n) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeEdge(edu.cmu.tetrad.graph.Edge)
	 */
	@Override
	public boolean removeEdge(Edge edge) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeEdge(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean removeEdge(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeEdges(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean removeEdges(Node node1, Node node2) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeEdges(java.util.Collection)
	 */
	@Override
	public boolean removeEdges(Collection<Edge> edges) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeNode(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean removeNode(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeNodes(java.util.List)
	 */
	@Override
	public boolean removeNodes(List<Node> nodes) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setEndpoint(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Endpoint)
	 */
	@Override
	public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#subgraph(java.util.List)
	 */
	@Override
	public Graph subgraph(List<Node> nodes) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#transferNodesAndEdges(edu.cmu.tetrad.graph.Graph)
	 */
	@Override
	public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#transferAttributes(edu.cmu.tetrad.graph.Graph)
	 */
	@Override
	public void transferAttributes(Graph graph) throws IllegalArgumentException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getAmbiguousTriples()
	 */
	@Override
	public Set<Triple> getAmbiguousTriples() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getUnderLines()
	 */
	@Override
	public Set<Triple> getUnderLines() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getDottedUnderlines()
	 */
	@Override
	public Set<Triple> getDottedUnderlines() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isAmbiguousTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isAmbiguousTriple(Node x, Node y, Node z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isUnderlineTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isUnderlineTriple(Node x, Node y, Node z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isDottedUnderlineTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addAmbiguousTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public void addAmbiguousTriple(Node x, Node y, Node Z) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addUnderlineTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public void addUnderlineTriple(Node x, Node y, Node Z) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addDottedUnderlineTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public void addDottedUnderlineTriple(Node x, Node y, Node Z) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeAmbiguousTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public void removeAmbiguousTriple(Node x, Node y, Node z) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeUnderlineTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public void removeUnderlineTriple(Node x, Node y, Node z) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeDottedUnderlineTriple(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setAmbiguousTriples(java.util.Set)
	 */
	@Override
	public void setAmbiguousTriples(Set<Triple> triples) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setUnderLineTriples(java.util.Set)
	 */
	@Override
	public void setUnderLineTriples(Set<Triple> triples) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setDottedUnderLineTriples(java.util.Set)
	 */
	@Override
	public void setDottedUnderLineTriples(Set<Triple> triples) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getCausalOrdering()
	 */
	@Override
	public List<Node> getCausalOrdering() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setHighlighted(edu.cmu.tetrad.graph.Edge, boolean)
	 */
	@Override
	public void setHighlighted(Edge edge, boolean highlighted) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isHighlighted(edu.cmu.tetrad.graph.Edge)
	 */
	@Override
	public boolean isHighlighted(Edge edge) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isParameterizable(edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public boolean isParameterizable(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#isTimeLagModel()
	 */
	@Override
	public boolean isTimeLagModel() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getTimeLagGraph()
	 */
	@Override
	public TimeLagGraph getTimeLagGraph() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeTriplesNotInGraph()
	 */
	@Override
	public void removeTriplesNotInGraph() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getSepset(edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.graph.Node)
	 */
	@Override
	public List<Node> getSepset(Node n1, Node n2) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#setNodes(java.util.List)
	 */
	@Override
	public void setNodes(List<Node> nodes) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getAllAttributes()
	 */
	@Override
	public Map<String, Object> getAllAttributes() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#getAttribute(java.lang.String)
	 */
	@Override
	public Object getAttribute(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#removeAttribute(java.lang.String)
	 */
	@Override
	public void removeAttribute(String key) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see edu.cmu.tetrad.graph.Graph#addAttribute(java.lang.String, java.lang.Object)
	 */
	@Override
	public void addAttribute(String key, Object value) {
		// TODO Auto-generated method stub

	}

}
