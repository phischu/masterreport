package de.phischu.masterreport;

import java.util.Objects;

public class Origin {

	public String module;
	public String name;
	
	public int hashCode(){
		return Objects.hash(module,name);
	}

	public Origin(String module, String name) {
		super();
		this.module = module;
		this.name = name;
	}
	
}
