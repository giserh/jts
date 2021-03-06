/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.locationtech.jtstest.function;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.LinearComponentExtracter;
import org.locationtech.jts.noding.BasicSegmentString;
import org.locationtech.jts.noding.FastNodingValidator;
import org.locationtech.jts.noding.NodingIntersectionFinder;
import org.locationtech.jts.noding.IntersectionAdder;
import org.locationtech.jts.noding.MCIndexNoder;
import org.locationtech.jts.noding.Noder;
import org.locationtech.jts.noding.ScaledNoder;
import org.locationtech.jts.noding.SegmentStringUtil;
import org.locationtech.jts.noding.snapround.GeometryNoder;
import org.locationtech.jts.noding.snapround.MCIndexSnapRounder;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jtstest.geomfunction.Metadata;


public class NodingFunctions 
{

  public static boolean isNodingValid(Geometry geom) {
    FastNodingValidator nv = new FastNodingValidator(
        SegmentStringUtil.extractNodedSegmentStrings(geom));
    return nv.isValid();
  }

  public static boolean isSegmentNodingValid(Geometry geom) {
    NodingIntersectionFinder intFinder = NodingIntersectionFinder
        .createInteriorIntersectionCounter( new RobustLineIntersector() );
    processNodes(geom, intFinder);
    return 0 == intFinder.count();
  }

  public static Geometry findOneNode(Geometry geom) {
    FastNodingValidator nv = new FastNodingValidator(
        SegmentStringUtil.extractNodedSegmentStrings(geom));
    nv.isValid();
    List intPts = nv.getIntersections();
    if (intPts.size() == 0) return null;
    return FunctionsUtil.getFactoryOrDefault(geom).createPoint((Coordinate) intPts.get(0));
  }
  
  @Metadata(description="Finds intersection points between linestrings")
  public static Geometry findNodes(Geometry geom)
  {
    List<Coordinate> intPtsList = FastNodingValidator.computeIntersections( 
        SegmentStringUtil.extractNodedSegmentStrings(geom) );
    return FunctionsUtil.getFactoryOrDefault(null)
        .createMultiPointFromCoords( dedup(intPtsList) );
  }

  private static Coordinate[] dedup(List<Coordinate> ptsList) {
    List<Coordinate> ptsNoDup = new ArrayList<>(
        new HashSet<>(ptsList));
    Coordinate[] pts = CoordinateArrays.toCoordinateArray(ptsNoDup);
    return pts;
  }
  
  @Metadata(description="Finds interior intersection points between segments")
  public static Geometry findInteriorNodes(Geometry geom)
  {
    NodingIntersectionFinder intFinder = NodingIntersectionFinder
        .createInteriorIntersectionsFinder( new RobustLineIntersector() );
    processNodes(geom, intFinder);
    List intPts = intFinder.getIntersections();
    return FunctionsUtil.getFactoryOrDefault(null)
        .createMultiPointFromCoords( dedup(intPts) );
  }
 
  public static int intersectionCount(Geometry geom)
  {
    NodingIntersectionFinder intCounter = NodingIntersectionFinder
        .createIntersectionCounter( new RobustLineIntersector() );
    processNodes(geom, intCounter);
    return intCounter.count();
  }

  public static int interiorIntersectionCount(Geometry geom)
  {
    NodingIntersectionFinder intCounter = NodingIntersectionFinder
        .createInteriorIntersectionCounter( new RobustLineIntersector() );
    processNodes(geom, intCounter);
    return intCounter.count();
  }

  private static void processNodes(Geometry geom, NodingIntersectionFinder intFinder) {
    Noder noder = new MCIndexNoder( intFinder );
    noder.computeNodes( SegmentStringUtil.extractNodedSegmentStrings(geom) );
  }

  public static Geometry MCIndexNodingWithPrecision(Geometry geom, double scaleFactor)
  {
    PrecisionModel fixedPM = new PrecisionModel(scaleFactor);
    
    LineIntersector li = new RobustLineIntersector();
    li.setPrecisionModel(fixedPM);

    Noder noder = new MCIndexNoder(new IntersectionAdder(li));
    noder.computeNodes( SegmentStringUtil.extractNodedSegmentStrings(geom) );
    return SegmentStringUtil.toGeometry( noder.getNodedSubstrings(), FunctionsUtil.getFactoryOrDefault(geom) );
  }

  public static Geometry MCIndexNoding(Geometry geom)
  {
    Noder noder = new MCIndexNoder(new IntersectionAdder(new RobustLineIntersector()));
    noder.computeNodes( SegmentStringUtil.extractNodedSegmentStrings(geom) );
    return SegmentStringUtil.toGeometry(noder.getNodedSubstrings(), FunctionsUtil.getFactoryOrDefault(geom));
  }
  
  /**
   * Reduces precision pointwise, then snap-rounds.
   * Note that output set may not contain non-unique linework
   * (and thus cannot be used as input to Polygonizer directly).
   * UnaryUnion is one way to make the linework unique.
   * 
   * @param geom a geometry containing linework to node
   * @param scaleFactor the precision model scale factor to use
   * @return the noded, snap-rounded linework
   */
  public static Geometry snapRoundWithPrecision(
      Geometry geom, double scaleFactor) {
    PrecisionModel pm = new PrecisionModel(scaleFactor);

    Geometry roundedGeom = GeometryPrecisionReducer.reducePointwise(geom, pm);

    List geomList = new ArrayList();
    geomList.add(roundedGeom);

    GeometryNoder noder = new GeometryNoder(pm);
    List lines = noder.node(geomList);

    return FunctionsUtil.getFactoryOrDefault(geom).buildGeometry(lines);
  }
  
  /**
   * Runs a ScaledNoder on input.
   * Input vertices should be rounded to precision model.
   * 
   * @param geom
   * @param scaleFactor
   * @return the noded geometry
   */
  public static Geometry scaledNoding(Geometry geom, double scaleFactor)
  {
    List segs = createSegmentStrings(geom);
    PrecisionModel fixedPM = new PrecisionModel(scaleFactor);
    Noder noder = new ScaledNoder(new MCIndexSnapRounder(new PrecisionModel(1.0)),
        fixedPM.getScale());
    noder.computeNodes(segs);
    Collection nodedSegStrings = noder.getNodedSubstrings();
    return SegmentStringUtil.toGeometry(nodedSegStrings, FunctionsUtil.getFactoryOrDefault(geom));
  }

  private static List createSegmentStrings(Geometry geom)
  {
    List segs = new ArrayList();
    List lines = LinearComponentExtracter.getLines(geom);
    for (Iterator i = lines.iterator(); i.hasNext(); ) {
      LineString line = (LineString) i.next();
      segs.add(new BasicSegmentString(line.getCoordinates(), null));
    }
    return segs;
  }
  
  

}
