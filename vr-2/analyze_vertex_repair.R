# =============================================================================
# Vertex Repair Simulation Analysis
# =============================================================================
# Usage: Rscript analyze_vertex_repair.R vertex_repair_results_XXXXXXXX.csv
# Or set CSV_FILE below and source() this script.
# =============================================================================

library(dplyr)
library(tidyr)
library(ggplot2)

# --- Load data ---------------------------------------------------------------

args <- commandArgs(trailingOnly = TRUE)
csv_file <- if (length(args) >= 1) args[1] else {
  # fallback: use the most recent CSV in the working directory
  csvs <- list.files(".", pattern = "vertex_repair_results.*\\.csv", full.names = TRUE)
  if (length(csvs) == 0) stop("No vertex_repair_results CSV found.")
  csvs[which.max(file.mtime(csvs))]
}

cat("Reading:", csv_file, "\n")
d <- read.csv(csv_file, stringsAsFactors = FALSE)

# Convert algorithm and sampleSize to factors with a sensible order
d$algorithm  <- factor(d$algorithm,  levels = c("PC", "FGES", "BOSS"))
d$sampleSize <- factor(d$sampleSize, levels = sort(unique(d$sampleSize)))

# Derived: did repair change the graph at all?
d$repair_changed <- d$edges_after != d$edges_before | d$delta_shd != 0

cat("Rows loaded:", nrow(d), "\n\n")

# =============================================================================
# 1. Summary table: mean (SE) for key metrics, by algorithm x sampleSize
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

cat("=== Summary Table (mean ± SE) ===\n")
print(summary_tbl, row.names = FALSE)
write.csv(summary_tbl, "summary_table.csv", row.names = FALSE)
cat("Written: summary_table.csv\n\n")

# Machine-readable summary (easier for analysis)
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
# 2. Paired Wilcoxon signed-rank tests for delta metrics
# =============================================================================

delta_metrics <- c("delta_shd", "delta_violations", "delta_modelP")

cat("=== Wilcoxon Signed-Rank Tests (H0: median delta = 0) ===\n")
cat(sprintf("%-10s  %-6s  %-14s  %-8s  %-10s  %s\n",
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
      cat(sprintf("%-10s  %-6s  %-14s  %+.4f  %-10s  %.4f\n",
                  alg, n, dm, med,
                  ifelse(is.na(wt$statistic), "NA",
                         sprintf("%.1f", wt$statistic)),
                  ifelse(is.na(wt$p.value), NA, wt$p.value)))
      
      wilcox_rows[[length(wilcox_rows)+1]] <- data.frame(
        algorithm = alg, sampleSize = n, metric = dm,
        median_delta = med,
        W = ifelse(is.na(wt$statistic), NA, wt$statistic),
        p_value = ifelse(is.na(wt$p.value), NA, wt$p.value),
        stringsAsFactors = FALSE
      )
    }
  }
}

cat("\n")
wilcox_tbl <- bind_rows(wilcox_rows)
write.csv(wilcox_tbl, "wilcoxon_tests.csv", row.names = FALSE)
cat("Written: wilcoxon_tests.csv\n\n")

# =============================================================================
# 3. Proportion of reps where repair improved / stayed / degraded SHD
# =============================================================================

cat("=== SHD Change Direction (% of reps) ===\n")
cat(sprintf("%-10s  %-6s  %10s  %10s  %10s\n",
            "Algorithm", "n", "Improved", "Same", "Degraded"))
cat(strrep("-", 52), "\n")

direction_rows <- list()

for (alg in levels(d$algorithm)) {
  for (n in levels(d$sampleSize)) {
    sub <- d %>% filter(algorithm == alg, sampleSize == n)
    total <- nrow(sub)
    imp  <- sum(sub$delta_shd < 0,  na.rm=TRUE)
    same <- sum(sub$delta_shd == 0, na.rm=TRUE)
    deg  <- sum(sub$delta_shd > 0,  na.rm=TRUE)
    cat(sprintf("%-10s  %-6s  %9.1f%%  %9.1f%%  %9.1f%%\n",
                alg, n,
                100*imp/total, 100*same/total, 100*deg/total))
    direction_rows[[length(direction_rows)+1]] <- data.frame(
      algorithm=alg, sampleSize=n,
      pct_improved=100*imp/total,
      pct_same=100*same/total,
      pct_degraded=100*deg/total,
      stringsAsFactors=FALSE)
  }
}

cat("\n")
write.csv(bind_rows(direction_rows), "shd_direction.csv", row.names = FALSE)
cat("Written: shd_direction.csv\n\n")

# =============================================================================
# 4. Violations: proportion reaching zero after repair
# =============================================================================

cat("=== Violations Reduced to Zero After Repair ===\n")
cat(sprintf("%-10s  %-6s  %16s  %16s\n",
            "Algorithm", "n", "Had violations", "Reached 0 after"))
cat(strrep("-", 56), "\n")

