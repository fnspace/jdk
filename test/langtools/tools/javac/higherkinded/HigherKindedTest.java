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

    public static void main(String[] args) throws Exception {
        compPass("class Foo<Bar<A<B<C>>, D, FooBar<E>>> {}");

        compPass(
                """
                    class Foo<C> {}
                    class Bar<A<B>> {
                      Bar<Foo> bar = new Bar<Foo>(); // TODO: handle diamond operator: new Bar1<>();
                    }
                """
        );

        compPass(
                """
                    class Foo<A, B> {}
                    class Bar<A<B, C>> {
                      Bar<Foo> bar = new Bar<Foo>();
                    }
                """
        );

        compFail(
                """
                    class Foo<A<A1>, B<B1>> {}
                    class Bar<A<B, C>> {
                      Bar<Foo> bar = new Bar<Foo>();
                    }
                """,
                "type constructor mismatch: expected A<B,C> but got Foo<A<A1>,B<B1>>"
        );

        compFail(
                "class Foo<Bar<A>, FooBar<Bar>> {}",
                "type variable Bar is already defined in class Foo"
        );

        compFail(
                """
                    class Foo {}
                    class Bar<A<B>> {
                      Bar<Foo> bar = new Bar<Foo>();
                    }
                """,
                "expected type constructor A<B> but got Foo"
        );

        compFail(
                """
                    class Foo<A, B> {}
                    class Bar<A<B>> {
                      Bar<Foo> bar = new Bar<Foo>();
                    }
                """,
                "type constructor mismatch: expected A<B> but got Foo<A,B>"
        );

//        compPass(
//                """
//                    interface Function<A, B> {
//                        B apply(A a);
//                    }
//
//                    interface Functor<Asd<C>> {
//                        <A, B> Asd<B> map(Asd<A> fa, Function<A, B> f);
//                    }
//                """
//        );
    }
}
