/*
 * Copyright 2010, 2011 Aaron Dunlop and Nathan Bodenstab
 * 
 * This file is part of the BUBS Parser.
 * 
 * The BUBS Parser is free software: you can redistribute it and/or 
 * modify  it under the terms of the GNU Affero General Public License 
 * as published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * The BUBS Parser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>
 */
package edu.ohsu.cslu.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

import cltool4j.BaseLogger;
import cltool4j.ConfigProperties;
import cltool4j.GlobalConfigProperties;
import cltool4j.ThreadLocalLinewiseClTool;
import cltool4j.Threadable;
import cltool4j.args4j.Option;
import edu.ohsu.cslu.datastructs.narytree.CharniakHeadPercolationRuleset;
import edu.ohsu.cslu.datastructs.narytree.HeadPercolationRuleset;
import edu.ohsu.cslu.grammar.ChildMatrixGrammar;
import edu.ohsu.cslu.grammar.ClusterTaggerTokenClassifier;
import edu.ohsu.cslu.grammar.CoarseGrammar;
import edu.ohsu.cslu.grammar.CsrSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.DecisionTreeTokenClassifier;
import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.InsideOutsideCscSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.LeftCscSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.LeftHashGrammar;
import edu.ohsu.cslu.grammar.LeftListGrammar;
import edu.ohsu.cslu.grammar.LeftRightListsGrammar;
import edu.ohsu.cslu.grammar.ListGrammar;
import edu.ohsu.cslu.grammar.RightCscSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.Int2IntHashPackingFunction;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.LeftShiftFunction;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.PerfectIntPairHashPackingFunction;
import edu.ohsu.cslu.grammar.TokenClassifier;
import edu.ohsu.cslu.parser.Parser.DecodeMethod;
import edu.ohsu.cslu.parser.Parser.InputFormat;
import edu.ohsu.cslu.parser.Parser.ParserType;
import edu.ohsu.cslu.parser.Parser.ReparseStrategy;
import edu.ohsu.cslu.parser.Parser.ResearchParserType;
import edu.ohsu.cslu.parser.cellselector.CellConstraintsComboModel;
import edu.ohsu.cslu.parser.cellselector.CellSelectorModel;
import edu.ohsu.cslu.parser.cellselector.CompleteClosureModel;
import edu.ohsu.cslu.parser.cellselector.LeftRightBottomTopTraversal;
import edu.ohsu.cslu.parser.cellselector.LimitedSpanTraversalModel;
import edu.ohsu.cslu.parser.cellselector.OHSUCellConstraintsModel;
import edu.ohsu.cslu.parser.cellselector.PerceptronBeamWidthModel;
import edu.ohsu.cslu.parser.chart.Chart.RecoveryStrategy;
import edu.ohsu.cslu.parser.fom.BoundaryLex;
import edu.ohsu.cslu.parser.fom.BoundaryPosModel;
import edu.ohsu.cslu.parser.fom.FigureOfMeritModel;
import edu.ohsu.cslu.parser.fom.FigureOfMeritModel.FOMType;
import edu.ohsu.cslu.parser.fom.InsideProb;
import edu.ohsu.cslu.parser.real.RealInsideOutsideCscSparseMatrixGrammar;
import edu.ohsu.cslu.parser.spmv.SparseMatrixVectorParser;
import edu.ohsu.cslu.parser.spmv.SparseMatrixVectorParser.PackingFunctionType;
import edu.ohsu.cslu.util.Evalb.BracketEvaluator;
import edu.ohsu.cslu.util.Evalb.EvalbResult;

/**
 * Driver class for all parser implementations. Based on the cltool4j command-line tool infrastructure
 * (http://code.google.com/p/cltool4j/).
 * 
 * @author Nathan Bodenstab
 * @author Aaron Dunlop
 * @since 2009
 */
@Threadable(defaultThreads = 1)
public class ParserDriver extends ThreadLocalLinewiseClTool<Parser<?>, ParseTask> {

