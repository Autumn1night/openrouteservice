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
package com.graphhopper.reader;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.*;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import gnu.trove.list.TLongList;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the OSMReader with the normal helper initialized.
 * <p/>
 * @author Peter Karich
 */
public class OSMReaderTest
{
    private final String file1 = "test-osm.xml";
    private final String file2 = "test-osm2.xml";
    private final String file3 = "test-osm3.xml";
    private final String file4 = "test-osm4.xml";
    private final String fileNegIds = "test-osm-negative-ids.xml";
    private final String fileBarriers = "test-barriers.xml";
    private final String dir = "./target/tmp/test-db";
    private CarFlagEncoder carEncoder;
    private FootFlagEncoder footEncoder;
    private EdgeExplorer carOutExplorer;
    private EdgeExplorer carAllExplorer;

    @Before
    public void setUp()
    {
        new File(dir).mkdirs();
    }

    @After
    public void tearDown()
    {
        Helper.removeDir(new File(dir));
    }

    GraphStorage buildGraph( String directory, EncodingManager encodingManager )
    {
        return new GraphHopperStorage(new RAMDirectory(directory, false), encodingManager);
    }

    class GraphHopperTest extends GraphHopper
    {
        String testFile;

        public GraphHopperTest( String file )
        {
            this.testFile = file;
            setGraphHopperLocation(dir);
            setEncodingManager(new EncodingManager("CAR,FOOT"));
            disableCHShortcuts();

            carEncoder = (CarFlagEncoder) getEncodingManager().getEncoder("CAR");
            footEncoder = (FootFlagEncoder) getEncodingManager().getEncoder("FOOT");
        }

        OSMReader createReader( GraphStorage tmpGraph )
        {
            return new OSMReader(tmpGraph, 1000);
        }

        @Override
        protected OSMReader importOSM( String ignore ) throws IOException
        {
            GraphStorage tmpGraph = buildGraph(dir, getEncodingManager());
            setGraph(tmpGraph);
            OSMReader osmReader = createReader(tmpGraph);
            osmReader.setEncodingManager(getEncodingManager());
            try
            {
                osmReader.doOSM2Graph(new File(getClass().getResource(testFile).toURI()));
            } catch (URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
            carOutExplorer = getGraph().createEdgeExplorer(new DefaultEdgeFilter(carEncoder, false, true));
            carAllExplorer = getGraph().createEdgeExplorer(new DefaultEdgeFilter(carEncoder, true, true));
            return osmReader;
        }
    }

    InputStream getResource( String file )
    {
        return getClass().getResourceAsStream(file);
    }

