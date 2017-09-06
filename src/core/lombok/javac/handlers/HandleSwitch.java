package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import lombok.core.AnnotationValues;
import lombok.experimental.Switch;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleSwitch extends JavacAnnotationHandler<Switch> {
    @Override
    public void handle(AnnotationValues<Switch> annotation, JCAnnotation ast, JavacNode annotationNode) {
        SwitchAnnotationsConverter.toGenerateDispatcherAnnotation(Switch.class, ast, annotationNode);
    }
}
