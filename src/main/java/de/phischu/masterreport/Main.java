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
			
			Set<Pair<String,String>> astpairs = IteratorUtil.asSet(differentButCompatibleASTs(graphDb));
			
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
	
	public static Collection<Pair<String,String>> differentButCompatibleASTs(GraphDatabaseService graphDb){
		
		Collection<Pair<String,String>> declarationastpairs = new LinkedList<Pair<String,String>>();
		
		Iterable<Node> symbolnodes = GlobalGraphOperations.at(graphDb).getAllNodesWithLabel(Labels.Symbol);
		
		for(Node symbolnode : symbolnodes){
			
			Collection<Node> declarationnodes = IteratorUtil.asCollection(
					previousNodes(RelationshipTypes.DECLAREDSYMBOL, symbolnode));
			
			for(Node declarationnode1 : declarationnodes){
				for(Node declarationnode2 : declarationnodes){
					
					String declarationast1 = (String) declarationnode1.getProperty("declarationast");
					String declarationast2 = (String) declarationnode2.getProperty("declarationast");
					
					if(declarationast1.equals(declarationast2)) continue;
					
					Node packagenode1 = declarationnode1.getSingleRelationship(RelationshipTypes.DECLARATION, Direction.INCOMING).getStartNode();
					Node packagenode2 = declarationnode2.getSingleRelationship(RelationshipTypes.DECLARATION, Direction.INCOMING).getStartNode();
					
					Iterable<Node> dependingpackagenodes1 = previousNodes(RelationshipTypes.DEPENDENCY,packagenode1);
					Iterable<Node> dependingpackagenodes2 = previousNodes(RelationshipTypes.DEPENDENCY,packagenode2);
					
					Set<Node> dependingpackagenodes = IteratorUtil.asSet(dependingpackagenodes1);
					dependingpackagenodes.retainAll(IteratorUtil.asCollection(dependingpackagenodes2));
					
					for(Node dependingpackagenode : dependingpackagenodes){
						
						Iterable<Node> dependingdeclarationnodes = nextNodes(RelationshipTypes.DECLARATION,dependingpackagenode);
						Iterable<Node> mentioningdeclarationnodes = previousNodes(RelationshipTypes.MENTIONEDSYMBOL, symbolnode);
						
					    Set<Node> intersection = IteratorUtil.asSet(dependingdeclarationnodes);
					    intersection.retainAll(IteratorUtil.asCollection(mentioningdeclarationnodes));
						
						if(!intersection.isEmpty()){
							declarationastpairs.add(Pair.of(declarationast1, declarationast2));
							break;
						}
					}
				}
			}
		}
		
		return declarationastpairs;
		
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
