package de.phischu.masterreport;

import org.neo4j.graphdb.Node;

public class UpdateScenario {
	
	public Update update;
	public Node packagenode;
	public UpdateScenario(Update update, Node packagenode) {
		super();
		this.update = update;
		this.packagenode = packagenode;
	}

}
