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

/**
 * directionFlag is necessary to store forward, backward or both directions.
 * 
 * @see DistEntry
 * @author Peter Karich, info@jetsli.de
 */
public class LinkedDistEntryWithFlags extends LinkedDistEntry {

    /**
     * 1 means: only forward direction. 2 means: only backward and 3 means: both directions
     */
    public byte directionFlag;

    /**
     * @param directionFlag 1 means: only forward direction. 2 means: only backward and 3 means: both directions
     */
    public LinkedDistEntryWithFlags(float distance, int loc, byte directionFlag) {
        super(loc, distance);
        this.directionFlag = directionFlag;
    }

    @Override
    public LinkedDistEntry clone() {
        return new LinkedDistEntryWithFlags(distance, node, directionFlag);
    }

    @Override
    public LinkedDistEntryWithFlags cloneFull() {
        return (LinkedDistEntryWithFlags) super.cloneFull();
    }
}
