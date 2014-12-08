package de.phischu.masterreport;

import static de.phischu.masterreport.Main.Labels.Declaration;
import static de.phischu.masterreport.Main.Labels.Package;
import static de.phischu.masterreport.Main.Labels.Symbol;
import static de.phischu.masterreport.RelationshipTypes.DECLARATION;
import static de.phischu.masterreport.RelationshipTypes.DECLAREDSYMBOL;
import static de.phischu.masterreport.RelationshipTypes.DEPENDENCY;
import static de.phischu.masterreport.RelationshipTypes.INSTALLATION;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
			
			System.out.println("Finding actually used declarations");
			
			Iterable<Node> packagenodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package);
			
			PrintWriter actually_used_file = new PrintWriter("actually_used", "UTF-8");
			
			for(Node packagenode : packagenodes){
				
				String packagename = (String)packagenode.getProperty("packagename");
				String packageversion = (String)packagenode.getProperty("packageversion");
				int installed_declarations_count = Iterables.size(installed_declaration(packagenode));
				int actually_used_declarations_count = Iterables.size(actually_uses_declaration(packagenode));
				System.out.println(
						packagename + "-" +
						packageversion + " " +
						installed_declarations_count + " " + 
						actually_used_declarations_count);
				
			}
			
			actually_used_file.close();

			System.out.println("Analysing Updates...");

			List<UpdateScenario> updateScenarioList = Lists.newLinkedList(updateScenarios(graphDb));
			System.out.println("Update scenarios: " + updateScenarioList.size());
			List<Boolean> isLegalList = Lists.newArrayList(Lists.transform(updateScenarioList, x -> legal(x)));
			System.out.println("Legal: " + countTrue(isLegalList));
			List<Boolean> isMinorList = Lists.newArrayList(Lists.transform(updateScenarioList, x -> x.update.minorMajor.equals("minor")));
			System.out.println("Minor: " + countTrue(isMinorList));
			List<Boolean> affectsList = Lists.newArrayList(Lists.transform(updateScenarioList, x -> affects(x.update,x.packagenode)));
			System.out.println("Affects: " + countTrue(affectsList));

			int hackagecount = 40160;
			int attemptedpackages = Iterables.size(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package));
			int packagecount = Iterables.size(packages(graphDb));
			int majorpackagecount = Iterables.size(majorPackages(graphDb));
			
			Iterable<Node> declarationnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Declaration);
			int declarationcount = FluentIterable.from(declarationnodes).size();
			int declarationastcount = FluentIterable.from(declarationnodes).
					transform(x -> source(x)).
					toSet().
					size();
			int symbolcount = Iterables.size(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Symbol));
			
			int updatecount = FluentIterable.from(packages(graphDb)).
					transformAndConcat(p -> update(p)).
					size();
			int updatescenariocount = updateScenarioList.size();
			
			int prohibitedcount = countFalse(isLegalList);
			int prohibitedunaffectedcount = countTrue(zipWith(isLegalList,affectsList,(l,a) -> !l && !a));
			int allowedunaffectedcount = countTrue(zipWith(isLegalList,affectsList,(l,a) -> l && !a));
			
			int minorcount = countTrue(isMinorList);
			int minorprohibitedcount = countTrue(zipWith(isMinorList,isLegalList,(m,l) -> m && !l));
			
			int majorcount = countFalse(isMinorList);
			int majorprohibitedcount = countTrue(zipWith(isMinorList,isLegalList,(m,l) -> !m && !l));
			int majorprohibitedunaffectedcount = countTrue(zipWith3(isMinorList,isLegalList,affectsList,
					m -> l -> a -> !m && !l && !a));
			int majorallowedcount = countTrue(zipWith(isMinorList,isLegalList,(m,l) -> !m && l));
			int majorallowedunaffectedcount = countTrue(zipWith3(isMinorList,isLegalList,affectsList,
					m -> l -> a -> !m && l && !a));
			
			PrintWriter counts_file = new PrintWriter("counts", "UTF-8");
			counts_file.println("Package count: " + packagecount);
			counts_file.println("Major Package count: " + majorpackagecount);
			counts_file.println("Declaration count: " + declarationcount);
			counts_file.println("Declaration AST count: " + declarationastcount);
			counts_file.println("Symbol count: " + symbolcount);
			counts_file.println("Update count: " + updatecount);
			counts_file.println("Update scenario count: " + updatescenariocount);
			counts_file.println("Prohibited: " + prohibitedcount);
			counts_file.println("Prohibited Unaffected: " + prohibitedunaffectedcount);
			counts_file.println("Allowed Unaffected: " + allowedunaffectedcount);
			counts_file.println("Minor: " + minorcount);
			counts_file.println("Minor Prohibited: " + minorprohibitedcount);
			counts_file.println("Major: " + majorcount);
			counts_file.println("Major Prohibited: " + majorprohibitedcount);
			counts_file.println("Major Prohibited Unaffected: " + majorprohibitedunaffectedcount);
			counts_file.println("Major Allowed: " + majorallowedcount);
			counts_file.println("Major Allowed Unaffected: " + majorallowedunaffectedcount);

			counts_file.close();

			System.out.println("Printing Refactorings...");
			printRefactorings(Sets.newHashSet(replaces(graphDb)));

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
	public static Iterable<Node> majorPackages(GraphDatabaseService graphDb){
		return FluentIterable.from(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package)).
				filter(p -> !Iterables.isEmpty(declares(p))).
				filter(p -> FluentIterable.from(p.getRelationships(INCOMING,NEXTVERSION)).allMatch(r -> r.getProperty("change").equals("major")));
	}
	public static String packagename(Node packagenode){
		return (String) packagenode.getProperty("packagename");
	}
	public static Iterable<Update> update(Node packagenode){
		return FluentIterable.from(packagenode.getRelationships(OUTGOING, NEXTVERSION)).
				filter(r -> !Iterables.isEmpty(declares(r.getEndNode()))).
				transform(r -> new Update((String)r.getProperty("change"), packagenode,r.getEndNode()));
	}
	public static Iterable<Update> majorUpdate(Node packagenode){

        Iterable<Update> currentUpdates = Lists.newArrayList(update(packagenode));
		
		while(!Iterables.isEmpty(currentUpdates)){
			Update currentUpdate = Iterables.getOnlyElement(currentUpdates);
			if(currentUpdate.minorMajor.equals("major")){
				return Collections.singleton(new Update("major",packagenode,currentUpdate.package2));
			}
			currentUpdates = Lists.newArrayList(update(currentUpdate.package2));
		}
		return Collections.emptyList();
	} 
	public static Iterable<Update> futureMajorUpdate(Node packagenode){
		
		List<Update> futureMajorupdates = Lists.newLinkedList();
		Iterable<Update> currentUpdates = Lists.newArrayList(update(packagenode));
		
		while(!Iterables.isEmpty(currentUpdates)){
			Update currentUpdate = Iterables.getOnlyElement(currentUpdates);
			futureMajorupdates.add(new Update("major",packagenode,currentUpdate.package2));
			currentUpdates = Lists.newArrayList(update(currentUpdate.package2));
		}
			
		return futureMajorupdates;
		
	}
