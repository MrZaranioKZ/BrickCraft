import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class TEST_SYNTAX_V017 {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("JDK requis");
        Path folder=Path.of(args.length==0?"src/main/java":args[0]);
        List<File> files;
        try(var paths=Files.walk(folder)){
            files=paths.filter(p->p.toString().endsWith(".java"))
                .map(Path::toFile).toList();
        }
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units=fm.getJavaFileObjectsFromFiles(files);
            JavacTask task=(JavacTask)compiler.getTask(new StringWriter(),fm,diagnostics,List.of("--release","21","-proc:none"),null,units);
            task.parse();
            for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)
                throw new IllegalStateException("Syntaxe Java invalide : "+d);
        }
        System.out.println("OK : "+files.size()+" sources Java 21 analysées (syntaxe uniquement, sans Fabric)");
    }
}
