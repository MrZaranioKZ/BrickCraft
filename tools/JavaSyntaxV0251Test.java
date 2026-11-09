import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
public final class JavaSyntaxV0251Test {
    public static void main(String[] args) throws IOException {
        Path root=Path.of("src/main/java/fr/brickcraft");
        List<Path> sources;
        try(var stream=Files.walk(root)){
            sources=stream.filter(p->p.toString().endsWith(".java")).sorted().toList();
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("JDK 21 required, not JRE");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)){
            var files=fm.getJavaFileObjectsFromPaths(sources);
            JavacTask task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none","--release","21"),null,files);
            task.parse(); // syntax only; does NOT resolve Minecraft/Fabric classes
        }
        var errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).toList();
        if(!errors.isEmpty()){
            for(var diagnostic:errors)System.err.println(diagnostic);
            System.exit(1);
        }
        System.out.println("OK: parsed "+sources.size()+" Java 21 sources (syntax only)");
    }
}
