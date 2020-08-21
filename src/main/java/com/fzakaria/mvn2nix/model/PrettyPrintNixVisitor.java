package com.fzakaria.mvn2nix.model;

import java.io.PrintStream;
import java.util.Map;

/*
 * Simple visitor which prints out the model as a Nix expression.
 * Tries very hard to look "pretty".
 */
public class PrettyPrintNixVisitor implements Visitor {

    public static final int DEFAULT_INDENTATION_SIZE = 2;
    private static final char DEFAULT_INDENTATION_CHAR = ' ';

    private final PrintStream stream;

    private final int indentationSize = DEFAULT_INDENTATION_SIZE;
    private final char indentationChar = DEFAULT_INDENTATION_CHAR;

    // the current indentation we are at
    private int indent = 0;

    public PrettyPrintNixVisitor(PrintStream stream) {
        this.stream = stream;
    }

    @Override
    public void visit(MavenNixInformation info) {
        writeln("{");
        for (Map.Entry<String, MavenArtifact> entry : info.dependencies.entrySet()) {
            String name = entry.getKey();
            MavenArtifact artifact = entry.getValue();

            indent(() -> {
                writeln("\"%s\" = {", name);
                indent(() -> {
                    artifact.accept(this);
                });
                writeln("};");
            });
        }
        writeln("}");
    }

    @Override
    public void visit(MavenArtifact artifact) {
        writeln("url = \"%s\";", artifact.url);
        writeln("layout = \"%s\";", artifact.layout);
        writeln("sha256 = \"%s\";", artifact.sha256);
    }

    private void indent(Runnable run) {
        indent += 1;
        run.run();
        indent -= 1;
    }

    private void write(final String format, Object... args){
        for(int i = 0; i < (indent * indentationSize); i++){
            stream.append(indentationChar);
        }
        stream.append(String.format(format, args));
    }

    private void writeln(final String format, Object... args) {
        write(format, args);
        stream.append('\n');
    }
}
