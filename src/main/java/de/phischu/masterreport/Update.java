package de.phischu.masterreport;

import org.neo4j.graphdb.Node;

public class Update {
	
	public Update(String minorMajor, Node package1, Node package2) {
		super();
		this.minorMajor = minorMajor;
		this.package1 = package1;
		this.package2 = package2;
	}
	public String minorMajor;
	public Node package1;
	public Node package2;

}
