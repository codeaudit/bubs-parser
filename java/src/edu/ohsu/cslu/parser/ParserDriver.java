package edu.ohsu.cslu.parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.EnumAliasMap;
import org.kohsuke.args4j.Option;

import cltool.BaseCommandlineTool;
import edu.ohsu.cslu.grammar.ArrayGrammar;
import edu.ohsu.cslu.grammar.GrammarByChildMatrix;
import edu.ohsu.cslu.grammar.GrammarByLeftNonTermHash;
import edu.ohsu.cslu.grammar.GrammarByLeftNonTermList;
import edu.ohsu.cslu.parser.fom.EdgeFOM;
import edu.ohsu.cslu.parser.fom.EdgeFOM.EdgeFOMType;
import edu.ohsu.cslu.parser.traversal.ChartTraversal.ChartTraversalType;
import edu.ohsu.cslu.parser.util.Log;
import edu.ohsu.cslu.parser.util.ParseTree;
import edu.ohsu.cslu.parser.util.ParserUtil;
import edu.ohsu.cslu.parser.util.StringToMD5;

public class ParserDriver extends BaseCommandlineTool {

    @Option(name = "-g", aliases = { "--grammar-file-prefix" }, required = true, metaVar = "prefix", usage = "Grammar file prefix")
    private String pcfgPrefix;

    @Option(name = "-scores", aliases = { "--print-inside-scores" }, usage = "Print inside probabilities")
    private boolean printInsideProbs = false;

    @Option(name = "-unk", aliases = { "--print-unk-labels" }, usage = "Print unknown labels")
    private boolean printUnkLabels = false;

    @Option(name = "-p", aliases = { "--parser", "--parser-implementation" }, metaVar = "parser", usage = "Parser implementation")
    private ParserType parserType = ParserType.ExhaustiveChartParser;

    // TODO Eventually we'd like to make this a command-line option, but not all combinations are implemented yet
    private ChartTraversalType chartTraversalType = ChartTraversalType.LeftRightBottomTopTraversal;

    @Option(name = "-ct", aliases = { "--cell-processing-type" }, metaVar = "type", usage = "Chart cell processing type")
    private ChartCellProcessingType chartCellProcessingType = ChartCellProcessingType.CellCrossList;

    @Option(name = "-fom", aliases = { "--figure-of-merit", "-FOM" }, metaVar = "fom", usage = "Figure of Merit")
    private EdgeFOMType edgeFOMType = EdgeFOMType.Inside;

    @Option(name = "-fomTrain", usage = "Train the specified FOM model")
    private boolean fomTrain = false;

    @Option(name = "-fudge", metaVar = "fudge", usage = "Fudge factor for FOM calculations")
    public static float fudgeFactor = (float) 1.0;

    @Option(name = "-fomModel", metaVar = "file", usage = "FOM model file")
    private String fomModelFileName = null;
    private BufferedReader fomModelStream = null;

    private ArrayGrammar grammar;
    private BufferedWriter outputStream = new BufferedWriter(new OutputStreamWriter(System.out));
    private BufferedReader inputStream = new BufferedReader(new InputStreamReader(System.in));

    public static void main(final String[] args) throws Exception {
        run(args);
    }

    @Override
    public void setup(final CmdLineParser cmdlineParser) throws CmdLineException {
        // setup() is run once for multiple threads
        final String pcfgFileName = pcfgPrefix + ".pcfg";
        final String lexFileName = pcfgPrefix + ".lex";

        try {
            switch (parserType) {
            case ExhaustiveChartParser:
                switch (chartCellProcessingType) {
                case CellCrossList:
                    grammar = new GrammarByLeftNonTermList(pcfgFileName, lexFileName);
                    break;
                case CellCrossHash:
                    grammar = new GrammarByLeftNonTermHash(pcfgFileName, lexFileName);
                    break;
                case CellCrossMatrix:
                    grammar = new GrammarByChildMatrix(pcfgFileName, lexFileName);
                    break;
                case GrammarLoop:
                case GrammarLoopBerkeleyFilter:
                    grammar = new ArrayGrammar(pcfgFileName, lexFileName);
                }
                break;
            // Both agenda parsers use GrammarByLeftNonTermList
            case AgendaParser:
            case AgendaParserWithGhostEdges:
                grammar = new GrammarByLeftNonTermList(pcfgFileName, lexFileName);
                break;
            default:
                throw new CmdLineException(cmdlineParser, "Unsupported parser type: " + parserType);
            }

            if (fomModelFileName != null) {
                fomModelStream = new BufferedReader(new FileReader(fomModelFileName));
            }

            if (edgeFOMType == EdgeFOMType.BoundaryNgram && fomTrain == false && fomModelFileName == null) {
                throw new CmdLineException(cmdlineParser, "BoundaryNgram FOM must also have -fomTrain or -fomModel param set");
            }

        } catch (final IOException e) {
            throw new CmdLineException(cmdlineParser, e);
        }

        final String prefix = "OPTS:";
        String s = "";
        s += prefix + "ParserType=" + parserType + "\n";
        s += prefix + "Traversal=" + chartTraversalType + "\n";
        s += prefix + "CellProcess=" + chartCellProcessingType + "\n";
        s += prefix + "FOM=" + edgeFOMType + "";
        Log.info(0, s);
    }

