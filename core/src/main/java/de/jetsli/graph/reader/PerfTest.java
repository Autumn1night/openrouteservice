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
package de.jetsli.graph.reader;

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeSimple;
import de.jetsli.graph.util.CoordFloat;
import de.jetsli.graph.util.Helper;
import java.util.Date;

/**
 * Memory usage calculation according to
 *
 * http://www.ibm.com/developerworks/opensource/library/j-codetoheap/index.html?ca=drs
 * http://kohlerm.blogspot.de/2009/02/how-to-really-measure-memory-usage-of.html
 *
 * TODO respect padding:
 *
 * http://www.codeinstructions.com/2008/12/java-objects-memory-structure.html
 *
 * @author Peter Karich
 */
public class PerfTest {

    public static void main(String[] args) throws Exception {
        if (args.length < 1)
            throw new IllegalArgumentException("Osm file missing");
        
        String osmFile = args[0];
        Graph g = OSMReaderTrials.defaultRead(osmFile, "/tmp/mmap-graph");
        new PerfTest(g).start();
    }
    Graph g;

    public PerfTest(Graph graph) {
        g = graph;
    }
    int latMin = 497354, latMax = 501594;
    int lonMin = 91924, lonMax = 105784;
    // Try to use MemoryMeter https://github.com/jbellis/jamm

    public void start() {
        System.out.println("locations:" + g.getLocations());
        // for fill: 1.82sec/iter

        // for query: 16 entriesPerNode seems to be fast and not such a memory waste
        // => approx 46 bytes/entry + sizeOf(Integer)
        // current results for 64 bits:
        // 10km search => 0.048s, ~  70k nodes per search retrieved
        // 20km search => 0.185s, ~ 300k
        // 40km search => 0.620s, ~ 850k

        // increase speed about
        //  => ~2%    when using int   instead double    in BBox (multiplied with 1e+7 before) => but too complicated
        //  => ~2%    when using float instead of double in CoordTrig => but bad in other cases. if double and float implementation => too complicated
        //  => ~10%   when using int   instead double    in SpatialKeyAlgo for de/encode => but problems with precision if allBits >= 46
        //  => ~30%   when using int   instead long      in SpatialKeyAlgo for de/encode => but problems with precision if allBits >= 46
        //  => ~1000% when using only 32 bits for encoding instead >=48
        int maxDist = 50;
        int maxEntriesPerL = 20;
        int minBits = 64;
        System.out.println(new Date() + "# maxDist:" + maxDist + ", maxEntries/leaf:" + maxEntriesPerL + ", minBits:" + minBits);

        measureFill(minBits, maxEntriesPerL);
//        measureSearch(minBits, maxDist, maxEntriesPerL);
    }

    private void measureFill(int minBits, int maxEPerL) {
        for (int bits = minBits; bits <= 64; bits += 16) {
            for (int entriesPerLeaf = 16; entriesPerLeaf < maxEPerL; entriesPerLeaf *= 2) {
                final QuadTree<Integer> quadTree = new QuadTreeSimple<Integer>(entriesPerLeaf, bits);
                fillQuadTree(quadTree, g);
                System.gc();
                System.gc();
                float mem = (float) quadTree.getMemoryUsageInBytes(1) / Helper.MB;
                quadTree.clear();
                System.out.println(new Date() + "# entries/leaf:" + entriesPerLeaf + ", bits:" + bits + ", mem:" + mem);
                final int epl = entriesPerLeaf;
                final int b = bits;
                new MiniTest("fill") {

                    @Override public long doCalc(int run) {
                        QuadTree<Integer> quadTree = new QuadTreeSimple<Integer>(epl, b);
                        fillQuadTree(quadTree, g);
                        return quadTree.size();
                    }
                }.setMax(40).start();
            }
        }
    }

    private void measureSearch(int minBits, int maxDist, int maxEPerL) {
        for (int bits = minBits; bits <= 64; bits += 16) {
            for (int distance = 10; distance < maxDist; distance *= 2) {
                for (int entriesPerLeaf = 16; entriesPerLeaf < maxEPerL; entriesPerLeaf *= 2) {
                    final QuadTree<Integer> quadTree = new QuadTreeSimple<Integer>(entriesPerLeaf, bits);
                    fillQuadTree(quadTree, g);
                    System.gc();
                    System.gc();
                    float mem = (float) quadTree.getMemoryUsageInBytes(1) / Helper.MB;
                    long emptyEntries = quadTree.getEmptyEntries(true);
                    long emptyAllEntries = quadTree.getEmptyEntries(false);
                    final int tmp = distance;
                    new MiniTest("neighbour search e/leaf:" + entriesPerLeaf + ", bits:" + bits
                            + ", dist:" + distance + ", mem:" + mem + ", empty entries:" + emptyEntries
                            + ", empty all entries:" + emptyAllEntries) {

                        @Override public long doCalc(int run) {
                            float lat = (random.nextInt(latMax - latMin) + latMin) / 10000.0f;
                            float lon = (random.nextInt(lonMax - lonMin) + lonMin) / 10000.0f;
                            return quadTree.getNodes(lat, lon, tmp).size();
                        }
                    }.setMax(100).setShowProgress(true).setSeed(0).start();
                }
            }
        }
    }

    public static void fillQuadTree(QuadTree quadTree, Graph graph) {
        // TODO LATER persist quad tree to make things faster and store osm ids instead nothing
        Integer empty = new Integer(1);
        int locs = graph.getLocations();
        for (int i = 0; i < locs; i++) {
            float lat = graph.getLatitude(i);
            float lon = graph.getLongitude(i);
            quadTree.add(lat, lon, new CoordFloat(lat, lon));
        }
    }
}
