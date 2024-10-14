package io.github.classgraph.issues.issue882;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

public class BinaryInterfaceTest {
	
    @Test
    void returnOnlyAccessibleClassDependencies() {
        ClassGraph classGraph = new ClassGraph();

        // return only dependencies that are accessible from the given class 'SubClass'
        classGraph.enableAcessibleInterClassDependencies();
        classGraph.acceptPackages("io.github.classgraph.issues.issue882");

        try(ScanResult scan = classGraph.scan()) {
            ClassInfo classInfo = scan.getClassInfo("io.github.classgraph.issues.issue882.SubClass");
            ClassInfoList classDependencies = classInfo.getClassDependencies();

            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ParentClass"), "dependency from public parent class must be found");
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PublicDependency"), "dependency from public method must be found");
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ProtectedFieldDependency"), "dependency from protected field must be found");
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ProtectedDependency"), "dependency from protected method must be found");
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PackagePrivateDependency"), "dependency from package private method must be found");
            assertFalse(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PrivateDependency"), "dependency from private method must NOT be found");
        }
    }

    @Test
    void returnOnlyAccessibleClassDependenciesForDifferentPackage() {
        ClassGraph classGraph = new ClassGraph();

        // return only dependencies that are accessible from the given class 'SubClass'
        classGraph.enableAcessibleInterClassDependencies();
        classGraph.acceptPackages("io.github.classgraph.issues.issue882", "io.github.classgraph.issues.issue882.subpackage");

        try(ScanResult scan = classGraph.scan()) {
            ClassInfo classInfo = scan.getClassInfo("io.github.classgraph.issues.issue882.subpackage.SubClassDifferentPackage");
            ClassInfoList classDependencies = classInfo.getClassDependencies();

            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ParentClass"), "dependency from public parent class must be found");
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PublicDependency"), "dependency from public method must be found");
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ProtectedFieldDependency"), "dependency from protected field must be found");
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ProtectedDependency"), "dependency from protected method must be found");
            assertFalse(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PackagePrivateDependency"), "dependency from package private method of different package must NOT be found");
            assertFalse(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PrivateDependency"), "dependency from private method must NOT be found");
        }
    }

    
    @Test
    void returnAllClassDependencies() {
        ClassGraph classGraph = new ClassGraph();

        // return all inter class dependencies, including dependencies from e.g. private fields
        classGraph.enableInterClassDependencies();
        classGraph.acceptPackages("io.github.classgraph.issues.issue882");

        try(ScanResult scan = classGraph.scan()) {
            ClassInfo classInfo = scan.getClassInfo("io.github.classgraph.issues.issue882.SubClass");
            ClassInfoList classDependencies = classInfo.getClassDependencies();

            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ParentClass"));
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PublicDependency"));
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ProtectedFieldDependency"));
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ProtectedDependency"));
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PackagePrivateDependency"));
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PrivateDependency"));
        }
    }

}
