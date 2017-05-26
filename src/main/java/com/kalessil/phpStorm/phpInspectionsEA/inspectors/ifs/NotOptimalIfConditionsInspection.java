package com.kalessil.phpStorm.phpInspectionsEA.inspectors.ifs;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.inspectors.ifs.strategy.AndOrWordsUsageStrategy;
import com.kalessil.phpStorm.phpInspectionsEA.inspectors.ifs.strategy.IssetAndNullComparisonStrategy;
import com.kalessil.phpStorm.phpInspectionsEA.inspectors.ifs.utils.ExpressionCostEstimateUtil;
import com.kalessil.phpStorm.phpInspectionsEA.inspectors.ifs.utils.ExpressionsCouplingCheckUtil;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.options.OptionsComponent;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.PhpLanguageUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.hierarhy.InterfacesExtractUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class NotOptimalIfConditionsInspection extends BasePhpInspection {
    // Inspection options.
    public boolean REPORT_LITERAL_OPERATORS     = true;
    public boolean REPORT_DUPLICATE_CONDITIONS  = true;
    public boolean REPORT_MISSING_PARENTHESISES = true;

    private static final String strProblemDescriptionInstanceOfComplementarity = "Probable bug: ensure this behaves properly with 'instanceof(...)' in this scenario.";

    private static final String strProblemDescriptionInstanceOfAmbiguous      = "This condition is ambiguous and can be safely removed.";
    private static final String messageOrdering                               = "This condition execution costs less than the previous one.";
    private static final String messageDuplicateConditions                    = "This condition is duplicated in another if/elseif branch.";
    private static final String messageDuplicateConditionPart                 = "This call is duplicated in conditions set.";
    private static final String strProblemDescriptionIssetCanBeMergedAndCase  = "This can be merged into the previous 'isset(..., ...[, ...])'.";
    private static final String strProblemDescriptionIssetCanBeMergedOrCase   = "This can be merged into the previous '!isset(..., ...[, ...])'.";
    private static final String messageConditionShouldBeWrapped               = "Confusing conditions structure: please wrap needed with '(...)'.";

    @NotNull
    public String getShortName() {
        return "NotOptimalIfConditionsInspection";
    }

    private HashSet<String> functionsSet = null;
    private HashSet<String> getFunctionsSet() {
        if (null == functionsSet) {
            functionsSet = new HashSet<>();

            functionsSet.add("array_key_exists");
            functionsSet.add("defined");
            functionsSet.add("is_array");
            functionsSet.add("is_string");
            functionsSet.add("is_bool");
            functionsSet.add("is_int");
            functionsSet.add("is_float");
            functionsSet.add("is_resource");
            functionsSet.add("is_numeric");
            functionsSet.add("is_scalar");
            functionsSet.add("is_object");
        }

        return functionsSet;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpIf(If ifStatement) {
                List<PsiElement> objAllConditions = new ArrayList<>();
                IElementType[] arrOperationHolder = { null };

                List<PsiElement> objConditionsFromStatement = this.inspectExpressionsOrder(ifStatement.getCondition(), arrOperationHolder);
                if (null != objConditionsFromStatement) {
                    objAllConditions.addAll(objConditionsFromStatement);

                    if (REPORT_MISSING_PARENTHESISES) {
                        this.inspectConditionsForMissingParenthesis(objConditionsFromStatement);
                    }
                    if (REPORT_DUPLICATE_CONDITIONS) {
                        this.inspectConditionsForDuplicatedCalls(objConditionsFromStatement);
                    }
                    this.inspectConditionsForMultipleIsSet(objConditionsFromStatement, arrOperationHolder[0]);
                    this.inspectConditionsForInstanceOfAndIdentityOperations(objConditionsFromStatement, arrOperationHolder[0]);

                    this.inspectConditionsForAmbiguousInstanceOf(objConditionsFromStatement);
                    IssetAndNullComparisonStrategy.apply(objConditionsFromStatement, holder);

                    objConditionsFromStatement.clear();

                    if (REPORT_LITERAL_OPERATORS) {
                        AndOrWordsUsageStrategy.apply(ifStatement.getCondition(), holder);
                    }
                }

                for (ElseIf objElseIf : ifStatement.getElseIfBranches()) {
                    objConditionsFromStatement = this.inspectExpressionsOrder(objElseIf.getCondition(), arrOperationHolder);
                    if (null != objConditionsFromStatement) {
                        objAllConditions.addAll(objConditionsFromStatement);

                        if (REPORT_MISSING_PARENTHESISES) {
                            this.inspectConditionsForMissingParenthesis(objConditionsFromStatement);
                        }
                        if (REPORT_DUPLICATE_CONDITIONS) {
                            this.inspectConditionsForDuplicatedCalls(objConditionsFromStatement);
                        }
                        this.inspectConditionsForMultipleIsSet(objConditionsFromStatement, arrOperationHolder[0]);
                        this.inspectConditionsForInstanceOfAndIdentityOperations(objConditionsFromStatement, arrOperationHolder[0]);

                        this.inspectConditionsForAmbiguousInstanceOf(objConditionsFromStatement);

                        objConditionsFromStatement.clear();

                        if (REPORT_LITERAL_OPERATORS) {
                            AndOrWordsUsageStrategy.apply(objElseIf.getCondition(), holder);
                        }
                    }
                }

                if (REPORT_DUPLICATE_CONDITIONS) {
                    this.inspectDuplicatedConditions(objAllConditions, ifStatement);
                }
                /* TODO: If not binary/ternary/assignment/array access expression,  */
                /* TODO: perform types lookup - nullable core types/classes should be compared with null.  */
                /* TODO: Inversion should be un-boxed to get expression. */

                objAllConditions.clear();
            }

            private void inspectConditionsForMissingParenthesis(@NotNull List<PsiElement> conditions) {
                for (final PsiElement condition : conditions) {
                    if (!(condition instanceof BinaryExpression)) {
                        continue;
                    }
                    final IElementType operationType = ((BinaryExpression) condition).getOperationType();
                    if (operationType != PhpTokenTypes.opAND && operationType != PhpTokenTypes.opOR) {
                        continue;
                    }

                    if (!(condition.getParent() instanceof ParenthesizedExpression)) {
                        holder.registerProblem(condition, messageConditionShouldBeWrapped, ProblemHighlightType.ERROR);
                    }
                }
            }

            // reports $value instanceof \DateTime OP $value instanceof \DateTimeInterface
            private void inspectConditionsForAmbiguousInstanceOf(@NotNull List<PsiElement> objBranchConditions) {
                if (objBranchConditions.size() < 2) {
                    return;
                }

                // find all instanceof expressions
                List<BinaryExpression> instanceOfExpressions = new ArrayList<>();
                for (PsiElement objExpression : objBranchConditions) {
                    if (objExpression instanceof BinaryExpression) {
                        PsiElement objOperation = ((BinaryExpression) objExpression).getOperation();
                        if (
                            null != objOperation && null != objOperation.getNode() &&
                            PhpTokenTypes.kwINSTANCEOF == objOperation.getNode().getElementType()
                        ) {
                            instanceOfExpressions.add((BinaryExpression) objExpression);
                        }
                    }
                }
                // terminate processing if not enough entries for analysis
                if (instanceOfExpressions.size() < 2) {
                    instanceOfExpressions.clear();
                    return;
                }

                // now we need to build up following structure:
                /*
                    'subject' => [
                                    condition => class,
                                    condition => class,
                                    condition => class
                                ]
                 */
                HashMap<PsiElement, HashMap<PsiElement, PhpClass>> mappedChecks = new HashMap<>();
                for (BinaryExpression instanceOfExpression : instanceOfExpressions) {
                    // ensure expression is well-formed
                    PsiElement subject = instanceOfExpression.getLeftOperand();
                    if (null == subject || !(instanceOfExpression.getRightOperand() instanceof ClassReference)) {
                        continue;
                    }

                    // ensure resolvable
                    ClassReference reference = (ClassReference) instanceOfExpression.getRightOperand();
                    if (!(reference.resolve() instanceof PhpClass)) {
                        continue;
                    }
                    PhpClass clazz = (PhpClass) reference.resolve();

                    // push subject properly, as expressions can be different objects with the same semantics
                    PsiElement registeredSubject = null;
                    for (PsiElement testSubject : mappedChecks.keySet()) {
                        if (PsiEquivalenceUtil.areElementsEquivalent(subject, testSubject)) {
                            registeredSubject = testSubject;
                            break;
                        }
                    }
                    // put empty container if it's not known
                    if (null == registeredSubject) {
                        mappedChecks.put(subject, new HashMap<>());
                        registeredSubject = subject;
                    }

                    // register condition for further analysis
                    mappedChecks.get(registeredSubject).put(instanceOfExpression, clazz);
                }
                // release references in the raw list
                instanceOfExpressions.clear();


                // process entries, perform subject container clean up on each iteration
                HashMap<PhpClass, HashSet<PhpClass>> resolvedInheritanceChains = new HashMap<>();
                for (HashMap<PsiElement, PhpClass> subjectContainer : mappedChecks.values()) {
                    // investigate one subject when it has multiple instanceof-expressions
                    if (subjectContainer.size() > 1) {
                        // walk through conditions
                        for (Map.Entry<PsiElement, PhpClass> instanceOf2class: subjectContainer.entrySet()) {
                            /* unpack the pair */
                            final PhpClass clazz                  = instanceOf2class.getValue();
                            final PsiElement instanceOfExpression = instanceOf2class.getKey();

                            // extract current condition details
                            HashSet<PhpClass> clazzParents = resolvedInheritanceChains.get(clazz);
                            if (null == clazzParents) {
                                clazzParents = InterfacesExtractUtil.getCrawlCompleteInheritanceTree(clazz, true);
                                resolvedInheritanceChains.put(clazz, clazzParents);
                            }

                            // inner loop for verification
                            for (Map.Entry<PsiElement, PhpClass> instanceOf2classInner : subjectContainer.entrySet()) {
                                // skip itself
                                if (instanceOf2classInner.getKey() == instanceOfExpression) {
                                    continue;
                                }

                                // if alternative references to base class current check is ambiguous
                                if (clazzParents.contains(instanceOf2classInner.getValue())) {
                                    holder.registerProblem(instanceOfExpression, strProblemDescriptionInstanceOfAmbiguous, ProblemHighlightType.WEAK_WARNING);
                                    break;
                                }
                            }
                        }
                    }
                    subjectContainer.clear();
                }
                // release inheritance cache as well
                for (HashSet<PhpClass> resolvedInheritance: resolvedInheritanceChains.values()) {
                    resolvedInheritance.clear();
                }
                resolvedInheritanceChains.clear();
                // release mapping as well
                mappedChecks.clear();
            }

            /* TODO: is_* functions */
            private void inspectConditionsForInstanceOfAndIdentityOperations(@NotNull List<PsiElement> objBranchConditions, @Nullable IElementType operationType) {
                if (operationType != PhpTokenTypes.opAND || objBranchConditions.size() < 2) {
                    return;
                }

                PsiElement objTestSubject = null;
                for (PsiElement objExpression : objBranchConditions) {
                    if (objExpression instanceof BinaryExpression) {
                        BinaryExpression objInstanceOfExpression = (BinaryExpression) objExpression;
                        if (
                            null != objInstanceOfExpression.getOperation() &&
                            null != objInstanceOfExpression.getOperation().getNode() &&
                            objInstanceOfExpression.getOperation().getNode().getElementType() == PhpTokenTypes.kwINSTANCEOF
                        ) {
                            objTestSubject = objInstanceOfExpression.getLeftOperand();
                            break;
                        }
                    }
                }
                if (null == objTestSubject) {
                    return;
                }

                for (PsiElement objExpression : objBranchConditions) {
                    if (objExpression instanceof BinaryExpression) {
                        BinaryExpression objBinaryExpression = (BinaryExpression) objExpression;
                        if (
                            null != objBinaryExpression.getOperation() &&
                            null != objBinaryExpression.getOperation().getNode() &&
                            null != objBinaryExpression.getLeftOperand() &&
                            null != objBinaryExpression.getRightOperand()

                        ) {
                            IElementType objConditionOperation = objBinaryExpression.getOperation().getNode().getElementType();
                            if (
                                objConditionOperation == PhpTokenTypes.opIDENTICAL ||
                                objConditionOperation == PhpTokenTypes.opNOT_IDENTICAL ||
                                objConditionOperation == PhpTokenTypes.opEQUAL ||
                                objConditionOperation == PhpTokenTypes.opNOT_EQUAL
                            ) {
                                if (
                                    PsiEquivalenceUtil.areElementsEquivalent(objTestSubject, objBinaryExpression.getLeftOperand()) ||
                                    PsiEquivalenceUtil.areElementsEquivalent(objTestSubject, objBinaryExpression.getRightOperand())
                                ) {
                                    holder.registerProblem(objExpression, strProblemDescriptionInstanceOfComplementarity, ProblemHighlightType.WEAK_WARNING);
                                }
                            }
                        }
                    }
                }
            }

            private void inspectConditionsForMultipleIsSet(@NotNull List<PsiElement> objBranchConditions, @Nullable IElementType operationType) {
                /* handle isset && isset ... */
                if (operationType == PhpTokenTypes.opAND) {
                    int intIssetCallsCount = 0;
                    for (PsiElement objExpression : objBranchConditions) {
                        if (!(objExpression instanceof PhpIsset)) {
                            continue;
                        }

                        ++intIssetCallsCount;
                        if (intIssetCallsCount > 1) {
                            holder.registerProblem(objExpression, strProblemDescriptionIssetCanBeMergedAndCase, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        }
                    }

                    return;
                }

                /* handle !isset || !isset ... */
                if (operationType == PhpTokenTypes.opOR) {
                    int intIssetCallsCount = 0;
                    for (PsiElement objExpression : objBranchConditions) {
                        if (!(objExpression instanceof UnaryExpression)) {
                            continue;
                        }
                        objExpression = ExpressionSemanticUtil.getExpressionTroughParenthesis(((UnaryExpression) objExpression).getValue());
                        if (!(objExpression instanceof PhpIsset)) {
                            continue;
                        }

                        ++intIssetCallsCount;
                        if (intIssetCallsCount > 1) {
                            holder.registerProblem(objExpression, strProblemDescriptionIssetCanBeMergedOrCase, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        }
                    }
                }
            }

            private void inspectConditionsForDuplicatedCalls(@NotNull List<PsiElement> conditions) {
                if (conditions.size() < 2) {
                    return;
                }

                /* extract calls */
                final List<PsiElement> callsExtracted = new ArrayList<>();
                for (final PsiElement condition : conditions) {
                    if (!(condition instanceof BinaryExpression)) {
                        continue;
                    }

                    PsiElement tempExpression = ((BinaryExpression) condition).getLeftOperand();
                    if (tempExpression instanceof FunctionReference) {
                        callsExtracted.add(tempExpression);
                    }
                    tempExpression = ((BinaryExpression) condition).getRightOperand();
                    if (tempExpression instanceof FunctionReference) {
                        callsExtracted.add(tempExpression);
                    }
                }

                /* scan for duplicates */
                for (final PsiElement expression : callsExtracted) {
                    if (null == expression) {
                        continue;
                    }

                    /* put a stub */
                    callsExtracted.set(callsExtracted.indexOf(expression), null);
                    /* search duplicates in current scope */
                    for (final PsiElement innerLoopExpression : callsExtracted) {
                        if (null == innerLoopExpression) {
                            continue;
                        }

                        if (PsiEquivalenceUtil.areElementsEquivalent(innerLoopExpression, expression)) {
                            holder.registerProblem(innerLoopExpression, messageDuplicateConditionPart, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                            callsExtracted.set(callsExtracted.indexOf(innerLoopExpression), null);
                        }
                    }
                }

            }

            /**
             * Checks if duplicates are introduced, conditions collection will be modified so it's empty in the end
             * @param objAllConditions to check
             * @param ifStatement current scope
             */
            private void inspectDuplicatedConditions(List<PsiElement> objAllConditions, If ifStatement) {
                List<PsiElement> objParentConditions = new ArrayList<>();

                /* collect parent scopes conditions */
                PsiElement objParent = ifStatement.getParent();
                while (null != objParent && !(objParent instanceof PhpFile)) {
                    if (objParent instanceof If) {
                        List<PsiElement> tempList = ExpressionSemanticUtil.getConditions(((If) objParent).getCondition(), null);
                        if (null != tempList) {
                            objParentConditions.addAll(tempList);
                            tempList.clear();
                        }

                        for (ElseIf objParentElseIf : ((If) objParent).getElseIfBranches()) {
                            tempList = ExpressionSemanticUtil.getConditions(objParentElseIf.getCondition(), null);
                            if (null != tempList) {
                                objParentConditions.addAll(tempList);
                                tempList.clear();
                            }
                        }
                    }

                    objParent = objParent.getParent();
                }


                /* scan for duplicates */
                for (PsiElement objExpression : objAllConditions) {
                    if (null == objExpression) {
                        continue;
                    }

                    /* put a stub */
                    int intOuterIndex = objAllConditions.indexOf(objExpression);
                    objAllConditions.set(intOuterIndex, null);


                    /* ignore variables (even if inverted) */
                    PsiElement variableCandidate = objExpression;
                    if (variableCandidate instanceof UnaryExpression) {
                        final PsiElement notOperatorCandidate = ((UnaryExpression) variableCandidate).getOperation();
                        if (null != notOperatorCandidate && notOperatorCandidate.getNode().getElementType() == PhpTokenTypes.opNOT) {
                            PsiElement invertedValue = ((UnaryExpression) variableCandidate).getValue();
                            invertedValue = ExpressionSemanticUtil.getExpressionTroughParenthesis(invertedValue);
                            if (null == invertedValue) {
                                continue;
                            }

                            variableCandidate = invertedValue;
                        }
                    }
                    /* ignore variables (even if compared with booleans) */
                    if (variableCandidate instanceof BinaryExpression) {
                        final PsiElement left  = ((BinaryExpression) variableCandidate).getLeftOperand();
                        final PsiElement right = ((BinaryExpression) variableCandidate).getRightOperand();
                        if (PhpLanguageUtil.isBoolean(right)) {
                            variableCandidate = left;
                        } else if (PhpLanguageUtil.isBoolean(left)) {
                            variableCandidate = right;
                        }
                    }
                    if (
                        variableCandidate instanceof Variable ||
                        variableCandidate instanceof ConstantReference ||
                        variableCandidate instanceof FieldReference
                    ) {
                        continue;
                    }
                    /* continue with sensible expressions analysis */


                    /* search duplicates in current scope */
                    for (PsiElement objInnerLoopExpression : objAllConditions) {
                        if (null == objInnerLoopExpression) {
                            continue;
                        }

                        boolean isDuplicate = PsiEquivalenceUtil.areElementsEquivalent(objInnerLoopExpression, objExpression);
                        if (isDuplicate) {
                            holder.registerProblem(objInnerLoopExpression, messageDuplicateConditions, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

                            int intInnerIndex = objAllConditions.indexOf(objInnerLoopExpression);
                            objAllConditions.set(intInnerIndex, null);
                        }
                    }

                    /* search duplicates in outer scopes */
                    for (PsiElement objOuterScopeExpression : objParentConditions) {
                        if (null == objOuterScopeExpression) {
                            continue;
                        }

                        boolean isDuplicate = PsiEquivalenceUtil.areElementsEquivalent(objOuterScopeExpression, objExpression);
                        if (isDuplicate) {
                            holder.registerProblem(objExpression, messageDuplicateConditions, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

                            int intOuterScopeIndex = objParentConditions.indexOf(objOuterScopeExpression);
                            objParentConditions.set(intOuterScopeIndex, null);
                        }
                    }
                }

                objParentConditions.clear();
            }

            /**
             * @param objCondition to inspect
             */
            @Nullable
            private List<PsiElement> inspectExpressionsOrder(PsiElement objCondition, @Nullable IElementType[] arrOperationHolder) {
                List<PsiElement> objPartsCollection = ExpressionSemanticUtil.getConditions(objCondition, arrOperationHolder);
                if (null == objPartsCollection) {
                    return null;
                }

                /* one item only, skip costs estimation */
                if (objPartsCollection.size() < 2) {
                    return objPartsCollection;
                }

                /* verify if costs estimated are optimal */
                int intPreviousCost                 = 0;
                PsiElement objPreviousCond          = null;
                HashSet<String> functionsSetToAllow = getFunctionsSet();

                for (PsiElement condition : objPartsCollection) {
                    int intLoopCurrentCost = ExpressionCostEstimateUtil.getExpressionCost(condition, functionsSetToAllow);

                    if (
                        null != objPreviousCond && intLoopCurrentCost < intPreviousCost &&
                        !ExpressionsCouplingCheckUtil.isSecondCoupledWithFirst(objPreviousCond, condition)
                    ) {
                        holder.registerProblem(condition, messageOrdering, ProblemHighlightType.WEAK_WARNING);
                    }

                    intPreviousCost = intLoopCurrentCost;
                    objPreviousCond = condition;
                }

                return objPartsCollection;
            }
        };
    }

    public JComponent createOptionsPanel() {
        return OptionsComponent.create((component) -> {
            component.addCheckbox("Report literal and/or operators", REPORT_LITERAL_OPERATORS, (isSelected) -> REPORT_LITERAL_OPERATORS = isSelected);
            component.addCheckbox("Report duplicate conditions", REPORT_DUPLICATE_CONDITIONS, (isSelected) -> REPORT_DUPLICATE_CONDITIONS = isSelected);
            component.addCheckbox("Report confusing conditions", REPORT_MISSING_PARENTHESISES, (isSelected) -> REPORT_MISSING_PARENTHESISES = isSelected);
        });
    }
}
