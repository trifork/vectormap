package com.trifork.multiversion_common

import junit.framework.TestResult
import org.specs.runner.JUnit4

import org.scalacheck.{Prop,Test,ConsoleReporter}

import org.specs.Specification
import org.specs.ScalaCheck

abstract class AbstractTest(spec:Specification) extends JUnit4(spec) {

  // Why isn't errors reported correctly by JUnit/Maven??
  override def run(r:TestResult) = {
    Console.println("Running test suite: "+getClass().getName()+"...");

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

class MySpecification extends Specification with ScalaCheck {
  //{setParams(List('verbose->1, 'stacktrace->1))}

  // Rolling our own, to some extent - what's the nice way to get visibility??
  def check(prop:Prop) = {
    val res = Test.check(prop);
    ConsoleReporter(3).onTestResult("dummy("+name+")", res); //TODO: actual Example name.
    res.passed must beTrue
  }
}

