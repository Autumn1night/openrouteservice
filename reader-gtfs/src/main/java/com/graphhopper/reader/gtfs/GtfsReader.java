package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import gnu.trove.map.hash.TIntIntHashMap;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

class GtfsReader {

    private static class EnterAndExitNodeIdWithStopId {
        final String stopId;
        final Collection<Integer> enterNodeIds;
        final int exitNodeId;

        private EnterAndExitNodeIdWithStopId(Collection<Integer> enterNodeIds, String stopId, int exitNodeId) {
            this.stopId = stopId;
            this.enterNodeIds = enterNodeIds;
            this.exitNodeId = exitNodeId;
        }
    }

    private static class TimelineNodeIdWithTripId {
        final String tripId;
        final int timelineNodeId;

        private TimelineNodeIdWithTripId(int timelineNodeId, String tripId) {
            this.tripId = tripId;
            this.timelineNodeId = timelineNodeId;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GtfsReader.class);

    private static final Frequency SINGLE_FREQUENCY = new Frequency();
    static {
        SINGLE_FREQUENCY.start_time = 0;
        SINGLE_FREQUENCY.end_time = 1;
        SINGLE_FREQUENCY.headway_secs = 1;
    }

    private final GraphHopperStorage graph;
    private final LocationIndex walkNetworkIndex;
    private final GtfsStorage gtfsStorage;

    private final DistanceCalc distCalc = Helper.DIST_EARTH;
    private Transfers transfers;
    private final NodeAccess nodeAccess;
    private final String id;
    private int i;
    private GTFSFeed feed;
    private TIntIntHashMap times;
    private SetMultimap<String, TimelineNodeIdWithTripId> stopTimelineNodes;
    private Collection<EnterAndExitNodeIdWithStopId> stopEnterAndExitNodes = new ArrayList<>();
    private SetMultimap<String, Integer> arrivals;
    private final PtFlagEncoder encoder;

    GtfsReader(String id, GraphHopperStorage ghStorage, LocationIndex walkNetworkIndex) {
        this.id = id;
        this.graph = ghStorage;
        this.gtfsStorage = (GtfsStorage) ghStorage.getExtension();
        this.nodeAccess = ghStorage.getNodeAccess();
        this.walkNetworkIndex = walkNetworkIndex;
        this.encoder = (PtFlagEncoder) graph.getEncodingManager().getEncoder("pt");
    }

    public void readGraph() {
        feed = this.gtfsStorage.getGtfsFeeds().get(id);
        transfers = new Transfers(feed.transfers.values());
        gtfsStorage.getFares().putAll(feed.fares);
        i = graph.getNodes();
        buildPtNetwork();
        EdgeFilter filter = new EverythingButPt(encoder);
        for (EnterAndExitNodeIdWithStopId entry : stopEnterAndExitNodes) {
            QueryResult source = walkNetworkIndex.findClosest(nodeAccess.getLat(entry.exitNodeId), nodeAccess.getLon(entry.exitNodeId), filter);
            Stop stop = feed.stops.get(entry.stopId);
            int streetNode;
            if (!source.isValid()) {
                streetNode = i;
                nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                graph.edge(streetNode, streetNode, 0.0, false);
            } else {
                streetNode = source.getClosestNode();
            }
            for (Integer enterNodeId : entry.enterNodeIds) {
                EdgeIteratorState entryEdge = graph.edge(streetNode, enterNodeId, 0.0, false);
                setEdgeType(entryEdge, GtfsStorage.EdgeType.ENTER_PT);
                entryEdge.setName(stop.stop_name);
            }
            EdgeIteratorState exitEdge = graph.edge(entry.exitNodeId, streetNode, 0.0, false);
            setEdgeType(exitEdge, GtfsStorage.EdgeType.EXIT_PT);
            exitEdge.setName(stop.stop_name);
        }
    }