    // Global vars to create parser
    public CellSelectorModel cellSelectorModel = LeftRightBottomTopTraversal.MODEL;

    public FigureOfMeritModel fomModel = null;
    Grammar grammar, coarseGrammar;

    // == Parser options ==
    @Option(name = "-p", metaVar = "PARSER", usage = "Parser implementation (cyk|beam|agenda|matrix)")
    private ParserType parserType = ParserType.Matrix;

    @Option(name = "-rp", hidden = true, metaVar = "PARSER", usage = "Research Parser implementation")
    private ResearchParserType researchParserType = null;

    // == Grammar options ==
    @Option(name = "-g", metaVar = "FILE", usage = "Grammar file (text, gzipped text, or binary serialized)")
    private String grammarFile = null;

    @Option(name = "-coarseGrammar", hidden = true, metaVar = "FILE", usage = "Coarse grammar file (text, gzipped text, or binary serialized)")
    private String coarseGrammarFile = null;

    @Option(name = "-m", metaVar = "FILE", usage = "Model file (binary serialized)")
    private File modelFile = null;

    // == Input options ==
    @Option(name = "-if", metaVar = "FORMAT", usage = "Input format type.  Choosing 'text' will tokenize the input before parsing.")
    public InputFormat inputFormat = InputFormat.Token;

    @Option(name = "-maxLength", metaVar = "LEN", usage = "Skip sentences longer than LEN")
    int maxLength = 200;

    // == Output options ==
    @Option(name = "-printUNK", usage = "Print unknown words as their UNK replacement class")
    boolean printUnkLabels = false;

    @Option(name = "-binary", usage = "Leave parse tree output in binary-branching form")
    public boolean binaryTreeOutput = false;

    // == Processing options ==
    @Option(name = "-decode", metaVar = "TYPE", hidden = true, usage = "Method to extract best tree from forest")
    public DecodeMethod decodeMethod = DecodeMethod.ViterbiMax;

    @Option(name = "-recovery", metaVar = "strategy", hidden = true, usage = "Recovery strategy in case of parse failure")
    public RecoveryStrategy recoveryStrategy = null;

    @Option(name = "-reparse", metaVar = "strategy or count", hidden = true, usage = "If no solution, loosen constraints and reparse using the specified strategy or double-beam-width n times")
    public ReparseStrategy reparseStrategy = ReparseStrategy.None;

    @Option(name = "-parseFromInputTags", hidden = true, usage = "Parse from input POS tags given by tagged or tree input.  Replaces 1-best tags from BoundaryInOut FOM if also specified.")
    public static boolean parseFromInputTags = false;

    @Option(name = "-inputTreeBeamRank", hidden = true, usage = "Print rank of input tree constituents during beam-search parsing.")
    public static boolean inputTreeBeamRank = false;

    @Option(name = "-fom", metaVar = "FOM", usage = "Figure-of-Merit edge scoring function (name or model file)")
    private String fomTypeOrModel = "Inside";

    @Option(name = "-pf", hidden = true, metaVar = "function", usage = "Packing function (only used for SpMV parsers)")
    private PackingFunctionType packingFunctionType = PackingFunctionType.PerfectHash;

    @Option(name = "-beamModel", metaVar = "FILE", usage = "Beam-width prediction model (Bodenstab et al., 2011)")
    private String beamModelFileName = null;

    @Option(name = "-ccModel", hidden = true, metaVar = "FILE", usage = "CSLU Chart Constraints model (Roark and Hollingshead, 2008)")
    private String chartConstraintsModel = null;

    @Option(name = "-ccClassifier", hidden = true, metaVar = "FILE", usage = "Complete closure classifier model (Java Serialized)")
    private File completeClosureClassifierFile = null;

