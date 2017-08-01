package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import lombok.ToggleOn;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleToggleOn extends JavacAnnotationHandler<ToggleOn> {
    @Override
    public void handle(AnnotationValues<ToggleOn> annotation, JCAnnotation ast, JavacNode annotationNode) {
        //TODO: implement
    }
}
