package com.fzakaria.mvn2nix.cmd;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Maven2NixIT {

    @Test
    public void simpleTest() {
        Maven2nix app = new Maven2nix();
        CommandLine cmd = new CommandLine(app);
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));

    }
}
