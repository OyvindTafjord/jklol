package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ListLocalContext;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Implementations of common {@code CcgLexicon} methods.
 * 
 * @author jayant
 *
 */
public abstract class AbstractCcgLexicon implements CcgLexicon {
  private static final long serialVersionUID = 2L;
  
  // Weights and word -> ccg category mappings for the
  // lexicon (terminals in the parse tree).
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;
  
  private final FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator;

  public AbstractCcgLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);
    VariableNumMap expectedTerminalVars = VariableNumMap.unionAll(terminalVar, ccgCategoryVar);
    Preconditions.checkArgument(expectedTerminalVars.equals(terminalDistribution.getVars()));
    
    this.featureGenerator = featureGenerator;
  }

  @Override
  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }
  
  public VariableNumMap getCcgCategoryVar() {
    return ccgCategoryVar;
  }
  
  public DiscreteFactor getTerminalDistribution() {
    return terminalDistribution;
  }

  @Override
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence) {
    return getLexiconEntriesFromFactor(wordSequence, terminalDistribution, terminalVar, ccgCategoryVar);
  }

  @Override
  public List<LexiconEntry> getLexiconEntriesWithUnknown(String word, String posTag) {
    return getLexiconEntriesWithUnknown(Arrays.asList(word), Arrays.asList(posTag));
  }

  @Override
  public List<LexiconEntry> getLexiconEntriesWithUnknown(List<String> originalWords, List<String> posTags) {
    Preconditions.checkArgument(originalWords.size() == posTags.size());
    List<String> words = preprocessInput(originalWords);    
    if (terminalVar.isValidOutcomeArray(words)) {
      return getLexiconEntries(words);
    } else if (words.size() == 1) {
      List<String> posTagBackoff = preprocessInput(Arrays.asList(CcgLexicon.UNKNOWN_WORD_PREFIX + posTags.get(0)));
      return getLexiconEntries(posTagBackoff);
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public boolean isPossibleLexiconEntry(List<String> originalWords, List<String> posTags, HeadedSyntacticCategory category) {
    Preconditions.checkArgument(originalWords.size() == posTags.size());

    for (LexiconEntry entry : getLexiconEntriesWithUnknown(originalWords, posTags)) {
      if (entry.getCategory().getSyntax().equals(category)) {
        return true;
      }
    }
    // System.out.println("No such lexicon entry: " + words + " -> " +
    // category);
    return false;
  }
  
  /**
   * Initializes {@code chart} with entries from the CCG lexicon for
   * {@code input}.
   * 
   * @param input
   * @param chart
   * @param parser
   */
  @Override
  public void initializeChartTerminals(SupertaggedSentence input, CcgChart chart, CcgParser parser) {
    initializeChartWithDistribution(input, chart, getTerminalVar(), getCcgCategoryVar(),
        getTerminalDistribution(), true, parser);
  }

  protected abstract double getCategoryWeight(List<String> originalWords, List<String> preprocessedWords, 
      List<String> pos, List<WordAndPos> ccgWordList, List<Tensor> featureVectors,
      int spanStart, int spanEnd, List<String> terminals, CcgCategory category);

  /**
   * This method is a hack.
   * 
   * @param terminals
   * @param posTags
   * @param chart
   * @param terminalVar
   * @param ccgCategoryVar
   * @param terminalDistribution
   * @param useUnknownWords
   * @param parser
   */
  private void initializeChartWithDistribution(SupertaggedSentence input, CcgChart chart,
      VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, boolean useUnknownWords, CcgParser parser) {
    List<String> terminals = input.getWords();
    List<String> posTags = input.getPosTags();

    List<String> preprocessedTerminals = preprocessInput(terminals);
    List<WordAndPos> ccgWordList = WordAndPos.createExample(terminals, posTags);
    List<Tensor> featureVectors = null;
    if (featureGenerator != null) {
      featureVectors = Lists.newArrayList();
      for (int i = 0; i < preprocessedTerminals.size(); i++) {
        LocalContext<WordAndPos> context = new ListLocalContext<WordAndPos>(ccgWordList, i);
        featureVectors.add(featureGenerator.apply(context));
      }
    }

    for (int i = 0; i < preprocessedTerminals.size(); i++) {
      for (int j = i; j < preprocessedTerminals.size(); j++) {
        List<String> terminalValue = preprocessedTerminals.subList(i, j + 1);
        String posTag = posTags.get(j);
        int numAdded = addChartEntriesForTerminal(terminals, preprocessedTerminals, posTags,
            ccgWordList, featureVectors, terminalValue, posTag, i, j, chart, terminalVar,
            ccgCategoryVar, terminalDistribution, parser);
        if (numAdded == 0 && i == j && useUnknownWords) {
          // Backoff to POS tags if the input is unknown.
          terminalValue = preprocessInput(Arrays.asList(CcgLexicon.UNKNOWN_WORD_PREFIX + posTags.get(i)));
          addChartEntriesForTerminal(terminals, preprocessedTerminals, posTags, ccgWordList, 
              featureVectors, terminalValue, posTag, i, j, chart, terminalVar, ccgCategoryVar,
              terminalDistribution, parser);
        }
      }
    }
  }

  private int addChartEntriesForTerminal(List<String> originalTerminals, List<String> preprocessedTerminals,
      List<String> posTags, List<WordAndPos> ccgWordList, List<Tensor> featureVectors,
      List<String> terminalValue, String posTag, int spanStart, int spanEnd, CcgChart chart,
      VariableNumMap terminalVar, VariableNumMap ccgCategoryVar, DiscreteFactor terminalDistribution,
      CcgParser parser) {
    int ccgCategoryVarNum = ccgCategoryVar.getOnlyVariableNum();
    Assignment assignment = terminalVar.outcomeArrayToAssignment(terminalValue);
    if (!terminalVar.isValidAssignment(assignment)) {
      return 0;
    }

    Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
    int numEntries = 0;
    while (iterator.hasNext()) {
      Outcome bestOutcome = iterator.next();
      CcgCategory category = (CcgCategory) bestOutcome.getAssignment().getValue(ccgCategoryVarNum);

      // Look up how likely this syntactic entry is according to
      // any additional parameters in subclasses.
      double subclassProb = getCategoryWeight(originalTerminals, preprocessedTerminals, posTags, ccgWordList,
          featureVectors, spanStart, spanEnd, terminalValue, category);

      // Add all possible chart entries to the ccg chart.
      ChartEntry entry = parser.ccgCategoryToChartEntry(terminalValue, category, spanStart, spanEnd);
      chart.addChartEntryForSpan(entry, bestOutcome.getProbability() * subclassProb,
          spanStart, spanEnd, parser.getSyntaxVarType());
      numEntries++;
    }
    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
    return numEntries;
  }

  /**
   * Gets the possible lexicon entries for {@code wordSequence} from
   * {@code terminalDistribution}, a distribution over CCG categories
   * given word sequences.
   * 
   * @param wordSequence
   * @param terminalDistribution
   * @param terminalVar
   * @param ccgCategoryVar
   * @return
   */
  public static List<LexiconEntry> getLexiconEntriesFromFactor(List<String> wordSequence,
      DiscreteFactor terminalDistribution, VariableNumMap terminalVar, VariableNumMap ccgCategoryVar) {
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    if (terminalVar.isValidOutcomeArray(wordSequence)) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(wordSequence);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        lexiconEntries.add(new LexiconEntry(wordSequence, ccgCategory));
      }
    }
    return lexiconEntries;
  }

  protected static List<String> preprocessInput(List<String> terminals) {
    List<String> preprocessedTerminals = Lists.newArrayList();
    for (String terminal : terminals) {
      preprocessedTerminals.add(terminal.toLowerCase());
    }
    return preprocessedTerminals;
  }
}
