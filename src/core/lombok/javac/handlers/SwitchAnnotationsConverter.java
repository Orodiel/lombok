package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.util.List;
import lombok.core.AST;
import lombok.experimental.GenerateDispatcher;
import lombok.experimental.Switch;
import lombok.experimental.ToggleOn;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;

import java.lang.annotation.Annotation;
import java.util.ArrayList;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;


public class SwitchAnnotationsConverter {
    static void toGenerateDispatcherAnnotation(Class<? extends Annotation> switchAnnotationClass, JCAnnotation ast, JavacNode annotationNode) {
        JavacNode targetNode = annotationNode.up();
        if (targetNode.getKind() != AST.Kind.METHOD) {
            annotationNode.addError("Annotation only allowed on methods");
            throw new RuntimeException("Annotation only allowed on methods");
        }

        deleteAnnotationIfNeccessary(annotationNode, switchAnnotationClass);

        JavacTreeMaker treeMaker = targetNode.getTreeMaker();
        JCAnnotation generateDispatcherAnnotation = treeMaker.Annotation(
                chainDotsString(annotationNode, GenerateDispatcher.class.getCanonicalName()),
                convertArguments(ast.args, annotationNode, switchAnnotationClass));

        JCMethodDecl targetMethod = (JCMethodDecl) targetNode.get();
        targetMethod.mods.annotations = targetMethod.mods.annotations.prepend(generateDispatcherAnnotation);
    }

    private static List<JCExpression> convertArguments(List<JCExpression> args, JavacNode node, Class<? extends Annotation> switchClass) {
        ArrayList<JCExpression> resultArgs = new ArrayList<JCExpression>();

        JavacTreeMaker treeMaker = node.getTreeMaker();
        JCAssign isToggleArg = treeMaker.Assign(
                treeMaker.Ident(node.toName("isToggle")),
                treeMaker.Literal(CTC_BOOLEAN,
                        switchClass == Switch.class
                                ? 0
                                : 1
                )
        );
        resultArgs.add(isToggleArg);

        if (switchClass != Switch.class) {
            JCAssign switchValueArg = treeMaker.Assign(
                    treeMaker.Ident(node.toName("switchValue")),
                    treeMaker.Literal(
                            switchClass == ToggleOn.class
                                    ? "true"
                                    : "false"
                    )
            );
            resultArgs.add(switchValueArg);
        }

        for (JCExpression arg : args) {
            JCAssign assign = (JCAssign) arg;
            JCIdent lhs = (JCIdent) assign.lhs;
            String argName = lhs.name.toString();
            if ("name".equals(argName)) {
                JCAssign switchNameArg = treeMaker.Assign(treeMaker.Ident(node.toName("switchName")), assign.rhs);
                resultArgs.add(switchNameArg);
            } else if ("value".equals(argName)) {
                JCAssign switchValueArg = treeMaker.Assign(treeMaker.Ident(node.toName("switchValue")), assign.rhs);
                resultArgs.add(switchValueArg);
            } else {
                resultArgs.add(arg);
            }
        }

        return List.from(resultArgs.toArray(new JCExpression[]{}));
    }
}