# =============================================================================
# Vertex Repair Simulation Analysis — v2 (100-rep version)
# =============================================================================
# Usage: Rscript analyze_vertex_repair_v2.R vertex_repair_results_XXXXXXXX.csv
# =============================================================================

library(dplyr)
library(tidyr)
library(ggplot2)

# --- Load data ---------------------------------------------------------------

args <- commandArgs(trailingOnly = TRUE)
csv_file <- if (length(args) >= 1) args[1] else {
  csvs <- list.files(".", pattern = "vertex_repair_results.*\\.csv", full.names = TRUE)
  if (length(csvs) == 0) stop("No vertex_repair_results CSV found.")
  csvs[which.max(file.mtime(csvs))]
}

cat("Reading:", csv_file, "\n")
d <- read.csv(csv_file, stringsAsFactors = FALSE)

d$algorithm  <- factor(d$algorithm,  levels = c("PC", "FGES", "BOSS"))
d$sampleSize <- factor(d$sampleSize, levels = sort(unique(d$sampleSize)))

# Derived columns
d$repair_changed  <- d$edges_after != d$edges_before | d$delta_shd != 0
d$repair_unchanged <- !d$repair_changed

cat("Rows loaded:", nrow(d), "\n\n")

# =============================================================================
# 1. Summary table — paper-ready (mean +/- SE)
# =============================================================================

se <- function(x) sd(x, na.rm = TRUE) / sqrt(sum(!is.na(x)))
fmt <- function(m, s) sprintf("%.3f (%.3f)", m, s)

metrics <- c("shd", "adjF1", "arrF1", "violations", "modelP", "edges")

summary_rows <- list()
for (alg in levels(d$algorithm)) {
  for (n in levels(d$sampleSize)) {
    sub <- d %>% filter(algorithm == alg, sampleSize == n)
    row <- list(algorithm = alg, sampleSize = n, N = nrow(sub))
    for (m in metrics) {
      b <- sub[[paste0(m, "_before")]]
      a <- sub[[paste0(m, "_after")]]
      row[[paste0(m, "_before")]] <- fmt(mean(b, na.rm=TRUE), se(b))
      row[[paste0(m, "_after")]]  <- fmt(mean(a, na.rm=TRUE), se(a))
    }
    summary_rows[[length(summary_rows)+1]] <- as.data.frame(row, stringsAsFactors=FALSE)
  }
}

summary_tbl <- bind_rows(summary_rows)
cat("=== Summary Table (mean +/- SE) ===\n")
print(summary_tbl, row.names = FALSE)
write.csv(summary_tbl, "summary_table.csv", row.names = FALSE)
cat("Written: summary_table.csv\n\n")

# Machine-readable wide format
summary_wide <- summary_tbl %>%
  select(algorithm, sampleSize, N,
         shd_before, shd_after,
         adjF1_before, adjF1_after,
         arrF1_before, arrF1_after,
         violations_before, violations_after,
         modelP_before, modelP_after,
         edges_before, edges_after)
write.csv(summary_wide, "summary_wide.csv", row.names = FALSE)
cat("Written: summary_wide.csv\n\n")

# =============================================================================
# 2. Wilcoxon signed-rank tests
# =============================================================================

delta_metrics <- c("delta_shd", "delta_violations", "delta_modelP")

cat("=== Wilcoxon Signed-Rank Tests (H0: median delta = 0) ===\n")
cat(sprintf("%-10s  %-6s  %-16s  %-8s  %-10s  %s\n",
            "Algorithm", "n", "Metric", "Median", "W", "p-value"))
cat(strrep("-", 68), "\n")

wilcox_rows <- list()
for (alg in levels(d$algorithm)) {
  for (n in levels(d$sampleSize)) {
    sub <- d %>% filter(algorithm == alg, sampleSize == n)
    for (dm in delta_metrics) {
      x <- sub[[dm]]
      x <- x[!is.na(x)]
      if (length(x) < 5) next
      wt <- tryCatch(
        wilcox.test(x, mu = 0, alternative = "two.sided", exact = FALSE),
        error = function(e) list(statistic = NA, p.value = NA)
      )
      med <- median(x, na.rm = TRUE)
      sig <- if (!is.na(wt$p.value) && wt$p.value < 0.001) "***" else
             if (!is.na(wt$p.value) && wt$p.value < 0.01)  "**"  else
             if (!is.na(wt$p.value) && wt$p.value < 0.05)  "*"   else ""
      cat(sprintf("%-10s  %-6s  %-16s  %+.4f  %-10s  %.4f %s\n",
                  alg, n, dm, med,
                  ifelse(is.na(wt$statistic), "NA", sprintf("%.1f", wt$statistic)),
                  ifelse(is.na(wt$p.value), NA, wt$p.value), sig))
      wilcox_rows[[length(wilcox_rows)+1]] <- data.frame(
        algorithm=alg, sampleSize=n, metric=dm,
        median_delta=med,
        W=ifelse(is.na(wt$statistic), NA, wt$statistic),
        p_value=ifelse(is.na(wt$p.value), NA, wt$p.value),
        sig=sig, stringsAsFactors=FALSE)
    }
  }
}
cat("\n")
write.csv(bind_rows(wilcox_rows), "wilcoxon_tests.csv", row.names = FALSE)
cat("Written: wilcoxon_tests.csv\n\n")

