package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.ccg.data.CcgExampleFormat;
import com.jayantkrish.jklol.ccg.data.CcgSyntaxTreeFormat;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.data.DataFormat;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

/**
 * Regression tests for training CCG parsers from dependency
 * structures and syntactic trees. This test encompasses
 * {@link CcgParser}, {@link ParametricCcgParser},
 * {@link CcgLoglikelihoodOracle} and {@link CcgPerceptronOracle}.
 * 
 * @author jayant
 */
public class CcgTrainingTest extends TestCase {

  private static final String[] lexicon = {
      "block,N{0},(lambda x (pred:block x)),0 pred:block", 
      "object,N{0},(lambda x (pred:object x)),0 pred:object",
      "red,(N{1}/N{1}){0},(lambda $1 (lambda x (and ($1 x) (pred:red x)))),0 pred:red,pred:red 1 1",
      "green,(N{1}/N{1}){0},(lambda $1 (lambda x (and ($1 x) (pred:green x)))),0 pred:green,pred:green 1 1",
      "green,N{0},(lambda x (pred:green x)),0 pred:green", 
      "the,(N{1}/N{1}){0},(lambda $1 $1),0 the", 
      "a,(N{1}/N{1}){0},(lambda $1 $1),0 the",
      "near,((N{1}\\N{1}){0}/N{2}){0},(lambda $2 $1 (lambda x (exists y (and ($1 x) (pred:close x y) ($2 y))))),0 pred:close,pred:close 1 1,pred:close 2 2",
      "near,((N{1}\\N{1}){0}/N{2}){0},(lambda $2 $1 (lambda x (exists y (and ($1 x) (pred:near x y) ($2 y))))),0 pred:near,pred:near 1 1,pred:near 2 2",
      "near,((S{1}/(S{1}\\N{0}){1}){0}/N{2}){0},(lambda $2 $1 ($1 (lambda x (exists y (and (pred:near x y) ($2 y)))))),0 pred:near,pred:near 2 2",
      "near,(PP{0}/N{1}){0},(lambda $1 $1),0 pred:near,pred:near 2 1",
      "kinda,((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},(lambda $1 (lambda x ((pred:almost $1) x))),0 pred:almost,pred:almost 1 2",
      "is,((S{0}\\N{1}){0}/N{2}){0},(lambda $2 $1 (exists x y (and ($1 x) (pred:equals x y) ($2 y)))),0 pred:equals,pred:equals 1 1,pred:equals 2 2",
      "\",\",((N{1}\\N{1}){0}/N{2}){0},(lambda $1 $2 (lambda x (and ($1 x) ($2 x)))),\"0 ,\",\", 1 1\",\", 2 2\"",
      "2,N{0},pred:num,0 NUM", 
      "2,(N{1}/N{1}){0},,0 NUM,NUM 1 1",
      "\"#\",(N{1}/N{1}){0},,0 #,# 1 1",
      "\"#\",((N{1}/N{1}){2}/(N{1}/N{1}){2}){0},,0 #,# 1 2",
      "foo,ABC{0},,0 foo", "foo,ABCD{0},,0 foo",
      "unk-jj,(N{1}/N{1}){0},,0 pred:unk-jj,pred:unk-jj 1 1",
      "unk-jj,N{0},,0 pred:unk-jj",
      "unk-jj,(PP{1}/N{1}){0},,0 pred:unk-jj,pred:unk-jj 1 1",
      "that,((N{1}\\N{1}){0}/(S{2}/N{1}){2}){0},,0 that,that 1 1,that 2 2"
  };

