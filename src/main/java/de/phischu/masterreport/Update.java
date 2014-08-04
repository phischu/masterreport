package de.phischu.masterreport;

public class Update {
	
	public Update(String packagename, String packageversion,
			String dependencyname1, String dependencyversion1,
			String dependencyname2, String dependencyversion2,
			Boolean safe, Boolean legal) {
		super();
		this.packagename = packagename;
		this.packageversion = packageversion;
		this.dependencyname1 = dependencyname1;
		this.dependencyversion1 = dependencyversion1;
		this.dependencyname2 = dependencyname2;
		this.dependencyversion2 = dependencyversion2;
		this.safe = safe;
		this.legal = legal;
	}

	public String packagename;
	public String packageversion;
	public String dependencyname1;
	public String dependencyversion1;
	public String dependencyname2;
	public String dependencyversion2;
	public Boolean safe;
	public Boolean legal;

}
