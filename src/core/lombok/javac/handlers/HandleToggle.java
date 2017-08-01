package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;

import com.sun.tools.javac.util.Name;
import lombok.Toggle;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import netscape.javascript.JSException;
import org.mangosdk.spi.ProviderFor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.sun.tools.javac.util.List.nil;
import static lombok.Toggle.ToggleState.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleToggle extends JavacAnnotationHandler<Toggle> {

    // 1) check if target is a method [done]
    // 1.5) delete annotation
    // 2) find mirror/opposite annotation/method (add error if absent)
    // 3) do signature compatibility check (skip for now)
    // 4) rename methods to "method"_$old or something and access modifiers
    // 5) replace old methods with evaluation plus if statement; (only for Toggle for now)
    @Override
    public void handle(AnnotationValues<Toggle> annotation, JCAnnotation ast, JavacNode annotationNode) {
        // 1) check if target is a method
        JavacNode owner = annotationNode.up();
        if (owner.getKind() != AST.Kind.METHOD) {
            annotationNode.addError("@Toggle is legal only on methods.");
        }

        // 1.5) delete annotation
        deleteAnnotationIfNeccessary(annotationNode, Toggle.class);

        // 2) find mirror annotation/method
        Toggle toggle = annotation.getInstance();
        String toggleName = toggle.value();
        Toggle.ToggleState state = toggle.state();

        List<Toggle> toggles = new ArrayList<Toggle>();
        Collection<JavacNode> fields = annotationNode.upFromAnnotationToFields();
        JavacNode targetField = null;
        for (JavacNode field : fields) {
            JCMethodDecl method = (JCMethodDecl) field.get();
            com.sun.tools.javac.util.List<JCAnnotation> annotations = method.getModifiers().annotations;
            for (JCAnnotation methodAnnotation : annotations) {
                if (methodAnnotation instanceof Toggle) {
                    Toggle toggleAnnotation = (Toggle) methodAnnotation;
                    if (toggleAnnotation.value().equals(toggleName)) {
                        toggles.add(toggleAnnotation);
                        targetField = field;
                    }
                }
            }
        }

        if (toggles.size() != 1) {
            annotationNode.addError("Must be exactly two @Toggle with same toggle name.");
        }
        Toggle oppositeToggle = toggles.get(0);
        if (oppositeToggle.state() == state) {
            annotationNode.addError("Opposite @Toggle must have opposite state.");
        }

        // toggle
        // owner

        // oppositeToggle
        // targetField

        // 4) rename methods to "method"_$old or something and access modifiers
        JCMethodDecl methodDeclaration = (JCMethodDecl) owner.get();
        Name oldName = methodDeclaration.name;
        methodDeclaration.name = owner.toName(oldName.toString() + "_$toggle_" + toggle.state());

        JCMethodDecl oppositeMethodDeclaration = (JCMethodDecl) targetField.get();
        Name oldOppositeName = oppositeMethodDeclaration.name;
        oppositeMethodDeclaration.name = owner.toName(oldOppositeName.toString() + "_$toggle_" + oppositeToggle.state());

        // 5) replace old methods with evaluation plus if statement; (only for Toggle for now)
        // only one new method and only with void as a return type
        JCClassDecl classDecl = (JCClassDecl) owner.up().get();
        com.sun.tools.javac.util.List<JCTree> members = classDecl.getMembers();
        JCMethodDecl stateGetter = null;
        for (JCTree member : members) {
            if (member instanceof JCMethodDecl) {
                JCMethodDecl methodDecl = (JCMethodDecl) member;
                if (methodDecl.getName().toString().endsWith("getToggleState")) {
                    stateGetter = methodDecl;
                    break;
                }
            }
        }
        if (stateGetter == null) {
            annotationNode.addError("No method \"getToggleState\" was found.");
        }

        // body generation:
        JavacTreeMaker maker = annotationNode.getTreeMaker();
        Name aThis = annotationNode.toName("this");
        Name getter = annotationNode.toName("getToggleState");

        JCMethodInvocation toggleStateEvaluationInvocation = maker.Apply(
                com.sun.tools.javac.util.List.<JCExpression>nil(),
                maker.Select(maker.Ident(aThis), getter),
                com.sun.tools.javac.util.List.of((JCExpression) maker.Literal(toggleName))
        );

        JCMethodInvocation invokeThis = maker.Apply(
                com.sun.tools.javac.util.List.<JCExpression>nil(),
                maker.Select(maker.Ident(aThis), methodDeclaration.name),
                com.sun.tools.javac.util.List.<JCExpression>nil()
        );

        JCMethodInvocation invokeThat = maker.Apply(
                com.sun.tools.javac.util.List.<JCExpression>nil(),
                maker.Select(maker.Ident(aThis), oppositeMethodDeclaration.name),
                com.sun.tools.javac.util.List.<JCExpression>nil()
        );

        com.sun.tools.javac.util.List<JCStatement> statements = com.sun.tools.javac.util.List.nil();
        statements.add(
                maker.If(
                        maker.Binary(
                                Javac.CTC_EQUAL,
                                toggleStateEvaluationInvocation,
                                maker.Literal(Javac.CTC_BOOLEAN, true)
                        ),
                        maker.Exec((state ==  ON) ? invokeThis : invokeThat),
                        maker.Exec((state == OFF) ? invokeThat : invokeThis)
                )
        );

        JCBlock newBody = maker.Block(0, statements);
        //

        JCMethodDecl newMethod = maker.MethodDef(
                oppositeMethodDeclaration.mods,
                oppositeMethodDeclaration.name,
                oppositeMethodDeclaration.restype,
                oppositeMethodDeclaration.typarams,
                oppositeMethodDeclaration.params,
                oppositeMethodDeclaration.thrown,
                newBody,
                oppositeMethodDeclaration.defaultValue
        );


        injectMethod(owner.up(), newMethod);
    }
}