    @Override
    public void run() throws Exception {
        ParseTree inputTree = null, bestParseTree = null;
        int sentNum = 0;
        long sentStartTimeMS;
        double sentParseTimeSeconds, totalParseTimeSeconds = 0.0;
        String insideProbStr;

        if (fomTrain == true) {
            final EdgeFOM fom = EdgeFOM.create(edgeFOMType, grammar);
            fom.train(inputStream);
            fom.writeModel(outputStream);
            System.exit(0);
        }

        final Parser parser = createParser();

        for (String sentence = inputStream.readLine(); sentence != null; sentence = inputStream.readLine()) {
            if (ParseTree.isBracketFormat(sentence)) {
                inputTree = ParseTree.readBracketFormat(sentence);
                sentence = ParserUtil.join(inputTree.getLeafNodesContent(), " ");
            }
            sentStartTimeMS = System.currentTimeMillis();

            bestParseTree = parser.findBestParse(sentence.trim());

            sentParseTimeSeconds = (System.currentTimeMillis() - sentStartTimeMS) / 1000.0;
            totalParseTimeSeconds += sentParseTimeSeconds;

            if (bestParseTree == null) {
                outputStream.write("No parse found.\n");
                insideProbStr = "-inf";
            } else {
                if (printUnkLabels == false) {
                    bestParseTree.replaceLeafNodes(ParserUtil.tokenize(sentence));
                }
                outputStream.write(bestParseTree.toString(printInsideProbs) + "\n");
                // System.out.println("STAT: sentNum="+sentNum+" inside="+bestParseTree.chartEdge.insideProb);
                insideProbStr = Float.toString(bestParseTree.chartEdge.insideProb);
            }
            // if (inputTree != null) { outputStream.write("GOLD: " + inputTree.toString() + "\n"); }

            final String stats = " sentNum=" + sentNum + " sentLen=" + ParserUtil.tokenize(sentence).length + " md5=" + StringToMD5.computeMD5(sentence) + " seconds="
                    + sentParseTimeSeconds + " inside=" + insideProbStr + " " + parser.getStats();
            outputStream.write("STAT:" + stats + "\n");
            outputStream.flush();
            sentNum++;
        }

        // TODO: allow gold trees as input and report F-score
        // TODO: need to port python tree transforms / de-transforms to Java
        // and either write our own eval or make external call to EVALB
        Log.info(1, "INFO: numSentences=" + sentNum + " totalSeconds=" + totalParseTimeSeconds + " avgSecondsPerSent=" + (totalParseTimeSeconds / sentNum));
    }

    private Parser createParser() throws Exception {
        Parser parser = null;
        EdgeFOM edgeFOM = null;

        switch (parserType) {
        case ExhaustiveChartParser:
            switch (chartCellProcessingType) {
            case CellCrossList:
                parser = new ECPCellCrossList((GrammarByLeftNonTermList) grammar, chartTraversalType);
                break;
            case CellCrossHash:
                parser = new ECPCellCrossHash((GrammarByLeftNonTermHash) grammar, chartTraversalType);
                break;
            case CellCrossMatrix:
                parser = new ECPCellCrossMatrix((GrammarByChildMatrix) grammar, chartTraversalType);
                break;
            case GrammarLoop:
                parser = new ECPGramLoop(grammar, chartTraversalType);
                break;
            case GrammarLoopBerkeleyFilter:
                parser = new ECPGramLoopBerkFilter(grammar, chartTraversalType);
            }
            break;

        case AgendaParser:
            edgeFOM = EdgeFOM.create(edgeFOMType, grammar);
            // TODO: this whole FOM setup is pretty ugly. It needs to be changed
            // TODO: the program should know which FOM to use given the model file
            if (fomModelStream != null) {
                edgeFOM.readModel(fomModelStream);
            }
            parser = new AgendaChartParser((GrammarByLeftNonTermList) grammar, edgeFOM);
            break;

        case AgendaParserWithGhostEdges:
            edgeFOM = EdgeFOM.create(edgeFOMType, grammar);
            parser = new AgendaChartParserGhostEdges((GrammarByLeftNonTermList) grammar, edgeFOM);
            break;

        default:
            throw new IllegalArgumentException("Unsupported parser type");
        }

        return parser;
    }

    static public enum ParserType {
        ExhaustiveChartParser("exhaustive"), AgendaParser("agenda"), AgendaParserWithGhostEdges("age"), SuperAgendaParser("sap");

        private ParserType(final String... aliases) {
            EnumAliasMap.singleton().addAliases(this, aliases);
        }
    }

    static public enum ChartCellProcessingType {
        CellCrossList("ccl"), CellCrossHash("cch"), CellCrossMatrix("ccm"), GrammarLoop("gl"), GrammarLoopBerkeleyFilter("glbf");

        private ChartCellProcessingType(final String... aliases) {
            EnumAliasMap.singleton().addAliases(this, aliases);
        }
    }
}