    // Leaving this around for a bit, in case we get back to limited-span parsing, but it doesn't work currently
    // @Option(name = "-lsccModel", hidden = true,
    // metaVar = "FILE", usage = "CSLU Chart Constraints model (Roark and Hollingshead, 2008)")
    // private String limitedSpanChartConstraintsModel = null;

    @Option(name = "-pm", aliases = { "-pruningmodel" }, hidden = true, metaVar = "FILE", usage = "Cell selector model file")
    private File[] pruningModels = null;

    @Option(name = "-tcm", aliases = { "--token-classifier-model" }, hidden = true, metaVar = "FILE", usage = "Token classifier model file")
    private File tokenClassifierModel = null;

    @Option(name = "-maxSubtreeSpan", hidden = true, metaVar = "span", usage = "Maximum subtree span for limited-depth parsing")
    private int maxSubtreeSpan;

    @Option(name = "-head-rules", hidden = true, metaVar = "ruleset or file", usage = "Enables head-finding using a Charniak-style head-finding ruleset. Specify ruleset as 'charniak' or a rule file. Ignored if -binary is specified.")
    private String headRules = null;
    private HeadPercolationRuleset headPercolationRuleset = null;

    @Option(name = "-geometricInsideNorm", hidden = true, usage = "Use the geometric mean of the Inside score. Only needed for agenda parsers")
    public static boolean geometricInsideNorm = false;

    @Option(name = "-ccPrint", hidden = true, usage = "Print Cell Constraints for each input sentence and exit (no parsing done)")
    public static boolean chartConstraintsPrint = false;

    @Option(name = "-help-long", usage = "List all research parsers and options")
    public boolean longHelp = false;

    @Option(name = "-debug", hidden = true, usage = "Exit on error with trace")
    public boolean debug = false;

    // @Option(name = "-printFeatMap", hidden = true, usage =
    // "Write lex/pos/nt feature strings and indicies for beam-width prediction and disc FOM to stdout.  Note this mapping must be identical for training and testing.")
    // public boolean printFeatMap = false;

    // corpus stats
    private long parseStartTime;
    private volatile int sentencesParsed = 0, wordsParsed = 0, failedParses = 0, reparsedSentences = 0,
            totalReparses = 0;
    private LinkedList<Parser<?>> parserInstances = new LinkedList<Parser<?>>();
    private final BracketEvaluator evaluator = new BracketEvaluator();

    /**
     * Configuration property key for the number of cell-level threads requested by the user. We handle threading at
     * three levels; threading per-sentence is handled by the command-line tool infrastructure and specified with the
     * standard '-xt' parameter. Cell-level and grammar-level threading are handled by the parser instance and specified
     * with this option and with {@link #OPT_GRAMMAR_THREAD_COUNT}.
     */
    public final static String OPT_CELL_THREAD_COUNT = "cellThreads";

    /**
     * Configuration property key for the number of grammar-level threads requested by the user. We handle threading at
     * three levels; threading per-sentence is handled by the command-line tool infrastructure and specified with the
     * standard '-xt' parameter. Cell-level and grammar-level threading are handled by the parser instance and specified
     * with this option and with {@link #OPT_CELL_THREAD_COUNT}.
     */
    public final static String OPT_GRAMMAR_THREAD_COUNT = "grammarThreads";

    /**
     * Configuration property key for the number of row-level or cell-level threads actually used. In some cases the
     * number of threads requested is impractical (e.g., if it is greater than the maximum number of cells in a row or
     * greater than the number of grammar rows). {@link Parser} instances which make use of
     * {@link #OPT_GRAMMAR_THREAD_COUNT} should populate this property to indicate the number of threads actually used.
     * Among other potential uses, this allows {@link #cleanup()} to report accurate timing information.
     */
    public final static String OPT_CONFIGURED_THREAD_COUNT = "actualThreads";

