package com.trifork.vmap

import org.scalacheck._
import org.scalacheck.Prop._
import org.specs._

import junit.framework.TestResult
import org.specs.runner.JUnit4

import java.util.HashMap;

trait Generators {
  def genPeer = {
    val i=Gen.choose(0,100)
    Gen.oneOf("jens", "peter", "hans", "thomas"+i)
  }

  def genCount = Gen.oneOf(Gen.choose(0,5), Gen.choose(0,100));

  val now : Int = (System.currentTimeMillis()/1000).asInstanceOf[Int]
  def genTimestamp = for (r<-Gen.choose(-50,50)) yield now+r

  type VClockEntry = (String, Int, Int)
  val genVClockEntry : Gen[VClockEntry] =
    for (p<-genPeer; c<-genCount; t<-genTimestamp) yield (p,c,t)

  def genVClock : Gen[VClock] = {
    val entriesGen : Gen[Array[VClockEntry]] = Gen.containerOf[Array, VClockEntry](genVClockEntry)
    entriesGen map {e=>new VClock(e map {_._1}, e map {_._2}, e map {_._3})}
  }

  implicit def arbVClock : Arbitrary[VClock] =  Arbitrary(genVClock)
}

// All JUnit4 tests must end with "Test"
// It must be a class, not an object, otherwise the class name would be mySpecTest$
class VClockTest extends JUnit4(VClockSpec) {

  // Why isn't errors reported correctly by JUnit/Maven??
  override def run(r:TestResult) = {
    Console.println("DB| running with "+r+"...");
    val res = super.run(r)
    Console.println("DB| ran with "+r+": "+r.failureCount()+"/"+r.errorCount()+"/"+r.runCount());
    for (tf <- new RichEnumeration(r.failures)) Console.println("*** Test Failure: *** "+tf);
    for (te <- new RichEnumeration(r.errors))   Console.println("*** Test Error: *** "+te);
    res
  }

  class RichEnumeration[T](enumeration:java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext:Boolean =  enumeration.hasMoreElements()
    def next:T = enumeration.nextElement()
  }
}


object VClockSpec extends Specification with Generators with ScalaCheck {
  val updateMaxIsCommutative = 
    (vc1:VClock, vc2:VClock) =>
      (vc2.updateMax(vc1.updateMax(new HashMap())) ==
	vc1.updateMax(vc2.updateMax(new HashMap())));

  "VClock.updateMax()" should {
    "be commutative" in {
      (forAll(updateMaxIsCommutative) must pass)
     
    }
  }
}
