package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
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
    private static final String DEFAULT_GROUP_NAME_VALUE = "";

    private String escapeString(String string) {
        return string.replaceAll("[^a-zA-Z0-9_$]", "$$");
    }

    @Override
    public void handle(AnnotationValues<ToggleOn> annotationValues, JCAnnotation ast, JavacNode annotationNode) {
        ToggleOn annotation = annotationValues.getInstance();
        JavacNode annotatedNode = annotationNode.up();
        JavacNode classNode = annotatedNode.up();

        assertMethodKind(annotatedNode);
        removeHandlingAnnotation(annotationNode);

        JCMethodDecl publicProxyMethod = (JCMethodDecl) annotatedNode.get();
        JCMethodDecl hiddenDelegateMethod = copyMethod(publicProxyMethod, annotatedNode.up().getTreeMaker());
        hiddenDelegateMethod.name = classNode.toName(mangleName(hiddenDelegateMethod.name.toString()));
        applyProvidedSettings(annotation, publicProxyMethod, hiddenDelegateMethod);
        addMethodToClass(classNode, hiddenDelegateMethod);

        JCMethodDecl dispatcherMethod = setupDispatcherMethod(annotatedNode, annotation);
        JCMethodDecl switchEvaluationMethod = findSwitchEvaluationMethod(classNode);
        addSwitchToDispatcher(dispatcherMethod, hiddenDelegateMethod, switchEvaluationMethod, annotation, classNode);

        publicProxyMethod.body = generateDispatcherCall(dispatcherMethod, classNode);
    }

    private JCBlock generateDispatcherCall(JCMethodDecl dispatcherMethod, JavacNode classNode) {
        JavacTreeMaker treeMaker = classNode.getTreeMaker();
        return treeMaker.Block(0, List.of(
                (JCStatement) treeMaker.Return(
                        treeMaker.Apply(
                                null,
                                treeMaker.Ident(dispatcherMethod.name),
                                generateArgList(dispatcherMethod.params, treeMaker)
                        )
                )
        ));
    }

    private void addMethodToClass(JavacNode classNode, JCMethodDecl method) {
        injectMethod(classNode, method);;
    }

    private JCMethodDecl copyMethod(JCMethodDecl method, JavacTreeMaker treeMaker) {
        return treeMaker.MethodDef(
                treeMaker.Modifiers(method.mods.flags),
                method.name,
                method.restype,
                method.typarams,
                method.params,
                method.thrown,
                method.body,
                method.defaultValue
        );
    }

    private void applyProvidedSettings(ToggleOn annotation, JCMethodDecl publicProxyMethod, JCMethodDecl hiddenDelegateMethod) {
        hiddenDelegateMethod.mods.flags = resetAccessModifiers(hiddenDelegateMethod.mods.flags);
        if (!annotation.makeDelegatePackagePrivate()) {
            hiddenDelegateMethod.mods.flags |= Flags.PRIVATE;
        }
        if (annotation.removeProxyAnnotations()) {
            removeAnnotations(publicProxyMethod);
        }
        if (annotation.removeDelegateAnnotations()) {
            removeAnnotations(hiddenDelegateMethod);
        }
    }

    private void removeAnnotations(JCMethodDecl method) {
        method.mods.annotations = List.nil();
    }

    private long resetAccessModifiers(long modifiers) {
        return (modifiers | Flags.AccessFlags) ^ Flags.AccessFlags;
    }

    private JCMethodDecl findSwitchEvaluationMethod(JavacNode classNode) {
        //TODO: add annotation support
        JCMethodDecl switchEvaluationMethod = findMethodByName(classNode, "getToggleState");
        if (switchEvaluationMethod == null) {
            classNode.addError("Was expecting to find 'boolean getToggleState(String)' method");
            throw new RuntimeException("Failed to find toggle evaluation method with correct signature");
        }
        return switchEvaluationMethod;
    }

    private JCMethodDecl setupDispatcherMethod(JavacNode annotatedNode, ToggleOn annotation) {
        String dispatcherMethodName = generateDispatcherName(annotation);
        JavacNode classNode = annotatedNode.up();
        JCMethodDecl dispatcherMethod = findMethodByName(classNode, dispatcherMethodName);
        if (dispatcherMethod == null) {
            dispatcherMethod = generateDispatcherMethod(dispatcherMethodName, annotatedNode);
            addMethodToClass(classNode, dispatcherMethod);
        }
        return dispatcherMethod;
    }

    private String generateDispatcherName(ToggleOn annotation) {
        String groupName = annotation.groupName();
        if (DEFAULT_GROUP_NAME_VALUE.equals(groupName)) {
            groupName = annotation.value();
        }
        return "dispatcher_for_" + escapeString(groupName);
    }

    private String mangleName(String oldTargetMethodName) {
        return oldTargetMethodName + "$_old";
    }

    private void removeHandlingAnnotation(JavacNode annotationNode) {
        JavacHandlerUtil.deleteAnnotationIfNeccessary(annotationNode, ToggleOn.class);
    }

    private JCMethodDecl generateDispatcherMethod(String dispatcherMethodName, JavacNode annotatedNode) {
        JCMethodDecl annotatedMethod = ((JCMethodDecl) annotatedNode.get());
        JavacTreeMaker treeMaker = annotatedNode.getTreeMaker();

        JCMethodDecl dispatcherMethod = copyMethod(annotatedMethod, treeMaker);
        dispatcherMethod.name = annotatedNode.toName(dispatcherMethodName);
        dispatcherMethod.mods.flags = resetAccessModifiers(dispatcherMethod.mods.flags) | Flags.PRIVATE;
        dispatcherMethod.body = treeMaker.Block(
                0,
                List.of(
                        throwExceptionStatement(
                                "java.lang.RuntimeException",
                                "Uncovered case reached",
                                annotatedNode
                        )
                )
        );

        return dispatcherMethod;
    }

    private JCStatement throwExceptionStatement(String exceptionClass, String exceptionMessage, JavacNode node) {
        JavacTreeMaker treeMaker = node.getTreeMaker();
        //TODO: replace with appropriate exception:
        JCExpression runtimeExceptionType = genTypeRef(node, exceptionClass);
        JCExpression expression = treeMaker.NewClass(
                null,
                List.<JCExpression>nil(),
                runtimeExceptionType,
                List.<JCExpression>of(treeMaker.Literal(exceptionMessage)),
                null);
        return treeMaker.Throw(expression);
    }


    private void addSwitchToDispatcher(JCMethodDecl dispatcher, JCMethodDecl delegate, JCMethodDecl evaluationMethod, ToggleOn annotation, JavacNode classNode) {
        ArrayList<JCStatement> statements = new ArrayList<JCStatement>(dispatcher.body.stats);
        JCStatement throwException = statements.remove(statements.size() - 1);
        String localVariableNameForSwitch = getSwitchVariableName(annotation.value());
        JCStatement switchInitialization = findSwitchInitialization(localVariableNameForSwitch, statements);
        if (switchInitialization == null) {
            switchInitialization = generateSwitchInitialization(annotation.value(), evaluationMethod, classNode);
            statements.add(switchInitialization);
        }
        JCStatement ifEvalStatement = generateIfEvalStatement(localVariableNameForSwitch, true, delegate, classNode);
        statements.add(ifEvalStatement);
        statements.add(throwException);

        dispatcher.body.stats = List.from(statements.toArray(new JCStatement[]{}));
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
                                //chainDots(classNode, "this", targetMethod.name.toString()),
                                treeMaker.Ident(targetMethod.name),
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

    private JCStatement generateSwitchInitialization(String switchName, JCMethodDecl switchEvaluationMethod, JavacNode classNode) {
        JavacTreeMaker treeMaker = classNode.getTreeMaker();

        JCMethodInvocation toggleStateEvaluationInvocation = treeMaker.Apply(
                List.<JCExpression>nil(),
                //chainDots(classNode, "this", switchEvaluationMethod.name.toString()),
                treeMaker.Ident(switchEvaluationMethod.name),
                List.of((JCExpression) treeMaker.Literal(switchName))
        );

        return treeMaker.VarDef(
                treeMaker.Modifiers(0),
                classNode.toName(getSwitchVariableName(switchName)),
//                chainDots(classNode, "java", "lang", "String"),
                treeMaker.TypeIdent(CTC_BOOLEAN),
                toggleStateEvaluationInvocation
        );
    }

    private String getSwitchVariableName(String toggleValue) {
        return escapeString(toggleValue) + "_value";
    }

    private JCStatement findSwitchInitialization(String toggleName, Collection<JCStatement> statements) {
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

    private JCMethodDecl findMethodByName(JavacNode classNode, final String switchMethodName) {
        MethodDeclarationFinder visitor = new MethodDeclarationFinder(switchMethodName);
        classNode.traverse(visitor);
        return visitor.getFoundDeclaration();
    }

    private void assertMethodKind(JavacNode annotationTargetNode) {
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



















