package de.phischu.masterreport;

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
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.Pair;
import org.neo4j.tooling.GlobalGraphOperations;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import static org.neo4j.graphdb.Direction.*;
import static de.phischu.masterreport.RelationshipTypes.*;

public class Main {

	public static final String DB_PATH = "data";

	public enum Labels implements Label {
		Package, Declaration, Symbol
	}

	public static void main(String[] args) throws IOException {

		GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);

		Transaction tx = graphDb.beginTx();
		try {
			
			printCounts(graphDb);
			
			plotPackages(graphDb);
			
			plotMentionHistogram(graphDb);
			
			plotDeclarationsHistogram(graphDb);
			
			printRefactorings(Sets.newTreeSet(refactorings(graphDb)));

			tx.success();
		} finally {
			tx.close();
		}

		graphDb.shutdown();

		System.out.println("done");

	}

	public static void printCounts(GraphDatabaseService graphDb) throws FileNotFoundException, UnsupportedEncodingException {
		
		Long packagecount = Iterables.count(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Labels.Package));
		
	    Iterable<Node> declarationnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Labels.Declaration);
		Long declarationcount = Iterables.count(declarationnodes);
		
		TreeSet<String> declarationasts = new TreeSet<String>();
		for(Node declarationnode : declarationnodes){
			declarationasts.add((String) declarationnode.getProperty("declarationast"));
		}
		int declarationastcount = declarationasts.size();
				
		Long symbolcount = Iterables.count(GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Labels.Symbol));
		
		PrintWriter writer = new PrintWriter("counts", "UTF-8");
		writer.println("Package count: " + packagecount);
		writer.println("Declaration count: " + declarationcount);
		writer.println("Declaration AST count: " + declarationastcount);
		writer.println("Symbol count: " + symbolcount);
		
		writer.close();
		
	}

	public static void plotPackages(GraphDatabaseService graphDb)
			throws IOException {

		Iterable<Node> packagenodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Labels.Package);
		long attemptedpackages = 0;
		long successfulpackages = 0;
		for (Node packagenode : packagenodes) {
			attemptedpackages += 1;
			if (packagenode.hasRelationship(Direction.OUTGOING,
					RelationshipTypes.DECLARATION)) {
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
	
	public static Map<Long,Long> mention(GraphDatabaseService graphDb){
		
		TreeMap<Long,Long> mentionmap = new TreeMap<Long,Long>();
		
		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb)
				.getAllNodesWithLabel(Labels.Symbol);
		
		for(Node symbolnode : symbolnodes){
			
			Iterable<Node> mentioningnodes = new Hop(Direction.INCOMING,RelationshipTypes.MENTIONEDSYMBOL).apply(symbolnode);
			mentionmap.put(symbolnode.getId(), Iterables.count(mentioningnodes));
			
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

	private static double[] toDoubles(Collection<Long> mentions) {
		double[] mentionarray = new double[mentions.size()];
		
		int i = 0;
		for(Long m : mentions){
			
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

	private static Map<Long,Long> differentASTs(GraphDatabaseService graphDb) {
		
			TreeMap<Long,Long> declarationsmap = new TreeMap<Long,Long>();
			
			Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb)
					.getAllNodesWithLabel(Labels.Symbol);
			
			for(Node symbolnode : symbolnodes){
				
				Iterable<Node> declaringnodes = new Hop(INCOMING,RelationshipTypes.DECLAREDSYMBOL).apply(symbolnode);
				
				TreeSet<String> declaringasts = new TreeSet<String>();
				for(Node declaringnode : declaringnodes){
					declaringasts.add((String) declaringnode.getProperty("declarationast"));
				}
				
				declarationsmap.put(symbolnode.getId(), (long) declaringasts.size());
				
			}
			
			return declarationsmap;
	}

	public static Collection<Pair<String, String>> refactorings(
			GraphDatabaseService graphDb) {

		Collection<Pair<String, String>> declarationastpairs = new LinkedList<Pair<String, String>>();

		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb)
				.getAllNodesWithLabel(Labels.Symbol);

		for (Node symbolnode : symbolnodes) {

			Collection<Pair<Node, Node>> usages = IteratorUtil.asCollection(usages(symbolnode));

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
				
				Iterable<Node> usingpackagenodes = new Hop(Direction.INCOMING,DECLARATION).apply(usingdeclarationnode);
				
				if(containsAll(dependencynodes,usingpackagenodes)){
					usages.add(Pair.of(usingdeclarationnode, useddeclarationnode));
				}
			}
		}

		return usages;

	}

	private static boolean containsAll(Iterable<Node> nodes1,Iterable<Node> nodes2) {
		
		for(Node node2 : nodes2){
			if(!Iterables.contains(nodes1, node2)) return false;
		}
		return true;
	}

}
