package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import lombok.ToggleProvider;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleToggleProvider extends JavacAnnotationHandler<ToggleProvider> {
    @Override
    public void handle(AnnotationValues<ToggleProvider> annotation, JCAnnotation ast, JavacNode annotationNode) {
        JavacNode owner = annotationNode.up();
        if (owner.getKind() != AST.Kind.METHOD) {
            annotationNode.addError("@ToggleProvider is legal only on methods.");
        }

        //TODO: add return type check (should only return boolean)
        /*((JCMethodDecl) owner.get()).getReturnType();*/

        //TODO: add arguments check (should only accept String)
    }
}
