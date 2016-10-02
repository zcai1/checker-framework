package org.checkerframework.dataflow.cfg;

import org.checkerframework.dataflow.cfg.CFGProcessor.CFGProcessResult;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

/**
 * Class to generate the DOT representation of the control flow graph of a given
 * method.
 *
 * @author Stefan Heule
 */

public class JavaSource2CFG {

    /**
     * @return the AST of a specific method in a specific class as well as the
     *         {@link CompilationUnitTree} in a specific file (or null they do
     *         not exist).
     */
    public static ControlFlowGraph generateMethodCFG(
            String file, String clas, final String method) {

        CFGProcessor cfgProcessor = new CFGProcessor(clas, method);

        Context context = new Context();

        JavaCompiler javac = new JavaCompiler(context);

        javac.attrParseOnly = true;
        JavacFileManager fileManager = (JavacFileManager) context
                .get(JavaFileManager.class);

        JavaFileObject l = fileManager
                .getJavaFileObjectsFromStrings(List.of(file)).iterator().next();

        PrintStream err = System.err;

        try {
            // redirect syserr to nothing (and prevent the compiler from issuing
            // warnings about our exception.
            System.setErr(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                }
            }));
            javac.compile(List.of(l), List.of(clas), List.of(cfgProcessor));
        } catch (Throwable e) {
            // ok
        } finally {
            System.setErr(err);
        }

        CFGProcessResult res = cfgProcessor.getCFGProcessResult();

        if (res == null) {
            printError("internal error in type processor! method typeProcessOver() doesn't get called.");
            // TODO: directly exit is not friendly, refactor this to using throw-catch
            System.exit(1);
        }

        if (!res.isSuccess()) {
            printError(res.getErrMsg());
            // TODO: directly exit is not friendly, refactor this to using throw-catch
            System.exit(1);
        }

        return res.getCFG();
    }

    /** Print an error message. */
    protected static void printError(String string) {
        System.err.println("ERROR: " + string);
    }
}
