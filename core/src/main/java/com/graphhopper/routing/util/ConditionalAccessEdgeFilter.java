package com.graphhopper.routing.util;

import ch.poole.conditionalrestrictionparser.Condition;
import ch.poole.conditionalrestrictionparser.ConditionalRestrictionParser;
import ch.poole.conditionalrestrictionparser.Restriction;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.weighting.DateTimeConverter;
import com.graphhopper.storage.ConditionalEdgesMap;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import us.dustinj.timezonemap.TimeZoneMap;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;


public class ConditionalAccessEdgeFilter implements TimeDependentEdgeFilter {
    private final BooleanEncodedValue conditionalEnc;
    private final ConditionalEdgesMap conditionalEdges;
    private final boolean fwd;
    private final boolean bwd;
    private final DateTimeConverter dateTimeConverter;

    public ConditionalAccessEdgeFilter(GraphHopperStorage graph, FlagEncoder encoder, TimeZoneMap timeZoneMap) {
        this(graph, encoder.toString(), timeZoneMap);
    }

    public ConditionalAccessEdgeFilter(GraphHopperStorage graph, String encoderName, TimeZoneMap timeZoneMap) {
        this(graph, encoderName, timeZoneMap, true, true);
    }

    ConditionalAccessEdgeFilter(GraphHopperStorage graph, String encoderName, TimeZoneMap timeZoneMap, boolean fwd, boolean bwd) {
        EncodingManager encodingManager = graph.getEncodingManager();
        conditionalEnc = encodingManager.getBooleanEncodedValue(EncodingManager.getKey(encoderName, "conditional_access"));
        conditionalEdges = graph.getConditionalAccess(encoderName);
        this.fwd = fwd;
        this.bwd = bwd;
        this.dateTimeConverter = new DateTimeConverter(graph, timeZoneMap);
    }

    @Override
    public final boolean accept(EdgeIteratorState iter, long time) {
        if (fwd && iter.get(conditionalEnc) || bwd && iter.getReverse(conditionalEnc)) {
            // for now the filter is used only in the context of fwd search so only edges going out of the base node are explored
            ZonedDateTime zonedDateTime = dateTimeConverter.localDateTime(iter, time);
            String value = conditionalEdges.getValue(iter.getEdge());
            boolean result = accept(value, zonedDateTime);
            //System.out.println(iter.getEdge() + ": " + value + " -> " + result);  //FIXME: debug string
            return result;
        }
        return true;
    }

    boolean accept(String conditional, ZonedDateTime zonedDateTime) {
        boolean matchValue = false;

        try {
            ConditionalRestrictionParser crparser = new ConditionalRestrictionParser(new ByteArrayInputStream(conditional.getBytes()));

            ArrayList<Restriction> restrictions = crparser.restrictions();

            // iterate over restrictions starting from the last one in order to match to the most specific one
            for (int i = restrictions.size() - 1 ; i >= 0; i--) {
                Restriction restriction = restrictions.get(i);

                matchValue = "yes".equals(restriction.getValue());

                List<Condition> conditions = restriction.getConditions();

                // stop as soon as time matches the combined conditions
                if (TimeDependentConditionalEvaluator.match(conditions, zonedDateTime))
                    return matchValue;
            }

            // no restrictions with matching conditions found
            return !matchValue;

        } catch (ch.poole.conditionalrestrictionparser.ParseException e) {
            //nop
        }

        return false;
    }

    public boolean acceptsBackward() {
        return bwd;
    }

    public boolean acceptsForward() {
        return fwd;
    }

    @Override
    public String toString() {
        return conditionalEnc + ", bwd:" + bwd + ", fwd:" + fwd;
    }
}
