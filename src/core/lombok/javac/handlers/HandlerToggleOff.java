package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import lombok.core.AnnotationValues;
import lombok.experimental.ToggleOff;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(JavacAnnotationHandler.class)
public class HandlerToggleOff extends JavacAnnotationHandler<ToggleOff> {
    @Override
    public void handle(AnnotationValues<ToggleOff> annotation, JCAnnotation ast, JavacNode annotationNode) {
        SwitchAnnotationsConverter.toGenerateDispatcherAnnotation(ToggleOff.class, ast, annotationNode);
    }
}
