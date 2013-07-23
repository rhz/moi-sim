/*
 * Authors: 
 * - Dominik Bucher, ETH Zurich, Github: dominikbucher
 */

package ed.mois.core.storm

import scala.collection.immutable.TreeMap
import scala.util.control.Breaks._

import ed.mois.core.storm._

case class StormChange(origin: Int, t: Double, dt: Double, chg: Map[Int, Any]) {
	def tEnd = t + dt
}


trait ChangeHelper {
	def intersect(init: Map[Int, StormField[_]], chgs: List[StormChange]): Option[Tuple3[Double, Double, List[StormChange]]] = {
		// Slice up the time frame into interesting parts
		val sliced = slices(chgs)
		// Create an empty var to store violators in any time slice
		var violators: Option[List[StormChange]] = None
		// Rush through slices and merge all the changes
		for (s <- sliced) {
			violators = tryMerge(init, s._2, s._1._1, s._1._2)
			// If there is an error in time slice s, exit intersect function and return
			// a tuple containing (errorSliceTStart, errorSliceTEnd, InvolvedChanges)
			if (violators.isDefined) {
				return Some(s._1._1, s._1._2, violators.get)
			}
		}
		// If the whole intersection went ok, return None, indicating there are no errors
		return None
	}

	/**
	 * Tries to merge a set of changes into the state denoted as init. Returns a list of violators
	 * if the merge fails. 
	 */
	def tryMerge(init: Map[Int, StormField[_]], chgs: List[StormChange], t: Double, tEnd: Double): Option[List[StormChange]] = {
		// For each field there is a number of involved changes, denoted by this map (id) -> InvolvedChanges
    	val involved = collection.mutable.Map.empty[Int, List[StormChange]]
    	// All the ids of fields that were violated in this merge
    	var violatedFields = List.empty[Int]
    	// For every change and every field, merge this into the init vector
    	for { chg <- chgs
    		fieldChg <- chg.chg } {
    			// The merge function returns false if the merge failed -> add the id to violated fields in that case
    			if (!init(fieldChg._1).merge(fieldChg._2, tEnd - t)) violatedFields = fieldChg._1 :: violatedFields
    			// Add the change to the involved list
				addToListMap(involved, fieldChg._1, chg)
    		}

    	// If there were any violations, gather up all the involved changes and return them
    	if (violatedFields.length > 0) Some(violatedFields.map(vf => involved(vf)).flatten.distinct)
    	else None
	}

	/** 
	 * Transforms a list of changes to a map which contains "interesting" time
	 * slices (tStart, tEnd) as keys and all the changes that are involved in an interesting 
	 * slice at that given time UNTIL the next time point. 
	 */
	def slices(chgs: List[StormChange]): TreeMap[Tuple2[Double, Double], List[StormChange]] = {
		// Create the return value map (consists of (tStart, tEnd) -> InterestingChanges)
    	val m = collection.mutable.Map.empty[Tuple2[Double, Double], List[StormChange]]

    	// Collect all interesting time points (mainly start and end times of changes)
		val timePoints = chgs.map(chg => List(chg.t, chg.tEnd)).flatten.distinct.sorted.sliding(2).toList
		chgs.foreach { chg => 
			// Get all time points that are interesting for this change
			val tps = timePoints.filter(tp => chg.t <= tp(0) && tp(0) < chg.tEnd)
			// And add the change to all tuples of interesting time points
			tps.foreach(tp => addToListMap(m, (tp(0), tp(1)), chg))
		}

		TreeMap(m.toArray:_*)
	}

	/**
	 * Adds an element to any map with a list as value (used because lists need to be
	 * created in the first addition).
	 */
	def addToListMap[A, B](m: collection.mutable.Map[A, List[B]], a: A, b: B) {
		if (m.contains(a)) {
			m(a) = b :: m(a)
		} else {
			m(a) = List(b)
		}
	}
}