    @Test
    public void testMain()
    {
        GraphHopper hopper = new GraphHopperTest(file1).importOrLoad();
        Graph graph = hopper.getGraph();
        assertEquals(4, graph.getNodes());
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 49);
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals("street 123, B 122", iter.getName());
        assertEquals(n50, iter.getAdjNode());
        AbstractGraphStorageTester.assertPList(Helper.createPointList(51.25, 9.43), iter.fetchWayGeometry(0));
        CarFlagEncoder flags = carEncoder;
        assertTrue(flags.isForward(iter.getFlags()));
        assertTrue(flags.isBackward(iter.getFlags()));

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93147, iter.getDistance(), 1);

        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);

        assertTrue(flags.isForward(iter.getFlags()));
        assertTrue(flags.isBackward(iter.getFlags()));
        assertFalse(iter.next());

        // get third added location id=30
        iter = carOutExplorer.setBaseNode(n30);
        assertTrue(iter.next());
        assertEquals("route 666", iter.getName());
        assertEquals(n20, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);

        assertEquals(9.4, graph.getLongitude(hopper.getLocationIndex().findID(51.2, 9.4)), 1e-3);
        assertEquals(10, graph.getLongitude(hopper.getLocationIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, graph.getLatitude(hopper.getLocationIndex().findID(51.2492152, 9.4317166)), 1e-3);

        // node 40 is on the way between 30 and 50 => 9.0
        assertEquals(9, graph.getLongitude(hopper.getLocationIndex().findID(51.25, 9.43)), 1e-3);
    }

    @Test
    public void testSort()
    {
        GraphHopper hopper = new GraphHopperTest(file1).setSortGraph(true).importOrLoad();
        Graph graph = hopper.getGraph();
        assertEquals(10, graph.getLongitude(hopper.getLocationIndex().findID(49, 10)), 1e-3);
        assertEquals(51.249, graph.getLatitude(hopper.getLocationIndex().findID(51.2492152, 9.4317166)), 1e-3);
    }

    @Test
    public void testWithBounds()
    {
        GraphHopper hopper = new GraphHopperTest(file1)
        {
            @Override
            OSMReader createReader( GraphStorage tmpGraph )
            {
                return new OSMReader(tmpGraph, 1000)
                {
                    @Override
                    public boolean isInBounds( OSMNode node )
                    {
                        return node.getLat() > 49 && node.getLon() > 8;
                    }
                };
            }
        };

        hopper.importOrLoad();

        Graph graph = hopper.getGraph();
        assertEquals(4, graph.getNodes());
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);
        int n40 = AbstractGraphStorageTester.getIdOf(graph, 51.25);

        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n40, iter.getAdjNode());
        AbstractGraphStorageTester.assertPList(Helper.createPointList(), iter.fetchWayGeometry(0));
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);
        assertTrue(iter.next());
        AbstractGraphStorageTester.assertPList(Helper.createPointList(), iter.fetchWayGeometry(0));
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);

        // get third added location => 2
        iter = carOutExplorer.setBaseNode(n30);
        assertTrue(iter.next());
        assertEquals(n20, iter.getAdjNode());
        assertEquals(93146.888, iter.getDistance(), 1);
        assertFalse(iter.next());
    }

    @Test
    public void testOneWay()
    {
        GraphHopper hopper = new GraphHopperTest(file2).importOrLoad();
        Graph graph = hopper.getGraph();

        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52.0);
        int n22 = AbstractGraphStorageTester.getIdOf(graph, 52.133);
        int n23 = AbstractGraphStorageTester.getIdOf(graph, 52.144);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);

        assertEquals(1, GHUtility.count(carOutExplorer.setBaseNode(n10)));
        assertEquals(2, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(0, GHUtility.count(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());

        CarFlagEncoder encoder = carEncoder;
        iter = carAllExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n23, iter.getAdjNode());
        assertTrue(encoder.isForward(iter.getFlags()));
        assertFalse(encoder.isBackward(iter.getFlags()));

        assertTrue(iter.next());
        assertEquals(n22, iter.getAdjNode());
        assertFalse(encoder.isForward(iter.getFlags()));
        assertTrue(encoder.isBackward(iter.getFlags()));

        assertTrue(iter.next());
        assertFalse(encoder.isForward(iter.getFlags()));
        assertTrue(encoder.isBackward(iter.getFlags()));

        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertTrue(encoder.isForward(iter.getFlags()));
        assertFalse(encoder.isBackward(iter.getFlags()));

        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertFalse(encoder.isForward(iter.getFlags()));
        assertTrue(encoder.isBackward(iter.getFlags()));
    }

    @Test
    public void testFerry()
    {
        GraphHopper hopper = new GraphHopperTest(file2)
        {
            @Override
            public void cleanUp()
            {
            }
        }.importOrLoad();
        Graph graph = hopper.getGraph();

        int n40 = AbstractGraphStorageTester.getIdOf(graph, 54.0);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55.0);
        assertEquals(GHUtility.asSet(n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n50)));

        // no duration is given => slow speed only!
        int n80 = AbstractGraphStorageTester.getIdOf(graph, 54.1);
        EdgeIterator iter = carOutExplorer.setBaseNode(n80);
        iter.next();
        assertEquals(5, carEncoder.getSpeed(iter.getFlags()));

        // more precise speed calculation! ~150km (from 54.0,10.1 to 55.0,10.1) in duration=70 minutes -> wow ;)
        // => 130km/h => / 1.4 => 92km/h        
        iter = carOutExplorer.setBaseNode(n40);
        iter.next();
        assertEquals(100, carEncoder.getSpeed(iter.getFlags()));
    }

    @Test
    public void testMaxSpeed()
    {
        GraphHopper hopper = new GraphHopperTest(file2)
        {
            @Override
            public void cleanUp()
            {
            }
        }.importOrLoad();
        Graph graph = hopper.getGraph();

        int n60 = AbstractGraphStorageTester.getIdOf(graph, 56.0);
        EdgeIterator iter = carOutExplorer.setBaseNode(n60);
        iter.next();
        assertEquals(35, carEncoder.getSpeed(iter.getFlags()));
    }

    @Test
    public void testWayReferencesNotExistingAdjNode()
    {
        GraphHopper hopper = new GraphHopperTest(file4).
                importOrLoad();
        Graph graph = hopper.getGraph();

        assertEquals(2, graph.getNodes());
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);

        assertEquals(GHUtility.asSet(n30), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
    }

    @Test
    public void testFoot()
    {
        GraphHopper hopper = new GraphHopperTest(file3).
                importOrLoad();
        Graph graph = hopper.getGraph();

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 11.1);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 12);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 11.2);
        int n40 = AbstractGraphStorageTester.getIdOf(graph, 11.3);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 10);

        assertEquals(GHUtility.asSet(n20, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30, n40), GHUtility.getNeighbors(carAllExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n30, n40), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n20)));

        EdgeExplorer footOutExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(footEncoder, false, true));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(n20, n50), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n30)));
        assertEquals(GHUtility.asSet(n10, n30), GHUtility.getNeighbors(footOutExplorer.setBaseNode(n20)));
    }

    @Test
    public void testNegativeIds()
    {
        GraphHopper hopper = new GraphHopperTest(fileNegIds).importOrLoad();
        Graph graph = hopper.getGraph();
        assertEquals(4, graph.getNodes());
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51.2492152);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 51.2);
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(3, GHUtility.count(carOutExplorer.setBaseNode(n20)));
        assertEquals(GHUtility.asSet(n20), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());

        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertEquals(93147, iter.getDistance(), 1);

        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertEquals(88643, iter.getDistance(), 1);
    }

    @Test
    public void testBarriers()
    {
        GraphHopper hopper = new GraphHopperTest(fileBarriers).importOrLoad();
        Graph graph = hopper.getGraph();
        assertEquals(8, graph.getNodes());

        int n10 = AbstractGraphStorageTester.getIdOf(graph, 51);
        int n20 = AbstractGraphStorageTester.getIdOf(graph, 52);
        int n30 = AbstractGraphStorageTester.getIdOf(graph, 53);
        int n50 = AbstractGraphStorageTester.getIdOf(graph, 55);

        // separate id
        int new20 = 4;
        assertNotEquals(n20, new20);
        assertEquals(graph.getLatitude(n20), graph.getLatitude(new20), 1e-5);
        assertEquals(graph.getLongitude(n20), graph.getLongitude(new20), 1e-5);

        assertEquals(n20, hopper.getLocationIndex().findID(52, 9.4));

        assertEquals(GHUtility.asSet(n20, n30), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n10)));
        assertEquals(GHUtility.asSet(new20, n10, n50), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n30)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n20);
        assertTrue(iter.next());
        assertEquals(n10, iter.getAdjNode());
        assertFalse(iter.next());

        iter = carOutExplorer.setBaseNode(new20);
        assertTrue(iter.next());
        assertEquals(n30, iter.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void testBarriersOnTowerNodes()
    {
        GraphHopper hopper = new GraphHopperTest(fileBarriers).importOrLoad();
        Graph graph = hopper.getGraph();
        assertEquals(8, graph.getNodes());

        int n60 = AbstractGraphStorageTester.getIdOf(graph, 56);

        int newId = 5;
        assertEquals(GHUtility.asSet(newId), GHUtility.getNeighbors(carOutExplorer.setBaseNode(n60)));

        EdgeIterator iter = carOutExplorer.setBaseNode(n60);
        assertTrue(iter.next());
        assertEquals(newId, iter.getAdjNode());
        assertFalse(iter.next());

        iter = carOutExplorer.setBaseNode(newId);
        assertTrue(iter.next());
        assertEquals(n60, iter.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void testFixWayName()
    {
        assertEquals("B8, B12", OSMReader.fixWayName("B8;B12"));
        assertEquals("B8, B12", OSMReader.fixWayName("B8; B12"));
    }

    @Test
    public void testEstimatedCenter()
    {
        final CarFlagEncoder encoder = new CarFlagEncoder()
        {
            private EncodedValue objectEncoder;

            @Override
            public int defineBits( int index, int shift )
            {
                shift = super.defineBits(index, shift);
                objectEncoder = new EncodedValue("oEnc", shift, 2, 1, 0, 3, false, true);
                return shift + 2;
            }

            @Override
            public long handleNodeTags( OSMNode node )
            {
                if (node.hasTag("test", "now"))
                    return -objectEncoder.setValue(0, 1);
                return 0;
            }

            public long applyNodeFlags( long wayFlags, long nodeFlags )
            {
                int speed = getSpeed(wayFlags);
                if (objectEncoder.getValue(nodeFlags) != 0)
                    speed -= 5;

                return speedEncoder.setValue(wayFlags, speed);
            }
        };
        EncodingManager manager = new EncodingManager()
        {

            
            {
                super.register(encoder);
            }
        };
        GraphStorage graph = buildGraph(dir, manager);
        final Map<Integer, Double> latMap = new HashMap<Integer, Double>();
        final Map<Integer, Double> lonMap = new HashMap<Integer, Double>();
        latMap.put(1, 1.1d);
        latMap.put(2, 1.2d);

        lonMap.put(1, 1.0d);
        lonMap.put(2, 1.0d);
        final AtomicInteger increased = new AtomicInteger(0);
        OSMReader osmreader = new OSMReader(graph, 1000)
        {
            // mock data access
            @Override
            double getTmpLatitude( int id )
            {
                return latMap.get(id);
            }

            @Override
            double getTmpLongitude( int id )
            {
                return lonMap.get(id);
            }

            @Override
            Collection<EdgeIteratorState> addOSMWay( TLongList osmNodeIds, long wayFlags )
            {
                // reduced speed due to node tags
                increased.incrementAndGet();
                assertEquals(100 - 5, encoder.getSpeed(wayFlags));
                return Collections.EMPTY_SET;
            }

        };
        osmreader.setEncodingManager(manager);
        // save some node tags for first node
        Map<String, String> nodeMap = new HashMap<String, String>();
        OSMNode osmNode = new OSMNode(1, nodeMap, 1.1d, 1.0d);
        osmNode.setTag("test", "now");
        osmreader.getNodeFlagsMap().put(1, encoder.handleNodeTags(osmNode));

        OSMWay way = new OSMWay(1L);
        way.getNodes().add(1);
        way.getNodes().add(2);
        way.setTag("highway", "motorway");
        osmreader.getNodeMap().put(1, 1);
        osmreader.getNodeMap().put(2, 2);
        osmreader.processWay(way);

        GHPoint p = way.getInternalTag("estimated_center", null);
        assertEquals(1.15, p.lat, 1e-3);
        assertEquals(1.0, p.lon, 1e-3);
        Double d = way.getInternalTag("estimated_distance", null);
        assertEquals(11119.5, d, 1e-1);
        assertEquals(1, increased.get());
    }
}
