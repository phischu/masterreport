package de.phischu.masterreport;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

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
	
	public enum Labels implements Label
	{
		Package,
		Declaration,
		Symbol
    }
	
	private static enum RelationshipTypes implements RelationshipType
	{
		DEPENDENCY,
	    DECLARATION,
	    MENTIONEDSYMBOL,
	    DECLAREDSYMBOL
	}

	public static void main(String[] args) throws IOException {

		GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);

		Transaction tx = graphDb.beginTx();
		try {
			
			plotPackages(graphDb);
			
			Set<Pair<String,String>> astpairs = IteratorUtil.asSet(refactoring(graphDb));
			
			System.out.println(astpairs.size());
			
			for(Pair<String,String> astpair : astpairs){
				System.out.println("AST ONE");
				System.out.println(astpair.first());
				System.out.println("AST TWO");
				System.out.println(astpair.other());
			}

			tx.success();
		} finally {
			tx.close();
		}

		graphDb.shutdown();

		System.out.println("done");

	}
	
	public static void plotPackages(GraphDatabaseService graphDb) throws IOException{
		
		Iterable<Node> packagenodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Labels.Package);
		long attemptedpackages = 0;
		long successfulpackages = 0;
		for(Node packagenode : packagenodes){
			attemptedpackages += 1;
			if(packagenode.hasRelationship(Direction.OUTGOING, RelationshipTypes.DECLARATION)){
				successfulpackages += 1;
			}
		}
		
		long allpackages = 40160;
		
		DefaultPieDataset dataset = new DefaultPieDataset();
		dataset.setValue("All packages", allpackages - attemptedpackages);
		dataset.setValue("Attempted packages", attemptedpackages - successfulpackages);
		dataset.setValue("Successful packages", successfulpackages);
		
		PiePlot plot = new PiePlot(dataset);
		plot.setSectionPaint("All packages", Color.gray);
		plot.setSectionPaint("Attempted packages", Color.red);
		plot.setSectionPaint("Successful packages", Color.green);
		
		JFreeChart chart = new JFreeChart(plot);
		
		ChartUtilities.saveChartAsPNG(new File("chart.png"), chart, 1024, 768);
		
	}
	
	public static Collection<Pair<String,String>> refactoring(GraphDatabaseService graphDb){
		
		Collection<Pair<String,String>> declarationastpairs = new LinkedList<Pair<String,String>>();
		
		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Labels.Symbol);
		
		for(Node symbolnode : symbolnodes){
			
			Iterable<Pair<Node,Node>> usages = usage(symbolnode);
			for(Pair<Node,Node> usage1 : IteratorUtil.asCollection(usages)){
				for(Pair<Node,Node> usage2 : IteratorUtil.asCollection(usages)){
					String usingast1 = (String) usage1.first().getProperty("declarationast");
					String usedast1 = (String) usage1.other().getProperty("declarationast");
					String usingast2 = (String) usage2.first().getProperty("declarationast");
					String usedast2 = (String) usage2.other().getProperty("declarationast");
					if(usingast1.equals(usingast2) && !usedast1.equals(usedast2)){
						declarationastpairs.add(Pair.of(usedast1, usedast2));
					}
				}
			}
			
			
		}
			
		
		
		return declarationastpairs;
		
	}
	
	private static Iterable<Pair<Node, Node>> usage(Node symbolnode) {
		// TODO Auto-generated method stub
		return null;
	}

	private static Iterable<Node> nextNodes(RelationshipTypes relationshiptype,Node node) {
		return Iterables.map(
				new Function<Relationship,Node>(){
					public Node apply(Relationship relationship) {
						return relationship.getEndNode();}},
				node.getRelationships(Direction.OUTGOING, relationshiptype));
	}

	public static Iterable<Node> previousNodes(RelationshipType relationshiptype,Node node){
		return Iterables.map(
				new Function<Relationship,Node>(){
					public Node apply(Relationship relationship) {
						return relationship.getStartNode();}},
				node.getRelationships(Direction.INCOMING, relationshiptype));
	}

}
