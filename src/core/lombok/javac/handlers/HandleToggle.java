package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCBlock;

import com.sun.tools.javac.util.Name;
import lombok.Toggle;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import java.util.HashMap;
import java.util.Map;

import static lombok.Toggle.ToggleState.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleToggle extends JavacAnnotationHandler<Toggle> {

    static class TogglePair {
        public JCMethodDecl on;
        public JCMethodDecl off;
        public JCMethodDecl oldOn;
        public JCMethodDecl oldOff;
    }

    @Override
    public void handle(AnnotationValues<Toggle> annotation, JCAnnotation ast, JavacNode annotationNode) {
        JavacNode classDeclWrapper = annotationNode.up().up();
        JCClassDecl classDecl = (JCClassDecl) classDeclWrapper.get();
//        Map<String, TogglePair> toggles = findAllToggles(classDecl);
        Map<String, TogglePair> toggles = new HashMap<String, TogglePair>();
        deleteAnnotationIfNeccessary(annotationNode, Toggle.class);
        TogglePair pair = new TogglePair();
        pair.on = (JCMethodDecl) annotationNode.up().get();
        toggles.put("Test", pair);
        for (TogglePair togglePair : toggles.values()) {
            handleToggle(togglePair, classDeclWrapper);
        }
    }

    private void handleToggle(TogglePair togglePair, JavacNode classDecl) {
        validateTogglePairDefinition(togglePair);
        renameOldMethods(togglePair, classDecl);
        constructNewMethods(togglePair, classDecl);
        injectNewMethods(togglePair);
    }

    private void injectNewMethods(TogglePair togglePair) {

    }

    private void constructNewMethods(TogglePair togglePair, JavacNode classDeclWrappper) {
        JCClassDecl classDecl = (JCClassDecl) classDeclWrappper.get();
//        com.sun.tools.javac.util.List<JCTree> members = classDecl.getMembers();
//        JCMethodDecl stateGetter = null;
//        for (JCTree member : members) {
//            if (member instanceof JCMethodDecl) {
//                JCMethodDecl methodDecl = (JCMethodDecl) member;
//                if (methodDecl.getName().toString().endsWith("getToggleState")) {
//                    stateGetter = methodDecl;
//                    break;
//                }
//            }
//        }
//        if (stateGetter == null) {
//            classDeclWrappper.addError("No method \"getToggleState\" was found.");
//        }

        // body generation:
        JavacTreeMaker maker = classDeclWrappper.getTreeMaker();
        Name aThis = classDeclWrappper.toName("this");
        Name getter = classDeclWrappper.toName("getToggleState");

        JCMethodInvocation toggleStateEvaluationInvocation = maker.Apply(
                com.sun.tools.javac.util.List.<JCExpression>nil(),
                maker.Select(maker.Ident(aThis), getter),
                com.sun.tools.javac.util.List.of((JCExpression) maker.Literal("Test"))
        );

        JCMethodInvocation invokeThis = maker.Apply(
                com.sun.tools.javac.util.List.<JCExpression>nil(),
                maker.Select(maker.Ident(aThis), classDeclWrappper.toName("test1")),
                com.sun.tools.javac.util.List.<JCExpression>nil()
        );

        JCMethodInvocation invokeThat = maker.Apply(
                com.sun.tools.javac.util.List.<JCExpression>nil(),
                maker.Select(maker.Ident(aThis), classDeclWrappper.toName("test2")),
                com.sun.tools.javac.util.List.<JCExpression>nil()
        );

        Toggle.ToggleState state = ON;
        com.sun.tools.javac.util.List<JCStatement> statements = com.sun.tools.javac.util.List.of(
                (JCStatement)
                maker.If(
                        maker.Binary(
                                Javac.CTC_EQUAL,
                                toggleStateEvaluationInvocation,
                                maker.Literal(Javac.CTC_BOOLEAN, 1)
                        ),
                        maker.Exec((state ==  ON) ? invokeThis : invokeThat),
                        maker.Exec((state == OFF) ? invokeThis : invokeThat)
                )
        );

        JCBlock newBody = maker.Block(0, statements);
        //

        JCMethodDecl newMethod = maker.MethodDef(
                togglePair.on.mods,
                classDeclWrappper.toName(togglePair.on.name.toString() + "_new"),
                togglePair.on.restype,
                togglePair.on.typarams,
                togglePair.on.params,
                togglePair.on.thrown,
                newBody,
                togglePair.on.defaultValue
        );


        injectMethod(classDeclWrappper, newMethod);
    }

    private void renameOldMethods(TogglePair togglePair, JavacNode classDecl) {
        System.out.println("Pair.on.name: [" + togglePair.on.name + "]");
        System.out.println("JavacNode.toName: [" + classDecl.toName(togglePair.on.name.toString() + "_test") + "]");
        System.out.println("Ident: " + classDecl.getTreeMaker().Ident(togglePair.on.name));
        System.out.println("New Ident: " + classDecl.getTreeMaker().Ident(classDecl.toName(togglePair.on.name.toString() + "_test")));
    }

    private void validateTogglePairDefinition(TogglePair togglePair) {
        System.out.println("Validation: skipped for now");
    }

    private Map<String, TogglePair> findAllToggles(JCClassDecl classDecl) {
        return new HashMap<String, TogglePair>();
    }


    // 1) check if target is a method [done]
    // 1.5) delete annotation
    // 2) find mirror/opposite annotation/method (add error if absent)
    // 3) do signature compatibility check (skip for now)
    // 4) rename methods to "method"_$old or something and access modifiers
    // 5) replace old methods with evaluation plus if statement; (only for Toggle for now)
    /*public void handle(AnnotationValues<Toggle> annotation, JCAnnotation ast, JavacNode annotationNode) {
        // 1) check if target is a method
        JavacNode owner = annotationNode.up();
        if (owner.getKind() != AST.Kind.METHOD) {
            annotationNode.addError("@Toggle is legal only on methods.");
        }

        // 1.5) delete annotation
//        deleteAnnotationIfNeccessary(annotationNode, Toggle.class);

        // 2) find mirror annotation/method
        System.out.println("AST: [" + ast + "]");
        System.out.println("args: [" + ast.args + "]");
        System.out.println("annotation type: [" + ast.annotationType + "]");
        System.out.println("class decl?: [" + annotationNode.up().up() + "]");

//        Map<String, TogglePair> toggles = findAllToggles(annotationNode.up().up()); // supposed to be class decl

        Toggle toggle = annotation.getInstance();
        String toggleName = toggle.value();
        Toggle.ToggleState state = toggle.state();

        List<Toggle> toggles = new ArrayList<Toggle>();
        Collection<JavacNode> fields = annotationNode.upFromAnnotationToFields();
        System.out.println(fields.size());
        JavacNode targetField = null;
        for (JavacNode field : fields) {
            System.out.println("" + field + ": " + field.getName());
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
            return;
        }
        Toggle oppositeToggle = toggles.get(0);
        if (oppositeToggle.state() == state) {
            annotationNode.addError("Opposite @Toggle must have opposite state.");
            return;
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
    }*/

}