    /**
     * Configuration property key for the comparator class used to order non-terminals. Implementations are in
     * {@link SparseMatrixGrammar}. The default is "PosEmbeddedComparator". Other valid values are "PosFirstComparator",
     * "LexicographicComparator".
     */
    public final static String OPT_NT_COMPARATOR_CLASS = "ntComparatorClass";

    /**
     * Configuration property key enabling complete categories above the span limit (when limiting span-length with
     * -maxSubtreeSpan). By default, only incomplete (factored) categories are allowed when L < span < n.
     */
    public final static String OPT_ALLOW_COMPLETE_ABOVE_SPAN_LIMIT = "allowCompleteAboveSpanLimit";

    /**
     * Configuration property key for discriminative training feature templates.
     */
    public final static String OPT_DISC_FEATURE_TEMPLATES = "featureTemplates";

    /**
     * Configuration property key enabling bracket evaluation of parse failures (i.e., penalizing recall in the event of
     * a parse failure). We default to ignoring empty parses (and reporting them separately), to match the behavior of
     * Collins' standard <code>evalb</code> tool. But in some cases, including those failures directly in the F1 measure
     * is useful. Note: This option is ignored when parsing from input other than gold trees.
     */
    public final static String OPT_EVAL_PARSE_FAILURES = "evalParseFailures";

    /**
     * Skip log-sum operations if the log probabilities differ by more than x. Default is 16 (approximately the
     * resolution of a 32-bit IEEE float).
     */
    public final static String PROPERTY_LOG_SUM_DELTA = "logSumDelta";

    /** Use a quantized approximation of the exp function when performing log-sum operations. Boolean property. */
    public final static String PROPERTY_APPROXIMATE_LOG_SUM = "approxLogSum";

    /** Compute the inside score only. Decode assuming all outside probabilities are 1. Boolean property. */
    public final static String PROPERTY_INSIDE_ONLY = "insideOnly";

    /**
     * Use the prioritization / FOM model's estimate of outside probabilities (eliminating the outside pass). Boolean
     * property
     */
    public final static String PROPERTY_HEURISTIC_OUTSIDE = "heuristicOutside";

    public static void main(final String[] args) {
        run(args);
    }

