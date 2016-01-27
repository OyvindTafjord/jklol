package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.CpsTransform;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.KbestHeap;

public class ContinuationIncrementalEval implements IncrementalEval {
  private final AmbEval eval;
  private final Environment env;
  private final ExpressionSimplifier simplifier;
  
  private final ExpressionParser<SExpression> sexpParser;

  private static final String FINAL_CONTINUATION="final-continuation";
  private static final String QUEUE_CONTINUATIONS="queue-k";
  
  public ContinuationIncrementalEval(AmbEval eval, Environment env, ExpressionSimplifier simplifier) {
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
    this.simplifier = Preconditions.checkNotNull(simplifier);

    this.sexpParser = ExpressionParser.sExpression(eval.getSymbolTable());
  }
  
  public void evaluateContinuation(State state, KbestHeap<State> heap, CcgChart chart,
      CcgParser parser) {
    Environment env = state.continuationEnv;
    FinalContinuation finalContinuation = (FinalContinuation) ((WrappedBuiltinFunction)
        env.getValue(FINAL_CONTINUATION, eval.getSymbolTable())).getBaseFunction();
    QueueContinuations queueContinuations = (QueueContinuations) ((WrappedBuiltinFunction)
        env.getValue(QUEUE_CONTINUATIONS, eval.getSymbolTable())).getBaseFunction();
    AmbFunctionValue continuation = (AmbFunctionValue) state.continuation;
    
    System.out.println("evaluating: " + continuation);
    
    int finalNumValues = finalContinuation.denotations.size();
    int queueNumValues = queueContinuations.getContinuations().size();
    continuation.apply(Arrays.asList(state.diagram), env, null);
    
    System.out.println("num final: " + finalContinuation.denotations.size());
    
    for (int i = finalNumValues; i < finalContinuation.denotations.size(); i++) {
      Object denotation = finalContinuation.denotations.get(i);
      // TODO
      double prob = 1.0;
      IncrementalEval.queueState(denotation, prob, state, heap, chart, parser);
    }
    
    List<Object> continuations = queueContinuations.getContinuations();
    List<Object> diagrams = queueContinuations.getDiagrams();
    for (int i = queueNumValues; i < continuations.size(); i++) {
      // TODO
      double prob = 1.0;
      
      State next = new State(state.stack, diagrams.get(i), continuations.get(i), null,
          env, prob);
      heap.offer(next, next.totalProb);
    }
  }

  public Environment getEnvironment(State currentState) {
    Environment continuationEnv = Environment.extend(env);
    return continuationEnv;
  }
  
  public AmbFunctionValue parseToContinuation(GroundedCcgParse parse, Environment env) {
    Expression2 lf = parse.getUnevaluatedLogicalForm(env, eval.getSymbolTable());
    lf = simplifier.apply(lf);
    Expression2 cpsLf = simplifier.apply(CpsTransform.apply(lf, Expression2.constant(FINAL_CONTINUATION)));

    env.bindName(FINAL_CONTINUATION, new WrappedBuiltinFunction(new FinalContinuation()),
        eval.getSymbolTable());
    env.bindName(QUEUE_CONTINUATIONS, new WrappedBuiltinFunction(new QueueContinuations()),
        eval.getSymbolTable());

    System.out.println(cpsLf);
    
    SExpression cpsSexp = sexpParser.parse(cpsLf.toString());
    EvalResult evalResult = eval.eval(cpsSexp, env, null);

    Preconditions.checkState(evalResult.getValue() instanceof AmbFunctionValue,
        "Expected AmbFunctionValue, Got: %s", evalResult.getValue());
    return (AmbFunctionValue) evalResult.getValue();
  }
  
  public boolean isEvaluatable(HeadedSyntacticCategory syntax) {
    return syntax.isAtomic();
  }

  private static class QueueContinuations implements FunctionValue {
    private final List<Object> continuations;
    private final List<Object> diagrams;

    public QueueContinuations() {
      this.continuations = Lists.newArrayList();
      this.diagrams = Lists.newArrayList();
    }

    @Override
    public Object apply(List<Object> args, Environment env) {
      LispUtil.checkArgument(args.size() == 2);
      AmbFunctionValue continuation = (AmbFunctionValue) args.get(0);
      
      List<Object> nextDiagrams = ConsValue.consListToList(args.get(1));
      System.out.println("queue: " + continuation + " " + nextDiagrams);
      for (Object diagram : nextDiagrams) {
        continuations.add(continuation.apply(Arrays.asList(diagram), env, null));
        diagrams.add(diagram);
      }
      
      return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, Environment env2) {
          return ConstantValue.NIL;
        }
      });
    }
    
    public List<Object> getContinuations() {
      return continuations;
    }
    
    public List<Object> getDiagrams() {
      return diagrams;
    }
  }

  private static class FinalContinuation implements FunctionValue {
    public final List<Object> denotations;
    public final List<Object> diagrams;
    
    public FinalContinuation() {
      this.denotations = Lists.newArrayList();
      this.diagrams = Lists.newArrayList();
    }

    public Object apply(List<Object> args1, Environment env1) {
      LispUtil.checkArgument(args1.size() == 1);
      Object denotation = args1.get(0);
      
      System.out.println("final denotation: " + denotation);

      return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, Environment env2) {
          LispUtil.checkArgument(args2.size() == 1);
          Object diagram = args2.get(0);
          
          denotations.add(denotation);
          diagrams.add(diagram);

          System.out.println("final diagram: " + diagram);
          
          return ConstantValue.NIL;
        }
      });
    }
  }
}
