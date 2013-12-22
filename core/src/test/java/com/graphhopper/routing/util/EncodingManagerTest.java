/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMWay;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class EncodingManagerTest
{
    @Test
    public void testCompatibility()
    {
        EncodingManager manager = new EncodingManager("CAR,BIKE,FOOT");
        BikeFlagEncoder bike = (BikeFlagEncoder) manager.getEncoder("BIKE");
        CarFlagEncoder car = (CarFlagEncoder) manager.getEncoder("CAR");
        FootFlagEncoder foot = (FootFlagEncoder) manager.getEncoder("FOOT");
        assertNotEquals(car, bike);
        assertNotEquals(car, foot);
        assertNotEquals(car.hashCode(), bike.hashCode());
        assertNotEquals(car.hashCode(), foot.hashCode());

        EncodingManager manager2 = new EncodingManager();
        FootFlagEncoder foot2 = new FootFlagEncoder()
        {
        };
        manager2.register(foot2);
        assertNotEquals(foot, foot2);
        assertNotEquals(foot.hashCode(), foot2.hashCode());

        EncodingManager manager3 = new EncodingManager();
        FootFlagEncoder foot3 = new FootFlagEncoder()
        {
        };
        manager3.register(foot3);
        assertEquals(foot3, foot2);
        assertEquals(foot3.hashCode(), foot2.hashCode());

    }

    @Test
    public void testEncoderAcceptNoException()
    {
        EncodingManager manager = new EncodingManager("CAR");
        assertTrue(manager.supports("CAR"));
        assertFalse(manager.supports("FOOT"));
    }

    @Test
    public void testTooManyEncoders()
    {
        EncodingManager manager = new EncodingManager();
        for (int i = 0; i < 4; i++)
        {
            manager.register(new FootFlagEncoder()
            {
            });
        }
        try
        {
            manager.register(new FootFlagEncoder()
            {
            });
            assertTrue(false);
        } catch (Exception ex)
        {
        }
    }

    @Test
    public void testCombineRelations()
    {
        Map<String, String> wayMap = new HashMap<String, String>();
        wayMap.put("highway", "track");
        OSMWay osmWay = new OSMWay(1, wayMap);

        Map<String, String> relMap = new HashMap<String, String>();
        OSMRelation osmRel = new OSMRelation(1, relMap);

        EncodingManager manager = new EncodingManager();
        BikeFlagEncoder defaultBike = new BikeFlagEncoder();
        manager.register(defaultBike);
        BikeFlagEncoder lessRelationCodes = new BikeFlagEncoder()
        {
            @Override
            public int defineRelationBits( int index, int shift )
            {
                relationCodeEncoder = new EncodedValue("RelationCode2", shift, 2, 1, 0, 3);
                return shift + 2;
            }

            @Override
            public long handleRelationTags( OSMRelation relation, long oldRelFlags )
            {
                if (relation.hasTag("route", "bicycle"))
                    return relationCodeEncoder.setValue(0, 2);
                return relationCodeEncoder.setValue(0, 0);
            }
        };
        manager.register(lessRelationCodes);

        // relation code is PREFER
        relMap.put("route", "bicycle");
        relMap.put("network", "lcn");
        long relFlags = manager.handleRelationTags(osmRel, 0);
        long allow = defaultBike.isAllowed(osmWay) | lessRelationCodes.isAllowed(osmWay);
        long flags = manager.handleWayTags(osmWay, allow, relFlags);

        assertEquals(18, defaultBike.getSpeed(flags));
        assertEquals(4, lessRelationCodes.getSpeed(flags));
    }
}
