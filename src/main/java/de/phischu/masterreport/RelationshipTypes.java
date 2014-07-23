package de.phischu.masterreport;

import org.neo4j.graphdb.RelationshipType;

public enum RelationshipTypes implements RelationshipType {
	DEPENDENCY, DECLARATION, MENTIONEDSYMBOL, DECLAREDSYMBOL, NEXTVERSION
}
