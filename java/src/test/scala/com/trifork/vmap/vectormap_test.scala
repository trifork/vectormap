package com.trifork.vmap

import org.scalacheck._
import org.scalacheck.Prop._
import org.specs._

import junit.framework.TestResult
import org.specs.runner.JUnit4

import scala.collection.immutable.Map

trait VMapGenerators {
}

// All JUnit4 tests must end with "Test"
// It must be a class, not an object, otherwise the class name would be mySpecTest$
class VectorMapRandomTest extends AbstractTest(VectorMapSpec);

object VectorMapSpec extends MySpecification with VClockGenerators {
  def performUpdate(org:(VectorMap,Map[String,String]),
		    update:(PeerName, String, Option[String]))
  : (VectorMap,Map[String,String]) = {
    val (vmap,map) = org;
    update match {
      case (peer, key, None)        => {vmap.setThisPeer(peer.name);
					vmap.remove(key);
					(vmap, map - (key))}
      case (peer, key, Some(value)) => {vmap.setThisPeer(peer.name);
					vmap.put(key,value);
					(vmap, map + ((key,value)))}
    }
  }

  val containsIsLikeSet =
    (updates : List[(PeerName, String, Option[String])]) =>
      {
	val initial : (VectorMap,Map[String,String]) = (new VectorMap(), Map())
	val (vmap, map) = updates.foldLeft(initial)(performUpdate(_,_));
	val keys :List[String] = updates.map {_._2}
	(keys map {vmap.containsKey(_)}) == (keys map {map.contains(_)})
      }

  val getIsLikeMap =
    (updates : List[(PeerName, String, Option[String])]) =>
      {
	val initial : (VectorMap,Map[String,String]) = (new VectorMap(), Map())
	val (vmap, map) = updates.foldLeft(initial)(performUpdate(_,_));
	val keys :List[String] = updates.map {_._2}
// 	val getFun = (key:String) => vmap.get(key) match {
// 	  case null => None
// 	  case x    => Some(x)
// 	}
	def nullToNone[T](x:T) = Option.apply(x)
	val strClass = classOf[String]
	(keys map {k=>nullToNone(vmap.get(k,strClass))}) == (keys map {map.get(_)})
      }


  "VectorMap.containsKey()" should {
    "behave as set membership, for linear history" >> check(forAll(containsIsLikeSet))
  };

  "VectorMap.get()" should {
    "behave as map access, for linear history" >> check(forAll(getIsLikeMap))
  }
}
