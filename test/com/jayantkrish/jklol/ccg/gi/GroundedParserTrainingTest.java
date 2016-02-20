package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.HashingFeatureVectorGenerator;
import com.jayantkrish.jklol.util.IndexedList;

public class GroundedParserTrainingTest {

    private static final String[] lexicon = {
    "1,N{0},1,0 num",
    "2,N{0},2,0 num",
    "3,N{0},3,0 num",
    "4,N{0},4,0 num",
    "+,((N{1}\\N{1}){0}/N{2}){0},(lambda (x y) (+-k x y)),0 +",
    "1_or_2,N{0},(amb-k (list-k 1 2)),0 num",
    "x,N{0},(resolve-k ~\"x~\")",
//    "1_or_2,N{0},1,0 num",
//    "1_or_2,N{0},2,0 num",
  };
  
  private static final String[] predicateDefs = {
    "(define list-k (k . args) (k args))",
    "(define +-k (k x y) (k (+ x y)))",
    "(define map (f l) (if (nil? l) l (cons (f (car l)) (map f (cdr l)))))",
    "(define alist-put (name value l) (if (nil? l) (list (list name value)) (if (= name (car (car l))) (cons (list name value) (cdr l)) (cons (car l) (alist-put name value (cdr l))))))",
    "(define alist-get (name l) (if (nil? l) l (if (= name (car (car l))) (car (cdr (car l))) (alist-get name (cdr l)))))",
    "(define alist-cput (name value l) (let ((old (alist-get name l))) (if (nil? old) (alist-put name value l) l)))",

    "(define get-k (k name) (lambda (world) ((k (alist-get name world)) world)))",
    "(define put-k (k name value) (lambda (world) ((k value) (alist-put name value world))))",
    "(define cput-k (k name value) (lambda (world) (let ((next-world (alist-cput name value world))) ((k (alist-get name next-world)) next-world))))",
    "(define possible-values (list (list \"x\" (list 1 2)) (list \"y\" (list 3 4))))",
  };

  private static final String[] evalDefs = {
    "(define amb-k (k l) (lambda (world) ((queue-k k l) (map (lambda (x) world) l)) ))",
    "(define score-k (k v tag) (lambda (world) ((queue-k k (list v) (list tag)) (list world)) ))",
    "(define resolve-k (k name) (lambda (world) (let ((v (alist-get name world))) (if (not (nil? v)) ((k v) world) ((amb-k (lambda (v) (cput-k k name v)) (alist-get name possible-values)) world)))))",
  };

  private static final String[] ruleArray = {"DUMMY{0} BLAH{0}"};

  private ParametricGroundedParser family;
  
  private ExpressionParser<SExpression> sexpParser;
  private ExpressionParser<Expression2> exp2Parser;
  private Environment env;
  private AmbEval ambEval;
  
  private static final double TOLERANCE = 1e-6;
  
  public void setUp() {
    ParametricCcgParser ccgFamily = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon),
        Collections.emptyList(), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory(false, false), null, true, null, false);
    
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    ambEval = new AmbEval(symbolTable);
    env = AmbEval.getDefaultEnvironment(symbolTable);
    sexpParser = ExpressionParser.sExpression(symbolTable);
    exp2Parser = ExpressionParser.expression2();
    SExpression predicateProgram = sexpParser.parse("(begin " + Joiner.on("\n").join(predicateDefs) + ")");
    ambEval.eval(predicateProgram, env, null);
    
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();
    
    String evalDefString = "(begin " + Joiner.on(" ").join(evalDefs) + ")";
    SExpression defs = sexpParser.parse(evalDefString);
    ContinuationIncEval eval = new ContinuationIncEval(ambEval, env, simplifier, defs);
    
    FeatureVectorGenerator<StateFeatures> featureVectorGen =
        new HashingFeatureVectorGenerator<StateFeatures>(100, new StateFeatureGen());
    ParametricIncEval evalFamily = ParametricContinuationIncEval.fromFeatureGenerator(
        featureVectorGen, eval);

    family = new ParametricGroundedParser(ccgFamily, evalFamily);
  }

  /*
  public void testTraining() {
    List<ValueGroundedParseExample> examples = parseExamples(expressions, labels);

    GroundedParserLoglikelihoodOracle oracle = new GroundedParserLoglikelihoodOracle(family, 100);
    GradientOptimizer trainer = new Lbfgs(100, 10, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    GroundedParser parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));

    assertDistributionEquals(trainedEval, "(resolve-k \"x\")", initialDiagram,
        new Object[] {1, 2}, new double[] {0.25, 0.75});
    assertDistributionEquals(trainedEval, "(resolve-k \"y\")", initialDiagram,
        new Object[] {3, 4}, new double[] {0.5, 0.5});
    assertDistributionEquals(trainedEval,
        "(score-k (+-k (amb-k (list-k 0 1)) (resolve-k \"x\")) \"foo\")", initialDiagram,
        new Object[] {1, 2, 3}, new double[] {0.0, 1.0, 0.0});
  }
  */

  private static class StateFeatureGen implements FeatureGenerator<StateFeatures, String> {
    private static final long serialVersionUID = 1L;

    @Override
    public Map<String, Double> generateFeatures(StateFeatures item) {
      Map<String, Double> features = Maps.newHashMap();
      Map<Object, Object> oldBindings = getBindings(item.getPrev().getDiagram());
      Map<Object, Object> newBindings = getBindings(item.getDiagram());

      for (Object newKey : newBindings.keySet()) {
        if (!oldBindings.containsKey(newKey)) {
          features.put(newKey + "=" + newBindings.get(newKey), 1.0);
        }
      }

      if (item.getOtherArg() != null) {
        // Denotation features.
        features.put("denotation_" + item.getOtherArg() + "=" + item.getDenotation(), 1.0);
      }

      return features;
    }

    private Map<Object, Object> getBindings(Object diagram) {
      Map<Object, Object> bindings = Maps.newHashMap();
      List<Object> bindingList = ConsValue.consListToList(diagram);
      for (Object o : bindingList) {
        List<Object> elt = ConsValue.consListToList(o);
        bindings.put(elt.get(0), elt.get(1));
      }
      return bindings;
    }
  }
}
