package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * CCG chart for performing exact inference.
 * 
 * @author jayantk
 */
public class CcgExactChart extends AbstractCcgChart {

  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[][] chartSizes;

  private final ChartFilter entryFilter;

  private static final int NUM_INITIAL_SPAN_ENTRIES = 100;

  public CcgExactChart(List<String> terminals, List<String> posTags, int[] posTagsInt,
      int[] wordDistances, int[] puncDistances, int[] verbDistances, ChartFilter entryFilter) {
    super(terminals, posTags, posTagsInt, wordDistances, puncDistances, verbDistances, entryFilter);
    int numTerminals = terminals.size();
    this.chart = new ChartEntry[numTerminals][numTerminals][];
    this.probabilities = new double[numTerminals][numTerminals][];
    this.chartSizes = new int[numTerminals][numTerminals];
    
    // Initialize chart arrays.
    for (int spanStart = 0; spanStart < numTerminals; spanStart++) {
      for (int spanEnd = 0; spanEnd < numTerminals; spanEnd++) {
        chart[spanStart][spanEnd] = new ChartEntry[NUM_INITIAL_SPAN_ENTRIES];
        probabilities[spanStart][spanEnd] = new double[NUM_INITIAL_SPAN_ENTRIES];
        chartSizes[spanStart][spanEnd] = 0;
      }
    }

    this.entryFilter = entryFilter;
  }

  /**
   * Gets the best CCG parse for the given span. Returns {@code null}
   * if no parse was found.
   * 
   * @param spanStart
   * @param spanEnd
   * @param parser
   * @param syntaxVarType
   * @return
   */
  public CcgParse decodeBestParseForSpan(int spanStart, int spanEnd,
      CcgParser parser, DiscreteVariable syntaxVarType) {
    double maxProb = -1;
    int maxEntryIndex = -1;
    double[] probs = getChartEntryProbsForSpan(spanStart, spanEnd);
    for (int i = 0; i < chartSizes[spanStart][spanEnd]; i++) {
      if (probs[i] > maxProb) {
        maxProb = probs[i];
        maxEntryIndex = i;
      }
    }

    if (maxEntryIndex == -1) {
      // No parses.
      return null;
    } else {
      return decodeParseFromSpan(spanStart, spanEnd, maxEntryIndex, parser, syntaxVarType);
    }
  }

  @Override
  public ChartEntry[] getChartEntriesForSpan(int spanStart, int spanEnd) {
    return chart[spanStart][spanEnd];
  }

  @Override
  public double[] getChartEntryProbsForSpan(int spanStart, int spanEnd) {
    return probabilities[spanStart][spanEnd];
  }

  @Override
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanStart][spanEnd];
  }

  @Override
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart,
      int spanEnd, DiscreteVariable syntaxVarType) {
    if (probability != 0.0 && (entryFilter == null || entryFilter.apply(entry, spanStart, spanEnd, syntaxVarType))) {
      int entryHeadedSyntax = entry.getHeadedSyntax();
      long entryHashCode = entry.getSyntaxHeadHashCode();

      int spanSize = chartSizes[spanStart][spanEnd];
      ChartEntry[] spanChart = chart[spanStart][spanEnd];
      double[] spanProbs = probabilities[spanStart][spanEnd];
      for (int i = 0; i < spanSize; i++) {
        ChartEntry other = spanChart[i];
        if (other.getSyntaxHeadHashCode() == entryHashCode 
            && other.getHeadedSyntax() == entryHeadedSyntax
            && longMultisetsEqual(entry.getUnfilledDependencies(), other.getUnfilledDependencies())
            && longMultisetsEqual(entry.getAssignments(), other.getAssignments())) {
          // Both entries have the same syntactic category and
          // semantics. Retain the entry with the highest probability.
          if (probability > spanProbs[i]) {
            // New entry better than old entry. Replace it.
            spanChart[i] = entry;
            spanProbs[i] = probability;
          } 
          return;
        }
      }

      // There is no existing entry with the same syntax and
      // semantics. Add the given entry as a new entry.
      if (spanSize == spanChart.length) {
        // Resize the chart spans to ensure space for the new entries.
        chart[spanStart][spanEnd] = Arrays.copyOf(spanChart, spanChart.length * 2);
        probabilities[spanStart][spanEnd] = Arrays.copyOf(spanProbs, spanChart.length * 2);
      }

      chart[spanStart][spanEnd][spanSize] = entry;
      probabilities[spanStart][spanEnd][spanSize] = probability;
      chartSizes[spanStart][spanEnd] = spanSize + 1;
    }
  }

  @Override
  public void clearChartEntriesForSpan(int spanStart, int spanEnd) {
    chartSizes[spanStart][spanEnd] = 0;
    // This second part is unnecessary, but makes debugging easier.
    Arrays.fill(chart[spanStart][spanEnd], null);
  }

  @Override
  public void doneAddingChartEntriesForSpan(int spanStart, int spanEnd) {
    // No work needs to be done.
  }

  /**
   * Checks if two multisets of long numbers contain the same keys
   * with the same frequency
   * 
   * @param first
   * @param second
   * @return
   */
  private static final boolean longMultisetsEqual(long[] firstDeps, long[] secondDeps) {
    if (firstDeps.length != secondDeps.length) {
      return false;
    }
    Arrays.sort(firstDeps);
    Arrays.sort(secondDeps);

    for (int i = 0; i < firstDeps.length; i++) {
      if (firstDeps[i] != secondDeps[i]) {
        return false;
      }
    }
    return true;
  }
}
