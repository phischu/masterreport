package de.phischu.masterreport;

import static de.phischu.masterreport.RelationshipTypes.DECLARATION;
import static de.phischu.masterreport.RelationshipTypes.DECLAREDSYMBOL;
import static de.phischu.masterreport.RelationshipTypes.DEPENDENCY;
import static de.phischu.masterreport.RelationshipTypes.MENTIONEDSYMBOL;
import static de.phischu.masterreport.RelationshipTypes.NEXTVERSION;
import static org.neo4j.graphdb.Direction.INCOMING;
import static org.neo4j.graphdb.Direction.OUTGOING;
import static de.phischu.masterreport.Main.Labels.Package;
import static de.phischu.masterreport.Main.Labels.Declaration;
import static de.phischu.masterreport.Main.Labels.Symbol;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.statistics.HistogramDataset;
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
import com.google.gson.Gson;

public class Main {

	public static final String DB_PATH = "data";

	public enum Labels implements Label {
		Package, Declaration, Symbol
	}

	public static void main(String[] args) throws IOException {

		GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);

		Transaction tx = graphDb.beginTx();
		try {
			
			System.out.println("Fetching Updates...");
			LinkedList<Update> updates = Lists.newLinkedList(updates(graphDb));

			System.out.println("Plotting Updates...");
			plotUpdates(updates);
			
			System.out.println("Printing Counts...");
			printCounts(graphDb,updates);
			
			System.out.println("Plotting Packages...");
			plotPackages(graphDb);
			
			System.out.println("Printing Refactorings...");
			printRefactorings(Sets.newHashSet(refactorings(graphDb)));
			
			System.out.println("Trying to install Updates...");
			LinkedList<Update> installedupdates = installUpdates(updates);

			tx.success();
		} finally {
			tx.close();
		}

		graphDb.shutdown();

