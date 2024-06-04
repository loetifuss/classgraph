package io.github.classgraph.issues.issue854;

import org.junit.jupiter.api.Test;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.ScanResult;

import static org.junit.jupiter.api.Assertions.*;

class Issue854Test {
    @Test
    void getFullyQualifiedClassName() {
        ClassLoader mainClassLoader = Issue854Test.class.getClassLoader();
        ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().ignoreClassVisibility()
                .ignoreFieldVisibility().ignoreMethodVisibility().overrideClassLoaders(mainClassLoader).scan();

        String anonymousClass = "com.google.common.collect.TreeRangeMap$SubRangeMap$1";
        ClassInfo classInfo = scanResult.getClassInfo(anonymousClass);
        ClassRefTypeSignature signature = classInfo.getTypeSignatureOrTypeDescriptor().getSuperclassSignature();

        String subRangeMapAsMapClassName = signature.getFullyQualifiedClassName();
        // This is what we get back
        assertEquals("com.google.common.collect.TreeRangeMap$SubRangeMap.SubRangeMapAsMap",
                subRangeMapAsMapClassName);
        // With the "$" rather than the "." it would actually be usable to get class info for
        String rightClassName = "com.google.common.collect.TreeRangeMap$SubRangeMap$SubRangeMapAsMap";
        assertNotNull(scanResult.getClassInfo(rightClassName));
        // But unfortunately it will fail here
        assertNotNull(scanResult.getClassInfo(subRangeMapAsMapClassName));
    }
}