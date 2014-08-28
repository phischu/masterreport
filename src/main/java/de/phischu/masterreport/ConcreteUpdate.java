package de.phischu.masterreport;

import org.neo4j.graphdb.Node;

public class ConcreteUpdate {
	
	public Update update;
	public Node packagenode;
	public ConcreteUpdate(Update update, Node packagenode) {
		super();
		this.update = update;
		this.packagenode = packagenode;
	}

}
