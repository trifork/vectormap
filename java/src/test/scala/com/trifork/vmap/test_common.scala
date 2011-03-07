package com.trifork.vmap

import junit.framework.TestResult
import org.specs.runner.JUnit4

import org.specs.Specification

abstract class AbstractTest(spec:Specification) extends JUnit4(spec) {

  // Why isn't errors reported correctly by JUnit/Maven??
  override def run(r:TestResult) = {
    Console.println("Running Specs tests...");
    val res = super.run(r)
    Console.println("=> Tests run: "+r.runCount()+", Failures: "+r.failureCount()+", Errors: "+r.errorCount());
    for (tf <- new RichEnumeration(r.failures)) Console.println("*** Test Failure: *** "+tf);
    for (te <- new RichEnumeration(r.errors))   Console.println("*** Test Error: *** "+te);
    res
  }

  class RichEnumeration[T](enumeration:java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext:Boolean =  enumeration.hasMoreElements()
    def next:T = enumeration.nextElement()
  }
}
