package de.phischu.masterreport;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

public class Hop implements Function<Node,Iterable<Node>>{
	
	public Direction direction;
	public RelationshipType relationshiptype;
	
	Hop(Direction direction,RelationshipType relationshiptype){
		
		this.direction = direction;
		this.relationshiptype = relationshiptype;
		
	}

	public Iterable<Node> apply(Node node) {
		return
			Iterables.transform(
					
				node.getRelationships(direction, relationshiptype),
				
				new Function<Relationship, Node>() {
					
					public Node apply(Relationship relationship) {
						if(direction.equals(Direction.OUTGOING)){
							return relationship.getEndNode();
						}else{
							return relationship.getStartNode();
						}
						
					}
					
				});
	}

}
