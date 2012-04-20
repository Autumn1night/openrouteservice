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
package de.jetsli.graph.trees;

import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.shapes.Shape;
import java.util.Collection;

/**
 * A quad tree interface - think Map<latitude+longitude, V> with the possibility to get neighbouring
 * entries fast.
 * 
 * @author Peter Karich, info@jetsli.de
 */
public interface QuadTree<V> {

    int size();
    
    /** only for tests - remove later*/
    int count();
    
    boolean isEmpty();
    
    QuadTree init(int maxItemsHint) throws Exception;
    
    V put(double lat, double lon, V value);
    
    V get(double lat, double lon);
    
    boolean remove(double lat, double lon);

    /**
     * @return points near the specified latitude/longitude
     */
    Collection<CoordTrig<V>> getNeighbours(double lat, double lon, double distanceInKm);
    
    Collection<CoordTrig<V>> getNeighbours(Shape boundingBox);
    
    void clear();
    
    String toDetailString();
        
    long getMemoryUsageInBytes(int factor);
}
