package de.phischu.masterreport;

import java.util.Map;
import java.util.Set;

public class Slice {
	
	Slice(Integer hash,Set<Integer> uses,Map<Symbol,Integer> symbols){
		this.hash = hash;
		this.uses = uses;
		this.symbols = symbols;
	}
	
	public Integer hash;
	public Set<Integer> uses;
	public Map<Symbol,Integer> symbols;
	
	public int hashCode(){
		return hash;
	}

}
