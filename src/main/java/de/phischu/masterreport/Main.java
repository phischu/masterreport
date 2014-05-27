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
		
		DefaultPieDataset dataset = new DefaultPieDataset();
		dataset.setValue("All packages", 30000);
		dataset.setValue("Attempted packages", 2345);
		dataset.setValue("Successful packages", 1034);
		
		PiePlot plot = new PiePlot(dataset);
		plot.setSectionPaint("All packages", Color.gray);
		plot.setSectionPaint("Attempted packages", Color.red);
		plot.setSectionPaint("Successful packages", Color.green);
		
		
		JFreeChart chart = new JFreeChart(plot);
		
		ChartUtilities.saveChartAsPNG(new File("chart.png"), chart, 1024, 768);
		
		System.out.println("done");
		
	}

}
