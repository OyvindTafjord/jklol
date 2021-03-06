package com.jayantkrish.jklol.ccg.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgExactInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.IoUtils;

public class TestSemanticParser extends AbstractCli {

  private OptionSpec<String> testData;
  private OptionSpec<String> model;

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    testData = parser.accepts("testData").withRequiredArg().ofType(String.class).required();
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    List<CcgExample> testExamples = TrainSemanticParser.readCcgExamples(
        options.valueOf(testData));
    System.out.println("Read " + testExamples.size() + " test examples");

    CcgParser parser = IoUtils.readSerializedObject(options.valueOf(model), CcgParser.class);

    int numCorrect = 0;
    int numParsed = 0;

    CcgInference inferenceAlg = new CcgExactInference(null, -1L, Integer.MAX_VALUE, 1);
    LogFunction log = new NullLogFunction();
    for (CcgExample example : testExamples) {
      CcgParse parse = inferenceAlg.getBestParse(parser, example.getSentence(), null, log);
      System.out.println("====");
      System.out.println("SENT: " + example.getSentence().getWords());
      if (parse != null) {
        Expression lf = parse.getLogicalForm().simplify();

        int correct = lf.simplify().functionallyEquals(example.getLogicalForm()) ? 1 : 0;

        System.out.println("PREDICTED: " + lf);
        System.out.println("TRUE: " + example.getLogicalForm());
        System.out.println("CORRECT: " + correct);

        numCorrect += correct;
        numParsed++;
      } else {
        System.out.println("NO PARSE");
      }
    }

    double precision = ((double) numCorrect) / numParsed;
    double recall = ((double) numCorrect) / testExamples.size();
    System.out.println("\nPrecision: " + precision);
    System.out.println("Recall: " + recall);
  }

  public static void main(String[] args) {
    new TestSemanticParser().run(args);
  }
}
