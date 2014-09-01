package de.phischu.masterreport;

import static de.phischu.masterreport.Main.Labels.Declaration;
import static de.phischu.masterreport.Main.Labels.Package;
import static de.phischu.masterreport.Main.Labels.Symbol;
import static de.phischu.masterreport.RelationshipTypes.DECLARATION;
import static de.phischu.masterreport.RelationshipTypes.DECLAREDSYMBOL;
import static de.phischu.masterreport.RelationshipTypes.DEPENDENCY;
import static de.phischu.masterreport.RelationshipTypes.MENTIONEDSYMBOL;
import static de.phischu.masterreport.RelationshipTypes.NEXTVERSION;
import static org.neo4j.graphdb.Direction.INCOMING;
import static org.neo4j.graphdb.Direction.OUTGOING;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.Pair;
import org.neo4j.tooling.GlobalGraphOperations;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class Main {

	public static final String DB_PATH = "data";

	public enum Labels implements Label {
		Package, Declaration, Symbol
	}

	public static void main(String[] args) throws IOException {

		// installable(new
		// Update("bytestring","0.10.2.0","base","4.6.0.1","base","4.6.0.1",false,false));

		GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);

		Transaction tx = graphDb.beginTx();
		try {
			
			//generateSlices(graphDb);

			System.out.println("Analysing Updates...");

			List<UpdateScenario> updateScenarioList = Lists.newLinkedList(updateScenarios(graphDb));
			List<Boolean> isLegalList = Lists.transform(updateScenarioList, x -> legal(x));
			List<Boolean> isMinorList = Lists.transform(updateScenarioList, x -> x.update.minorMajor.equals("minor"));
			List<Boolean> affectsList = Lists.transform(updateScenarioList, x -> affects(x.update,x.packagenode));

			System.out.println("Counting ...");

			int hackagecount = 40160;
			int attemptedpackages = Iterables.size(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package));
			int packagecount = Iterables.size(packages(graphDb));
			
			Iterable<Node> declarationnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Declaration);
			int declarationcount = FluentIterable.from(declarationnodes).size();
			int declarationastcount = FluentIterable.from(declarationnodes).
					transform(x -> x.getProperty("declarationast")).
					toSet().
					size();
			int symbolcount = Iterables.size(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Symbol));

			int updatecount = FluentIterable.from(packages(graphDb)).
					transformAndConcat(p -> update(p)).
					size();
			int updatescenariocount = updateScenarioList.size();
			
			int prohibitedcount = countFalse(isLegalList);
			int prohibitedunaffectedcount = countTrue(zipWith(isLegalList,affectsList,(l,a) -> !l && !a));
			
			int minorcount = countTrue(isMinorList);
			int minorprohibitedcount = countTrue(zipWith(isMinorList,isLegalList,(m,l) -> m && !l));
			
			int majorcount = countFalse(isMinorList);
			int majorprohibitedcount = countTrue(zipWith(isMinorList,isLegalList,(m,l) -> !m && !l));
			int majorprohibitedunaffectedcount = countTrue(zipWith3(isMinorList,isLegalList,affectsList,
					m -> l -> a -> !m && !l && !a));
			int majorallowedcount = countTrue(zipWith(isMinorList,isLegalList,(m,l) -> !m && l));
			int majorallowedunaffectedcount = countTrue(zipWith3(isMinorList,isLegalList,affectsList,
					m -> l -> a -> !m && l && !a));

			PrintWriter writer = new PrintWriter("counts", "UTF-8");
			writer.println("Package count: " + packagecount);
			writer.println("Declaration count: " + declarationcount);
			writer.println("Declaration AST count: " + declarationastcount);
			writer.println("Symbol count: " + symbolcount);
			writer.println("Update count: " + updatecount);
			writer.println("Update scenario count: " + updatescenariocount);
			writer.println("Prohibited: " + prohibitedcount);
			writer.println("Prohibited Unaffected: " + prohibitedunaffectedcount);
			writer.println("Minor: " + minorcount);
			writer.println("Minor Prohibited: " + minorprohibitedcount);
			writer.println("Major: " + majorcount);
			writer.println("Major Prohibited: " + majorprohibitedcount);
			writer.println("Major Prohibited Unaffected: " + majorprohibitedunaffectedcount);
			writer.println("Major Allowed: " + majorallowedcount);
			writer.println("Major Allowed Unaffected: " + majorallowedunaffectedcount);

			writer.close();

			System.out.println("Printing Refactorings...");
			printRefactorings(Sets.newHashSet(refactorings(graphDb)));

			System.out.println("Plotting ...");

			plotBinary("legalupdates.png", "Legal", "Illegal", isLegalList);
			plotBinary("safeupdates.png", "Safe", "Unsafe", Iterables.transform(affectsList, x -> !x));
			//plotBinary("illegalSafeYetInstallable.png","Illegal Safe Installable", "Illegal Safe Installable",illegalSafeYetInstallable);

			DefaultPieDataset dataset = new DefaultPieDataset();
			dataset.setValue("All packages", hackagecount - attemptedpackages);
			dataset.setValue("Attempted packages", attemptedpackages - packagecount);
			dataset.setValue("Successful packages", packagecount);

			PiePlot plot = new PiePlot(dataset);
			plot.setSectionPaint("All packages", Color.gray);
			plot.setSectionPaint("Attempted packages", Color.red);
			plot.setSectionPaint("Successful packages", Color.green);

			JFreeChart chart = new JFreeChart(plot);

			ChartUtilities.saveChartAsPNG(new File("hackage.png"), chart, 1024, 768);

			tx.success();
		} finally {
			tx.close();
		}

		graphDb.shutdown();

		System.out.println("done");

	}

	public static <A,B,C> Iterable<C> zipWith(Iterable<A> iterableA, Iterable<B> iterableB, BiFunction<A,B,C> fn) {
	    return () -> new Iterator<C>() {
	        private Iterator<A> itA = iterableA.iterator();
	        private Iterator<B> itB = iterableB.iterator();
	 
	        public boolean hasNext() {
	            return itA.hasNext() && itB.hasNext();
	        }
	 
	        public C next() {
	            return fn.apply(itA.next(), itB.next());
	        }
	    };
	}
	
	public static <A,B,C,D> Iterable<D> zipWith3(
			Iterable<A> iterableA, Iterable<B> iterableB, Iterable<C> iterableC, Function<A,Function<B,Function<C,D>>> fn) {
	    return () -> new Iterator<D>() {
	        private Iterator<A> itA = iterableA.iterator();
	        private Iterator<B> itB = iterableB.iterator();
	        private Iterator<C> itC = iterableC.iterator();
	        
	        public boolean hasNext() {
	            return itA.hasNext() && itB.hasNext() && itC.hasNext();
	        }
	 
	        public D next() {
	            return fn.apply(itA.next()).apply(itB.next()).apply(itC.next());
	        }
	    };
	}
	
	public static int countTrue(Iterable<Boolean> bs){
		return FluentIterable.from(bs).filter(x -> x).size();
	}
	
	public static int countFalse(Iterable<Boolean> bs){
		return FluentIterable.from(bs).filter(x -> !x).size();
	}
	
	public static void plotBinary(String outputpath, String name1, String name2, Iterable<Boolean> data)
			throws IOException {

		DefaultPieDataset dataset = new DefaultPieDataset();
		dataset.setValue(name1, Iterables.frequency(data, true));
		dataset.setValue(name2, Iterables.frequency(data, false));

		PiePlot plot = new PiePlot(dataset);
		plot.setSectionPaint(name1, Color.green);
		plot.setSectionPaint(name2, Color.red);

		JFreeChart chart = new JFreeChart(plot);
		ChartUtilities.saveChartAsPNG(new File(outputpath), chart, 1024, 768);

	}
