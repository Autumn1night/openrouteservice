/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.util.PriorityQueue;

/**
 * Implements a single source shortest path algorithm
 * http://en.wikipedia.org/wiki/Dijkstra's_algorithm
 * <p/>
 * @author Peter Karich
 */
public class Dijkstra extends AbstractRoutingAlgorithm
{
    private TIntObjectMap<EdgeEntry> fromMap;
    private PriorityQueue<EdgeEntry> fromHeap;
    private int visitedNodes;
    private int to1 = -1;
    private int to2 = -1;
    private EdgeEntry currEdge;

    public Dijkstra( Graph graph, FlagEncoder encoder, WeightCalculation type )
    {
        super(graph, encoder, type);
        initCollections(1000);
    }

    protected void initCollections( int size )
    {
        fromHeap = new PriorityQueue<EdgeEntry>(size);
        fromMap = new TIntObjectHashMap<EdgeEntry>(size);
    }

    @Override
    public Path calcPath( LocationIDResult fromRes, LocationIDResult toRes )
    {
        checkAlreadyRun();
        EdgeIteratorState from = fromRes.getClosestEdge();
        EdgeIteratorState to = toRes.getClosestEdge();
        if (flagEncoder.isForward(from.getFlags()))
            fromMap.put(from.getAdjNode(), createEdgeEntry(from.getAdjNode(), fromRes.getAdjDistance()));

        if (flagEncoder.isBackward(from.getFlags()))
            fromMap.put(from.getBaseNode(), createEdgeEntry(from.getBaseNode(), fromRes.getBasedDistance()));

        if (flagEncoder.isForward(to.getFlags()))
            to1 = to.getBaseNode();

        if (flagEncoder.isBackward(to.getFlags()))
            to2 = to.getAdjNode();

        if (fromMap.isEmpty() || to1 < 0 && to2 < 0)
            throw new IllegalStateException("Either 'from'-edge or 'to'-edge is inaccessible. From:" + fromMap + ", to1:" + to1 + ", to2:" + to2);

        currEdge = fromMap.valueCollection().iterator().next();
        return runAlgo();
    }

    @Override
    public Path calcPath( int from, int to )
    {
        checkAlreadyRun();
        to1 = to;
        currEdge = createEdgeEntry(from, 0);
        fromMap.put(from, currEdge);
        return runAlgo();
    }

    private Path runAlgo()
    {
        EdgeExplorer explorer = outEdgeExplorer;
        while (true)
        {
            visitedNodes++;
            if (finished())
                break;

            int neighborNode = currEdge.endNode;
            explorer.setBaseNode(neighborNode);
            while (explorer.next())
            {
                if (!accept(explorer))
                    continue;

                int tmpNode = explorer.getAdjNode();
                double tmpWeight = weightCalc.getWeight(explorer) + currEdge.weight;

                EdgeEntry nEdge = fromMap.get(tmpNode);
                if (nEdge == null)
                {
                    nEdge = new EdgeEntry(explorer.getEdge(), tmpNode, tmpWeight);
                    nEdge.parent = currEdge;
                    fromMap.put(tmpNode, nEdge);
                    fromHeap.add(nEdge);
                } else if (nEdge.weight > tmpWeight)
                {
                    fromHeap.remove(nEdge);
                    nEdge.edge = explorer.getEdge();
                    nEdge.weight = tmpWeight;
                    nEdge.parent = currEdge;
                    fromHeap.add(nEdge);
                }

                updateShortest(nEdge, neighborNode);
            }

            if (fromHeap.isEmpty())
                return createEmptyPath();

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }
        return extractPath();
    }

    @Override
    protected boolean finished()
    {
        return currEdge.endNode == to1 || currEdge.endNode == to2;
    }

    @Override
    protected Path extractPath()
    {
        if (currEdge == null || !finished())
            return createEmptyPath();
        return new Path(graph, flagEncoder).setEdgeEntry(currEdge).extract();
    }

    @Override
    public String getName()
    {
        return "dijkstra";
    }

    @Override
    public int getVisitedNodes()
    {
        return visitedNodes;
    }
}