  private static final String[] trainingData = {
      "red block###pred:red (N{1}/N{1}){0} 0 1 pred:block 1######(lambda x (and (pred:red x) (pred:block x)))",
      "red green block###pred:red (N{1}/N{1}){0} 0 1 pred:block 2,pred:green (N{1}/N{1}){0} 1 1 pred:block 2######(lambda x (and (pred:red x) (pred:green x) (pred:block x)))",
      "red object near the green block###pred:red (N{1}/N{1}){0} 0 1 pred:object 1,pred:green (N{1}/N{1}){0} 4 1 pred:block 5,pred:near ((N{1}\\N{1}){0}/N{2}){0} 2 1 pred:object 1,pred:near ((N{1}\\N{1}){0}/N{2}){0} 2 2 pred:block 5######(lambda x (exists y (and (pred:red x) (pred:object x) (pred:near x y) (pred:green y) (pred:block y))))",
      "red block near the green block###pred:red (N{1}/N{1}){0} 0 1 pred:block 1,pred:green (N{1}/N{1}){0} 4 1 pred:block 5,pred:near ((N{1}\\N{1}){0}/N{2}){0} 2 1 pred:block 1,pred:near ((N{1}\\N{1}){0}/N{2}){0} 2 2 pred:block 5######(lambda x (exists y (and (pred:red x) (pred:block x) (pred:near x y) (pred:green y) (pred:block y))))",
      "the kinda red block###pred:red (N{1}/N{1}){0} 2 1 pred:block 3,pred:almost ((N{1}/N{1}){2}/(N{1}/N{1}){2}){0} 1 1 pred:red 2######(lambda x (and (pred:block x) ((pred:almost pred:red) x)))",
      "near the object is the red block###pred:near ((S{1}/(S{1}\\N{0}){1}){0}/N{2}){0} 0 2 pred:object 2,pred:equals ((S{0}\\N{1}){0}/N{2}){0} 3 1 pred:near 0,pred:equals ((S{0}\\N{1}){0}/N{2}){0} 3 2 pred:block 6,pred:red (N{1}/N{1}){0} 5 1 pred:block 6######(exists x y z (and (pred:object x) (pred:near y x) (pred:equals y z) (pred:red z) (pred:block z)))",
      "block , object###\", ((N{1}\\N{1}){0}/N{2}){0} 1 1 pred:block 0\",\", ((N{1}\\N{1}){0}/N{2}){0} 1 2 pred:object 2\"######(lambda x (and (pred:block x) (pred:object x)))",
  };

  private static final String[] trainingDataWithSyntax = {
    "the block is green###pred:equals ((S{0}\\N{1}){0}/N{2}){0} 2 1 pred:block 1,pred:equals ((S{0}\\N{1}){0}/N{2}){0} 2 2 pred:green 3###<S <N <(N/N) DT the> <N NN block>> <(S\\N) <(S\\N)/N VB is> <N NN green>>>",
    "block that block is###that ((N{1}\\N{1}){0}/(S{2}/N{1}){2}){0} 1 1 pred:block 0,that ((N{1}\\N{1}){0}/(S{2}/N{1}){2}){0} 1 2 pred:equals 3,pred:equals ((S{0}\\N{1}){0}/N{2}){0} 3 1 pred:block 2,pred:equals ((S{0}\\N{1}){0}/N{2}){0} 3 2 pred:block 0###"
        + "<N <N NN block> <(N\\N) <((N\\N)/(S/N)) NN that> <(S/N) <(S/(S\\N)_N NN block> <(S\\N)/N VB is>>>> ",
    "red block###pred:red (N{1}/N{1}){0} 0 1 pred:block 1###<N <(N/N) JJ red> <N NN block>>",
    "red green block###pred:red (N{1}/N{1}){0} 0 1 pred:block 2,pred:green (N{1}/N{1}){0} 1 1 pred:block 2###<N <(N/N) JJ red> <N <(N/N) JJ green> <N NN block>>>",
    "red block near the green block###pred:red (N{1}/N{1}){0} 0 1 pred:block 1,pred:green (N{1}/N{1}){0} 4 1 pred:block 5,pred:near ((N{1}\\N{1}){0}/N{2}){0} 2 1 pred:block 1,pred:near ((N{1}\\N{1}){0}/N{2}){0} 2 2 pred:block 5###"
        + "<N <N <(N/N) JJ red> <N NN block>> <N\\N <(N\\N)/N IN near> <N <N/N DT the> <N <(N/N) JJ green> <N NN block>>>>>",
    "# 2 block###\"# ((N{1}/N{1}){2}/(N{1}/N{1}){2}){0} 0 1 NUM 1\",\"NUM (N{1}/N{1}){0} 1 1 pred:block 2\"###<N <N/N <((N/N)/(N/N)) JJ #> <(N/N) JJ 2>> <N NN block>>",
    "foo######<ABCD NN foo>",
    "block######<N NN block>",
    "not_in_lexicon block###pred:unk-jj (N{1}/N{1}){0} 0 1 pred:block 1###<N <(N/N) JJ not_in_lexicon> <N NN block>>"
  };