for (alg in levels(d$algorithm)) {
  for (n in levels(d$sampleSize)) {
    sub <- d %>% filter(algorithm == alg, sampleSize == n)
    had_viol   <- sum(sub$violations_before > 0, na.rm=TRUE)
    reached_0  <- sum(sub$violations_before > 0 & sub$violations_after == 0, na.rm=TRUE)
    cat(sprintf("%-10s  %-6s  %13d     %13d (%.0f%%)\n",
                alg, n, had_viol, reached_0,
                ifelse(had_viol > 0, 100*reached_0/had_viol, 0)))
  }
}
cat("\n")

# =============================================================================
# 5. Plots
# =============================================================================

theme_set(theme_bw(base_size = 12))

# -- 5a. Violin: delta_shd by algorithm and sample size ----------------------

p1 <- ggplot(d, aes(x = algorithm, y = delta_shd, fill = algorithm)) +
  geom_hline(yintercept = 0, linetype = "dashed", colour = "grey50") +
  geom_violin(trim = FALSE, alpha = 0.6) +
  geom_boxplot(width = 0.12, outlier.size = 1, fill = "white") +
  facet_wrap(~ paste0("n = ", sampleSize)) +
  scale_fill_manual(values = c(PC = "#E69F00", FGES = "#56B4E9", BOSS = "#009E73")) +
  labs(title = "Change in SHD after Vertex Repair",
       subtitle = "Negative = improvement; dashed line = no change",
       x = NULL, y = "ΔSHD (after − before)", fill = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_delta_shd.pdf", p1, width = 7, height = 4.5)
ggsave("plot_delta_shd.png", p1, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_delta_shd.pdf / .png\n")

# -- 5b. Scatter: violations before vs after, coloured by algorithm ----------

p2 <- ggplot(d, aes(x = violations_before, y = violations_after,
                    colour = algorithm, shape = algorithm)) +
  geom_abline(intercept = 0, slope = 1, linetype = "dashed", colour = "grey50") +
  geom_jitter(width = 0.25, height = 0.25, alpha = 0.7, size = 2) +
  facet_wrap(~ paste0("n = ", sampleSize)) +
  scale_colour_manual(values = c(PC = "#E69F00", FGES = "#56B4E9", BOSS = "#009E73")) +
  labs(title = "Markov Violations Before vs After Repair",
       subtitle = "Points below the diagonal indicate improvement",
       x = "Violations before repair", y = "Violations after repair",
       colour = "Algorithm", shape = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_violations.pdf", p2, width = 7, height = 4.5)
ggsave("plot_violations.png", p2, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_violations.pdf / .png\n")

# -- 5c. Adj F1 before vs after ----------------------------------------------

p3 <- ggplot(d, aes(x = adjF1_before, y = adjF1_after,
                    colour = algorithm, shape = algorithm)) +
  geom_abline(intercept = 0, slope = 1, linetype = "dashed", colour = "grey50") +
  geom_jitter(width = 0.005, height = 0.005, alpha = 0.7, size = 2) +
  facet_wrap(~ paste0("n = ", sampleSize)) +
  scale_colour_manual(values = c(PC = "#E69F00", FGES = "#56B4E9", BOSS = "#009E73")) +
  coord_fixed(xlim = c(0, 1), ylim = c(0, 1)) +
  labs(title = "Adjacency F1 Before vs After Repair",
       subtitle = "Points above diagonal = improvement; below = degradation",
       x = "Adj F1 before repair", y = "Adj F1 after repair",
       colour = "Algorithm", shape = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_adjF1.pdf", p3, width = 7, height = 4.5)
ggsave("plot_adjF1.png", p3, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_adjF1.pdf / .png\n")

# -- 5d. Model-P before vs after ---------------------------------------------

d_mp <- d %>% filter(!is.na(modelP_before) & !is.na(modelP_after))

p4 <- ggplot(d_mp, aes(x = modelP_before, y = modelP_after,
                       colour = algorithm, shape = algorithm)) +
  geom_abline(intercept = 0, slope = 1, linetype = "dashed", colour = "grey50") +
  geom_hline(yintercept = 0.05, linetype = "dotted", colour = "red", alpha = 0.5) +
  geom_vline(xintercept = 0.05, linetype = "dotted", colour = "red", alpha = 0.5) +
  geom_point(alpha = 0.7, size = 2) +
  facet_wrap(~ paste0("n = ", sampleSize)) +
  scale_colour_manual(values = c(PC = "#E69F00", FGES = "#56B4E9", BOSS = "#009E73")) +
  coord_fixed(xlim = c(0, 1), ylim = c(0, 1)) +
  labs(title = "Model-P (Markov Checker KS p-value) Before vs After Repair",
       subtitle = "Red dotted lines = α=0.05 threshold",
       x = "Model-P before repair", y = "Model-P after repair",
       colour = "Algorithm", shape = "Algorithm") +
  theme(legend.position = "bottom")

ggsave("plot_modelP.pdf", p4, width = 7, height = 4.5)
ggsave("plot_modelP.png", p4, width = 7, height = 4.5, dpi = 150)
cat("Written: plot_modelP.pdf / .png\n")

cat("\nAll outputs written to:", getwd(), "\n")