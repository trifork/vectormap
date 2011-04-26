package com.trifork.vmap

import com.trifork.multiversion_common.AbstractTest
import com.trifork.multiversion_common.MySpecification
import com.trifork.multiversion_common.VClockGenerators

import org.scalacheck._
import org.scalacheck.Prop._
import org.specs._

import junit.framework.TestResult
import org.specs.runner.JUnit4

import scala.collection.immutable.Map

import com.trifork.activation.ProtobufDataContentHandler
import com.trifork.multiversion_common.Digest

trait VMapGenerators extends VClockGenerators {
  def nullToNone[T](x:T) = Option.apply(x)

  type Update = (PeerName, String, Option[String]);

  val genKey = Gen.frequency((1, Gen.choose(1,10) map {x=>x.toString}), // Get some key collisions
			     (5, genSaneString))
			     

  val genUpdate : Gen[Update] =
    for (p<-genPeerName;
	 k<-genKey;
	 v<-Gen.oneOf(None, genSaneString map {x=>Some(x)}))
    yield (p,k,v);

  val genUpdates : Gen[List[Update]] =
    Gen.containerOf[List,Update](genUpdate)

  def performUpdate(vmap:VectorMap, update:Update) : VectorMap = {
    update match {
      case (peer, key, None)        => {vmap.setThisPeer(peer.name);
					vmap.remove(key);
					vmap}
      case (peer, key, Some(value)) => {vmap.setThisPeer(peer.name);
					vmap.put(key,value);
					vmap}
    }
  }

  def performUpdate(map:Map[String,String], update:Update) : Map[String,String] = {
    update match {
      case (peer, key, None)        => map - (key)
      case (peer, key, Some(value)) => map + ((key,value))
    }
  }

  def performUpdate(org:(VectorMap,Map[String,String]), update:Update)
  : (VectorMap,Map[String,String]) = {
    val (vmap,map) = org;
    (performUpdate(vmap,update), performUpdate(map,update))
  }

  def performUpdates(org:(VectorMap,Map[String,String]), updates:List[Update]) =
    updates.foldLeft(org)(performUpdate(_,_))

  def performUpdates(org:(VectorMap), updates:List[Update]) =
    updates.foldLeft(org)(performUpdate(_,_))

  def genVMap : Gen[VectorMap] =
    genUpdates map {performUpdates(new VectorMap(), _)}

  implicit def arbVMap : Arbitrary[VectorMap] = Arbitrary(genVMap)

  def genUpdatePairWithDisjointKeyset =
    for (u1 <- genUpdates; u2 <- genUpdates)
    yield {
      val filtered_u2 = u2 filter {case(_,k2,_)=> u1.forall {case (_,k1,_) => k1 != k2}};
      (u1, filtered_u2)
    }
}

// All JUnit4 tests must end with "Test"
// It must be a class, not an object, otherwise the class name would be mySpecTest$
class VectorMapRandomTest extends AbstractTest(VectorMapSpec);

object VectorMapSpec extends MySpecification with VClockGenerators with VMapGenerators {
  // We need to install this content handler before running some of the tests:
  {ProtobufDataContentHandler.install();}

  val containsIsLikeSet =
    (updates : List[(PeerName, String, Option[String])]) =>
      {
	val initial : (VectorMap,Map[String,String]) = (new VectorMap(), Map())
	val (vmap, map) = updates.foldLeft(initial)(performUpdate(_,_));
	val keys = updates.map {_._2}
	(keys map {vmap.containsKey(_)}) == (keys map {map.contains(_)})
      }

  val getIsLikeMap =
    (updates : List[(PeerName, String, Option[String])]) =>
      {
	val initial : (VectorMap,Map[String,String]) = (new VectorMap(), Map())
	val (vmap, map) = updates.foldLeft(initial)(performUpdate(_,_));

	// Verify:
	val keys = updates.map {_._2}
	val strClass = classOf[String]
	val r1 = (keys map {k=>nullToNone(vmap.get(k,strClass))});
	val r2 = (keys map {map.get(_)});
	r1==r2
      }


  "VectorMap.containsKey()" should {
    "behave as set membership, for linear history" >> check(forAll(genUpdates)(containsIsLikeSet))
  };

