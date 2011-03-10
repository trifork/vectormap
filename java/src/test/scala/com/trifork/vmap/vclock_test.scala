package com.trifork.vmap

import com.trifork.vmap.VClock.Time;

import org.scalacheck._
import org.scalacheck.Prop._
import org.specs._

import junit.framework.TestResult
import org.specs.runner.JUnit4

import java.util.HashMap;

trait VClockGenerators {
  case class VClockMapEntry(val key:String, var value:Time) extends java.util.Map.Entry[String,Time] {
    def getKey() = key
    def getValue() = value
    def setValue(v:Time) = {val old = value; value = v; old}
  }

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
    entriesGen map {e=>
		    val entries : Array[java.util.Map.Entry[String,Time]] =
		      e map {case (p,c,t) => VClockMapEntry(p, new Time(c,t))}
		    new VClock(entries)
		  }
  }

  def genSaneString : Gen[String] =
    Gen.containerOf[List,Char](Gen.choose(Character.MIN_VALUE,Character.MAX_VALUE)) map {_.filter{c=>Character.isDefined(c) && !Character.isLowSurrogate(c) && !Character.isHighSurrogate(c)}.mkString}


  // Type aliases for easy data generation purposes:
   case class SaneString(string:String);
   implicit def unwrapSaneString(x:SaneString) : String = x.string;
   implicit val arbSaneString: Arbitrary[SaneString] = Arbitrary(genSaneString map {x=>SaneString(x)})

  case class PeerName(name:String);
  def genPeerName : Gen[PeerName] = genPeer map {x=>PeerName(x)}
  implicit def arbPeerName : Arbitrary[PeerName] = Arbitrary(genPeerName)

  // Other 'Arbitrary' implicits:
  implicit def arbVClock : Arbitrary[VClock] =  Arbitrary(genVClock)
}

// All JUnit4 tests must end with "Test"
// It must be a class, not an object, otherwise the class name would be mySpecTest$
class VClockTest extends AbstractTest(VClockSpec);

object VClockSpec extends MySpecification with VClockGenerators {
  type TimeMap = HashMap[String,Time];

  def updateAs(vc:VClock, peer:PeerName) : VClock = {
    val map = new TimeMap();
    vc.updateLUB(map);
    VClock.incrementForPeer(map, peer.name);
    new VClock(map)
  }

  val updateLUBIsCommutative = 
    (vc1:VClock, vc2:VClock) => VClock.lub(vc1, vc2) == VClock.lub(vc2, vc1);

  val updateLUBIsAssociative = 
    (vc1:VClock, vc2:VClock, vc3:VClock) => 
      (VClock.lub(vc1, VClock.lub(vc2, vc3)) ==
	VClock.lub(VClock.lub(vc1, vc2), vc3));

  "VClock.updateLUB()" should {
    "be commutative" >> {check(forAll(updateLUBIsCommutative))}
    "be associative" >> {check(forAll(updateLUBIsAssociative))}
  }

  def mirrorCompareResult(x:Int) = x match {
    case VClock.SAME       => VClock.SAME
    case VClock.BEFORE     => VClock.AFTER
    case VClock.AFTER      => VClock.BEFORE
    case VClock.CONCURRENT => VClock.CONCURRENT
  }

  val compareIsCorrectForReflexion =
    (vc:VClock) => VClock.compare(vc,vc) == VClock.SAME;

  val compareIsCorrectForCommutation =
    (vc1:VClock, vc2:VClock) => {
      val cmp1 = VClock.compare(vc1,vc2);
      val cmp2 = VClock.compare(vc2,vc1);
      Prop.collect(cmp1)(cmp1 == mirrorCompareResult(cmp2))
    }

  val compareDetectsConcurrentUpdates =
    (vc:VClock, p1:PeerName, p2:PeerName) => {
      (p1 != p2) ==>
	(VClock.compare(updateAs(vc, p1), updateAs(vc, p2)) == VClock.CONCURRENT)
    }

  val compareHandlesLinearHistory =
    (vc:VClock, editors:List[PeerName]) => {
      val vc_after = editors.foldLeft(vc)(updateAs(_,_))
      VClock.compare(vc, vc_after) ==
	(if (editors != Nil) VClock.BEFORE else VClock.SAME)
    }


  "VClock.compare()" should {
    "treat reflection correctly" >> {check(forAll(compareIsCorrectForReflexion))}
    "treat commutation correctly" >> {check(forAll(compareIsCorrectForCommutation))}

    "handles linear history correctly" >> {
      check(forAll(compareHandlesLinearHistory))
    }

    "detect concurrent edits" >> {
      check(forAll(compareDetectsConcurrentUpdates))
    }
  }
}