    private void buildPtNetwork() {
        stopTimelineNodes = HashMultimap.create();
        arrivals = HashMultimap.create();
        times = new TIntIntHashMap();
        LocalDate startDate = feed.calculateStats().getStartDate();
        gtfsStorage.setStartDate(startDate);
        LocalDate endDate = feed.calculateStats().getEndDate();
        BitSet alwaysValid = new BitSet((int) DAYS.between(startDate, endDate));
        alwaysValid.set(0, alwaysValid.size());
        gtfsStorage.getOperatingDayPatterns().put(alwaysValid, 0);
        HashMultimap<String, Trip> blockTrips = HashMultimap.create();
        for (Trip trip : feed.trips.values()) {
            if (trip.block_id != null) {
                blockTrips.put(trip.block_id, trip);
            } else {
                blockTrips.put("non-block-trip"+trip.trip_id, trip);
            }
        }
        blockTrips.asMap().values().forEach(unsortedTrips -> {
            ArrayList<Trip> trips = new ArrayList<>(unsortedTrips);
            trips.sort(Comparator.comparingInt(trip -> getInterpolatedStopTimesForTrip(trip.trip_id).iterator().next().departure_time));
            if (trips.stream().map(trip -> feed.getFrequencies(trip.trip_id)).distinct().count() != 1) {
                throw new RuntimeException("Found a block with frequency-based trips. Not supported.");
            }
            Collection<Frequency> frequencies = feed.getFrequencies(trips.iterator().next().trip_id);
            for (Frequency frequency : (frequencies.isEmpty() ? Collections.singletonList(SINGLE_FREQUENCY) : frequencies)) {
                for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                    List<Integer> arrivalNodes = new ArrayList<>();
                    for (Trip trip : trips) {
                        Service service = feed.services.get(trip.service_id);
                        BitSet validOnDay = new BitSet((int) DAYS.between(startDate, endDate));
                        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                            if (service.activeOn(date)) {
                                validOnDay.set((int) DAYS.between(startDate, date));
                            }
                        }
                        StopTime prev = null;
                        int arrivalNode = -1;
                        int departureNode = -1;
                        for (StopTime stopTime : getInterpolatedStopTimesForTrip(trip.trip_id)) {
                            Stop stop = feed.stops.get(stopTime.stop_id);
                            arrivalNode = i++;
                            nodeAccess.setNode(arrivalNode, stop.stop_lat, stop.stop_lon);
                            nodeAccess.setAdditionalNodeField(arrivalNode, NodeType.INTERNAL_PT.ordinal());
                            times.put(arrivalNode, stopTime.arrival_time + time);
                            arrivals.put(stopTime.stop_id, arrivalNode);
                            if (prev != null) {
                                Stop fromStop = feed.stops.get(prev.stop_id);
                                double distance = distCalc.calcDist(
                                        fromStop.stop_lat,
                                        fromStop.stop_lon,
                                        stop.stop_lat,
                                        stop.stop_lon);
                                EdgeIteratorState edge = graph.edge(
                                        departureNode,
                                        arrivalNode,
                                        distance,
                                        false);
                                edge.setName(stop.stop_name);
                                setEdgeType(edge, GtfsStorage.EdgeType.HOP);
                                edge.setFlags(encoder.setTime(edge.getFlags(), stopTime.arrival_time - prev.departure_time));
                                gtfsStorage.getStopSequences().put(edge.getEdge(), stopTime.stop_sequence);
                            }
                            nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                            final int timelineNode = i - 1;
                            nodeAccess.setAdditionalNodeField(timelineNode, NodeType.INTERNAL_PT.ordinal());
                            times.put(timelineNode, stopTime.departure_time + time);
                            stopTimelineNodes.put(stopTime.stop_id, new TimelineNodeIdWithTripId(timelineNode, trip.trip_id));
                            departureNode = i++;
                            nodeAccess.setNode(departureNode, stop.stop_lat, stop.stop_lon);
                            nodeAccess.setAdditionalNodeField(departureNode, NodeType.INTERNAL_PT.ordinal());
                            times.put(departureNode, stopTime.departure_time + time);
                            EdgeIteratorState edge = graph.edge(
                                    i - 2,
                                    departureNode,
                                    0.0,
                                    false);
                            edge.setName(getRouteName(feed, trip));
                            int dayShift = stopTime.departure_time / (24 * 60 * 60);
                            setEdgeType(edge, GtfsStorage.EdgeType.BOARD);
                            gtfsStorage.getStopSequences().put(edge.getEdge(), stopTime.stop_sequence);
                            gtfsStorage.getExtraStrings().put(edge.getEdge(), trip.trip_id);
                            BitSet validOn = getValidOn(validOnDay, dayShift);
                            int validityId;
                            if (gtfsStorage.getOperatingDayPatterns().containsKey(validOn)) {
                                validityId = gtfsStorage.getOperatingDayPatterns().get(validOn);
                            } else {
                                validityId = gtfsStorage.getOperatingDayPatterns().size();
                                gtfsStorage.getOperatingDayPatterns().put(validOn, validityId);
                            }
                            edge.setFlags(encoder.setValidityId(edge.getFlags(), validityId));
                            edge.setFlags(encoder.setTransfers(edge.getFlags(), 1));
                            edge = graph.edge(
                                    i - 3,
                                    departureNode,
                                    0.0,
                                    false);
                            edge.setName(getRouteName(feed, trip));
                            setEdgeType(edge, GtfsStorage.EdgeType.DWELL);
                            edge.setFlags(encoder.setTime(edge.getFlags(), stopTime.departure_time - stopTime.arrival_time));
                            if (prev == null) {
                                insertInboundBlockTransfers(arrivalNodes, trip, departureNode, stopTime, stop, validityId);
                            }
                            prev = stopTime;
                        }
                        arrivalNodes.add(arrivalNode);
                    }
                }
            }
        });

