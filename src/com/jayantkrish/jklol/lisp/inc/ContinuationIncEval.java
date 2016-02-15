package com.jayantkrish.jklol.lisp.inc;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.gi.GroundedCcgParse;
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

/**
 * Incremental evaluation oracle for a nondeterministic lambda
 * calculus that represents nondeterminism using continuations. 
 * A program in this lambda calculus can have many possible futures
 * (each of which is represented by a continuation); these continuations
 * are queued on to the {@code GroundedParser} stack and searched
 * jointly with parses of the sentence.
 * 
 * @author jayantk
 *
 */
public class ContinuationIncEval extends AbstractIncEval {
  protected final AmbEval eval;
  protected final Environment env;
  protected final ExpressionSimplifier simplifier;
  
  protected final ExpressionParser<SExpression> sexpParser;
  protected final SExpression defs;

  public static final String FINAL_CONTINUATION = "final-continuation";
  public static final String QUEUE_CONTINUATIONS = "queue-k";
  
  public ContinuationIncEval(AmbEval eval, Environment env, ExpressionSimplifier simplifier,
      SExpression defs) {
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
    this.simplifier = Preconditions.checkNotNull(simplifier);

    this.sexpParser = ExpressionParser.sExpression(eval.getSymbolTable());
    this.defs = defs;
  }
  
  public AmbEval getEval() {
    return eval;
  }
  
  /**
   * Gets the initial environment for evaluation. Note that
   * this environment does not include bindings for
   * {@code FINAL_CONTINUATION} or {@code QUEUE_CONTINUATIONS}. 
   * 
   * @return
   */
  public Environment getEnv() {
    return env;
  }
  
  public ExpressionSimplifier getSimplifier() {
    return simplifier;
  }
  
  public SExpression getDefs() {
    return defs;
  }

  @Override
  public void evaluateContinuation(IncEvalState state, List<IncEvalState> resultQueue) {
    Environment env = state.getEnvironment();
    FinalContinuation finalContinuation = (FinalContinuation) ((WrappedBuiltinFunction)
        env.getValue(FINAL_CONTINUATION, eval.getSymbolTable())).getBaseFunction();
    QueueContinuations queueContinuations = (QueueContinuations) ((WrappedBuiltinFunction)
        env.getValue(QUEUE_CONTINUATIONS, eval.getSymbolTable())).getBaseFunction();
    AmbFunctionValue currentContinuation = (AmbFunctionValue) state.getContinuation();
    
    // System.out.println("evaluating: " + state.getContinuation());
    // System.out.println("diagram: " + state.getDiagram());
    
    int finalNumValues = finalContinuation.denotations.size();
    int queueNumValues = queueContinuations.getContinuations().size();
    currentContinuation.apply(Arrays.asList(state.getDiagram()), env, null);
    
    for (int i = finalNumValues; i < finalContinuation.denotations.size(); i++) {
      Object denotation = finalContinuation.denotations.get(i);
      Object diagram = finalContinuation.diagrams.get(i);
      IncEvalState next = nextState(state, null, Environment.extend(env), denotation, diagram);
      resultQueue.add(next);
    }

    List<Object> continuations = queueContinuations.getContinuations();
    List<Object> denotations = queueContinuations.getDenotations();
    List<Object> diagrams = queueContinuations.getDiagrams();
    for (int i = queueNumValues; i < continuations.size(); i++) {
      Object continuation = continuations.get(i);
      Object denotation = denotations.get(i);
      Object diagram = diagrams.get(i);
      IncEvalState next = nextState(state, continuation, Environment.extend(env), denotation, diagram);
      resultQueue.add(next);
    }
  }

  /**
   * Override this method in subclasses to implement scoring of search states
   * and accumulating features.
   * 
   * @return
   */
  protected IncEvalState nextState(IncEvalState prev, Object continuation, Environment env, Object denotation,
      Object diagram) {
    return new IncEvalState(continuation, env, denotation, diagram, 1.0 * prev.getProb(), null);
  }

