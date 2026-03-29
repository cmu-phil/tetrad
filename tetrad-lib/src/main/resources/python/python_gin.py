#!/usr/bin/env python3
"""
One-shot GIN runner for Tetrad integration.

Usage:
    python gin_runner.py <csv_path> [indep_test_method] [alpha]

Arguments:
    csv_path           Path to CSV data file (with header row of variable names).
    indep_test_method  Independence test to use: 'kci' (default) or 'hsic'.
    alpha              Significance level (default 0.05).
                       Note: causal-learn's GIN does not currently expose alpha
                       as a direct parameter; it is included here for forward
                       compatibility and logged but not yet forwarded.

Output (single JSON line to stdout):

  On success:
    {
      "ok": true,
      "nodes": [{"name": "X1", "latent": false}, {"name": "L1", "latent": true}, ...],
      "edges": [{"node1": "X1", "endpoint1": "TAIL", "node2": "X2", "endpoint2": "ARROW"}, ...],
      "causal_order": [["X1", "X2"], ["X3", "X4"], ...]
    }

  On failure:
    {"ok": false, "error": "...", "traceback": "..."}

Endpoint values: "TAIL", "ARROW", "CIRCLE"

Edge semantics (endpoint1 at node1, endpoint2 at node2):
  TAIL  / ARROW  ->  directed edge  node1 -> node2
  ARROW / ARROW  ->  bidirected     node1 <-> node2
  TAIL  / TAIL   ->  undirected     node1 --- node2
  CIRCLE/ ARROW  ->  partial anc.   node1 o-> node2
  CIRCLE/ CIRCLE ->  nondirected    node1 o-o node2

Causal order: list of groups (each group a list of variable names),
ordered from causally earliest to latest.  Groups correspond to the
causal order K returned by causal-learn's GIN.
"""

import sys
import json
import traceback

import numpy as np


# ---------------------------------------------------------------------------
# CSV loading
# ---------------------------------------------------------------------------

def load_csv(path):
    """
    Load a numeric CSV file that has a header row of variable names.
    Returns (data_matrix, column_names).
    data_matrix has shape (n_samples, n_features).
    """
    with open(path, 'r') as fh:
        header_line = fh.readline().strip()

    col_names = [c.strip().strip('"').strip("'") for c in header_line.split(',')]

    data = np.genfromtxt(path, delimiter=',', skip_header=1)

    # genfromtxt returns a 1-D array when there is only one row
    if data.ndim == 1:
        data = data.reshape(1, -1)

    return data, col_names


# ---------------------------------------------------------------------------
# Endpoint serialization
# ---------------------------------------------------------------------------

def endpoint_to_str(endpoint):
    """Convert a causal-learn Endpoint enum value to a string."""
    # Import here so that import errors are caught inside run_gin's try/except
    from causallearn.graph.Endpoint import Endpoint
    if endpoint == Endpoint.TAIL:
        return "TAIL"
    elif endpoint == Endpoint.ARROW:
        return "ARROW"
    elif endpoint == Endpoint.CIRCLE:
        return "CIRCLE"
    else:
        return "UNKNOWN"


# ---------------------------------------------------------------------------
# Node name mapping
# ---------------------------------------------------------------------------

def build_name_map(nodes, col_names):
    """
    Map causal-learn node names to Tetrad-friendly names.

    When GIN is called with a plain numpy array, causal-learn assigns
    observed nodes the names "X1", "X2", ..., "Xp" (1-indexed).
    Some versions use 0-indexed names "X0".."X(p-1)".
    Latent nodes discovered by GIN are named "L1", "L2", etc.

    Observed nodes are remapped to the original CSV column names.
    Latent node names are kept as assigned by causal-learn.

    Returns a dict: cl_name -> tetrad_name
    """
    n_obs = len(col_names)
    name_map = {}

    for node in nodes:
        cl_name = node.get_name()

        if cl_name.startswith('X') and cl_name[1:].isdigit():
            idx = int(cl_name[1:])
            if 1 <= idx <= n_obs:
                # 1-indexed convention (most common)
                name_map[cl_name] = col_names[idx - 1]
            elif 0 <= idx < n_obs:
                # 0-indexed convention (some versions)
                name_map[cl_name] = col_names[idx]
            else:
                # Index out of expected range — keep as-is
                name_map[cl_name] = cl_name
        else:
            # Latent node or any other non-observed node — keep the name
            name_map[cl_name] = cl_name

    return name_map


# ---------------------------------------------------------------------------
# Main GIN invocation
# ---------------------------------------------------------------------------

def run_gin(csv_path, indep_test_method='kci', alpha=0.05):
    """
    Load data from csv_path, run causal-learn GIN, return a JSON-serialisable
    dict describing the resulting graph.
    """
    from causallearn.search.HiddenCausal.GIN.GIN import GIN

    data, col_names = load_csv(csv_path)
    n_observed = len(col_names)
    observed_set = set(col_names)

    # ------------------------------------------------------------------
    # Run GIN
    # Returns:
    #   G : causallearn.graph.GeneralGraph  (observed + latent nodes)
    #   K : list of lists of int            (causal order, indices into
    #                                        observed variable columns)
    # ------------------------------------------------------------------
    G, K = GIN(data, indep_test_method=indep_test_method)

    nodes = G.get_nodes()
    name_map = build_name_map(nodes, col_names)

    # ------------------------------------------------------------------
    # Serialize nodes
    # ------------------------------------------------------------------
    node_list = []
    for node in nodes:
        cl_name = node.get_name()
        tetrad_name = name_map[cl_name]
        is_latent = tetrad_name not in observed_set
        node_list.append({"name": tetrad_name, "latent": is_latent})

    # ------------------------------------------------------------------
    # Serialize edges
    # ------------------------------------------------------------------
    edge_list = []
    for edge in G.get_graph_edges():
        n1_name = name_map[edge.get_node1().get_name()]
        n2_name = name_map[edge.get_node2().get_name()]
        ep1 = endpoint_to_str(edge.get_endpoint1())
        ep2 = endpoint_to_str(edge.get_endpoint2())
        edge_list.append({
            "node1": n1_name, "endpoint1": ep1,
            "node2": n2_name, "endpoint2": ep2
        })

    # ------------------------------------------------------------------
    # Serialize causal order
    # K is a list of lists of integer indices into the observed columns.
    # Convert to variable names.
    # ------------------------------------------------------------------
    causal_order = []
    for group in K:
        group_names = []
        for idx in group:
            if isinstance(idx, (int, np.integer)) and 0 <= int(idx) < n_observed:
                group_names.append(col_names[int(idx)])
            else:
                # Fallback: stringify whatever index was returned
                group_names.append(str(idx))
        causal_order.append(group_names)

    return {
        "ok": True,
        "nodes": node_list,
        "edges": edge_list,
        "causal_order": causal_order
    }


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        result = {
            "ok": False,
            "error": "Usage: gin_runner.py <csv_path> [indep_test_method] [alpha]"
        }
        print(json.dumps(result))
        sys.stdout.flush()
        sys.exit(1)

    csv_path = sys.argv[1]
    indep_test_method = sys.argv[2] if len(sys.argv) > 2 else 'kci'

    try:
        alpha = float(sys.argv[3]) if len(sys.argv) > 3 else 0.05
    except (ValueError, IndexError):
        alpha = 0.05

    try:
        result = run_gin(csv_path, indep_test_method, alpha)
    except Exception as e:
        result = {
            "ok": False,
            "error": str(e),
            "traceback": traceback.format_exc()
        }

    print(json.dumps(result))
    sys.stdout.flush()


if __name__ == "__main__":
    main()
