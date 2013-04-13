package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Regression tests for training compositional vector space models.
 * 
 * @author jayantk
 */
public class CvsmTrainingTest extends TestCase {

  private static final String[] lexicon = {
      "block,N{0},vec:block,0 block",
      "table,N{0},vec:table,0 table",
      "red,(N{1}/N{1}){0},(lambda $1 (logit (mul mat:red $1))),0 red,red 1 1",
      "on,((N{1}\\N{1}){0}/N{2}){0},(lambda $2 $1 (logit (plus (mul mat:on:1 $1) (mul mat:on:2 $2)))),0 on,on 1 1,on 2 2"
  };

  private static final String[] rules = {
      "FOO{0} FOO{0}"
  };

  private static final String[] vectorNames = {
      "vec:block",
      "vec:table",
      "vec:distribution",
      "vec:logistic"
  };

  private static final String[] matrixNames = {
      "mat:red",
      "mat:green",
      "weights:logreg"
  };

  private static final String[] tensor3Names = {
      "t3:on"
  };

  private static final String[] affineExamples = {
      "vec:block",
      "vec:table",
      "(op:sum_out (op:hadamard mat:red vec:block) 0)",
      "(op:sum_out (op:hadamard mat:red vec:table) 0)",
      "(op:sum_out (op:hadamard (op:sum_out (op:hadamard t3:on vec:block) 0) vec:table) 0)",
  };

  private static final double[][] affineTargets = {
      { 1, 1, 0 },
      { 0, 0, 1 },
      { 3, 1, -1 },
      { 2, 1, 2 },
      { 1, 2, 3 },
  };

  private static final String[] softmaxExamples = {
      "vec:block",
      "vec:table",
      "(op:softmax vec:distribution)",
      "(op:softmax (op:sum_out (op:hadamard weights:logreg vec:block) 0))",
      "(op:softmax (op:sum_out (op:hadamard weights:logreg vec:table) 0))",
  };

  private static final double[][] softmaxTargets = {
      { 1, 1, 0 },
      { 0, 0, 1 },
      { 0.25, 0.5, 0.25 },
      { 0.9, 0, 0.1 },
      { 0, 0.5, 0.5 },
  };

  private static final String[] logisticExamples = {
      "(op:logistic vec:logistic)"
  };

  private static final double[][] logisticTargets = {
      { 1.0, 0.75, 0.2 },
  };

  private static final String[] tprodExamples = {
      "vec:block",
      "vec:table",
      "(op:sum_out (op:hadamard (op:sum_out (op:hadamard t3:on vec:block) 0) vec:block) 0)",
      "(op:sum_out (op:hadamard (op:sum_out (op:hadamard t3:on vec:block) 0) vec:table) 0)",
      "(op:sum_out (op:hadamard (op:sum_out (op:hadamard t3:on vec:table) 0) vec:block) 0)",
      "(op:sum_out (op:hadamard (op:sum_out (op:hadamard t3:on vec:table) 0) vec:table) 0)",
  };

  private static final double[][] tprodTargets = {
      { 0, 1, 0 },
      { 1, 0, 0 },
      { 0, 0, 0 },
      { 1, 0, 0 },
      { 1, 0, 0 },
      { 0, 0, 0 },
  };

  private static final int NUM_DIMS = 3;

  private ParametricCcgParser family;
  private CcgParser parser;

  private CvsmFamily cvsmFamily;

  public void setUp() {
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon), Arrays.asList(rules),
        null, null, true, null, false);
    parser = family.getModelFromParameters(family.getNewSufficientStatistics());

    DiscreteVariable dimType = DiscreteVariable.sequence("seq", NUM_DIMS);
    VariableNumMap vectorVars = VariableNumMap.singleton(0, "dim-0", dimType);
    VariableNumMap matrixVars = new VariableNumMap(Ints.asList(0, 1),
        Arrays.asList("dim-0", "dim-1"), Arrays.asList(dimType, dimType));
    VariableNumMap t3Vars = new VariableNumMap(Ints.asList(0, 1, 2),
        Arrays.asList("dim-0", "dim-1", "dim-2"), Arrays.asList(dimType, dimType, dimType));

    IndexedList<String> tensorNames = IndexedList.create();
    List<VariableNumMap> tensorDims = Lists.newArrayList();
    for (int i = 0; i < vectorNames.length; i++) {
      tensorNames.add(vectorNames[i]);
      tensorDims.add(vectorVars);
    }

    for (int i = 0; i < matrixNames.length; i++) {
      tensorNames.add(matrixNames[i]);
      tensorDims.add(matrixVars);
    }

    for (int i = 0; i < tensor3Names.length; i++) {
      tensorNames.add(tensor3Names[i]);
      tensorDims.add(t3Vars);
    }

    cvsmFamily = new CvsmFamily(tensorNames, tensorDims);
  }

  public void testParse() {
    List<CcgParse> parses = parser.beamSearch(Arrays.asList("red", "block", "on", "table"), 10);

    System.out.println(parses.get(0).getLogicalForm().simplify());
  }

  public void testCvsmAffineTraining() {
    runCvsmTrainingTest(parseExamples(affineExamples, affineTargets), cvsmFamily);
  }

  public void testCvsmSoftmaxTraining() {
    runCvsmTrainingTest(parseExamples(softmaxExamples, softmaxTargets), cvsmFamily);
  }

  public void testCvsmLogisticTraining() {
    runCvsmTrainingTest(parseExamples(logisticExamples, logisticTargets), cvsmFamily);
  }

  public void testCvsmTensorTraining() {
    List<CvsmExample> cvsmTensorExamples = parseExamples(tprodExamples, tprodTargets);
    runCvsmTrainingTest(cvsmTensorExamples, cvsmFamily);
  }

  private static void runCvsmTrainingTest(List<CvsmExample> cvsmExamples, CvsmFamily cvsmFamily) {
    CvsmLoglikelihoodOracle oracle = new CvsmLoglikelihoodOracle(cvsmFamily, true);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(
        1000, 1, 1.0, true, 0.0001, new DefaultLogFunction(1, false));
    SufficientStatistics parameters = trainer.train(oracle,
        cvsmFamily.getNewSufficientStatistics(), cvsmExamples);

    System.out.println(cvsmFamily.getParameterDescription(parameters));

    Cvsm cvsm = cvsmFamily.getModelFromParameters(parameters);

    for (CvsmExample example : cvsmExamples) {
      CvsmTree tree = cvsm.getInterpretationTree(example.getLogicalForm());
      Tensor predictedValue = tree.getValue();

      Tensor deltas = predictedValue.elementwiseAddition(example.getTargetDistribution().elementwiseProduct(-1.0));
      double squareLoss = deltas.innerProduct(deltas).getByDimKey();
      System.out.println(example.getLogicalForm() + " loss: " + squareLoss);
      assertTrue(squareLoss <= 0.1);
    }
  }

  private static List<CvsmExample> parseExamples(String[] examples, double[][] targets) {
    Preconditions.checkArgument(examples.length == targets.length);
    ExpressionParser exp = new ExpressionParser();

    List<CvsmExample> cvsmExamples = Lists.newArrayList();
    for (int i = 0; i < examples.length; i++) {
      Tensor target = new DenseTensor(new int[] { 0 }, new int[] { NUM_DIMS }, targets[i]);

      cvsmExamples.add(new CvsmExample(exp.parseSingleExpression(examples[i]), target, null));
    }

    return cvsmExamples;
  }
}
