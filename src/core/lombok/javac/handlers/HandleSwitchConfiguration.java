package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.experimental.GenerateDispatcher;
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
public class HandleSwitchConfiguration extends JavacAnnotationHandler<GenerateDispatcher> {
    public static final String EVALUATION_METHOD_NAME = "__evaluateSwitch";
    private JavacNode annotatedNode;
    private JavacNode classNode;
    private GenerateDispatcher annotation;
    private JavacTreeMaker treeMaker;

    @Override
    public void handle(AnnotationValues<GenerateDispatcher> annotationValues, JCAnnotation ast, JavacNode annotationNode) {
        annotation = annotationValues.getInstance();
        annotatedNode = annotationNode.up();
        classNode = annotatedNode.up();

        assertMethodKind(annotatedNode);
        removeHandlingAnnotation(annotationNode);

        treeMaker = classNode.getTreeMaker();

        JCMethodDecl publicProxyMethod = (JCMethodDecl) annotatedNode.get();
        JCMethodDecl hiddenDelegateMethod = shallowCopy(publicProxyMethod);
        hiddenDelegateMethod.name = classNode.toName(generateDelegateMethodName(hiddenDelegateMethod.name.toString()));
        hiddenDelegateMethod.mods = resetAccessModifiers(hiddenDelegateMethod.mods);
        applyProvidedSettings(annotation, publicProxyMethod, hiddenDelegateMethod);
        addMethodToClass(hiddenDelegateMethod);

        JCMethodDecl dispatcherMethod = setupDispatcherMethod();
        JCMethodDecl switchEvaluationMethod = findSwitchEvaluationMethod();
        addSwitchToDispatcher(dispatcherMethod, switchEvaluationMethod, hiddenDelegateMethod);

        publicProxyMethod.body = generateDispatcherCall(dispatcherMethod);
    }

    private void assertMethodKind(JavacNode annotationTargetNode) {
        if (annotationTargetNode.getKind() != AST.Kind.METHOD) {
            annotationTargetNode.addError("Annotation only allowed on methods");
            throw new RuntimeException("Annotation used not on a method");
        }
    }

    private void removeHandlingAnnotation(JavacNode annotationNode) {
        JavacHandlerUtil.deleteAnnotationIfNeccessary(annotationNode, GenerateDispatcher.class);
    }

    private JCMethodDecl shallowCopy(JCMethodDecl method) {
        return treeMaker.MethodDef(
                method.mods,
                method.name,
                method.restype,
                method.typarams,
                method.params,
                method.thrown,
                method.body,
                method.defaultValue
        );
    }

    private String generateDelegateMethodName(String originalName) {
        return originalName + "$_old";
    }

