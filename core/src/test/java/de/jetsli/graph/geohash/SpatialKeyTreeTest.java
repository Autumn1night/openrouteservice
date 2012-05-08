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
package de.jetsli.graph.geohash;

import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeTester;
import java.util.Random;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class SpatialKeyTreeTest {
//extends QuadTreeTester {
//
//    @Override
//    protected QuadTree<Integer> createQuadTree(long items) {
//        try {
//            return new SpatialKeyTree().init(items);
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        }
//    }

    @Test
    public void testSize2() throws Exception {
        SpatialKeyTree key = new SpatialKeyTree(0, 7).init(20);
        assertTrue(key.getEntriesPerBucket() + "", key.getEntriesPerBucket() >= 7);
        assertTrue(key.getMaxBuckets() + " " + key.getEntriesPerBucket(),
                key.getMaxBuckets() * key.getEntriesPerBucket() >= 20);
    }

    @Test
    public void testBucketIndex() throws Exception {
        for (int i = 9; i < 20; i += 3) {
            SpatialKeyTree tree = createSKTWithoutBuffer(i);
            SpatialKeyAlgo algo = tree.getAlgo();
            Random rand = new Random();
            for (int j = 0; j < 10000; j++) {
                double lat = rand.nextDouble() * 5;
                double lon = rand.nextDouble() * 5;
                try {
                    tree.getBucketIndex(algo.encode(lat, lon));
                } catch (Exception ex) {
                    assertFalse("Problem while " + lat + "," + lon + " " + ex.getMessage(), false);
                }
            }
        }
    }

    @Test
    public void testStatsNoError() throws Exception {
        SpatialKeyTree tree = new SpatialKeyTree(10, 2).init(10000);
        Random rand = new Random(12);
        for (int i = 0; i < 10000; i++) {
            tree.add(Math.abs(rand.nextDouble()), Math.abs(rand.nextDouble()), i * 100);
        }
        tree.getEntries("e");
        tree.getOverflowEntries("o");
        tree.getOverflowOffset("oo");
    }

    @Test
    public void testAddAndGet() {
        SpatialKeyTree tree = createTree(10, false);
        int max = tree.getEntriesPerBucket() * 2;
        long[] vals = new long[max];

        Random rand = new Random(0);
        for (int i = 0; i < max; i++) {
            vals[i] = rand.nextLong() / 123000;
        }
        for (int i = 0; i < max; i++) {
            try {
                tree.add(vals[i], i);
            } catch (Exception ex) {
                ex.printStackTrace();
                assertFalse("Problem with " + i + " " + vals[i] + " " + ex.getMessage(), true);
            }
        }

        for (int i = 0; i < max; i++) {
            assertEquals(1, tree.getNodes(vals[i]).size());
        }
    }

    @Test
    public void testWriteAndGetKey() {
        SpatialKeyTree tree = createTree(0, false);
        tree.putKey(0, 123);
        assertEquals(123, tree.getKey(0));
        tree.putKey(2, Long.MAX_VALUE / 3);
        assertEquals(Long.MAX_VALUE / 3, tree.getKey(2));
        tree.putKey(0, -1);
        assertEquals(-1, tree.getKey(0));
        tree.putKey(30, 123);
        assertEquals(123, tree.getKey(30));

        tree.writeNoOfEntries(0, 3, false);
        assertFalse(tree.isOverflowed(0));
        tree.writeNoOfEntries(0, 3, true);
        assertTrue(tree.isOverflowed(0));
    }

    @Test
    public void testKeyDuplicatesForceOverflow() {
        // 0 => force that it is a bad hash creation algo
        // false => do not compress key
        SpatialKeyTree tree = createTree(0, false);
        int max = tree.getEntriesPerBucket() * 2;
        for (int i = 0; i < max; i++) {
            tree.add(0, i);
            tree.add(1, i);
            tree.add(2, i);
        }

        assertEquals(max, tree.getNodes(0).size());
        assertEquals(max, tree.getNodes(1).size());
        assertEquals(max, tree.getNodes(2).size());
    }

    SpatialKeyTree createTree(final int skipLeft, boolean compress) {
        try {
            return new SpatialKeyTree(skipLeft, 1).setCompressKey(compress).init(120);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    // faster - but not functional

    SpatialKeyTree createSKTWithoutBuffer(int i) {
        return new SpatialKeyTree(i) {

            @Override protected void initBuffers() {
            }
        };
    }
}
