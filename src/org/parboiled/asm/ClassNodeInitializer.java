/*
 * Copyright (C) 2009 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.parboiled.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.MethodNode;

public class ClassNodeInitializer extends EmptyVisitor implements ClassTransformer, Opcodes {

    private final ClassTransformer nextTransformer;
    private ParserClassNode classNode;

    public ClassNodeInitializer(ClassTransformer nextTransformer) {
        this.nextTransformer = nextTransformer;
    }

    public ParserClassNode transform(ParserClassNode classNode) throws Exception {
        this.classNode = classNode;

        // walk up the parser parent class chain
        Class<?> parentClass = classNode.parentClass;
        String newParserType = classNode.getParentType().getInternalName() + "$$parboiled";
        while (!Object.class.equals(parentClass)) {
            Type superType = Type.getType(parentClass);

            // initialize classNode super types list
            classNode.superTypes.add(superType);

            // extract methods from super type
            ClassReader classReader = new ClassReader(superType.getClassName());
            classReader.accept(
                    new RemappingClassAdapter(this, new SimpleRemapper(superType.getInternalName(), newParserType)),
                    ClassReader.SKIP_FRAMES
            );

            parentClass = parentClass.getSuperclass();
        }

        return nextTransformer != null ? nextTransformer.transform(classNode) : classNode;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (classNode.name == null) {
            classNode.visit(V1_5, ACC_PUBLIC, name, null, classNode.getParentType().getInternalName(), null);
        }
    }

    @Override
    public void visitSource(String source, String debug) {
        classNode.visitSource(source, debug);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (isRuleCreatingMethod(desc)) {
            // TODO: throw on private methods
            // create method overriding original rule creating method copying the implementation from the super class
            MethodNode method = new MethodNode(access, name, desc, signature, exceptions);
            classNode.methods.add(method);
            return method; // return the newly created method in order to have it "filled" with the supers code
        } else if (classNode.constructor == null && "<init>".equals(name)) {
            // TODO: throw on private constructor
            classNode.constructor = new MethodNode(ACC_PUBLIC, name, desc, signature, exceptions);
        }
        return null;
    }

    private boolean isRuleCreatingMethod(String methodDesc) {
        return AsmUtils.RULE_TYPE.equals(Type.getReturnType(methodDesc)) && Type.getArgumentTypes(methodDesc).length == 0;
    }

    @Override
    public void visitEnd() {
        classNode.visitEnd();
    }

}