    @Override
    // run once at initialization regardless of number of threads
    public void setup() throws Exception {

        if (this.longHelp) {

            BaseLogger.singleton().info("\nPossible values for -rp PARSER:");
            for (final ResearchParserType type : Parser.ResearchParserType.values()) {
                BaseLogger.singleton().info("\t" + type.toString());
            }
            // NB: Is there a way to print the entire properties file, comments and all?
            BaseLogger.singleton().info(
                    "\nDefault options using -O <key>=<value>:\n\t"
                            + GlobalConfigProperties.singleton().toString().replaceAll("\n", "\n\t"));
            System.exit(0);
        } else if (grammarFile == null && modelFile == null) {
            throw new IllegalArgumentException("-g GRAMMAR or -m MODEL is required");
        }

        // map simplified parser choices to the specific research version
        if (researchParserType == null) {
            researchParserType = parserType.researchParserType;
        }

        BaseLogger.singleton().info(
                "INFO: parser=" + researchParserType + " fom=" + fomTypeOrModel + " decode=" + decodeMethod);
        BaseLogger.singleton().info("INFO: " + commandLineArguments());

        if (headRules != null) {
            if (headRules.equalsIgnoreCase("charniak")) {
                headPercolationRuleset = new CharniakHeadPercolationRuleset();
            } else {
                headPercolationRuleset = new HeadPercolationRuleset(new FileReader(headRules));
            }
        }

        final TokenClassifier tokenClassifier = tokenClassifierModel != null ? new ClusterTaggerTokenClassifier(
                tokenClassifierModel) : new DecisionTreeTokenClassifier();

        if (modelFile != null) {
            final ObjectInputStream ois = new ObjectInputStream(fileAsInputStream(modelFile));
            @SuppressWarnings("unused")
            final String metadata = (String) ois.readObject();
            final ConfigProperties props = (ConfigProperties) ois.readObject();
            GlobalConfigProperties.singleton().mergeUnder(props);

            BaseLogger.singleton().finer("Reading grammar...");
            this.grammar = (Grammar) ois.readObject();

            BaseLogger.singleton().finer("Reading FOM...");
            fomModel = (FigureOfMeritModel) ois.readObject();

        } else {
            this.grammar = createGrammar(fileAsBufferedReader(grammarFile), researchParserType, tokenClassifier,
                    packingFunctionType);

            if (fomTypeOrModel.equals("Inside")) {
                fomModel = new InsideProb();

            } else if (fomTypeOrModel.equals("InsideWithFwdBkwd")) {
                // fomModel = new BoundaryInOut(FOMType.InsideWithFwdBkwd);
                throw new IllegalArgumentException("FOM InsideWithFwdBkwd no longer supported");

            } else if (new File(fomTypeOrModel).exists()) {
                // read first line and extract model type
                final BufferedReader tmp = fileAsBufferedReader(fomTypeOrModel);
                final HashMap<String, String> keyValue = Util.readKeyValuePairs(tmp.readLine().trim());
                tmp.close();

                if (!keyValue.containsKey("type")) {
                    throw new IllegalArgumentException(
                            "FOM model file has unexpected format.  Looking for 'type=' in first line.");
                }
                final String fomType = keyValue.get("type");
                if (fomType.equals("BoundaryInOut")) {
                    Grammar fomGrammar = grammar;
                    if (this.coarseGrammarFile != null) {
                        coarseGrammar = new CoarseGrammar(coarseGrammarFile, this.grammar);
                        BaseLogger.singleton().fine("FOM coarse grammar stats: " + coarseGrammar.getStats());
                        fomGrammar = coarseGrammar;
                    }
                    fomModel = new BoundaryPosModel(FOMType.BoundaryPOS, fomGrammar,
                            fileAsBufferedReader(fomTypeOrModel));

                } else if (fomType.equals("BoundaryLex")) {
                    fomModel = new BoundaryLex(FOMType.BoundaryLex, grammar, fileAsBufferedReader(fomTypeOrModel));

                } else {
                    throw new IllegalArgumentException("FOM model type '" + fomType + "' in file " + fomTypeOrModel
                            + "' not expected.");
                }

            } else {
                throw new IllegalArgumentException("-fom value '" + fomTypeOrModel + "' not valid.");
            }

            boolean defaultCellSelector = true;
            OHSUCellConstraintsModel cellConstraints = null;
            if (chartConstraintsModel != null) {
                cellConstraints = new OHSUCellConstraintsModel(fileAsBufferedReader(chartConstraintsModel), null);
                cellSelectorModel = cellConstraints;
                defaultCellSelector = false;
            }

            PerceptronBeamWidthModel beamConstraints = null;
            if (beamModelFileName != null) {
                beamConstraints = defaultCellSelector ? new PerceptronBeamWidthModel(
                        fileAsBufferedReader(beamModelFileName), null) : new PerceptronBeamWidthModel(
                        fileAsBufferedReader(beamModelFileName), cellSelectorModel);
                cellSelectorModel = beamConstraints;
                defaultCellSelector = false;
            } else if (pruningModels != null && pruningModels.length > 0) {
                final ObjectInputStream ois = new ObjectInputStream(fileAsInputStream(pruningModels[0]));
                cellSelectorModel = (CellSelectorModel) ois.readObject();
                ois.close();
                cellSelectorModel = beamConstraints;
                defaultCellSelector = false;
            }

            if (cellConstraints != null && beamConstraints != null) {
                final CellConstraintsComboModel constraintsCombo = new CellConstraintsComboModel();
                constraintsCombo.addModel(cellConstraints);
                constraintsCombo.addModel(beamConstraints);
                cellSelectorModel = constraintsCombo;
                defaultCellSelector = false;
            } else if (maxSubtreeSpan != 0) {
                cellSelectorModel = defaultCellSelector ? new LimitedSpanTraversalModel(maxSubtreeSpan, null)
                        : new LimitedSpanTraversalModel(maxSubtreeSpan, cellSelectorModel);
                defaultCellSelector = false;
            }

            if (completeClosureClassifierFile != null) {
                cellSelectorModel = defaultCellSelector ? new CompleteClosureModel(completeClosureClassifierFile,
                        grammar, null) : new CompleteClosureModel(completeClosureClassifierFile, grammar,
                        cellSelectorModel);
            }
        }

        BaseLogger.singleton().fine(grammar.getStats());

        parseStartTime = System.currentTimeMillis();
    }