# =============================================================================
# 3. SHD direction table
# =============================================================================

cat("=== SHD Change Direction (% of reps) ===\n")
cat(sprintf("%-10s  %-6s  %10s  %10s  %10s  %12s\n",
            "Algorithm", "n", "Improved", "Same", "Degraded", "Unchanged"))
cat(strrep("-", 64), "\n")

direction_rows <- list()
for (alg in levels(d$algorithm)) {
  for (n in levels(d$sampleSize)) {
    sub <- d %>% filter(algorithm == alg, sampleSize == n)
    total <- nrow(sub)
    imp     <- sum(sub$delta_shd < 0,  na.rm=TRUE)
    same    <- sum(sub$delta_shd == 0, na.rm=TRUE)
    deg     <- sum(sub$delta_shd > 0,  na.rm=TRUE)
    unchg   <- sum(sub$repair_unchanged, na.rm=TRUE)
    cat(sprintf("%-10s  %-6s  %9.1f%%  %9.1f%%  %9.1f%%  %10.1f%%\n",
                alg, n,
                100*imp/total, 100*same/total, 100*deg/total, 100*unchg/total))
    direction_rows[[length(direction_rows)+1]] <- data.frame(
      algorithm=alg, sampleSize=n,
      pct_improved=100*imp/total, pct_same=100*same/total,
      pct_degraded=100*deg/total, pct_unchanged=100*unchg/total,
      stringsAsFactors=FALSE)
  }
}
cat("\n")
write.csv(bind_rows(direction_rows), "shd_direction.csv", row.names = FALSE)
cat("Written: shd_direction.csv\n\n")

# =============================================================================
# 4. Violations reduced to zero
# =============================================================================

cat("=== Violations Reduced to Zero After Repair ===\n")
cat(sprintf("%-10s  %-6s  %16s  %16s\n",
            "Algorithm", "n", "Had violations", "Reached 0 after"))
cat(strrep("-", 56), "\n")

for (alg in levels(d$algorithm)) {
  for (n in levels(d$sampleSize)) {
    sub <- d %>% filter(algorithm == alg, sampleSize == n)
    had   <- sum(sub$violations_before > 0, na.rm=TRUE)
    zero  <- sum(sub$violations_before > 0 & sub$violations_after == 0, na.rm=TRUE)
    cat(sprintf("%-10s  %-6s  %13d     %13d (%.0f%%)\n",
                alg, n, had, zero,
                ifelse(had > 0, 100*zero/had, 0)))
  }
}
cat("\n")

# =============================================================================
# 5. Plots
# =============================================================================

theme_set(theme_bw(base_size = 12))

alg_colors <- c(PC = "#E69F00", FGES = "#56B4E9", BOSS = "#009E73")

# Helper label
n_label <- function(n) paste0("n = ", n)

# -- 5a. Violin: delta_shd ----------------------------------------------------