  @Override
  public Environment getEnvironment() {
    Environment continuationEnv = Environment.extend(env);
    continuationEnv.bindName(FINAL_CONTINUATION, new WrappedBuiltinFunction(new FinalContinuation()),
        eval.getSymbolTable());
    continuationEnv.bindName(QUEUE_CONTINUATIONS, new WrappedBuiltinFunction(new QueueContinuations()),
        eval.getSymbolTable());

    if (defs != null) {
      eval.eval(defs, continuationEnv, null);
    }

    return continuationEnv;
  }
  
  @Override
  public AmbFunctionValue parseToContinuation(GroundedCcgParse parse, Environment env) {
    Expression2 lf = parse.getUnevaluatedLogicalForm(env, eval.getSymbolTable());
    return lfToContinuation(lf, env);
  }
  
  @Override
  public AmbFunctionValue lfToContinuation(Expression2 lf, Environment env) {
    lf = simplifier.apply(lf);
    // System.out.println(lf);
    Expression2 cpsLf = simplifier.apply(CpsTransform.apply(lf, Expression2.constant(FINAL_CONTINUATION)));
    // System.out.println(cpsLf);
    
    SExpression cpsSexp = sexpParser.parse(cpsLf.toString());
    EvalResult evalResult = eval.eval(cpsSexp, env, null);
    // System.out.println(evalResult.getValue());

    Preconditions.checkState(evalResult.getValue() instanceof AmbFunctionValue,
        "Expected AmbFunctionValue, Got: %s", evalResult.getValue());
    return (AmbFunctionValue) evalResult.getValue();
  }
  
  @Override
  public boolean isEvaluatable(HeadedSyntacticCategory syntax) {
    return syntax.isAtomic();
  }

  public static class QueueContinuations implements FunctionValue {
    private final List<Object> continuations;
    private final List<Object> denotations;
    private final List<Object> diagrams;

    public QueueContinuations() {
      this.continuations = Lists.newArrayList();
      this.denotations = Lists.newArrayList();
      this.diagrams = Lists.newArrayList();
    }

    @Override
    public Object apply(List<Object> args, Environment env) {
      LispUtil.checkArgument(args.size() == 2);
      AmbFunctionValue continuation = (AmbFunctionValue) args.get(0);
      List<Object> nextDenotations = ConsValue.consListToList(args.get(1));
      
      return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, Environment env2) {
          LispUtil.checkArgument(args2.size() == 1, "Expected 1 argument, got: %s", args2);
          List<Object> nextDiagrams = ConsValue.consListToList(args2.get(0));
          LispUtil.checkState(nextDiagrams.size() == nextDenotations.size());
          
          for (int i = 0; i < nextDiagrams.size(); i++) {
            Object denotation = nextDenotations.get(i);
            Object diagram = nextDiagrams.get(i);
            // System.out.println("queue: " + continuation + " " + denotation + " " + diagram);
          
            continuations.add(continuation.apply(Arrays.asList(denotation), env, null));
            denotations.add(denotation);
            diagrams.add(diagram);
          }
          return ConstantValue.NIL;
        }
      });
    }

    public List<Object> getContinuations() {
      return continuations;
    }

    public List<Object> getDenotations() {
      return denotations;
    }

    public List<Object> getDiagrams() {
      return diagrams;
    }
  }

  public static class FinalContinuation implements FunctionValue {
    public final List<Object> denotations;
    public final List<Object> diagrams;
    
    public FinalContinuation() {
      this.denotations = Lists.newArrayList();
      this.diagrams = Lists.newArrayList();
    }

    public Object apply(List<Object> args1, Environment env1) {
      LispUtil.checkArgument(args1.size() == 1);
      Object denotation = args1.get(0);
      
      // System.out.println("final denotation: " + denotation);

      return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, Environment env2) {
          LispUtil.checkArgument(args2.size() == 1);
          Object diagram = args2.get(0);
          
          denotations.add(denotation);
          diagrams.add(diagram);

          // System.out.println("final diagram: " + diagram);
          
          return ConstantValue.NIL;
        }
      });
    }
  }
}