  private static final String[] ruleArray = {"N{0} (S{1}/(S{1}\\N{0}){1}){1}", "ABC{0} ABCD{0}"};

  private DataFormat<CcgExample> exampleReader;
  private ParametricCcgParser family, wordSkipFamily;
  private List<CcgExample> trainingExamples;
  private List<CcgExample> trainingExamplesWithSyntax;
  private List<CcgExample> trainingExamplesSyntaxOnly;
  private List<CcgExample> trainingExamplesDepsOnly;
  private List<CcgExample> trainingExamplesLfOnly;
  private Set<String> posTags;

  private static final double TOLERANCE = 1e-10;

  public void setUp() {
    exampleReader = new CcgExampleFormat(new CcgSyntaxTreeFormat(), false);
    
    trainingExamples = Lists.newArrayList();
    for (int i = 0; i < trainingData.length; i++) {
      trainingExamples.add(exampleReader.parseFrom(trainingData[i]));
    }
    
    trainingExamplesLfOnly = Lists.newArrayList();
    for (CcgExample example : trainingExamples) {
      trainingExamplesLfOnly.add(new CcgExample(example.getSentence().removeSupertags(), null,
          null, example.getLogicalForm()));
    }
    
    trainingExamplesDepsOnly = Lists.newArrayList();
    for (CcgExample example : trainingExamples) {
      trainingExamplesDepsOnly.add(new CcgExample(example.getSentence().removeSupertags(),
          example.getDependencies(), null, null));
    }

    trainingExamplesWithSyntax = Lists.newArrayList();
    for (int i = 0; i < trainingDataWithSyntax.length; i++) {
      trainingExamplesWithSyntax.add(exampleReader.parseFrom(trainingDataWithSyntax[i]));
    }
    posTags = CcgExample.getPosTagVocabulary(trainingExamplesWithSyntax);
    posTags.add(ParametricCcgParser.DEFAULT_POS_TAG);

    trainingExamplesSyntaxOnly = Lists.newArrayList();
    for (CcgExample syntaxExample : trainingExamplesWithSyntax) {
      trainingExamplesSyntaxOnly.add(new CcgExample(syntaxExample.getSentence().removeSupertags(), 
          null, syntaxExample.getSyntacticParse(), null));
    }

    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory(DefaultCcgFeatureFactory.getPosFeatureGenerator(trainingExamplesWithSyntax), true),
        posTags, true, null, false, false);
    