p1 <- ggplot(d, aes(x = algorithm, y = delta_shd, fill = algorithm)) +
  geom_hline(yintercept = 0, linetype = "dashed", colour = "grey50") +
  geom_violin(trim = FALSE, alpha = 0.6) +
  geom_boxplot(width = 0.12, outlier.size = 0.8, fill = "white") +
  facet_wrap(~ n_label(sampleSize)) +
  scale_fill_manual(values = alg_colors) +
  labs(title = "Change in SHD after Vertex Repair",
       subtitle = "Negative = improvement; dashed line = no change",
       x = NULL, y = "SHD after - SHD before", fill = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_delta_shd.pdf", p1, width = 7, height = 4.5)
ggsave("plot_delta_shd.png", p1, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_delta_shd.pdf / .png\n")

# -- 5b. Scatter: violations before vs after ----------------------------------

p2 <- ggplot(d, aes(x = violations_before, y = violations_after,
                    colour = algorithm, shape = algorithm)) +
  geom_abline(intercept = 0, slope = 1, linetype = "dashed", colour = "grey50") +
  geom_jitter(width = 0.3, height = 0.3, alpha = 0.5, size = 1.5) +
  facet_wrap(~ n_label(sampleSize)) +
  scale_colour_manual(values = alg_colors) +
  labs(title = "Markov Violations Before vs After Repair",
       subtitle = "Points below diagonal = improvement",
       x = "Violations before repair", y = "Violations after repair",
       colour = "Algorithm", shape = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_violations.pdf", p2, width = 7, height = 4.5)
ggsave("plot_violations.png", p2, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_violations.pdf / .png\n")

# -- 5c. Arrowhead F1 before vs after ----------------------------------------

p3 <- ggplot(d, aes(x = arrF1_before, y = arrF1_after,
                    colour = algorithm, shape = algorithm)) +
  geom_abline(intercept = 0, slope = 1, linetype = "dashed", colour = "grey50") +
  geom_jitter(width = 0.005, height = 0.005, alpha = 0.5, size = 1.5) +
  facet_wrap(~ n_label(sampleSize)) +
  scale_colour_manual(values = alg_colors) +
  coord_fixed(xlim = c(0, 1), ylim = c(0, 1)) +
  labs(title = "Arrowhead F1 Before vs After Repair",
       subtitle = "Points above diagonal = improvement",
       x = "Arrowhead F1 before", y = "Arrowhead F1 after",
       colour = "Algorithm", shape = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_arrF1.pdf", p3, width = 7, height = 4.5)
ggsave("plot_arrF1.png", p3, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_arrF1.pdf / .png\n")

# -- 5d. Adjacency F1 before vs after ----------------------------------------

p4 <- ggplot(d, aes(x = adjF1_before, y = adjF1_after,
                    colour = algorithm, shape = algorithm)) +
  geom_abline(intercept = 0, slope = 1, linetype = "dashed", colour = "grey50") +
  geom_jitter(width = 0.005, height = 0.005, alpha = 0.5, size = 1.5) +
  facet_wrap(~ n_label(sampleSize)) +
  scale_colour_manual(values = alg_colors) +
  coord_fixed(xlim = c(0, 1), ylim = c(0, 1)) +
  labs(title = "Adjacency F1 Before vs After Repair",
       subtitle = "Points above diagonal = improvement",
       x = "Adjacency F1 before", y = "Adjacency F1 after",
       colour = "Algorithm", shape = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_adjF1.pdf", p4, width = 7, height = 4.5)
ggsave("plot_adjF1.png", p4, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_adjF1.pdf / .png\n")

# -- 5e. Model-P before vs after ---------------------------------------------

d_mp <- d %>% filter(!is.na(modelP_before) & !is.na(modelP_after))

p5 <- ggplot(d_mp, aes(x = modelP_before, y = modelP_after,
                        colour = algorithm, shape = algorithm)) +
  geom_abline(intercept = 0, slope = 1, linetype = "dashed", colour = "grey50") +
  geom_hline(yintercept = 0.05, linetype = "dotted", colour = "red", alpha = 0.5) +
  geom_vline(xintercept = 0.05, linetype = "dotted", colour = "red", alpha = 0.5) +
  geom_point(alpha = 0.5, size = 1.5) +
  facet_wrap(~ n_label(sampleSize)) +
  scale_colour_manual(values = alg_colors) +
  coord_fixed(xlim = c(0, 1), ylim = c(0, 1)) +
  labs(title = "Model-P (Markov Checker KS p-value) Before vs After Repair",
       subtitle = "Red dotted lines: alpha = 0.05 threshold",
       x = "Model-P before repair", y = "Model-P after repair",
       colour = "Algorithm", shape = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_modelP.pdf", p5, width = 7, height = 4.5)
ggsave("plot_modelP.png", p5, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_modelP.pdf / .png\n")

# -- 5f. Delta SHD vs violations before (key insight plot) -------------------
# This plot directly shows: repair helps most when violations are large

p6 <- ggplot(d, aes(x = violations_before, y = delta_shd,
                    colour = algorithm)) +
  geom_hline(yintercept = 0, linetype = "dashed", colour = "grey50") +
  geom_jitter(width = 0.2, height = 0.1, alpha = 0.4, size = 1.5) +
  geom_smooth(method = "loess", se = TRUE, linewidth = 0.8) +
  facet_wrap(~ n_label(sampleSize)) +
  scale_colour_manual(values = alg_colors) +
  labs(title = "Repair Benefit vs Initial Violation Count",
       subtitle = "Negative delta SHD = improvement; trend lines via LOESS",
       x = "Violations before repair",
       y = "SHD after - SHD before",
       colour = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_delta_shd_vs_violations.pdf", p6, width = 7, height = 4.5)
ggsave("plot_delta_shd_vs_violations.png", p6, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_delta_shd_vs_violations.pdf / .png\n")

# =============================================================================
# 6. Crossover analysis — at what violation count does repair start helping?
# =============================================================================

cat("=== Mean delta_shd by violation count bin ===\n")

d$viol_bin <- cut(d$violations_before,
                  breaks = c(-Inf, 0, 2, 4, 6, 10, Inf),
                  labels = c("0", "1-2", "3-4", "5-6", "7-10", "11+"))

crossover <- d %>%
  group_by(algorithm, sampleSize, viol_bin) %>%
  summarise(
    n = n(),
    mean_delta_shd = mean(delta_shd, na.rm = TRUE),
    se_delta_shd   = sd(delta_shd, na.rm = TRUE) / sqrt(n()),
    .groups = "drop"
  )

print(crossover, n = Inf)
write.csv(crossover, "crossover_analysis.csv", row.names = FALSE)
cat("Written: crossover_analysis.csv\n\n")

cat("\nAll outputs written to:", getwd(), "\n")