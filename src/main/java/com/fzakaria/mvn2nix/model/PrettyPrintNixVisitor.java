package com.fzakaria.mvn2nix.model;

import java.io.PrintWriter;
import java.util.Map;

/*
 * Simple visitor which prints out the model as a Nix expression.
 * Tries very hard to look "pretty".
 */
public class PrettyPrintNixVisitor implements Visitor {

    public static final int DEFAULT_INDENTATION_SIZE = 2;
    private static final char DEFAULT_INDENTATION_CHAR = ' ';

    private final PrintWriter writer;

    private final int indentationSize = DEFAULT_INDENTATION_SIZE;
    private final char indentationChar = DEFAULT_INDENTATION_CHAR;

    // the current indentation we are at
    private int indent = 0;

    public PrettyPrintNixVisitor(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public void visit(MavenNixInformation info) {
        writeln("{");
        indent(() -> {
            writeln("project = {");
            indent(() -> info.project.accept(this));
            writeln("};");
            writeln("dependencies = {");
            indent(() -> {
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
            });
            writeln("};");
        });
        writeln("}");
    }

    @Override
    public void visit(MavenArtifact artifact) {
        writeln("url = \"%s\";", artifact.url);
        writeln("layout = \"%s\";", artifact.layout);
        writeln("sha256 = \"%s\";", artifact.sha256);
    }

    @Override
    public void visit(Project project) {
        writeln("group = \"%s\";", project.group);
        writeln("name = \"%s\";", project.name);
        writeln("version = \"%s\";", project.version);
    }

    private void indent(Runnable run) {
        indent += 1;
        run.run();
        indent -= 1;
    }

    private void write(final String format, Object... args) {
        for(int i = 0; i < (indent * indentationSize); i++){
            writer.print(indentationChar);
        }
        writer.printf(format, args);
    }

    private void writeln(final String format, Object... args) {
        write(format, args);
        writer.println("");
    }
}
