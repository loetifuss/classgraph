package io.github.classgraph.issues.issue882;

public abstract class ParentClass {

	private String privateField = "I'm private";
	
	protected ProtectedFieldDependency protectedField = new ProtectedFieldDependency();
	
    private PrivateDependency privateDependency() {
        return new PrivateDependency();
    }

    protected ProtectedDependency protectedDependency() {
        return new ProtectedDependency();
    }
    
    public PublicDependency publicDependency() {
        return new PublicDependency();
    }
    
    PackagePrivateDependency packagePrivateDependency() {
        return new PackagePrivateDependency();
    }
    

}
