package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import lombok.ToggleOff;
import lombok.ToggleOn;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacASTAdapter;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import java.util.ArrayList;
import java.util.Collection;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleToggle extends JavacAnnotationHandler<ToggleOn> {
    private String escapeString(String string) {
        return string.replaceAll("[^a-zA-Z0-9_$]", "$$");
    }

    @Override
    public void handle(AnnotationValues<ToggleOn> annotation, JCAnnotation ast, JavacNode annotationNode) {
        JavacNode annotationTargetNode = annotationNode.up();
        assertAnnotationIsOnMethod(annotationTargetNode);
        removeAnnotation(annotationNode);

        JCMethodDecl targetMethod = (JCMethodDecl) (annotationTargetNode.get());
        assertNoMoreAnnotations(targetMethod, annotationTargetNode);
        String oldTargetMethodName = targetMethod.getName().toString();
        String newTargetMethodName = mangleName(oldTargetMethodName);
        targetMethod.name = annotationTargetNode.toName(newTargetMethodName);

        String switchMethodName = generateSwitchMethodName(annotation.getInstance().value());

        JavacNode classNode = annotationTargetNode.up();
        JCMethodDecl switchMethod = tryToFindSwitchMethod(switchMethodName, classNode);
        if (switchMethod == null) {
            switchMethod = createSwitchMethod(switchMethodName, annotationTargetNode);
        }
        addSwitchEvaluation(switchMethod, targetMethod, annotation.getInstance(), classNode);
        //TODO: implement
        createDelegateMethod();
    }

    private String generateSwitchMethodName(String s) {
        return "switch_" + escapeString(s);
    }

    private void assertNoMoreAnnotations(JCMethodDecl targetMethod, JavacNode annotationTargetNode) {
        if (!targetMethod.getModifiers().annotations.isEmpty()) {
            annotationTargetNode.addError("Currently annotations is not supported");
            throw new RuntimeException("Can't handle annotation on method with another annotations");
        }
    }

    private String mangleName(String oldTargetMethodName) {
        return oldTargetMethodName + "$_old";
    }

    private void removeAnnotation(JavacNode annotationNode) {
        JavacHandlerUtil.deleteAnnotationIfNeccessary(annotationNode, ToggleOn.class);
    }

    private void createDelegateMethod() {
    }

    private JCMethodDecl createSwitchMethod(String switchMethodName, JavacNode annotationTargetNode) {
        JCMethodDecl targetMethod = ((JCMethodDecl) annotationTargetNode.get());

        //TODO: find out if this is correct to use targetMethod flags with access level set to private
        JCModifiers modifiers = targetMethod.getModifiers();
        long newFlags = modifiers.flags ^ Flags.PUBLIC ^ Flags.PROTECTED & modifiers.flags | Flags.PRIVATE;

        JavacTreeMaker treeMaker = annotationTargetNode.getTreeMaker();
        JCMethodDecl switchMethod = treeMaker.MethodDef(
                treeMaker.Modifiers(newFlags),
                annotationTargetNode.toName(switchMethodName),
                targetMethod.restype,
                targetMethod.typarams,
                targetMethod.params,
                targetMethod.thrown,
                treeMaker.Block(
                        0,
                        List.of(generate(annotationTargetNode))
                ),
                targetMethod.defaultValue
        );

        JCClassDecl classDeclaration = (JCClassDecl) annotationTargetNode.up().get();
        classDeclaration.defs = classDeclaration.defs.prepend(switchMethod);
        return switchMethod;
    }

    private JCStatement generate(JavacNode annotationTargetNode) {
        JavacTreeMaker treeMaker = annotationTargetNode.getTreeMaker();
        //TODO: replace with appropriate exception:
        JCExpression runtimeExceptionType = genTypeRef(annotationTargetNode, "java.lang.RuntimeException");
        JCExpression expression = treeMaker.NewClass(
                null,
                List.<JCExpression>nil(),
                runtimeExceptionType,
                List.<JCExpression>of(treeMaker.Literal("Uncovered case reached")),
                null);
        return treeMaker.Throw(expression);
    }

    private void addSwitchEvaluation(JCMethodDecl switchMethod, JCMethodDecl targetMethod, ToggleOn instance, JavacNode classNode) {
        addSwitchEvaluation(switchMethod, targetMethod, instance.value(), true, classNode);
    }

    private void addSwitchEvaluation(JCMethodDecl switchMethod, JCMethodDecl targetMethod, ToggleOff instance, JavacNode classNode) {
        addSwitchEvaluation(switchMethod, targetMethod, instance.value(), false, classNode);
    }

    private void addSwitchEvaluation(JCMethodDecl switchMethod, JCMethodDecl targetMethod, String toggleName, boolean toggleValue, JavacNode classNode) {
        MethodDeclarationFinder visitor = new MethodDeclarationFinder("getToggleState");
        classNode.traverse(visitor);
        JCMethodDecl stateEvaluationMethod = visitor.getFoundDeclaration();

        if (stateEvaluationMethod == null) {
            classNode.addError("Was expecting to find 'boolean getToggleState(String)' method");
            throw new RuntimeException("Failed to find toggle evaluation method with correct signature");
        }


        ArrayList<JCStatement> statements = new ArrayList<JCStatement>(switchMethod.body.stats);
        JCStatement throwException = statements.remove(statements.size() - 1);
        JCStatement toggleEvaluation = tryToFindToggleEvaluation(toggleName, statements);
        if (toggleEvaluation == null) {
            toggleEvaluation = generateToggleEvaluation(toggleName, stateEvaluationMethod, classNode);
            statements.add(toggleEvaluation);
        }
        JCStatement ifEvalStatement = generateIfEvalStatement(toggleName, toggleValue, targetMethod, classNode);
        statements.add(ifEvalStatement);
        statements.add(throwException);

        switchMethod.body.stats = List.from(statements.toArray(new JCStatement[]{}));
    }

    private JCStatement generateIfEvalStatement(String toggleName, boolean toggleValue, JCMethodDecl targetMethod, JavacNode classNode) {
        JavacTreeMaker treeMaker = classNode.getTreeMaker();
        return treeMaker.If(
                treeMaker.Binary(
                        CTC_EQUAL,
                        treeMaker.Ident(classNode.toName(toggleName)),
                        treeMaker.Literal(CTC_BOOLEAN, toggleValue ? 1 : 0)),
                treeMaker.Return(
                        treeMaker.Apply(
                                List.<JCExpression>nil(),
                                chainDots(classNode, "this", targetMethod.name.toString()),
                                generateArgList(targetMethod.params, treeMaker)
                        )
                ),
                null);
    }

    private List<JCExpression> generateArgList(List<JCVariableDecl> params, JavacTreeMaker treeMaker) {
        ArrayList<JCExpression> result = new ArrayList<JCExpression>();
        for (JCVariableDecl variableDeclaration : params) {
            result.add(treeMaker.Ident(variableDeclaration.name));
        }
        return List.from(result.toArray(new JCExpression[]{}));
    }

    private JCStatement generateToggleEvaluation(String toggleName, JCMethodDecl stateEvaluationMethod, JavacNode classNode) {
        JavacTreeMaker treeMaker = classNode.getTreeMaker();

        JCMethodInvocation toggleStateEvaluationInvocation = treeMaker.Apply(
                List.<JCExpression>nil(),
                chainDots(classNode, "this", stateEvaluationMethod.name.toString()),
                List.of((JCExpression) treeMaker.Literal(toggleName))
        );

        return treeMaker.VarDef(
                treeMaker.Modifiers(0),
                classNode.toName(getToggleVariableName(toggleName)),
                treeMaker.TypeIdent(CTC_BOOLEAN),
                toggleStateEvaluationInvocation
        );
    }

    private String getToggleVariableName(String toggleValue) {
        return escapeString(toggleValue) + "_value";
    }

    private JCStatement tryToFindToggleEvaluation(String toggleName, Collection<JCStatement> statements) {
        for (JCStatement statement : statements) {
            if (statement instanceof JCVariableDecl) {
                JCVariableDecl variableDeclaration = (JCVariableDecl) statement;
                if (variableDeclaration.name.toString().equals(toggleName)) {
                    return statement;
                }
            }
        }
        return null;
    }

    private JCMethodDecl tryToFindSwitchMethod(final String switchMethodName, JavacNode classNode) {
        MethodDeclarationFinder visitor = new MethodDeclarationFinder(switchMethodName);
        classNode.traverse(visitor);
        return visitor.getFoundDeclaration();
    }

    private void assertAnnotationIsOnMethod(JavacNode annotationTargetNode) {
        if (annotationTargetNode.getKind() != AST.Kind.METHOD) {
            annotationTargetNode.addError("Annotation only allowed on methods");
            throw new RuntimeException("Annotation used not on a method");
        }
    }

    private static class MethodDeclarationFinder extends JavacASTAdapter {
        private String searchMethodName;
        private JCMethodDecl foundDeclaration;

        MethodDeclarationFinder(String searchMethodName) {
            this.searchMethodName = searchMethodName;
        }

        @Override
        public void visitMethod(JavacNode methodNode, JCMethodDecl method) {
            if (methodNode.getName().equals(searchMethodName)) {
                foundDeclaration = method;
            }
        }

        JCMethodDecl getFoundDeclaration() {
            return foundDeclaration;
        }
    }
}



















