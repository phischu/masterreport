package de.phischu.masterreport;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.tooling.GlobalGraphOperations;

public class Main {

	public static final String DB_PATH = "/home/pschuster/Projects/symbols/data";
	
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
			
			plotPackages(allpackages - attemptedpackages, attemptedpackages - successfulpackages, successfulpackages);

			tx.success();
		} finally {
			tx.close();
		}

		graphDb.shutdown();

		System.out.println("done");

	}
	
	public static void plotPackages(Number allpackages,Number attemptedpackages,Number successfulpackages) throws IOException{
		
		DefaultPieDataset dataset = new DefaultPieDataset();
		dataset.setValue("All packages", allpackages);
		dataset.setValue("Attempted packages", attemptedpackages);
		dataset.setValue("Successful packages", successfulpackages);
		
		PiePlot plot = new PiePlot(dataset);
		plot.setSectionPaint("All packages", Color.gray);
		plot.setSectionPaint("Attempted packages", Color.red);
		plot.setSectionPaint("Successful packages", Color.green);
		
		JFreeChart chart = new JFreeChart(plot);
		
		ChartUtilities.saveChartAsPNG(new File("chart.png"), chart, 1024, 768);
		
	}

}
