package de.phischu.masterreport;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.helpers.Function;
import org.neo4j.helpers.Pair;
import org.neo4j.tooling.GlobalGraphOperations;

public class Main {

	public static final String DB_PATH = "data";

	public enum Labels implements Label {
		Package, Declaration, Symbol
	}

	private static enum RelationshipTypes implements RelationshipType {
		DEPENDENCY, DECLARATION, MENTIONEDSYMBOL, DECLAREDSYMBOL
	}

	public static void main(String[] args) throws IOException {

		GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);

		Transaction tx = graphDb.beginTx();
		try {
			
			plotPackages(graphDb);
			
			printRefactorings(IteratorUtil.asSet(refactoring(graphDb)));

			tx.success();
		} finally {
			tx.close();
		}

		graphDb.shutdown();

		System.out.println("done");

	}

	public static void plotPackages(GraphDatabaseService graphDb)
			throws IOException {

		Iterable<Node> packagenodes = GlobalGraphOperations.at(graphDb)
				.getAllNodesWithLabel(Labels.Package);
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

		ChartUtilities.saveChartAsPNG(new File("chart.png"), chart, 1024, 768);

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

	public static Collection<Pair<String, String>> refactoring(
			GraphDatabaseService graphDb) {

		Collection<Pair<String, String>> declarationastpairs = new LinkedList<Pair<String, String>>();

		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb)
				.getAllNodesWithLabel(Labels.Symbol);

		for (Node symbolnode : symbolnodes) {

			Iterable<Pair<Node, Node>> usages = usage(symbolnode);

			for (Pair<Node, Node> usage1 : IteratorUtil.asCollection(usages)) {
				for (Pair<Node, Node> usage2 : IteratorUtil
						.asCollection(usages)) {

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

	private static Iterable<Pair<Node, Node>> usage(Node symbolnode) {

		LinkedList<Pair<Node, Node>> usages = new LinkedList<Pair<Node, Node>>();

		Iterable<Node> usingnodes = previousNodes(
				RelationshipTypes.MENTIONEDSYMBOL, symbolnode);
		Iterable<Node> usednodes = previousNodes(
				RelationshipTypes.DECLAREDSYMBOL, symbolnode);

		for (Node usingnode : usingnodes) {
			for (Node usednode : usednodes) {

				for (Node dependingnode : previousNodes(
						RelationshipTypes.DECLARATION, usingnode)) {
					for (Node dependencynode : previousNodes(
							RelationshipTypes.DECLARATION, usednode)) {

						Collection<Node> dependencynodes = IteratorUtil
								.asCollection(nextNodes(
										RelationshipTypes.DEPENDENCY,
										dependingnode));

						if (dependencynodes.contains(dependencynode)) {
							usages.add(Pair.of(usingnode, usednode));
						}
					}
				}
			}
		}

		return usages;

	}

	private static Iterable<Node> nextNodes(RelationshipTypes relationshiptype,
			Node node) {
		return Iterables.map(new Function<Relationship, Node>() {
			public Node apply(Relationship relationship) {
				return relationship.getEndNode();
			}
		}, node.getRelationships(Direction.OUTGOING, relationshiptype));
	}

	public static Iterable<Node> previousNodes(
			RelationshipType relationshiptype, Node node) {
		return Iterables.map(new Function<Relationship, Node>() {
			public Node apply(Relationship relationship) {
				return relationship.getStartNode();
			}
		}, node.getRelationships(Direction.INCOMING, relationshiptype));
	}

}
