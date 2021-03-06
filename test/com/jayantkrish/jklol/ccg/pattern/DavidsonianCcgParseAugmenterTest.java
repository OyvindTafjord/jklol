package com.jayantkrish.jklol.ccg.pattern;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.ForAllExpression;
import com.jayantkrish.jklol.cvsm.ccg.DavidsonianCcgParseAugmenter;

public class DavidsonianCcgParseAugmenterTest extends TestCase {
  
  public void testLfFromSyntax1() {
    runCategoryTest("N{0}", "foo", "(lambda x (foo x))");
    runCategoryTest("N{1}", "foo", "(lambda x (foo x))");
  }
  
  public void testLfFromSyntax2() {
    runCategoryTest("(N{1}/N{1}){0}", "foo", "(lambda f (lambda x (and (f x) (foo x))))");
    runCategoryTest("((N{1}/N{1}){0}/N{2}){3}", "foo", "(lambda f (lambda g (lambda x (exists y (and (g x) (foo x y) (f y))))))");
  }
  
  public void testLfFromSyntax3() {
    runCategoryTest("((N{2}/N{2}){1}/(N{2}/N{2}){1}){0}", "foo", "(lambda f (lambda g (lambda x (and ((f (lambda y (= y x))) x) (foo x) (g x)))))");
  }
  
  public void testLfFromSyntax4() {
    runCategoryTest("(((S{1}\\N{2}){3}/(S{1}\\N{2}){3}){0}/NP{4}){0}", "foo",
        "(lambda f (lambda g (lambda h (lambda x (exists y z (and ((g (lambda w (= w y))) x) (foo x y z) (f z) (h y)))))))");
  }
  
  public void testLfFromSyntax5() {
    runCategoryTest("(((S{1}\\N{2}){3}/(S{1}\\N{4}){5}){0}/NP{6}){0}", "foo",
        "(lambda f (lambda g (lambda h (lambda w (exists x y z (and (foo w x y z) (f z) ((g (lambda q (= q y))) w) (h x)))))))");
  }

  public void testLfFromSyntax6() {
    runCategoryTest("(((S{1}\\N{2}){3}/(S{1}\\N{6}){5}){0}/NP{4}){0}", "foo",
        "(lambda f (lambda g (lambda h (lambda w (exists x y z (and (foo w x y z) (f z) ((g (lambda q (= q y))) w) (h x)))))))");
  }
  
  public void testLfFromSyntax7() {
    runCategoryTest("((N{1}\\N{1}){2}/(S{3}\\N{1}){4}){0}", "foo",
        "(lambda f (lambda g (lambda x (exists y (and (foo x y) ((f (lambda w (= w x))) y) (g x))))))");
  }
  
  public void testBinaryRule1() {
    runBinaryRuleTest("NP{0}", ",{5}", "(S[9]{1}/S[9]{1}){0}", Combinator.Type.OTHER, 
        "(lambda $L $R (lambda f (lambda e (exists x y (and ($L x) ($R y) (f e))))))");
  }

  public void testBinaryRule2() {
    runBinaryRuleTest(",{1}", "N{0}", "(N{0}\\N{0}){1}", Combinator.Type.CONJUNCTION, 
        "(lambda $L $R (lambda f (lambda x (forall (pred (set $R f)) (pred x)))))");
  }

  public void testBinaryRule3() {
    runBinaryRuleTest(",{3}", "(N{0}/N{0}){1}", "((N{0}/N{0}){1}\\(N{0}/N{0}){1}){2}", Combinator.Type.CONJUNCTION, 
        "(lambda $L $R (lambda f (lambda g (lambda x (forall (pred (set $R f)) (and ((pred g) x)))))))");
  }

  public void testBinaryRule4() {
    runBinaryRuleTest("conj{0}", "N{1}", "N{1}", Combinator.Type.OTHER, 
        "(lambda $L $R (lambda e (exists y (and ($L y) ($R e)))))");
  }
  
  public void testBinaryRule5() {
    runBinaryRuleTest("LRB{5}", "((S[1]{1}\\NP{2}){1}\\(S[1]{1}\\NP{2}){1}){0}",
        "((S[1]{1}\\NP{2}){1}\\(S[1]{1}\\NP{2}){1}){0}", Combinator.Type.OTHER, 
        "(lambda $L $R $1 f (lambda e (exists y (and ($L y) ((($R $1) f) e)))))");
  }

  public void testBinaryRule6() {
    runBinaryRuleTest(",{2}", "(N{0}\\N{0}){1}", "(N{0}\\N{0}){1}", Combinator.Type.OTHER, 
        "(lambda $L $R (lambda f (lambda x (exists e2 (and (($R f) x) ($L e2))))))");
  }

  private static void runBinaryRuleTest(String leftCatString, String rightCatString, 
      String parentCatString, Combinator.Type type, String expectedExpressionString) {
    HeadedSyntacticCategory left = HeadedSyntacticCategory.parseFrom(leftCatString);
    HeadedSyntacticCategory right = HeadedSyntacticCategory.parseFrom(rightCatString);
    HeadedSyntacticCategory parent = HeadedSyntacticCategory.parseFrom(parentCatString);
    
    Expression result = DavidsonianCcgParseAugmenter.logicalFormFromBinaryRule(left,
        right, parent, type).simplify();
    Expression expected = ExpressionParser.lambdaCalculus()
        .parseSingleExpression(expectedExpressionString).simplify();
    
    System.out.println(left + " " + right + " -> " + parent + " result: " + result
        + " exp: " + expected);

    if (result instanceof ForAllExpression) {
      System.out.println(((ForAllExpression) result).expandQuantifier().simplify());
    }

    assertTrue(expected.functionallyEquals(result));
  }
  
  private static void runCategoryTest(String catString, String word, String expectedExpressionString) {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(catString);
    Expression expected = ExpressionParser.lambdaCalculus().parseSingleExpression(expectedExpressionString).simplify();
    Expression result = DavidsonianCcgParseAugmenter.logicalFormFromSyntacticCategory(cat, word).simplify();
    
    System.out.println(cat + " result: " + result + " exp: " + expected);
    
    assertTrue(expected.functionallyEquals(result));
  }
}
