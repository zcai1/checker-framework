package org.checkerframework.dataflow.cfg;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.analysis.Analysis;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.analysis.TransferFunction;

/**
 * TODO: better name?
 * @author charleszhuochen
 *
 */
public class CFGVisualizeLauncher {

    /** Main method. */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        String input = args[0];
        String output = args[1];
        File file = new File(input);
        if (!file.canRead()) {
            JavaSource2CFG.printError("Cannot read input file: " + file.getAbsolutePath());
            printUsage();
            System.exit(1);
        }

        String method = "test";
        String clas = "Test";
        boolean pdf = false;
        boolean error = false;

        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("-pdf")) {
                pdf = true;
            } else if (args[i].equals("-method")) {
                if (i >= args.length - 1) {
                    // TODO: extract this static util method out to Util class if we accumulate enough number of this kind of util methods
                    JavaSource2CFG.printError("Did not find <name> after -method.");
                    continue;
                }
                i++;
                method = args[i];
            } else if (args[i].equals("-class")) {
                if (i >= args.length - 1) {
                    JavaSource2CFG.printError("Did not find <name> after -class.");
                    continue;
                }
                i++;
                clas = args[i];
            } else {
                JavaSource2CFG.printError("Unknown command line argument: " + args[i]);
                error = true;
            }
        }

        if (error) {
            System.exit(1);
        }

        generateDOTofCFGWithoutAnalysis(input, output, method, clas, pdf);
    }

    public static void generateDOTofCFGWithoutAnalysis(
            String inputFile, String outputDir, String method, String clas, boolean pdf) {
        generateDOTofCFG(inputFile, outputDir, method, clas, pdf, null);
    }

    /**
     * Generate the DOT representation of the CFG for a method.
     *
     * @param inputFile
     *            Java source input file.
     * @param outputDir
     *            Source output directory.
     * @param method
     *            Method name to generate the CFG for.
     * @param pdf
     *            Also generate a PDF?
     * @param analysis
     *            Analysis to perform befor the visualization (or
     *            <code>null</code> if no analysis is to be performed).
     */
    public static <V extends AbstractValue<V>, S extends Store<S>, T extends TransferFunction<V, S>>
            void generateDOTofCFG(
                    String inputFile,
                    String outputDir,
                    String method,
                    String clas,
                    boolean pdf,
                    Analysis<V, S, T> analysis) {
        ControlFlowGraph cfg = JavaSource2CFG.generateMethodCFG(inputFile, clas, method);
        if (analysis != null) {
            analysis.performAnalysis(cfg);
        }

        Map<String, Object> args = new HashMap<>();
        args.put("outdir", outputDir);
        args.put("checkerName", "");

        CFGVisualizer<V, S, T> viz = new DOTCFGVisualizer<V, S, T>();
        viz.init(args);
        Map<String, Object> res = viz.visualize(cfg, cfg.getEntryBlock(), analysis);
        viz.shutdown();

        if (pdf) {
            producePDF((String) res.get("dotFileName"));
        }
    }

    /**
     * Invoke DOT to generate a PDF.
     */
    protected static void producePDF(String file) {
        try {
            String command = "/usr/local/bin/dot -Tpdf \"" + file + "\" -o \"" + file + ".pdf\"";
            Process child = Runtime.getRuntime().exec(command);
            child.waitFor();
            System.out.println("generating pdf, command is:\n" + command);
            System.out.println("success!");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    //TODO refresh usage
    /** Print usage information. */
    protected static void printUsage() {
        System.out.println(
                "Generate the control flow graph of a Java method, represented as a DOT graph.");
        System.out.println(
                "Parameters: <inputfile> <outputdir> [-method <name>] [-class <name>] [-pdf]");
        System.out.println("    -pdf:    Also generate the PDF by invoking 'dot'.");
        System.out.println("    -method: The method to generate the CFG for (defaults to 'test').");
        System.out.println(
                "    -class:  The class in which to find the method (defaults to 'Test').");
    }
}