        for (Stop stop : feed.stops.values()) {
            if (stop.location_type == 0) { // Only stops. Not interested in parent stations for now.
                nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                int stopExitNode = i-1;
                nodeAccess.setAdditionalNodeField(stopExitNode, NodeType.STOP_EXIT_NODE.ordinal());
                for (Integer arrivalNodeId : arrivals.get(stop.stop_id)) {
                    EdgeIteratorState leaveTimeExpandedNetworkEdge = graph.edge(arrivalNodeId, stopExitNode, 0.0, false);
                    setEdgeType(leaveTimeExpandedNetworkEdge, GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK);
                    int arrivalTime = times.get(arrivalNodeId);
                    leaveTimeExpandedNetworkEdge.setFlags(encoder.setTime(leaveTimeExpandedNetworkEdge.getFlags(), arrivalTime));
                }
                final Map<String, List<TimelineNodeIdWithTripId>> timelineNodesByRoute = stopTimelineNodes.get(stop.stop_id).stream().collect(Collectors.groupingBy(t -> feed.trips.get(t.tripId).route_id));

                List<Integer> stopEnterNodeIds = new ArrayList<>();
                timelineNodesByRoute.forEach((routeId, timelineNodesWithTripId) -> {
                    nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                    int stopEnterNode = i-1;
                    nodeAccess.setAdditionalNodeField(stopEnterNode, NodeType.STOP_ENTER_NODE.ordinal());
                    stopEnterNodeIds.add(stopEnterNode);
                    NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes = new TreeSet<>();
                    timelineNodesWithTripId.stream().map(t -> t.timelineNodeId)
                            .forEach(nodeId -> timeNodes.add(new Fun.Tuple2<>(times.get(nodeId) % (24*60*60), nodeId)));
                    wireUpAndAndConnectTimeline(stop, routeId,stopEnterNode, timeNodes);
                });
                stopEnterAndExitNodes.add(new EnterAndExitNodeIdWithStopId(stopEnterNodeIds, stop.stop_id, stopExitNode));
            }
        }
    }

    private void wireUpAndAndConnectTimeline(Stop stop, String routeId, int stopEnterNode, NavigableSet<Fun.Tuple2<Integer, Integer>> timeNodes) {
        int time = 0;
        int prev = -1;
        for (Fun.Tuple2<Integer, Integer> e : timeNodes.descendingSet()) {
            EdgeIteratorState enterTimeExpandedNetworkEdge = graph.edge(stopEnterNode, e.b, 0.0, false);
            enterTimeExpandedNetworkEdge.setName(stop.stop_name);
            setEdgeType(enterTimeExpandedNetworkEdge, GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK);
            enterTimeExpandedNetworkEdge.setFlags(encoder.setTime(enterTimeExpandedNetworkEdge.getFlags(), e.a));
            gtfsStorage.getExtraStrings().put(enterTimeExpandedNetworkEdge.getEdge(), id);
            if (prev != -1) {
                EdgeIteratorState edge = graph.edge(e.b, prev, 0.0, false);
                setEdgeType(edge, GtfsStorage.EdgeType.WAIT);
                edge.setName(stop.stop_name);
                edge.setFlags(encoder.setTime(edge.getFlags(), time-e.a));
            }
            time = e.a;
            prev = e.b;
        }
        if (!timeNodes.isEmpty()) {
            EdgeIteratorState edge = graph.edge(timeNodes.last().b, timeNodes.first().b, 0.0, false);
            int rolloverTime = 24 * 60 * 60 - timeNodes.last().a + timeNodes.first().a;
            setEdgeType(edge, GtfsStorage.EdgeType.OVERNIGHT);
            edge.setName(stop.stop_name);
            edge.setFlags(encoder.setTime(edge.getFlags(), rolloverTime));
        }
        final Optional<Transfer> withinStationTransfer = transfers.getTransfersToStop(stop, routeId).filter(t -> t.from_stop_id.equals(stop.stop_id)).filter(t -> t.transfer_type == 2).findAny();
        insertInboundTransfers(stop.stop_id, withinStationTransfer.isPresent() ? withinStationTransfer.get().min_transfer_time : 0, timeNodes);
        transfers.getTransfersToStop(stop, routeId).filter(t -> !t.from_stop_id.equals(stop.stop_id)).filter(t -> t.transfer_type == 2).forEach(transfer -> {
            insertInboundTransfers(transfer.from_stop_id, transfer.min_transfer_time, timeNodes);
        });
    }

    private void insertInboundBlockTransfers(List<Integer> arrivalNodes, Trip trip, int departureNode, StopTime stopTime, Stop stop, int validityId) {
        EdgeIteratorState edge;
        for (int lastTripArrivalNode : arrivalNodes) {
            int dwellTime = times.get(departureNode) - times.get(lastTripArrivalNode);
            if (dwellTime >= 0) {
                nodeAccess.setNode(i++, stop.stop_lat, stop.stop_lon);
                nodeAccess.setAdditionalNodeField(i-1, NodeType.INTERNAL_PT.ordinal());

                edge = graph.edge(
                        lastTripArrivalNode,
                        i-1,
                        0.0,
                        false);
                setEdgeType(edge, GtfsStorage.EdgeType.TRANSFER);
                edge.setFlags(encoder.setTime(edge.getFlags(), dwellTime));

                edge = graph.edge(
                        i-1,
                        departureNode,
                        0.0,
                        false);
                setEdgeType(edge, GtfsStorage.EdgeType.BOARD);
                edge.setFlags(encoder.setValidityId(edge.getFlags(), validityId));
                gtfsStorage.getStopSequences().put(edge.getEdge(), stopTime.stop_sequence);
                gtfsStorage.getExtraStrings().put(edge.getEdge(), trip.trip_id);
            }
        }
    }

    private Iterable<StopTime> getInterpolatedStopTimesForTrip(String trip_id) {
        try {
            return feed.getInterpolatedStopTimesForTrip(trip_id);
        } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes e) {
            throw new RuntimeException(e);
        }
    }

    private void insertInboundTransfers(String fromStopId, int minimumTransferTime, SortedSet<Fun.Tuple2<Integer, Integer>> toStopTimelineNode) {
        for (Integer arrivalNodeId : arrivals.get(fromStopId)) {
            int arrivalTime = times.get(arrivalNodeId);
            SortedSet<Fun.Tuple2<Integer, Integer>> tailSet = toStopTimelineNode.tailSet(new Fun.Tuple2<>(arrivalTime + minimumTransferTime, -1));
            if (!tailSet.isEmpty()) {
                Fun.Tuple2<Integer, Integer> e = tailSet.first();
                EdgeIteratorState edge = graph.edge(arrivalNodeId, e.b, 0.0, false);
                setEdgeType(edge, GtfsStorage.EdgeType.TRANSFER);
                edge.setFlags(encoder.setTime(edge.getFlags(), e.a-arrivalTime));
            }
        }
    }

    private String getRouteName(GTFSFeed feed, Trip trip) {
        Route route = feed.routes.get(trip.route_id);
        return (route.route_long_name != null ? route.route_long_name : route.route_short_name) + " " + trip.trip_headsign;
    }

    private void setEdgeType(EdgeIteratorState edge, GtfsStorage.EdgeType edgeType) {
        edge.setFlags(encoder.setEdgeType(edge.getFlags(), edgeType));
    }

    private BitSet getValidOn(BitSet validOnDay, int dayShift) {
        if (dayShift == 0) {
            return validOnDay;
        } else {
            BitSet bitSet = new BitSet(validOnDay.length() + 1);
            for (int i=0; i<validOnDay.length(); i++) {
                if (validOnDay.get(i)) {
                    bitSet.set(i+1);
                }
            }
            return bitSet;
        }
    }

}
