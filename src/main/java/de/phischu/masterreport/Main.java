package de.phischu.masterreport;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;

public class Main {
	
	public static void main(String[] args) throws IOException{
		
        plotPackages(30000,2355,1034);
		
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
