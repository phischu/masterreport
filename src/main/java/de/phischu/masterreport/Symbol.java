package de.phischu.masterreport;

import java.util.Objects;

public class Symbol {
	
	public Origin origin;
	public String entity;
	
	public int hashCode(){
		return Objects.hash(origin,entity);
	}

	public Symbol(Origin origin, String entity) {
		super();
		this.origin = origin;
		this.entity = entity;
	}

}