    /**
     * Reads in a grammar from a file and creates a {@link Grammar} instance of the appropriate class for the specified
     * parser type.
     * 
     * @param grammarFile
     * @param parserType
     * @return a {@link Grammar} instance appropriate for the specified parser type
     * @throws IOException
     */
    public static Grammar readGrammar(final String grammarFile, final ResearchParserType parserType,
            final PackingFunctionType packingFunctionType) throws IOException {
        // Handle gzipped and non-gzipped grammar files
        return createGrammar(fileAsBufferedReader(grammarFile, Charset.forName("UTF-8")), parserType,
                new DecisionTreeTokenClassifier(), packingFunctionType);
    }

    /**
     * Reads in a grammar from a file and creates a {@link Grammar} instance of the appropriate class for the specified
     * parser type.
     * 
     * @param grammarFile
     * @param parserType
     * @param tokenClassifier Type of token-classifier (e.g. decision-tree or tagger)
     * @return a {@link Grammar} instance appropriate for the specified parser type
     * @throws IOException
     */
    public static Grammar createGrammar(final Reader grammarFile, final ResearchParserType parserType,
            final TokenClassifier tokenClassifier, final PackingFunctionType packingFunctionType) throws IOException {

        switch (parserType) {
        case ECPInsideOutside:
        case ECPCellCrossList:
            return new LeftListGrammar(grammarFile, tokenClassifier);

        case ECPCellCrossHashGrammarLoop:
        case ECPCellCrossHashGrammarLoop2:
        case ECPCellCrossHash:
            return new LeftHashGrammar(grammarFile, tokenClassifier);

        case ECPCellCrossMatrix:
            return new ChildMatrixGrammar(grammarFile, tokenClassifier);

        case ECPGrammarLoop:
        case ECPGrammarLoopBerkeleyFilter:
            return new ListGrammar(grammarFile, tokenClassifier);

        case AgendaParser:
        case APWithMemory:
        case APGhostEdges:
        case APDecodeFOM:
            return new LeftRightListsGrammar(grammarFile, tokenClassifier);

        case BeamSearchChartParser:
        case BSCPSplitUnary:
        case BSCPPruneViterbi:
        case BSCPOnlineBeam:
        case BSCPBoundedHeap:
        case BSCPExpDecay:
        case BSCPPerceptronCell:
        case BSCPFomDecode:
        case BSCPBeamConfTrain:
            // case BSCPBeamConf:
        case CoarseCellAgenda:
        case CoarseCellAgendaCSLUT:
            return new LeftHashGrammar(grammarFile, tokenClassifier);

        case CsrSpmv:
        case GrammarParallelCsrSpmv:
            switch (packingFunctionType) {
            case Simple:
                return new CsrSparseMatrixGrammar(grammarFile, tokenClassifier, LeftShiftFunction.class);
            case PerfectHash:
                return new CsrSparseMatrixGrammar(grammarFile, tokenClassifier, PerfectIntPairHashPackingFunction.class);
            default:
                throw new IllegalArgumentException("Unsupported packing-function type: " + packingFunctionType);
            }

        case PackedOpenClSpmv:
        case DenseVectorOpenClSpmv:
            return new CsrSparseMatrixGrammar(grammarFile, tokenClassifier, LeftShiftFunction.class);

        case CscSpmv:
        case GrammarParallelCscSpmv:
            switch (packingFunctionType) {
            case Simple:
                return new LeftCscSparseMatrixGrammar(grammarFile, tokenClassifier, LeftShiftFunction.class);
            case PerfectHash:
                return new LeftCscSparseMatrixGrammar(grammarFile, tokenClassifier,
                        PerfectIntPairHashPackingFunction.class);
            default:
                throw new IllegalArgumentException("Unsupported packing-function type: " + packingFunctionType);
            }

        case LeftChildMl:
        case CartesianProductBinarySearchMl:
        case CartesianProductBinarySearchLeftChildMl:
        case CartesianProductHashMl:
        case CartesianProductLeftChildHashMl:
            switch (packingFunctionType) {
            case Simple:
                return new LeftCscSparseMatrixGrammar(grammarFile, tokenClassifier, LeftShiftFunction.class);
            case Hash:
                return new LeftCscSparseMatrixGrammar(grammarFile, tokenClassifier, Int2IntHashPackingFunction.class);
            case PerfectHash:
                return new LeftCscSparseMatrixGrammar(grammarFile, tokenClassifier,
                        PerfectIntPairHashPackingFunction.class);
            default:
                throw new IllegalArgumentException("Unsupported packing-function type: " + packingFunctionType);
            }
        case RightChildMl:
            return new RightCscSparseMatrixGrammar(grammarFile, tokenClassifier, LeftShiftFunction.class);
        case GrammarLoopMl:
            return new CsrSparseMatrixGrammar(grammarFile, tokenClassifier, LeftShiftFunction.class);
        case InsideOutsideCartesianProductHash:
        case ViterbiInOutCph:
            return new InsideOutsideCscSparseMatrixGrammar(grammarFile, tokenClassifier,
                    PerfectIntPairHashPackingFunction.class);

        case ConstrainedCartesianProductHashMl:
            // Don't restrict the beam for constrained parsing
            GlobalConfigProperties.singleton().setProperty(Parser.PROPERTY_MAX_BEAM_WIDTH, "0");
            return new LeftCscSparseMatrixGrammar(grammarFile, tokenClassifier, LeftShiftFunction.class);

        case RealInsideOutsideCartesianProductHash:
            return new RealInsideOutsideCscSparseMatrixGrammar(grammarFile);

        default:
            throw new IllegalArgumentException("Unsupported parser type: " + parserType);
        }
    }