  "VectorMap.get()" should {
    "behave as map access, for linear history" >> check(forAll(genUpdates)(getIsLikeMap))
  }

  val mergeWithSameYieldsSameSize =
    (vmap: VectorMap) => VectorMap.merge(vmap,vmap).size() == vmap.size()
  val mergeWithSameYieldsNoConflicts =
    (vmap: VectorMap) => VectorMap.merge(vmap,vmap).conflicts().size() == 0

  "VectorMap.merge()" should {
    "for merge with self..." >> check(
      ("result in map of same size" |: forAll(mergeWithSameYieldsSameSize)) &&
      ("result in map with no conflicts" |: forAll(mergeWithSameYieldsNoConflicts))
    )
  }

  val mergeDetectsSimpleConflicts =
    (org: VectorMap, peer1: PeerName, peer2: PeerName, k: SaneString, v1: Option[SaneString], v2: Option[SaneString]) => {
      val key = k.string;
      val edit1 = performUpdate(new VectorMap(org), (peer1, key, v1 map {_.string}))
      val edit2 = performUpdate(new VectorMap(org), (peer2, key, v2 map {_.string}))
      val mrg = VectorMap.merge(edit1, edit2);
      // If peer1==peer2 there may or may not be a conflict.
      val expectedConflicts = if (peer1==peer2) -1 else if (v1 == v2) 0 else 1;
      Prop.collect(expectedConflicts)(mrg.conflicts.size() == expectedConflicts ||
				    expectedConflicts == -1)
    }
      
  "VectorMap.merge()" should {
    "detect simple conflicts correctly" >> check(forAll(mergeDetectsSimpleConflicts))
  }

  def performSimpleNestedUpdate(mainKey:String, org:(VectorMap,Map[String,String]), update:Update)
  : (VectorMap,Map[String,String]) = {
    val (vmap,map) = org;
    vmap.put2(mainKey,
	      performUpdate(vmap.get(mainKey,classOf[VectorMap]), update))
    (vmap, performUpdate(map,update))
  }

  val nestedGetIsLikeMap =
    (mainKey:String, updates : List[Update]) =>
      {
	val initial : (VectorMap,Map[String,String]) = (new VectorMap(), Map());
	initial._1.setThisPeer("main_editor");
	initial._1.put2(mainKey, new VectorMap())
	val (vmap, map) = updates.foldLeft(initial)(performSimpleNestedUpdate(mainKey,_,_));

	// Verify:
	val keys = updates.map {_._2}

	val submap = vmap.get(mainKey, classOf[VectorMap])
	val strClass = classOf[String]
	val r1 = (keys map {k=>nullToNone(submap.get(k,strClass))});
	val r2 = (keys map {map.get(_)});
	r1==r2
      }

  "VectorMap" should {
    "support simple nested maps" >> check(forAll(for (u<-genUpdates;
						      k<-genKey) yield (k,u))
					  (x=>nestedGetIsLikeMap(x._1,x._2)))
  }

  val hashIsMergeOrderAgnostic =
    (vmap1:VectorMap, vmap2:VectorMap, vmap3:VectorMap) => {
      val mrg1 = VectorMap.merge(vmap1, VectorMap.merge(vmap2, vmap3))
      val mrg2 = VectorMap.merge(VectorMap.merge(vmap1, vmap2), vmap3)
      Digest.digestOf(mrg1) sameElements Digest.digestOf(mrg2)
    }

  "VectorMap hash" should {
    "be indifferent to merge order" >> check(
      forAll(hashIsMergeOrderAgnostic))
  }

  /*
  val hashIsUpdateOrderAgnostic = (updatePair: (List[Update], List[Update])) =>
    updatePair match {case (u1,u2) => {
      val m1 = performUpdates(performUpdates(new VectorMap(), u1), u2)
      val m2 = performUpdates(performUpdates(new VectorMap(), u2), u1)
      val r = Digest.digestOf(m1) sameElements Digest.digestOf(m2)
      r
    }
   }

  "VectorMap hash" should {
    "be indifferent to update order" >> check(
      forAll(genUpdatePairWithDisjointKeyset)(hashIsUpdateOrderAgnostic))
  }
  */

}
