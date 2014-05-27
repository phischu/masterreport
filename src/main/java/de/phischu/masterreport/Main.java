package de.phischu.masterreport;

import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.data.xy.DefaultXYDataset;

public class Main {
	
	public static void main(String[] args) throws IOException{
		
		DefaultXYDataset dataset = new DefaultXYDataset();
		NumberAxis xaxis = new NumberAxis();
		NumberAxis yaxis = new NumberAxis();
		DefaultXYItemRenderer itemrenderer = new DefaultXYItemRenderer();
		
		XYPlot plot = new XYPlot(dataset,xaxis,yaxis,itemrenderer);
		
		JFreeChart chart = new JFreeChart(plot);
		
		ChartUtilities.saveChartAsPNG(new File("chart.png"), chart, 1024, 768);
		
	}

}
