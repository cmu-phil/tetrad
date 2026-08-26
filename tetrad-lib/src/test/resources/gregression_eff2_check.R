## Cross-check of Tetrad's GRegression against the R package eff2 (Guo & Perkovic, 2022).
## Run GRegressionEff2CrossCheck (Java) first, then, from the output directory:
##   Rscript gregression_eff2_check.R
## Requires: install.packages("eff2")  (which pulls in pcalg; graph/RBGL from Bioconductor if needed)

suppressPackageStartupMessages(library(eff2))

data  <- read.csv("data.csv", check.names = FALSE)
cases <- read.csv("cases.csv", stringsAsFactors = FALSE)

amats <- list(
  dag   = as.matrix(read.csv("amat_dag.csv",   header = FALSE)),
  cpdag = as.matrix(read.csv("amat_cpdag.csv", header = FALSE)),
  mpdag = as.matrix(read.csv("amat_mpdag.csv", header = FALSE))
)
# for (nm in names(amats)) {
#   rownames(amats[[nm]]) <- colnames(amats[[nm]]) <- colnames(data)
#   stopifnot(pcalg::isValidGraph(amats[[nm]], type = if (nm == "dag") "dag" else "pdag"))
# }

for (nm in names(amats)) {
  dimnames(amats[[nm]]) <- NULL
  stopifnot(pcalg::isValidGraph(amats[[nm]], type = if (nm == "dag") "dag" else "pdag"))
}

parse_ints <- function(s) as.integer(strsplit(s, ";")[[1]])
parse_nums <- function(s) if (is.na(s) || s == "NA") NA_real_ else as.numeric(strsplit(s, ";")[[1]])

id_mismatch <- 0; est_checked <- 0; max_abs_diff <- 0

for (r in seq_len(nrow(cases))) {
  g    <- cases$graph[r]
  amat <- amats[[g]]
  x    <- parse_ints(cases$A[r])
  y    <- as.integer(cases$Y[r])
  type <- if (g == "dag") "dag" else "pdag"

  r_ident <- eff2::isIdentified(amat, x, y, type = type)
  t_ident <- as.logical(cases$identified[r])

  if (r_ident != t_ident) {
    id_mismatch <- id_mismatch + 1
    cat(sprintf("IDENTIFICATION MISMATCH: graph=%s A=%s Y=%d  tetrad=%s eff2=%s\n",
                g, cases$A[r], y, t_ident, r_ident))
    next
  }

  if (t_ident) {
    r_est <- as.numeric(eff2::estimateEffect(data, x, y, amat))
    t_est <- parse_nums(cases$estimate[r])
    d <- max(abs(r_est - t_est))
    max_abs_diff <- max(max_abs_diff, d)
    est_checked <- est_checked + 1
    if (d > 1e-6) {
      cat(sprintf("ESTIMATE MISMATCH: graph=%s A=%s Y=%d\n  tetrad=%s\n  eff2  =%s\n",
                  g, cases$A[r], y, paste(signif(t_est, 10), collapse = ","),
                  paste(signif(r_est, 10), collapse = ",")))
    }
  }
}

cat(sprintf("\n%d cases; %d identification mismatches; %d estimates compared; max |diff| = %.3e\n",
            nrow(cases), id_mismatch, est_checked, max_abs_diff))
