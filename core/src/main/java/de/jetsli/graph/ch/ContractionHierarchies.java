/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.ch;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyOpenBitSet;
import de.jetsli.graph.dijkstra.DijkstraBidirection;
import de.jetsli.graph.dijkstra.DijkstraPath;
import de.jetsli.graph.storage.DistEntry;
import de.jetsli.graph.storage.GraphWrapper;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.MyIteratorable;
import java.util.Date;
import java.util.PriorityQueue;

/**
 * This class implements the transformation of a Graph into a 'contracted' one.
 *
 * You'll need to read some paper to understand the basics.
 *
 * go to http://algo2.iti.kit.edu/1087.php => (THE paper) http://algo2.iti.kit.edu/1536.php =>
 * (slides) http://algo2.iti.kit.edu/1537.php
 *
 * diploma thesis: http://algo2.iti.kit.edu/documents/routeplanning/geisberger_dipl.pdf 17 german
 * courses: http://i11www.iti.uni-karlsruhe.de/teaching/sommer2011/routenplanung/index google tech
 * talk: http://www.youtube.com/watch?v=-0ErpE8tQbw
 *
 * some slides: http://www14.informatik.tu-muenchen.de/lehre/2010SS/sarntal/12_protsenko_slides.pdf
 *
 * @author Peter Karich, info@jetsli.de
 */
public class ContractionHierarchies {

    /**
     * TODO specified Graph != contracted Graph as BiDijkstra should use (or even prefer?) shortcuts
     */
    public Graph contract(Graph g) {
        PriorityQueue<DistEntry> heap = new PriorityQueue<DistEntry>();
        int locations = g.getLocations();

        // TODO calculate edge difference => yet another dikstra necessary!?
        for (int i = 0; i < locations; i++) {
            heap.add(new DistEntry(i, MyIteratorable.count(g.getOutgoing(i))));
        }
        DistEntry curr;
        MyBitSet alreadyContracted = new MyOpenBitSet(locations);
        int counter = 0;
        int newEdges = 0;
        GraphWrapper gWrapper = new GraphWrapper(g);
        gWrapper.setIgnoreNodes(alreadyContracted);
        while ((curr = heap.poll()) != null) {
            if(counter++ % 1000 == 0) {
                System.out.println(new Date() + ", heap " + heap.size() + ", new edges:" + newEdges);
                newEdges = 0;
            }
            // inDE = v
            // curr = u
            //outDE = w            
            for (DistEntry inDE : gWrapper.getIncoming(curr.node)) {
                // this makes sure that 
                //  1. ORDER(inDE.node) > ORDER(curr.node) is true, as already contracted nodes are less important
                //  2. we ignore contracted nodes
                if (alreadyContracted.contains(inDE.node))
                    continue;

                float maxOutDist = 0;
                for (DistEntry outDE : gWrapper.getOutgoing(curr.node)) {
                    if (inDE.node == outDE.node)
                        continue;
                    
                    if (outDE.distance > maxOutDist)
                        maxOutDist = outDE.distance;
                }

                for (DistEntry outDE : gWrapper.getOutgoing(curr.node)) {
                    if (inDE.node == outDE.node)
                        continue;

                    // calc shortest path from inDE.loc to outDE.loc without curr.loc
                    final float maxDist = inDE.distance + maxOutDist;
                    // TODO ignore alreadyContracted
                    DijkstraBidirection db = new DijkstraBidirection(gWrapper) {

                        @Override
                        public boolean checkFinishCondition() {
                            float min = Math.min(shortest.distance, maxDist);
                            if (currFrom == null)
                                return currTo.distance >= min;
                            else if (currTo == null)
                                return currFrom.distance >= min;
                            return currFrom.distance + currTo.distance >= min;
                        }
                    };
                    db.addSkipNode(curr.node);
                    DijkstraPath witnessPath = db.calcShortestPath(inDE.node, outDE.node);
                    float dist = inDE.distance + outDE.distance;
                    // add the shortcut <in,curr,out> only if the found witness path is longer or not existent
                    if (witnessPath == null || witnessPath.distance() > 0 && witnessPath.distance() > dist) {
                        alreadyContracted.add(curr.node);
                        g.edge(inDE.node, outDE.node, dist, false);
                        newEdges++;
                    }
                }
            }
        }
        return g;
    }
}
