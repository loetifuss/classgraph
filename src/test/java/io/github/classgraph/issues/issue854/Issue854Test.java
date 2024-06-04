package io.github.classgraph.issues.issue854;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.ScanResult;

class Issue854Test {
    @Test
    void getFullyQualifiedClassName() {
        ClassLoader mainClassLoader = Issue854Test.class.getClassLoader();
        ScanResult scanResult = new ClassGraph().enableClassInfo().enableAnnotationInfo().ignoreClassVisibility()
                .ignoreFieldVisibility().ignoreMethodVisibility().overrideClassLoaders(mainClassLoader)
                .acceptPackages("com.google.common.collect").scan();

        String anonymousClass = "com.google.common.collect.TreeRangeMap$SubRangeMap$1";
        ClassInfo classInfo = scanResult.getClassInfo(anonymousClass);
        ClassRefTypeSignature signature = classInfo.getTypeSignatureOrTypeDescriptor().getSuperclassSignature();

        // Before the fix to 854, this would give the following, because type parameter token parsing
        // did not stop at '.':
        // com.google.common.collect.TreeRangeMap$SubRangeMap.SubRangeMapAsMap
        // But the fully-qualified class name in the classfile is:
        // com.google.common.collect.TreeRangeMap$SubRangeMap$SubRangeMapAsMap
        String subRangeMapAsMapClassName = signature.getFullyQualifiedClassName();
        assertThat(subRangeMapAsMapClassName)
                .isEqualTo("com.google.common.collect.TreeRangeMap$SubRangeMap$SubRangeMapAsMap");
        assertNotNull(scanResult.getClassInfo(subRangeMapAsMapClassName));
    }
}