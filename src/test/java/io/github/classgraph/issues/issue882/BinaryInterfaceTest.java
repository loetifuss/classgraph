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

        classGraph.enableAcessibleInterClassDependencies();
        classGraph.acceptPackages("io.github.classgraph.issues.issue882");

        try(ScanResult scan = classGraph.scan()) {
            ClassInfo classInfo = scan.getClassInfo("io.github.classgraph.issues.issue882.SubClass");
            ClassInfoList classDependencies = classInfo.getClassDependencies();

            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PublicDependency"));
            assertTrue(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.ProtectedDependency"));
            assertFalse(classDependencies.getNames().contains("io.github.classgraph.issues.issue882.PrivateDependency"));
        }
    }
}
