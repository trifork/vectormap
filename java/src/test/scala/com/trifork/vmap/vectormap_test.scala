package com.trifork.vmap

import org.scalacheck._
import org.scalacheck.Prop._
import org.specs._

import junit.framework.TestResult
import org.specs.runner.JUnit4

import scala.collection.immutable.Map

trait VMapGenerators extends VClockGenerators {
  type Update = (PeerName, String, Option[String]);

  val genUpdate : Gen[Update] =
    for (p<-genPeerName;
	 k<-genSaneString;
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

  def genVMap : Gen[VectorMap] =
    genUpdates map {_.foldLeft(new VectorMap())(performUpdate(_,_))};

  implicit def arbVMap : Arbitrary[VectorMap] = Arbitrary(genVMap)
    
}

// All JUnit4 tests must end with "Test"
// It must be a class, not an object, otherwise the class name would be mySpecTest$
class VectorMapRandomTest extends AbstractTest(VectorMapSpec);

object VectorMapSpec extends MySpecification with VClockGenerators with VMapGenerators {

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
	val keys = updates.map {_._2}
	def nullToNone[T](x:T) = Option.apply(x)
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

}
