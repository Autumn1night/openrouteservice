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
package de.jetsli.graph.storage;

import de.jetsli.graph.util.MyIteratorable;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public interface Graph {

    void ensureCapacity(int cap);

    /**
     * @return current number of locations
     */
    int getLocations();

    /**
     * Creates a new location with the returned id. If you do not have lat,lon you should create
     * edges only via edge(int,int,distance,bool) - e.g. in the case for none-real world graphs.
     *
     * @return id of new location
     */
    int addLocation(float lat, float lon);
    
    void removeLocation(int index);

    float getLatitude(int index);

    float getLongitude(int index);

    /**
     * @param a
     * @param b
     * @param distance necessary if no addLocation is called - e.g. if the graph is not a real world
     * geo-graph
     * @param bothDirections
     */
    void edge(int a, int b, float distance, boolean bothDirections);

    MyIteratorable<DistEntry> getEdges(int index);

    MyIteratorable<DistEntry> getIncoming(int index);

    MyIteratorable<DistEntry> getOutgoing(int index);

    Graph clone();
}