    @Override
    public Parser<?> createLocal() {
        try {
            // Apply specialized cell-selector model when appropriate (e.g., constrained parsing)
            final CellSelectorModel csm = researchParserType.cellSelectorModel();
            if (csm != null) {
                cellSelectorModel = csm;
            }

            // Construct an instance of the appropriate parser class
            @SuppressWarnings("unchecked")
            final Constructor<Parser<?>> c = (Constructor<Parser<?>>) Class.forName(researchParserType.classname())
                    .getConstructor(ParserDriver.class, grammar.getClass());

            final Parser<?> parser = c.newInstance(this, grammar);
            // Each thread creates its own parser instance. We need to maintain (and protect) a master list that we'll
            // use to shut them down in cleanup()
            synchronized (parserInstances) {
                parserInstances.add(parser);
            }
            return parser;

        } catch (final Exception e) {
            throw new IllegalArgumentException("Unsupported parser type: " + e.toString());
        }
    }

    @Override
    protected FutureTask<ParseTask> lineTask(final String input) {
        return new FutureTask<ParseTask>(new Callable<ParseTask>() {

            @Override
            public ParseTask call() throws Exception {
                if (debug) {
                    return getLocal().parseSentence(input, recoveryStrategy);
                }
                try {
                    return getLocal().parseSentence(input, recoveryStrategy);
                } catch (final Exception e) {
                    BaseLogger.singleton().log(Level.SEVERE, e.toString());
                    return null;
                }
            }
        });
    }