		System.out.println("done");

	}

	public static LinkedList<Update> installUpdates(LinkedList<Update> updates) {
		
		LinkedList<Update> installedUpdates = new LinkedList<Update>();
		for(Update update : updates){
			Update installedUpdate = update;
			System.out.println("Trying to install: ");
			System.out.println(update.packagename + "-" + update.packageversion);
			System.out.println("with");
			System.out.println("--contraint=\"" + update.dependencyname2 + "==" + update.dependencyversion2+"\"");
			try {
				systemCall("cabal","sandbox","init");
				systemCall("cabal","sandbox","delete");
				Integer exitcode = systemCall("cabal","install",
						"--allow-newer=" + update.dependencyname2,
						"--contraint=\"" + update.dependencyname2 + "==" + update.dependencyversion2+"\"",
						update.packagename + "-" + update.packageversion);
				installedUpdate.installs = exitcode == 0;
			} catch (InterruptedException | IOException e) {
				e.printStackTrace();
			}
			installedUpdates.add(installedUpdate);
		}
		return installedUpdates;
		
	}
	
	public static int systemCall(String ... command) throws InterruptedException, IOException{
		
		ProcessBuilder processbuilder = new ProcessBuilder(command);
		return processbuilder.start().waitFor();
		
	}

	public static void saveUpdates(LinkedList<Update> updates) throws FileNotFoundException, UnsupportedEncodingException {
		
		Gson gson = new Gson();
		PrintWriter writer = new PrintWriter("updates.json", "UTF-8");
		writer.print(gson.toJson(Iterables.filter(updates, x -> !(x.legal && !x.symbolchanged))));
		writer.close();
	}

	public static void plotUpdates(Iterable<Update> updates) throws IOException {
		
		int updatecount = Iterables.size(updates);
		int legalupdatecount = Iterables.size(Iterables.filter(updates,x -> x.legal));
		int safeupdatecount = Iterables.size(Iterables.filter(updates,x -> !x.symbolchanged));
		
		DefaultPieDataset legalupdatedataset = new DefaultPieDataset();
		legalupdatedataset.setValue("Legal", legalupdatecount);
		legalupdatedataset.setValue("Illegal", updatecount - legalupdatecount);
		
		DefaultPieDataset safeupdatedataset = new DefaultPieDataset();
		safeupdatedataset.setValue("Safe", safeupdatecount);
		safeupdatedataset.setValue("Unsafe", updatecount - safeupdatecount);

		PiePlot legalupdateplot = new PiePlot(legalupdatedataset);
		legalupdateplot.setSectionPaint("Legal", Color.green);
		legalupdateplot.setSectionPaint("Illegal", Color.red);
		
		PiePlot safeupdateplot = new PiePlot(safeupdatedataset);
		safeupdateplot.setSectionPaint("Safe", Color.green);
		safeupdateplot.setSectionPaint("Unsafe", Color.red);

		JFreeChart legalupdatechart = new JFreeChart(legalupdateplot);
		JFreeChart safeupdatechart = new JFreeChart(safeupdateplot);

		ChartUtilities.saveChartAsPNG(new File("legalupdates.png"), legalupdatechart, 1024, 768);
		ChartUtilities.saveChartAsPNG(new File("safeupdates.png"), safeupdatechart, 1024, 768);

	}

	public static void printCounts(GraphDatabaseService graphDb,Iterable<Update> updates) throws FileNotFoundException, UnsupportedEncodingException {
		
		int packagecount = Iterables.size(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package));
		
	    Iterable<Node> declarationnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Declaration);
		int declarationcount = Iterables.size(declarationnodes);
		
		TreeSet<String> declarationasts = new TreeSet<String>();
		for(Node declarationnode : declarationnodes){
			declarationasts.add((String) declarationnode.getProperty("declarationast"));
		}
		int declarationastcount = declarationasts.size();
				
		int symbolcount = Iterables.size(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Symbol));
		int updatecount = Iterables.size(updates);
		int legalsafeupdatecount = Iterables.size(Iterables.filter(updates,x -> x.legal && !x.symbolchanged));
		int legalunsafeupdatecount = Iterables.size(Iterables.filter(updates,x -> x.legal && x.symbolchanged));
		int illegalsafeupdatecount = Iterables.size(Iterables.filter(updates,x -> !x.legal && !x.symbolchanged));
		int illegalunsafeupdatecount = Iterables.size(Iterables.filter(updates,x -> !x.legal && x.symbolchanged));
		
		PrintWriter writer = new PrintWriter("counts", "UTF-8");
		writer.println("Package count: " + packagecount);
		writer.println("Declaration count: " + declarationcount);
		writer.println("Declaration AST count: " + declarationastcount);
		writer.println("Symbol count: " + symbolcount);
		writer.println("Update count:" + updatecount);
		writer.println("Legal safe update count: " + legalsafeupdatecount);
		writer.println("Legal unsafe update count: " + legalunsafeupdatecount);
		writer.println("Illegal safe update count: " + illegalsafeupdatecount);
		writer.println("Illegal unsafe update count: " + illegalunsafeupdatecount);
		
		writer.close();
		
	}

	public static void plotPackages(GraphDatabaseService graphDb)
			throws IOException {

		Iterable<Node> packagenodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package);
		long attemptedpackages = 0;
		long successfulpackages = 0;
		for (Node packagenode : packagenodes) {
			attemptedpackages += 1;
			if (packagenode.hasRelationship(OUTGOING,DECLARATION)) {
				successfulpackages += 1;
			}
		}

		long allpackages = 40160;

		DefaultPieDataset dataset = new DefaultPieDataset();
		dataset.setValue("All packages", allpackages - attemptedpackages);
		dataset.setValue("Attempted packages", attemptedpackages
				- successfulpackages);
		dataset.setValue("Successful packages", successfulpackages);

		PiePlot plot = new PiePlot(dataset);
		plot.setSectionPaint("All packages", Color.gray);
		plot.setSectionPaint("Attempted packages", Color.red);
		plot.setSectionPaint("Successful packages", Color.green);

		JFreeChart chart = new JFreeChart(plot);

		ChartUtilities.saveChartAsPNG(new File("hackage.png"), chart, 1024, 768);

	}
	
	public static void printRefactorings(Collection<Pair<String,String>> astpairs) throws FileNotFoundException, UnsupportedEncodingException{
		
		PrintWriter writer = new PrintWriter("refactorings", "UTF-8");
		writer.println(astpairs.size());
		for(Pair<String,String> astpair : astpairs){
			writer.println("AST ONE");
			writer.println(astpair.first());
			writer.println("AST TWO");
			writer.println(astpair.other());
		}
		writer.close();
		
	}
	
	public static Map<Long,Integer> mention(GraphDatabaseService graphDb){
		
		TreeMap<Long,Integer> mentionmap = new TreeMap<Long,Integer>();
		
		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb)
				.getAllNodesWithLabel(Symbol);
		
		for(Node symbolnode : symbolnodes){
			
			Iterable<Node> mentioningnodes = new Hop(INCOMING,MENTIONEDSYMBOL).apply(symbolnode);
			mentionmap.put(symbolnode.getId(), Iterables.size(mentioningnodes));
			
		}
		
		return mentionmap;
		
	}
	
	public static void plotMentionHistogram(GraphDatabaseService graphDb) throws IOException{
		
		HistogramDataset dataset = new HistogramDataset();
		dataset.addSeries("", toDoubles(mention(graphDb).values()), 10);		
		
		LogarithmicAxis rangeaxis = new LogarithmicAxis("Number of Number of mentions");
		rangeaxis.setAllowNegativesFlag(true);
		
		XYPlot plot = new XYPlot(
				dataset,
				new NumberAxis("ln(Number of Mentions)"),
				rangeaxis,
				new DefaultXYItemRenderer());
		
		JFreeChart chart = new JFreeChart(plot);

		ChartUtilities.saveChartAsPNG(new File("mentionhistogram.png"), chart, 1024, 768);
		
	}

	private static double[] toDoubles(Collection<Integer> mentions) {
		double[] mentionarray = new double[mentions.size()];
		
		int i = 0;
		for(Integer m : mentions){
			
			mentionarray[i] = Math.log(m+1);
			i++;
			
		}
		return mentionarray;
	}
	
	public static void plotDeclarationsHistogram(GraphDatabaseService graphDb) throws IOException{
		
		HistogramDataset dataset = new HistogramDataset();
		dataset.addSeries("", toDoubles(differentASTs(graphDb).values()), 160);	
		
		NumberAxis domainaxis = new NumberAxis();
		
		LogarithmicAxis rangeaxis = new LogarithmicAxis("Number of Number of Declarations");
		rangeaxis.setAllowNegativesFlag(true);
		
		XYPlot plot = new XYPlot(dataset, domainaxis, rangeaxis, new DefaultXYItemRenderer());
		
		JFreeChart chart = new JFreeChart(plot);

		ChartUtilities.saveChartAsPNG(new File("declarationhistogram.png"), chart, 1024, 768);
		
	}

	private static Map<Long,Integer> differentASTs(GraphDatabaseService graphDb) {
		
			TreeMap<Long,Integer> declarationsmap = new TreeMap<Long,Integer>();
			
			Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Symbol);
			
			for(Node symbolnode : symbolnodes){
				
				Iterable<Node> declaringnodes = new Hop(INCOMING,DECLAREDSYMBOL).apply(symbolnode);
				
				TreeSet<String> declaringasts = new TreeSet<String>();
				for(Node declaringnode : declaringnodes){
					declaringasts.add((String) declaringnode.getProperty("declarationast"));
				}
				
				declarationsmap.put(symbolnode.getId(), declaringasts.size());
				
			}
			
			return declarationsmap;
	}

	public static Collection<Pair<String, String>> refactorings(
			GraphDatabaseService graphDb) {

		Collection<Pair<String, String>> declarationastpairs = new LinkedList<Pair<String, String>>();

		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb)
				.getAllNodesWithLabel(Symbol);

		for (Node symbolnode : symbolnodes) {

			Collection<Pair<Node, Node>> usages = Lists.newArrayList(usages(symbolnode));

			for (Pair<Node, Node> usage1 : usages) {
				for (Pair<Node, Node> usage2 : usages) {

					String usingast1 = (String) usage1.first().getProperty(
							"declarationast");
					String usedast1 = (String) usage1.other().getProperty(
							"declarationast");
					String usingast2 = (String) usage2.first().getProperty(
							"declarationast");
					String usedast2 = (String) usage2.other().getProperty(
							"declarationast");

					if (usingast1.equals(usingast2)
							&& !usedast1.equals(usedast2)) {
						declarationastpairs.add(Pair.of(usedast1, usedast2));
					}

				}
			}

		}

		return declarationastpairs;

	}


	public static Iterable<Pair<Node, Node>> usages(Node symbolnode) {

		LinkedList<Pair<Node, Node>> usages = new LinkedList<Pair<Node, Node>>();

		Iterable<Node> usingdeclarationnodes = new Hop(INCOMING,MENTIONEDSYMBOL).apply(symbolnode);
		Iterable<Node> useddeclarationnodes = new Hop(INCOMING,DECLAREDSYMBOL).apply(symbolnode);

		for (Node usingdeclarationnode : usingdeclarationnodes) {
			for (Node useddeclarationnode : useddeclarationnodes) {
				
				Iterable<Node> dependencynodes = 
					FluentIterable
					    .from(Collections.singleton(usingdeclarationnode))
					    .transformAndConcat(new Hop(INCOMING,DECLARATION))
					    .transformAndConcat(new Hop(OUTGOING,DEPENDENCY));
				
				Iterable<Node> usedpackagenodes = new Hop(INCOMING,DECLARATION).apply(useddeclarationnode);
				
				if(containsAll(dependencynodes,usedpackagenodes)){
					usages.add(Pair.of(usingdeclarationnode, useddeclarationnode));
				}
			}
		}

		return usages;

	}

	private static boolean containsAll(Iterable<? extends Object> nodes1,Iterable<? extends Object> nodes2) {
		
		for(Object node2 : nodes2){
			if(!Iterables.contains(nodes1, node2)) return false;
		}
		return true;
	}

	private static Iterable<Update> updates(GraphDatabaseService graphDb){
		
		LinkedList<Update> updates = new LinkedList<Update>();
		
		Iterable<Node> packagenodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Package);
		
		for(Node packagenode : packagenodes){
			
			for(Node dependencynode1 : new Hop(OUTGOING,DEPENDENCY).apply(packagenode)){
				
				for(Node dependencynode2 : new Hop(OUTGOING,NEXTVERSION).apply(dependencynode1)){
					
					Boolean updatelegal = Iterables.contains(new Hop(OUTGOING,DEPENDENCY).apply(packagenode),dependencynode2);
				
					Boolean symbolchanged = false;
					
					for(Node usedsymbolnode : FluentIterable
							.from(Collections.singleton(packagenode))
							.transformAndConcat(new Hop(OUTGOING,DECLARATION))
							.transformAndConcat(new Hop(OUTGOING,MENTIONEDSYMBOL))){
						
						FluentIterable<Node> declarationnodes = FluentIterable
						    .from(Collections.singleton(usedsymbolnode))
							.transformAndConcat(new Hop(INCOMING,DECLAREDSYMBOL));
						
						FluentIterable<String> originaldeclarationasts = declarationnodes
						    .filter(x -> Iterables.contains(new Hop(INCOMING,DECLARATION).apply(x),dependencynode1))
						    .transform(x -> (String) x.getProperty("declarationast"));
						
						FluentIterable<String> updateddeclarationasts = declarationnodes
							 .filter(x -> Iterables.contains(new Hop(INCOMING,DECLARATION).apply(x),dependencynode2))
							 .transform(x -> (String) x.getProperty("declarationast"));
						
						if(!containsAll(updateddeclarationasts, originaldeclarationasts)) {
							symbolchanged = true;
						}
					}
					
					updates.add(new Update(
								(String) packagenode.getProperty("packagename"),
								(String) packagenode.getProperty("packageversion"),
								(String) dependencynode1.getProperty("packagename"),
								(String) dependencynode1.getProperty("packageversion"),
								(String) dependencynode2.getProperty("packagename"),
								(String) dependencynode2.getProperty("packageversion"),
								symbolchanged,
								updatelegal));
					
				}
				
			}
			
		}
		
		return updates;
		
	}

}
