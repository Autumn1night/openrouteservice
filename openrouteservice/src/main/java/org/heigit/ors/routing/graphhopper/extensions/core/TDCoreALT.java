/*  This file is part of Openrouteservice.
 *
 *  Openrouteservice is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1
 *  of the License, or (at your option) any later version.

 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.

 *  You should have received a copy of the GNU Lesser General Public License along with this library;
 *  if not, see <https://www.gnu.org/licenses/>.
 */
package org.heigit.ors.routing.graphhopper.extensions.core;

import com.graphhopper.routing.EdgeBasedTDCoreBidirPathExtractor;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.routing.SPTEntry;
import com.graphhopper.storage.RoutingCHEdgeIteratorState;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.util.Parameters;

public class TDCoreALT extends CoreALT {
    private boolean reverse;

    public TDCoreALT(RoutingCHGraph graph, Weighting weighting, boolean reverse) {
        super(graph, weighting);
        this.reverse = reverse;
    }

    /*TODO: seems obsolete as AccessEdgeFilter is not used anymore in TD algos (was neccessary before e.g. in TDDijkstra)
    @Override
    protected void initPhase2() {
        inEdgeExplorer = graph.createEdgeExplorer(AccessEdgeFilter.inEdges(flagEncoder));
        outEdgeExplorer = graph.createEdgeExplorer(AccessEdgeFilter.outEdges(flagEncoder));
    }
    */
    @Override
    protected Path extractPath() {
        if (finished())
            return EdgeBasedTDCoreBidirPathExtractor.extractPath(chGraph, weighting, bestFwdEntry, bestBwdEntry, bestWeight);

        return createEmptyPath();
    }

    @Override
    public boolean fillEdgesFromCore() {
        if (reverse)
            return true;

        return super.fillEdgesFromCore();
    }

    @Override
    public boolean fillEdgesToCore() {
        if (!reverse)
            return true;

        return super.fillEdgesToCore();
    }

    @Override
    double calcEdgeWeight(RoutingCHEdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        return calcWeight(iter, reverse, currEdge.originalEdge, currEdge.time) + currEdge.getWeightOfVisitedPath();
    }

    @Override
    long calcEdgeTime(RoutingCHEdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        return currEdge.time + (reverse ? -1 : 1) * calcTime(iter, reverse, currEdge.time);
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.TD_ASTAR;
    }
}
