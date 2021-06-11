package org.checkerframework.dataflow.util;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.Trees;

import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.analysis.AnalysisResult;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.javacutil.BugInCF;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TypeRefinementVisualizer {
    /** The line separator. */
    private final String lineSeparator = System.lineSeparator();

    /** The output directory. */
    private final String outDir;

    /** Path of the file that stores a mapping from source file to output file. */
    private final String storedMapping;

    /** Mapping from source file URI to output file name. */
    private final Map<URI, String> srcToOutput = new HashMap<>();

    public TypeRefinementVisualizer(String outDir, String checkerName) {
        this.outDir = outDir;
        this.storedMapping = outDir + "/fileMapping-" + checkerName + ".txt";
    }

    public void visualize(
            CompilationUnitTree root,
            Trees trees,
            AnalysisResult<? extends AbstractValue<?>, ? extends Store<?>> analysisResult) {
        String outputPath = getOutputPath(root);

        try (BufferedWriter out = new BufferedWriter(new FileWriter(outputPath, true))) {
            for (Map.Entry<Node, ?> entry : analysisResult.getNodeValues().entrySet()) {
                // Only consider types of local variables.
                if (!(entry.getKey() instanceof LocalVariableNode)) continue;

                LocalVariableNode node = (LocalVariableNode) entry.getKey();
                String identifier = node.getName();
                long startPos = trees.getSourcePositions().getStartPosition(root, node.getTree());
                LineMap lineMap = root.getLineMap();

                // TODO(Zhiping): the start position is not necessarily pointing to the identifier
                out.write(String.valueOf(lineMap.getLineNumber(startPos)));
                out.write(',');
                out.write(String.valueOf(lineMap.getColumnNumber(startPos)));
                out.write(',');
                out.write(identifier);
                out.write(',');
                out.write(entry.getValue().toString());
                out.write(lineSeparator);
            }
        } catch (IOException e) {
            throw new BugInCF(e);
        }
    }

    private String getOutputPath(CompilationUnitTree root) {
        URI srcUri = root.getSourceFile().toUri();
        if (!"file".equalsIgnoreCase(srcUri.getScheme())) {
            throw new BugInCF("Unexpected source file uri: " + srcUri);
        }

        if (srcToOutput.containsKey(srcUri)) {
            return outDir + "/" + srcToOutput.get(srcUri);
        }

        // If there's no existing mapping, create a new file name with UUID.
        String outputName = UUID.randomUUID() + ".txt";
        String outputPath = outDir + "/" + outputName;

        // Create the new file and write "[<Source File URI>]".
        try (BufferedWriter out = new BufferedWriter(new FileWriter(outputPath))) {
            out.write('[');
            out.write(srcUri.toString());
            out.write(']');
            out.write(lineSeparator);
        } catch (IOException e) {
            throw new BugInCF(e);
        }

        // Write the new mapping to storedMapping.
        try (BufferedWriter out = new BufferedWriter(new FileWriter(storedMapping, true))) {
            out.write(srcUri.toString());
            out.write(',');
            out.write(outputName);
            out.write(lineSeparator);
        } catch (IOException e) {
            throw new BugInCF(e);
        }

        srcToOutput.put(srcUri, outputName);
        return outputPath;
    }
}
