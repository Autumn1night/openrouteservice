/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
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
package com.graphhopper.storage;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.Weighting;

import java.util.Arrays;
// ORS-GH MOD START
// CALT
import java.util.Collections;
// ORS-GH MOD END

/**
 * For now this is just a helper class to quickly create a GraphStorage.
 * <p>
 *
 * @author Peter Karich
 */
public class GraphBuilder {
    private final EncodingManager encodingManager;
    private String location;
    private boolean mmap;
    private boolean store;
    private boolean elevation;
    private long byteCapacity = 100;
    private Weighting singleCHWeighting;
    // ORS-GH MOD START
    // CALT
    private Weighting singleCoreWeighting;
    // ORS-GH MOD END

    public GraphBuilder(EncodingManager encodingManager) {
        this.encodingManager = encodingManager;
    }

    /**
     * This method enables creating a CHGraph with the specified weighting.
     */
    public GraphBuilder setCHGraph(Weighting singleCHWeighting) {
        this.singleCHWeighting = singleCHWeighting;
        return this;
    }

    // ORS-GH MOD START
    // CALT
    /**
     * This method enables creating a CoreGraph with the specified weighting.
     */
    public GraphBuilder setCoreGraph(Weighting singleCoreWeighting) {
        this.singleCoreWeighting = singleCoreWeighting;
        return this;
    }

    // ORS-GH MOD END
    public GraphBuilder setLocation(String location) {
        this.location = location;
        return this;
    }

    public GraphBuilder setStore(boolean store) {
        this.store = store;
        return this;
    }

    public GraphBuilder setMmap(boolean mmap) {
        this.mmap = mmap;
        return this;
    }

    public GraphBuilder setExpectedSize(byte cap) {
        this.byteCapacity = cap;
        return this;
    }

    public GraphBuilder set3D(boolean withElevation) {
        this.elevation = withElevation;
        return this;
    }

    public boolean hasElevation() {
        return elevation;
    }

    /**
     * Creates a CHGraph
     */
    public CHGraph chGraphCreate(Weighting singleCHWeighting) {
        return setCHGraph(singleCHWeighting).create().getGraph(CHGraph.class, singleCHWeighting);
    }

    /**
     * Default graph is a GraphStorage with an in memory directory and disabled storing on flush.
     * Afterwards you'll need to call GraphStorage. Create to have a usable object. Better use
     * create.
     */
    public GraphHopperStorage build() {
        Directory dir;
        if (mmap)
            dir = new MMapDirectory(location);
        else
            dir = new RAMDirectory(location, store);

        GraphHopperStorage graph;
        // ORS-GH MOD START
        // CALT
        //if (encodingManager.needsTurnCostsSupport() || singleCHWeighting == null)
        if (encodingManager.needsTurnCostsSupport() || (singleCHWeighting == null && singleCoreWeighting == null))
        // ORS-GH MOD END
            graph = new GraphHopperStorage(dir, encodingManager, elevation, new TurnCostExtension());
        // ORS-GH MOD START
        // CALT
        else if (singleCoreWeighting != null && singleCHWeighting != null)
            throw new IllegalStateException("Cannot build CHGraph and CoreGraph at the same time");
        else if (singleCHWeighting != null)
            graph = new GraphHopperStorage(Arrays.asList(singleCHWeighting),
                    dir,
                    encodingManager,
                    elevation,
                    new TurnCostExtension.NoOpExtension(),
                    Collections.singletonList("ch"));
        // ORS-GH MOD END
        else
            // ORS-GH MOD START
            // CALT
            //graph = new GraphHopperStorage(Arrays.asList(singleCHWeighting), dir, encodingManager, elevation, new TurnCostExtension.NoOpExtension());
        graph = new GraphHopperStorage(Arrays.asList(singleCoreWeighting),
                dir,
                encodingManager,
                elevation,
                new TurnCostExtension.NoOpExtension(),
                Collections.singletonList("core"));
            // ORS-GH MOD END

        return graph;
    }

    /**
     * Default graph is a GraphStorage with an in memory directory and disabled storing on flush.
     */
    public GraphHopperStorage create() {
        return build().create(byteCapacity);
    }

    /**
     * @throws IllegalStateException if not loadable.
     */
    public GraphHopperStorage load() {
        GraphHopperStorage gs = build();
        if (!gs.loadExisting()) {
            throw new IllegalStateException("Cannot load graph " + location);
        }
        return gs;
    }
}
