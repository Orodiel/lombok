package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import lombok.ToggleProvider;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleToggleProvider extends JavacAnnotationHandler<ToggleProvider> {
    @Override
    public void handle(AnnotationValues<ToggleProvider> annotation, JCAnnotation ast, JavacNode annotationNode) {
        //TODO: implement
    }
}