    private void applyProvidedSettings(GenerateDispatcher annotation, JCMethodDecl publicProxyMethod, JCMethodDecl hiddenDelegateMethod) {
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

    private JCModifiers resetAccessModifiers(JCModifiers modifiers) {
        return treeMaker.Modifiers((modifiers.flags | Flags.AccessFlags) ^ Flags.AccessFlags, modifiers.annotations);
    }

    private void removeAnnotations(JCMethodDecl method) {
        method.mods.annotations = List.nil();
    }

    private void addMethodToClass(JCMethodDecl method) {
        injectMethod(classNode, method);
    }

    private JCMethodDecl setupDispatcherMethod() {
        String dispatcherMethodName = generateDispatcherName(annotation);
        JavacNode classNode = annotatedNode.up();
        JCMethodDecl dispatcherMethod = findMethodByName(dispatcherMethodName);
        if (dispatcherMethod == null) {
            dispatcherMethod = generateDispatcherMethod(dispatcherMethodName);
            addMethodToClass(dispatcherMethod);
        }
        return dispatcherMethod;
    }

    private String generateDispatcherName(GenerateDispatcher annotation) {
        String groupName = annotation.groupName();
        if (GenerateDispatcher.DEFAULT_GROUP_NAME.equals(groupName)) {
            groupName = annotation.switchName();
        }
        return "dispatcher_for_" + escapeString(groupName);
    }

    private String escapeString(String string) {
        return string.replaceAll("[^a-zA-Z0-9_$]", "$$");
    }

    private JCMethodDecl findMethodByName(final String method) {
        MethodDeclarationFinder visitor = new MethodDeclarationFinder(method);
        classNode.traverse(visitor);
        return visitor.getFoundDeclaration();
    }

    private JCMethodDecl generateDispatcherMethod(String dispatcherMethodName) {
        JCMethodDecl annotatedMethod = ((JCMethodDecl) annotatedNode.get());
        JCMethodDecl dispatcherMethod = shallowCopy(annotatedMethod);
        dispatcherMethod.name = annotatedNode.toName(dispatcherMethodName);
        dispatcherMethod.mods = resetAccessModifiers(dispatcherMethod.mods);
        dispatcherMethod.mods.flags |= Flags.PRIVATE;
        dispatcherMethod.body = treeMaker.Block(
                0,
                List.of(
                        throwExceptionStatement(
                                //TODO: replace with appropriate exception:
                                "java.lang.RuntimeException",
                                "Uncovered case reached"
                        )
                )
        );

        return dispatcherMethod;
    }

    private JCStatement throwExceptionStatement(String exceptionClass, String exceptionMessage) {
        JCExpression runtimeExceptionType = genTypeRef(annotatedNode, exceptionClass);
        JCExpression expression = treeMaker.NewClass(
                null,
                List.nil(),
                runtimeExceptionType,
                List.of(treeMaker.Literal(exceptionMessage)),
                null);
        return treeMaker.Throw(expression);
    }

    private JCMethodDecl findSwitchEvaluationMethod() {
        JCMethodDecl switchEvaluationMethod = findMethodByName(EVALUATION_METHOD_NAME);
        if (switchEvaluationMethod == null) {
            annotatedNode.addError("Was expecting to find evaluation method");
            throw new RuntimeException("Failed to find evaluation method");
        }
        return switchEvaluationMethod;
    }

    private void addSwitchToDispatcher(JCMethodDecl dispatcher, JCMethodDecl evaluationMethod, JCMethodDecl delegate) {
        ArrayList<JCStatement> statements = new ArrayList<JCStatement>(dispatcher.body.stats);
        JCStatement throwException = statements.remove(statements.size() - 1);
        String switchName = annotation.switchName();
        String switchVariableName = getSwitchVariableName(switchName);
        JCStatement switchInitialization = findSwitchInitialization(switchVariableName, statements);
        if (switchInitialization == null) {
            switchInitialization = generateSwitchInitialization(evaluationMethod, switchName, switchVariableName);
            statements.add(switchInitialization);
        }
        JCStatement ifEvalStatement = generateSwitchBranch(switchVariableName, annotation.switchValue(), delegate);
        statements.add(ifEvalStatement);
        statements.add(throwException);

        dispatcher.body.stats = List.from(statements.toArray(new JCStatement[]{}));
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

    private JCStatement generateSwitchInitialization(JCMethodDecl evaluationMethod, String switchName, String variableName) {
        JCMethodInvocation toggleStateEvaluationInvocation = treeMaker.Apply(
                List.nil(),
                treeMaker.Ident(evaluationMethod.name),
                List.of(treeMaker.Literal(switchName))
        );

        return treeMaker.VarDef(
                treeMaker.Modifiers(0),
                classNode.toName(variableName),
                annotation.isToggle()
                        ? treeMaker.TypeIdent(CTC_BOOLEAN)
                        : chainDots(classNode, "java", "lang", "String"),
                toggleStateEvaluationInvocation
        );
    }

    private JCStatement generateSwitchBranch(String variableName, String targetValue, JCMethodDecl targetMethod) {
        JavacTreeMaker treeMaker = classNode.getTreeMaker();
        return treeMaker.If(
                generateEqualityCheck(variableName, targetValue),
                generateSwitchExecution(targetMethod),
                null);
    }

    private JCStatement generateSwitchExecution(JCMethodDecl targetMethod) {
        if (!targetMethod.restype.toString().equals("void")) {
            return treeMaker.Return(
                    treeMaker.Apply(
                            List.nil(),
                            treeMaker.Ident(targetMethod.name),
                            generateArgList(targetMethod.params)
                    )
            );
        }
        throw new RuntimeException("Procedures aren't supported yet");
    }

    private JCExpression generateEqualityCheck(String variableName, String value) {
        if (annotation.isToggle()) {
            return generateToggleEqualityCheck(variableName, "true".equals(value));
        } else {
            return generateSwitchEqualityCheck(variableName, value);
        }
    }

    private JCMethodInvocation generateSwitchEqualityCheck(String variableName, String value) {
        return treeMaker.Apply(
                List.nil(),
                treeMaker.Select(treeMaker.Literal(value), classNode.toName("equals")),
                List.of(treeMaker.Ident(annotatedNode.toName(variableName))));
    }

    private JCBinary generateToggleEqualityCheck(String variableName, boolean value) {
        return treeMaker.Binary(
                CTC_EQUAL,
                treeMaker.Ident(classNode.toName(variableName)),
                treeMaker.Literal(CTC_BOOLEAN, value ? 1 : 0));
    }

    private List<JCExpression> generateArgList(List<JCVariableDecl> params) {
        ArrayList<JCExpression> result = new ArrayList<JCExpression>();
        for (JCVariableDecl variableDeclaration : params) {
            result.add(treeMaker.Ident(variableDeclaration.name));
        }
        return List.from(result.toArray(new JCExpression[]{}));
    }

    private JCBlock generateDispatcherCall(JCMethodDecl dispatcherMethod) {
        return treeMaker.Block(0, List.of(
                treeMaker.Return(
                        treeMaker.Apply(
                                null,
                                treeMaker.Ident(dispatcherMethod.name),
                                generateArgList(dispatcherMethod.params)
                        )
                )
        ));
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
