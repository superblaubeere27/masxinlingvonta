package net.superblaubeere27.masxinlingvonta.test.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "mlv-test", subcommands = {RunCommand.class, RunCfgTest.class})
public class TestCLIMain {

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new TestCLIMain());

        cli.execute(args);
    }

}
