/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Parser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import toolbox.ToolBox;
import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;


/**
 * @test
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.parser
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @summary higher-kinded generics
 */
public class HigherKindedTest {

    private final static ToolBox toolbox = new ToolBox();
    private final static String JAVA_VERSION = System.getProperty("java.specification.version");

    public static void compPass(String code) throws IOException {
        new JavacTask(toolbox)
                .sources(code)
                .classpath(".")
                .options("-encoding", "utf8", "--enable-preview", "-source", JAVA_VERSION)
                .run(Task.Expect.SUCCESS);
    }

    public static void compFail(final String code, final Optional<String> expectedErrMsg) throws IOException {
        final String errorMsg = new JavacTask(toolbox)
                .sources(code)
                .classpath(".")
                .options("-encoding", "utf8", "--enable-preview", "-source", JAVA_VERSION)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        expectedErrMsg.ifPresent(errMsg -> {
            if (!errorMsg.contains(errMsg)) {
                throw new RuntimeException("Expected error message: " + errMsg + ", but got: " + errorMsg);
            }
        });
    }

    public static void compFail(final String code) throws IOException {
        compFail(code, Optional.empty());
    }

    public static void compFail(final String code, final String expectedErrMsg) throws IOException {
        compFail(code, Optional.of(expectedErrMsg));
    }


//    private static String compile(final Task.Expect expect, final String sources) throws IOException {
//        return new JavacTask(toolbox)
//                .sources(sources)
//                .classpath(".")
//                .options("-encoding", "utf8", "--enable-preview", "-source", JAVA_VERSION)
//                .run(expect)
//                .writeAll()
//                .getOutput(Task.OutputKind.DIRECT);
//    }
//
//    private static String compile(final String... sources) throws IOException {
//        return compile(String.join("\n", sources) + "\n");
//    }

    static void run() throws Exception {

        Context context = new Context();
        Log log = Log.instance(context);

        JavacFileManager.preRegister(context);
        ParserFactory pfac = ParserFactory.instance(context);

        final String text =
                "public class Foo<A<C<X1>, D<B>, X>> {\n"
                        + "  public static void main(String[] args) {\n"
                        + " "
                        + "}\n";
        JavaFileObject fo = new SimpleJavaFileObject(URI.create("Foo"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };

        log.useSource(fo);

        FileWriter writer = new FileWriter("/home/roman/Documents/personal/code/jdk/test-log.txt");

        CharSequence cs = fo.getCharContent(false);
        Parser parser = pfac.newParser(cs, false, false, false);
        JCTree.JCCompilationUnit tree = parser.parseCompilationUnit();

        tree.accept(new TreeScanner() {
            @Override
            public void visitClassDef(JCTree.JCClassDecl tree) {
                super.visitClassDef(tree);

                try {
                    writer.write(tree.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void visitTypeParameter(JCTree.JCTypeParameter tree) {
                try {
                    writer.write(tree.name + " " + tree.isTypeConstructor() + " " + tree.getTypeConstructorArity() + "\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                super.visitTypeParameter(tree);
            }
        });
        writer.flush();
    }

    public static void main(String[] args) throws Exception {

        compPass("""
            
            class Bar1<A<B>> {
              // int x = "asd";
              // class A<X, D> {}
              
             // B<A<String, >>
              
              class Foo1<C<T<D>>> {}
              
              Bar1<Foo1> d = new Bar1<Foo1>();
              
              // void doSomething(A<String> xx) {}
            }
        """);

        compPass("""
            class Foo<Bar<A<B<C>>, D, FooBar<E>>> {}
        """);


        compFail(
                "class Foo<Bar<A>, FooBar<Bar>> {}",
                "type variable Bar is already defined in class Foo"
        );
//        compFail("""
//            class Foo {}
//            class Bar<A<B>> { static Bar<Foo> d = new Bar<>(); }
//        """,
//        "type Foo does not take parameters"
//        );
//
//        run();

    }
}