/*
	public static Boolean installable(ConcreteUpdate update) {

		System.out.println("Trying to install: ");
		System.out.println(update.packagename + "-" + update.packageversion);
		System.out.println("with");
		System.out.println("--constraint=\"" + update.dependencyname2 + "==" + update.dependencyversion2 + "\"");
		try {
			systemCall("cabal","sandbox","delete");
			systemCall("cabal","sandbox","init");
			Integer exitcode = systemCall("cabal", "install", "--reinstall", "--force-reinstalls",
					"--disable-documentation",
					"--disable-library-profiling", "--allow-newer=" + update.dependencyname2, "--constraint="
							+ update.dependencyname2 + "==" + update.dependencyversion2, update.packagename + "-"
							+ update.packageversion);
			System.out.println(exitcode);
			return exitcode.equals(0);
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
			return false;
		}
	}
*/
	public static int systemCall(String... command) throws InterruptedException, IOException {

		ProcessBuilder processbuilder = new ProcessBuilder(command);
		Process process = processbuilder.start();
		//ByteStreams.copy(process.getInputStream(), System.out);
		//ByteStreams.copy(process.getErrorStream(), System.out);
		return process.waitFor();

	}

	public static void printRefactorings(Collection<Pair<String, String>> astpairs) throws FileNotFoundException,
			UnsupportedEncodingException {

		PrintWriter writer = new PrintWriter("refactorings", "UTF-8");
		writer.println(astpairs.size());
		for (Pair<String, String> astpair : astpairs) {
			writer.println("AST ONE");
			writer.println(astpair.first());
			writer.println("AST TWO");
			writer.println(astpair.other());
		}
		writer.close();

	}
	public static Iterable<Node> packages(GraphDatabaseService graphDb){
		return FluentIterable.from(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package)).
				filter(p -> !Iterables.isEmpty(declares(p)));
	}
	public static Iterable<Update> update(Node packagenode){
		return FluentIterable.from(packagenode.getRelationships(OUTGOING, NEXTVERSION)).
				transform(r -> new Update((String)r.getProperty("change"), packagenode,r.getEndNode()));
	}
	public static Iterable<Node> dependency(Node packagenode){
		return new Hop(OUTGOING,DEPENDENCY).apply(packagenode);
	}
	public static Boolean legal(UpdateScenario updateScenario){
		Collection<Node> packageDependencies = Sets.newHashSet(dependency(updateScenario.packagenode));
		return packageDependencies.contains(updateScenario.update.package1) &&
				packageDependencies.contains(updateScenario.update.package2);
	}
	public static Iterable<Node> declares(Node packagenode){
		return new Hop(OUTGOING,DECLARATION).apply(packagenode);
	}
	public static Iterable<Node> binds(Node declarationnode){
		return new Hop(OUTGOING,DECLAREDSYMBOL).apply(declarationnode);
	}
	public static Iterable<Node> boundBy(Node symbolnode){
		return new Hop(INCOMING,DECLAREDSYMBOL).apply(symbolnode);
	}
	public static Iterable<Node> mentions(Node declarationnode){
		return new Hop(OUTGOING,MENTIONEDSYMBOL).apply(declarationnode);
	}
	public static String source(Node declarationnode){
		return (String) declarationnode.getProperty("declarationast");
	}
	public static Iterable<UpdateScenario> updateScenarios(GraphDatabaseService graphDb){
		List<UpdateScenario> us = Lists.newLinkedList();
		for(Node p : packages(graphDb)){
			for(Node d : dependency(p)){
				for(Update u : update(d)){
					us.add(new UpdateScenario(u,p));
				}
			}
		}
		return us;
	}

	public static Iterable<Node> provides(Node packagenode){
		return FluentIterable.
				from(Collections.singleton(packagenode)).
				transformAndConcat(p -> declares(p)).
				transformAndConcat(d -> binds(d));
	}
	
	public static Iterable<Node> removes(Update update){
		Set<Node> package2Symbols = Sets.newHashSet(provides(update.package2));
		return FluentIterable.
				from(provides(update.package1)).
				filter(symbol -> !package2Symbols.contains(symbol));
		
	}
	
	public static Iterable<Node> adds(Update update){
		Set<Node> package1Symbols = Sets.newHashSet(provides(update.package1));
		return FluentIterable.
				from(provides(update.package2)).
				filter(symbol -> !package1Symbols.contains(symbol));
		
	}
	
	public static Iterable<Node> alters(Update update){
		Set<Node> package2Declarations = Sets.newHashSet(declares(update.package2));
		return FluentIterable.from(declares(update.package1)).
				transformAndConcat(d1 ->
				    FluentIterable.from(binds(d1)).
				        transformAndConcat(s -> FluentIterable.from(boundBy(s)).
				            anyMatch(d2 -> package2Declarations.contains(d2) && source(d1).equals(source(d2))) ?
				            	Collections.singleton(s) : Collections.emptySet()));
		
	}
	
	public static Iterable<Node> breaks(Update update){
		Iterable<Node> removedSymbols = removes(update);
		Iterable<Node> alteredSymbols = update.minorMajor.equals("major") ? alters(update) : Collections.emptySet();
		return Iterables.concat(removedSymbols,alteredSymbols);
	}
	
	public static Iterable<Node> fixes(Update update){
		return update.minorMajor.equals("minor") ? alters(update) : Collections.emptySet();
	}
	
	public static Iterable<Node> requires(Node packagenode){
		return FluentIterable.from(Collections.singleton(packagenode)).
				transformAndConcat(p -> declares(p)).
				transformAndConcat(d -> mentions(d));
	}
	
	public static Boolean affects(Update update,Node packagenode){
		Collection<Node> brokenSymbols = Sets.newHashSet(breaks(update));
		return FluentIterable.from(requires(packagenode)).
				anyMatch(s -> brokenSymbols.contains(s));
	}
	
	public static Collection<Pair<String, String>> refactorings(GraphDatabaseService graphDb) {

		Collection<Pair<String, String>> declarationastpairs = new LinkedList<Pair<String, String>>();

		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Symbol);

		for (Node symbolnode : symbolnodes) {

			Collection<Pair<Node, Node>> usages = Lists.newArrayList(usages(symbolnode));

			for (Pair<Node, Node> usage1 : usages) {
				for (Pair<Node, Node> usage2 : usages) {

					String usingast1 = (String) usage1.first().getProperty("declarationast");
					String usedast1 = (String) usage1.other().getProperty("declarationast");
					String usingast2 = (String) usage2.first().getProperty("declarationast");
					String usedast2 = (String) usage2.other().getProperty("declarationast");

					if (usingast1.equals(usingast2) && !usedast1.equals(usedast2)) {
						declarationastpairs.add(Pair.of(usedast1, usedast2));
					}
				}
			}
		}

		return declarationastpairs;

	}

	public static Iterable<Pair<Node, Node>> usages(Node symbolnode) {

		LinkedList<Pair<Node, Node>> usages = new LinkedList<Pair<Node, Node>>();

		Iterable<Node> usingdeclarationnodes = new Hop(INCOMING, MENTIONEDSYMBOL).apply(symbolnode);
		Iterable<Node> useddeclarationnodes = new Hop(INCOMING, DECLAREDSYMBOL).apply(symbolnode);

		for (Node usingdeclarationnode : usingdeclarationnodes) {
			for (Node useddeclarationnode : useddeclarationnodes) {

				Iterable<Node> dependencynodes = FluentIterable.from(Collections.singleton(usingdeclarationnode))
						.transformAndConcat(new Hop(INCOMING, DECLARATION))
						.transformAndConcat(new Hop(OUTGOING, DEPENDENCY));

				Iterable<Node> usedpackagenodes = new Hop(INCOMING, DECLARATION).apply(useddeclarationnode);

				if (containsAll(dependencynodes, usedpackagenodes)) {
					usages.add(Pair.of(usingdeclarationnode, useddeclarationnode));
				}
			}
		}

		return usages;

	}

	private static boolean containsAll(Iterable<? extends Object> nodes1, Iterable<? extends Object> nodes2) {

		for (Object node2 : nodes2) {
			if (!Iterables.contains(nodes1, node2))
				return false;
		}
		return true;
	}

	public static void generateSlices(GraphDatabaseService graphDb) {

		// global map of all slices for each declaration
		HashMap<Node, List<Slice>> slices = new HashMap<Node, List<Slice>>();

		// for each declaration
		for (Node declarationnode : GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Declaration)) {
			
			System.out.println(Iterables.size(generateDeclarationSlices(slices, declarationnode)));	

		}

	}

	public static List<Slice> generateDeclarationSlices(HashMap<Node, List<Slice>> slices, Node declarationnode) {

		List<Slice> declarationSlices = slices.get(declarationnode);

		if (declarationSlices == null) {

			// for each symbol the set of slices that might be used
			List<Set<Slice>> choicePoints = Lists.newArrayList();

			// for each symbol
			for (Node symbolnode : new Hop(OUTGOING, MENTIONEDSYMBOL).apply(declarationnode)) {

				// declarations declaring this symbol and are a proper dependency
				List<Node> usedDeclarations = Lists.newLinkedList();
				for(Node usedDeclarationNode : new Hop(INCOMING, DECLAREDSYMBOL).apply(symbolnode)){
					
					Iterable<Node> dependencynodes = FluentIterable.from(Collections.singleton(declarationnode))
							.transformAndConcat(new Hop(INCOMING, DECLARATION))
							.transformAndConcat(new Hop(OUTGOING, DEPENDENCY));

					Iterable<Node> usedpackagenodes = new Hop(INCOMING, DECLARATION).apply(usedDeclarationNode);

					if (containsAll(dependencynodes, usedpackagenodes)) {
						usedDeclarations.add(usedDeclarationNode);
					}
					
				}

				// if there is nor declaration for this symbol it is primitive
				// (probably from base)
				if (Iterables.isEmpty(usedDeclarations)) {

					// create a primitive slice for this symbol
					Slice primitiveSlice = new Slice(new Integer(Objects.hash(recoverSymbol(symbolnode))),
							Collections.<Integer> emptySet(), Collections.<Symbol, Integer> emptyMap());

					// add this single choice for this symbol to the list of
					// choices
					choicePoints.add(Collections.singleton(primitiveSlice));

				} else {

					// set of options we will have
					Set<Slice> choicePoint = Sets.newHashSet();

					// for every declaration used
					for (Node usedDeclaration : usedDeclarations) {

						// if used declaration is same as this one disregard it
						if (usedDeclaration.equals(declarationnode))
							continue;
						
						// add all slices for the declaration
						choicePoint.addAll(generateDeclarationSlices(slices, usedDeclaration));

					}
					// add the options for this symbol
					choicePoints.add(choicePoint);

				}

			}

			// actual choices from possible choices
			Set<List<Slice>> choices = Sets.cartesianProduct(choicePoints);

			// for every choice of slices
			for (List<Slice> choice : choices) {

				// every slice should only be used once
				Set<Slice> uses = new HashSet<Slice>(choice);

				// the hashes of all used slices
				Set<Integer> usedHashes = Sets.newHashSet(Iterables.transform(uses, use -> use.hash));

				// the ast of this declaration
				String ast = (String) declarationnode.getProperty("declarationast");

				// the hash for this slice
				Integer hash = Objects.hash(ast, uses);

				Boolean consistent = true;

				// empty map from symbol to slice hash to find conflicts
				HashMap<Symbol, Integer> symbols = new HashMap<Symbol, Integer>();

				// insert symbols declared by this slice
				for (Node symbolnode : new Hop(OUTGOING, DECLAREDSYMBOL).apply(declarationnode)) {

					// put the symbol for this slice into the symbol map
					symbols.put(recoverSymbol(symbolnode), hash);

				}

				// for every used slice
				for (Slice use : uses) {

					// for every symbol
					for (Entry<Symbol, Integer> entry : use.symbols.entrySet()) {

						Symbol symbol = entry.getKey();
						Integer presentHash = symbols.get(symbol);
						Integer newHash = entry.getValue();

						// if symbol is not present insert it
						if (presentHash == null) {
							symbols.put(symbol, newHash);
						} else {
							
							// if symbol is present but different we have a inconsistency
							if (presentHash != newHash) {
								consistent = false;
								break;
							}
						}

					}
					if (!consistent)
						break;

				}

				// if symbol usage is consistent
				if (consistent) {

					Slice slice = new Slice(hash, usedHashes, symbols);
					// add this slice to the slices for this declaration
					if(slices.get(declarationnode) == null){
						slices.put(declarationnode, Lists.newArrayList(slice));
					}else{
						slices.get(declarationnode).add(slice);
					}
				}

			}
			return slices.get(declarationnode);

		} else {
			// return the slices found earlier
			return declarationSlices;
		}

	}

	private static Symbol recoverSymbol(Node symbolnode) {

		String symbolname = (String) symbolnode.getProperty("symbolname");
		String symbolmodule = (String) symbolnode.getProperty("symbolmodule");
		String symbolgenre = (String) symbolnode.getProperty("symbolgenre");
		return new Symbol(new Origin(symbolmodule, symbolname), symbolgenre);
	}

}