public static Iterable<Update> futureUpdate(Node packagenode){
		
		List<Update> futureUpdates = Lists.newLinkedList();
		Iterable<Update> currentUpdates = Lists.newArrayList(update(packagenode));
		Boolean minor = true;
		
		while(!Iterables.isEmpty(currentUpdates)){
			Update currentUpdate = Iterables.getOnlyElement(currentUpdates);
			if(currentUpdate.minorMajor.equals("major")) minor = false;
			
			futureUpdates.add(new Update(minor ? "minor" : "major",packagenode,currentUpdate.package2));
			currentUpdates = Lists.newArrayList(update(currentUpdate.package2));
		}
			
		return futureUpdates;
		
	}
	public static Iterable<Node> dependency(Node packagenode){
		return FluentIterable.from(new Hop(OUTGOING,DEPENDENCY).apply(packagenode)).
				filter(d -> !packagename(d).equals("base")).
				filter(d -> !Iterables.isEmpty(declares(d)));
	}
	public static Iterable<Node> majorDependency(Node packagenode){
		return FluentIterable.from(new Hop(OUTGOING,DEPENDENCY).apply(packagenode)).
				filter(d -> !packagename(d).equals("base")).
				filter(d -> !Iterables.isEmpty(declares(d))).
				filter(d -> FluentIterable.from(d.getRelationships(INCOMING,NEXTVERSION)).allMatch(r -> r.getProperty("change").equals("major")));
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
	public static Iterable<Node> installed_dependency(Node packagenode){
		return new Hop(OUTGOING,INSTALLATION).apply(packagenode);
	}
	public static Iterable<UpdateScenario> updateScenarios(GraphDatabaseService graphDb){
		return FluentIterable.from(packages(graphDb)).
				transformAndConcat(p -> FluentIterable.from(dependency(p)).
						transformAndConcat(d -> FluentIterable.from(futureUpdate(d)).
								transform(u -> new UpdateScenario(u,p))));
	}
	public static Iterable<UpdateScenario> majorUpdateScenarios(GraphDatabaseService graphDb){
		return FluentIterable.from(majorPackages(graphDb)).
				transformAndConcat(p -> FluentIterable.from(majorDependency(p)).
						transformAndConcat(d -> FluentIterable.from(futureMajorUpdate(d)).
								transform(u -> new UpdateScenario(u,p))));
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
				            transformAndConcat(d2 -> package2Declarations.contains(d2) && !source(d1).equals(source(d2)) ?
				            	Collections.singleton(s) : Collections.emptySet())));
		
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
	
	public static Iterable<Pair<String,String>> replaces(GraphDatabaseService graphDb){
		Iterable<Pair<Node,Node>> same_source_declaration_pairs = same_source(graphDb);
		Set<Pair<String,String>> result = Sets.newHashSet();
		for(Pair<Node,Node> same_source_declaration_pair : same_source_declaration_pairs){
			for(Node replacing_declaration : uses_legally(same_source_declaration_pair.first())){
				for(Node replaced_declaration : uses_legally(same_source_declaration_pair.other())){
					result.add(Pair.of(source(replacing_declaration), source(replaced_declaration)));
				}
			}
		}
		return result;
	}
	
	public static Iterable<Pair<Node,Node>> same_source(GraphDatabaseService graphDb){
		return Collections.emptySet();
	}
	
	public static Iterable<Node> uses_legally(Node using_declaration){
		return Collections.emptySet();
	}
	
	public static Iterable<Node> uses(Node declarationnode){
		return FluentIterable.from(Collections.singleton(declarationnode))
				.transformAndConcat(d -> mentions(d))
				.transformAndConcat(s -> boundBy(s));
	}
	
	public static Iterable<Node> actually_uses_transitively(HashSet<Node> installed_declarations, Node declarationnode){
		HashSet<Node> used_declarationnodes = Sets.newHashSet(uses(declarationnode));
		do{
			HashSet<Node> next_declarationnodes = Sets.newHashSet(FluentIterable
					    .from(used_declarationnodes)
					    .transformAndConcat(d -> uses(d)));
			next_declarationnodes.retainAll(installed_declarations);
			if(used_declarationnodes.equals(next_declarationnodes)){
				return used_declarationnodes;
			}else{
				used_declarationnodes = next_declarationnodes;
			}
		}while(true);
	}
	
	public static Iterable<Node> installed_declaration(Node packagenode){
		return FluentIterable.from(Collections.singleton(packagenode))
				.transformAndConcat(p -> installed_dependency(p))
				.transformAndConcat(i -> declares(i));
	}
	
	public static Iterable<Node> actually_uses_declaration(Node packagenode){
		HashSet<Node> installed_declarations = Sets.newHashSet(installed_declaration(packagenode));
		HashSet<Node> used_declarations = Sets.newHashSet(FluentIterable.from(declares(packagenode))
				.transformAndConcat(d -> actually_uses_transitively(installed_declarations, d)));
		return Sets.intersection(installed_declarations, used_declarations);
	}

	private static boolean containsAll(Iterable<? extends Object> nodes1, Iterable<? extends Object> nodes2) {

		for (Object node2 : nodes2) {
			if (!Iterables.contains(nodes1, node2))
				return false;
		}
		return true;
	}

}
