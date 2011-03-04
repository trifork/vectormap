package com.trifork.vmap

import org.scalacheck._
import org.scalacheck.Prop._
import org.specs._

import junit.framework.TestResult
import org.specs.runner.JUnit4

import java.util.HashMap;

trait Generators {
  def genPeer = {
    Gen.sized (s => Gen.frequency((  1, Gen.value("jens")),
				  (  1, Gen.value("peter")),
				  (  1, Gen.value("hans")),
				  (  1, Gen.alphaStr),
				  (5*s, Gen.choose(1,100*s) map {"thomas"+_})))
  }

  def genCount = Gen.oneOf(Gen.choose(0,5), Gen.choose(0,100));

  val now : Int = (System.currentTimeMillis()/1000).asInstanceOf[Int]
  def genTimestamp = for (r<-Gen.choose(-20,20)) yield now+r

  type VClockEntry = (String, Int, Int)
  val genVClockEntry : Gen[VClockEntry] =
    for (p<-genPeer; c<-genCount; t<-genTimestamp) yield (p,c,t)

  def genVClock : Gen[VClock] = {
    val namesAreDistinct = (entries:Array[VClockEntry]) => {
      val names = entries map {_._1}
      names.size == (Set() ++ names).size
    }
    val entriesGen : Gen[Array[VClockEntry]] = Gen.containerOf[Array, VClockEntry](genVClockEntry) suchThat namesAreDistinct
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
  def maxOf2(a:VClock, b:VClock) =
    b.updateMax(a.updateMax(new HashMap()));

  val updateMaxIsCommutative = 
    (vc1:VClock, vc2:VClock) => maxOf2(vc1, vc2) == maxOf2(vc2, vc1);

  val updateMaxIsAssociative = 
    (vc1:VClock, vc2:VClock, vc3:VClock) => 
      (maxOf2(vc1, new VClock(maxOf2(vc2, vc3))) ==
	maxOf2(new VClock(maxOf2(vc1, vc2)), vc3));
 

  "VClock.updateMax()" should {
    "be commutative" >> {(forAll(updateMaxIsCommutative) must pass)}
    "be associative" >> {(forAll(updateMaxIsAssociative) must pass)}
  }
}