    @Override
    protected void output(final ParseTask parseTask) {
        // We'll count the sentence even if it failed with an exception (and record it as failed below). However, we
        // don't currently include the words of such sentences in wordsParsed. That's a bit of an inconsistency, but
        // it's OK for now.
        sentencesParsed++;
        if (parseTask != null) {
            final StringBuilder output = new StringBuilder(512);
            output.append(parseTask.parseBracketString(binaryTreeOutput, printUnkLabels, headPercolationRuleset));
            try {
                parseTask.evaluate(evaluator);
                output.append(parseTask.statsString());
            } catch (final Exception e) {
                if (BaseLogger.singleton().isLoggable(Level.SEVERE)) {
                    output.append("\nERROR: Evaluation failed: " + e.toString());
                }
                output.append(parseTask.statsString());
            }
            System.out.println(output.toString());
            wordsParsed += parseTask.sentenceLength();
            if (parseTask.parseFailed()) {
                failedParses++;
            } else if (parseTask.reparseStages > 0) {
                reparsedSentences++;
            }
            totalReparses += parseTask.reparseStages;
        } else {
            failedParses++;
            System.out.println("()");
        }
    }

    @Override
    protected void cleanup() {
        final float parseTime = (System.currentTimeMillis() - parseStartTime) / 1000f;

        // If the individual parser configured a thread count (e.g. CellParallelCsrSpmvParser), compute
        // CPU-time using that thread count; otherwise, assume maxThreads is correct
        final int threads = GlobalConfigProperties.singleton().containsKey(OPT_CONFIGURED_THREAD_COUNT) ? GlobalConfigProperties
                .singleton().getIntProperty(OPT_CONFIGURED_THREAD_COUNT) : maxThreads;

        // Note that this CPU-time computation does not include GC time
        final float cpuTime = parseTime * threads;

        final StringBuilder sb = new StringBuilder();
        sb.append(String
                .format("INFO: numSentences=%d numFail=%d reparsedSentences=%d totalReparses=%d totalSeconds=%.3f cpuSeconds=%.3f avgSecondsPerSent=%.3f wordsPerSec=%.3f",
                        sentencesParsed, failedParses, reparsedSentences, totalReparses, parseTime, cpuTime, parseTime
                                / sentencesParsed, wordsParsed / parseTime));

        if (!parserInstances.isEmpty() && parserInstances.getFirst() instanceof SparseMatrixVectorParser) {
            sb.append(String.format(" totalXProductTime=%d totalBinarySpMVTime=%d",
                    SparseMatrixVectorParser.totalCartesianProductTime, SparseMatrixVectorParser.totalBinarySpmvNs));
        }

        if (inputFormat == InputFormat.Tree) {
            final EvalbResult evalbResult = evaluator.accumulatedResult();
            sb.append(String.format(" f1=%.2f prec=%.2f recall=%.2f", evalbResult.f1() * 100,
                    evalbResult.precision() * 100, evalbResult.recall() * 100));
        }

        BaseLogger.singleton().info(sb.toString());

        // Synchronize again, just to be sure we don't somehow try to add a new instance during cleanup. It should be
        // rare, but the (usually) uncontested sync is cheap.
        synchronized (parserInstances) {
            for (final Parser<?> p : parserInstances) {
                try {
                    p.shutdown();
                } catch (final Exception ignore) {
                }
            }
        }
    }

    /**
     * Sets the grammar. Used when embedding BUBS into an independent system (see {@link EmbeddedExample}).
     * 
     * @param g Grammar
     */
    public void setGrammar(final Grammar g) {
        this.grammar = g;
    }

    static public ParserDriver defaultTestOptions() {
        final ParserDriver opts = new ParserDriver();
        BaseLogger.singleton().setLevel(Level.FINER);
        return opts;
    }
}
