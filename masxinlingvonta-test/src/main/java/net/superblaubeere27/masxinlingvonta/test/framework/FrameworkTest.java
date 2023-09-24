package net.superblaubeere27.masxinlingvonta.test.framework;

import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvonta.test.framework.antlr4.mlvirLexer;
import net.superblaubeere27.masxinlingvonta.test.framework.antlr4.mlvirParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.BitSet;
import java.util.stream.Collectors;

public class FrameworkTest {

    public static void main(String[] args) throws IOException {
        String input = new String(FrameworkTest.class.getResourceAsStream("/test.mlvir").readAllBytes());

        mlvirLexer lexer = new mlvirLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        mlvirParser parser = new mlvirParser(tokens);

        var lines = input.lines().collect(Collectors.toList());

        parser.addErrorListener(new ANTLRErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                System.out.println("file:" + line + ":" + charPositionInLine + ": error: " + msg);
                System.out.println(lines.get(line - 1));

                System.out.println(" ".repeat(charPositionInLine) + "^");
            }

            @Override
            public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {

            }

            @Override
            public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {

            }

            @Override
            public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {

            }
        });

        TextParser textParser = new TextParser(parser, new CompilerClass(new ClassNode(), false));

        try {
            textParser.visitFile(parser.file());
        } catch (RecognitionException e) {
            parser.notifyErrorListeners(e.getOffendingToken(), e.getMessage(), e);

            throw e;
        }

        System.out.println(textParser.cfg);
    }

}