    wordSkipFamily = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory(DefaultCcgFeatureFactory.getPosFeatureGenerator(trainingExamplesWithSyntax), true),
        posTags, true, null, true, false);
  }
  
  public void testSyntacticChartFilter1() {
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    CcgExample example = trainingExamplesSyntaxOnly.get(0);

    SyntacticChartCost filter = SyntacticChartCost.createAgreementCost(example.getSyntacticParse());
    List<CcgParse> correctParses = parser.beamSearch(example.getSentence(), 10,
        filter, new DefaultLogFunction(), -1, Integer.MAX_VALUE, 1);

    for (CcgParse correct : correctParses) {
      System.out.println(correct);
    }
    
    assertEquals(1, correctParses.size());
  }
  
  public void testSyntacticChartFilter2() {
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    CcgExample example = trainingExamplesSyntaxOnly.get(1);
    System.out.println("expected: " + example.getSyntacticParse());
    List<CcgParse> parses = parser.beamSearch(example.getSentence(), 10);
    for (CcgParse parse : parses) {
      System.out.println(parse);
    }

    SyntacticChartCost filter = SyntacticChartCost.createAgreementCost(example.getSyntacticParse());
    List<CcgParse> correctParses = parser.beamSearch(example.getSentence(),
        10, filter, new DefaultLogFunction(), -1, Integer.MAX_VALUE, 1);

    for (CcgParse correct : correctParses) {
      System.out.println(correct);
    }
    
    assertEquals(1, correctParses.size());
  }
  
  public void testSyntacticChartFilter3() {
    CcgExample example = exampleReader.parseFrom("foo######<ABCD_ABC NN foo>");
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    
    List<CcgParse> parses = parser.beamSearch(example.getSentence(), 10);
    for (CcgParse parse : parses) {
      System.out.println("parse: " + parse);
    }

    SyntacticChartCost filter = SyntacticChartCost.createAgreementCost(example.getSyntacticParse());
    List<CcgParse> correctParses = parser.beamSearch(example.getSentence(),
        10, filter, new DefaultLogFunction(), -1, Integer.MAX_VALUE, 1);

    for (CcgParse correct : correctParses) {
      System.out.println(correct);
    }

    assertEquals(2, correctParses.size());
  }

  public void testParseFromLexicon() {
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    List<CcgParse> parses = beamSearch(parser, Arrays.asList("block"), 10);
    assertEquals(2, parses.size());

    parses = beamSearch(parser, Arrays.asList("near"), 10);
    assertEquals(4, parses.size());

    parses = beamSearch(parser, Arrays.asList(","), 10);
    assertEquals(1, parses.size());

    parses = beamSearch(parser, Arrays.asList("#"), 10);
    assertEquals(2, parses.size());

    parses = beamSearch(parser, Arrays.asList("#", "2", "block"), 10);
    // 3 parses that produce a noun at the root, times 2 because
    // of the N -> (S/(S\N)) type-raising rule.
    assertEquals(6, parses.size());
  }

  public void testTrainLoglikelihoodDependenciesOnly() {
    CcgParser parser = trainLoglikelihoodParser(family, trainingExamplesDepsOnly);
    assertZeroDependencyError(parser, trainingExamples);
    // Check that the resulting parameters are sensible.
    assertEquals(1.0, beamSearch(parser, Arrays.asList("red"), 10).get(0).getSubtreeProbability(), 0.000001);
  }
  
  public void testTrainLoglikelihoodLogicalFormOnly() {
    CcgParser parser = trainLoglikelihoodParser(family, trainingExamplesLfOnly);
    assertZeroDependencyError(parser, trainingExamples);
    // Check that the resulting parameters are sensible.
    assertEquals(1.0, beamSearch(parser, Arrays.asList("red"), 10).get(0).getSubtreeProbability(), 0.000001);
  }

  public void testTrainLoglikelihoodWithSyntax() {
    CcgParser parser = trainLoglikelihoodParser(family, trainingExamplesWithSyntax);
    assertZeroDependencyError(parser, trainingExamplesWithSyntax);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainLoglikelihoodSyntaxOnly() {
    CcgParser parser = trainLoglikelihoodParser(family, trainingExamplesSyntaxOnly);
    assertTrainedParserUsesSyntax(parser);

    List<CcgParse> parses = filterNonAtomicParses(beamSearch(parser, 
        Arrays.asList("object", "near", "block"), 10));
    // The two parses differ only in the semantics of near, which is
    // unconstrained in the training data.
    assertEquals(2, parses.size());
    assertEquals(parses.get(0).getSubtreeProbability(), parses.get(1).getSubtreeProbability(), TOLERANCE);
  }

  public void testTrainPerceptronLogicalFormOnly() {
    CcgParser parser = trainPerceptronParser(family, trainingExamplesLfOnly, false, false);
    assertZeroDependencyError(parser, trainingExamples);
  }

  public void testTrainPerceptronWithSyntax() {
    CcgParser parser = trainPerceptronParser(family, trainingExamplesWithSyntax, false, false);
    assertZeroDependencyError(parser, trainingExamplesWithSyntax);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainPerceptronSyntaxOnly() {
    CcgParser parser = trainPerceptronParser(family, trainingExamplesSyntaxOnly, false, false);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainPerceptronSyntaxOnlyExactInference() {
    CcgParser parser = trainPerceptronParser(family, trainingExamplesSyntaxOnly, true, false);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainMaxMarginWithSyntax() {
    CcgParser parser = trainPerceptronParser(family, trainingExamplesWithSyntax, false, true);
    assertZeroDependencyError(parser, trainingExamplesWithSyntax);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainMaxMarginSyntaxOnly() {
    CcgParser parser = trainPerceptronParser(family, trainingExamplesSyntaxOnly, false, true);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainMaxMarginSyntaxOnlyExactInference() {
    CcgParser parser = trainPerceptronParser(family, trainingExamplesSyntaxOnly, true, true);
    assertTrainedParserUsesSyntax(parser);
  }

  public void testTrainWordSkip() {
    CcgParser parser = trainLoglikelihoodParser(wordSkipFamily, trainingExamplesWithSyntax);
    assertZeroDependencyError(parser, trainingExamplesWithSyntax);
  }

  private CcgParser trainLoglikelihoodParser(ParametricCcgParser family, List<CcgExample> examples) {
    CcgLoglikelihoodOracle oracle = new CcgLoglikelihoodOracle(family, 100);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10, 1, 1,
        true, false, 0.1, new DefaultLogFunction());

    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), examples);
    CcgParser parser = family.getModelFromParameters(parameters);
    return parser;
  }

  private CcgParser trainPerceptronParser(ParametricCcgParser family,
      List<CcgExample> examples, boolean exactInference, boolean maxMargin) {
    CcgInference inferenceAlg = null;
    if (exactInference) {
      inferenceAlg = new CcgExactInference(null, -1, Integer.MAX_VALUE, 1);
    } else {
      inferenceAlg = new CcgBeamSearchInference(null, 100, -1, Integer.MAX_VALUE, 1, true);
    }
    CcgPerceptronOracle oracle = new CcgPerceptronOracle(
        family, inferenceAlg, maxMargin ? 1.0 : 0.0);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(100,
        1, 1, false, true, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    CcgParser parser = family.getModelFromParameters(parameters);
    return parser;
  }

  private void assertTrainedParserUsesSyntax(CcgParser parser) {
    List<CcgParse> parses = filterNonAtomicParses(beamSearch(parser, Arrays.asList("the", "red", "block"), 
        Arrays.asList("DT", "NN", "NN"), 10));

    // Check that syntactic information is being used in the learned
    // parser.
    assertEquals(2, parses.size());
    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
    CcgParse bestParse = parses.get(0);
    // Best parse should be <N <N/N> <N <N/N> <N>>>
    assertTrue(bestParse.getLeft().isTerminal());
    assertTrue(bestParse.getSubtreeProbability() > parses.get(1).getSubtreeProbability() + 0.000001);
    
    // Check that weights are being learned for unary rules.
    parses = beamSearch(parser, Arrays.asList("foo"), Arrays.asList("NN"), 100);
    assertEquals(3, parses.size());
    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
    assertNull(parses.get(0).getUnaryRule());
    assertEquals("ABCD", parses.get(0).getSyntacticCategory().getValue());
    assertTrue(parses.get(0).getSubtreeProbability() > parses.get(1).getSubtreeProbability() + 0.000001);

    parses = beamSearch(parser, Arrays.asList("block"), Arrays.asList("NN"), 100);
    assertEquals(2, parses.size());
    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
    assertTrue(parses.get(0).getSyntacticCategory().isAtomic());
    assertTrue(parses.get(0).getSubtreeProbability() > parses.get(1).getSubtreeProbability() + 0.000001);
    
    // Check that backoff to POS tags works properly.
    parses = beamSearch(parser, Arrays.asList("another_new_word", "block"), Arrays.asList("JJ", "NN"), 100);
    for (CcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse);
    }
    assertEquals(3, parses.size());
    assertTrue(parses.get(0).getSyntacticCategory().equals(SyntacticCategory.parseFrom("N")));
  }

  private void assertZeroDependencyError(CcgParser parser,
      Iterable<CcgExample> examples) {
    // Test that zero training error is achieved.
    for (CcgExample example : examples) {
      List<CcgParse> parses = beamSearch(parser, example.getSentence().getWords(), example.getSentence().getPosTags(), 100);
      CcgParse bestParse = parses.get(0);

      System.out.println(example.getSentence().getWords() + " " + bestParse);
      System.out.println(example.getDependencies());
      System.out.println(bestParse.getAllDependencies());
      assertEquals(example.getDependencies(), Sets.newHashSet(bestParse.getAllDependencies()));
    }
  }

  private List<CcgParse> beamSearch(CcgParser parser, List<String> words,
      int beamSize) {
    return parser.beamSearch(ListSupertaggedSentence.createWithUnobservedSupertags(words,
        Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG)), beamSize);
  }

  private List<CcgParse> beamSearch(CcgParser parser, List<String> words,
      List<String> posTags, int beamSize) {
    return parser.beamSearch(ListSupertaggedSentence.createWithUnobservedSupertags(words, 
        posTags), beamSize);
  }

  private List<CcgParse> filterNonAtomicParses(List<CcgParse> parses) {
    List<CcgParse> atomicParses = Lists.newArrayList();
    for (CcgParse parse : parses) {
      if (parse.getSyntacticCategory().isAtomic()) {
        atomicParses.add(parse);
      }
    }
    return atomicParses;
  }
}
