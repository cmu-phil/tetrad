#!/usr/bin/env python3
import sys, json, traceback
import numpy as np

## TEMP FILE.

# You may need to adjust this import depending on causal-learn version.
# Typical causal-learn:
#   from causallearn.utils.cit import CIT
from causallearn.utils.cit import CIT

X = None
cit = None
params = {}
verbose = False

def send(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()

def load_csv(path):
    # Assumes numeric CSV with header row.
    # If your DataSet includes names, keep header and let genfromtxt use names=True;
    # otherwise load plain numeric.
    data = np.genfromtxt(path, delimiter=",", skip_header=1)
    if data.ndim == 1:
        data = data.reshape(-1, 1)
    return data

def handle_init(msg):
    global X, cit, params, verbose

    csv_path = msg.get("csv_path")
    if not csv_path:
        raise ValueError("init requires csv_path")

    params = msg.get("params", {}) or {}
    verbose = bool(msg.get("verbose", False))

    X = load_csv(csv_path)

    # Build CIT object for KCI.
    # CIT supports different tests; "kci" is typical.
    # Some versions accept kwargs like kernel, width, etc through params.
    cit = CIT(X, "kci", **params)

    return {"ok": True, "n": int(X.shape[0]), "p": int(X.shape[1])}

def handle_update_params(msg):
    global cit, params
    params = msg.get("params", {}) or {}
    if X is None:
        return {"ok": True, "note": "params stored (no init yet)"}
    # Recreate CIT with new params
    cit = CIT(X, "kci", **params)
    return {"ok": True}

def handle_pvalue(msg):
    if cit is None:
        raise RuntimeError("not initialized")

    x = int(msg["x"])
    y = int(msg["y"])
    z = msg.get("z", [])
    z = [int(i) for i in z]

    # CIT convention: cit(x, y, z) returns p-value
    p = float(cit(x, y, z))
    return {"ok": True, "p": p}

def main():
    send({"ok": True, "ready": True})
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
            op = msg.get("op")
            if op == "init":
                out = handle_init(msg)
            elif op == "update_params":
                out = handle_update_params(msg)
            elif op == "pvalue":
                out = handle_pvalue(msg)
            elif op == "close":
                out = {"ok": True, "bye": True}
                send(out)
                return
            else:
                out = {"ok": False, "error": f"unknown op: {op}"}
            send(out)
        except Exception as e:
            if verbose:
                traceback.print_exc()
            send({"ok": False, "error": str(e)})

if __name__ == "__main__":
    main()