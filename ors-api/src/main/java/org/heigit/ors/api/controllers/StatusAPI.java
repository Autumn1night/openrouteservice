/*
 * This file is part of Openrouteservice.
 *
 * Openrouteservice is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, see <https://www.gnu.org/licenses/>.
 */

package org.heigit.ors.api.controllers;

import com.graphhopper.storage.StorableProperties;
import com.typesafe.config.ConfigException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.heigit.ors.api.EndpointsProperties;
import org.heigit.ors.api.util.AppConfigMigration;
import org.heigit.ors.api.util.AppInfo;
import org.heigit.ors.localization.LocalizationManager;
import org.heigit.ors.routing.RoutingProfile;
import org.heigit.ors.routing.RoutingProfileManager;
import org.heigit.ors.routing.RoutingProfileManagerStatus;
import org.heigit.ors.routing.configuration.RouteProfileConfiguration;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "Status service", description = "Get information on the status of the api")
@RequestMapping("/v2/status")
public class StatusAPI {
    private final EndpointsProperties endpointsProperties;

    public StatusAPI(EndpointsProperties endpointsProperties) {
        this.endpointsProperties = AppConfigMigration.overrideEndpointsProperties(endpointsProperties);
    }

    @GetMapping
    public ResponseEntity<String> fetchHealth(HttpServletRequest request) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        org.json.JSONObject jInfo = new org.json.JSONObject(true);
        jInfo.put("engine", AppInfo.getEngineInfo());
        if (RoutingProfileManagerStatus.isReady()) {
            RoutingProfileManager profileManager = RoutingProfileManager.getInstance();
            if (!profileManager.getProfiles().getUniqueProfiles().isEmpty()) {
                List<String> list = new ArrayList<>(4);
                if (endpointsProperties.getRouting().isEnabled())
                    list.add("routing");
                if (endpointsProperties.getIsochrone().isEnabled())
                    list.add("isochrones");
                if (endpointsProperties.getMatrix().isEnabled())
                    list.add("matrix");
                jInfo.put("services", list);
                jInfo.put("languages", LocalizationManager.getInstance().getLanguages());
                jInfo.put("profiles", getProfiles(profileManager));
            }
        }
        return new ResponseEntity<>(constructResponse(request, jInfo), headers, HttpStatus.OK);
    }

    private static JSONObject getProfiles(RoutingProfileManager profileManager) {
        JSONObject jProfiles = new JSONObject(true);
        int i = 1;

        for (RoutingProfile rp : profileManager.getProfiles().getUniqueProfiles()) {
            RouteProfileConfiguration rpc = rp.getConfiguration();
            JSONObject jProfileProps = new JSONObject(true);

            jProfileProps.put("profiles", rpc.getProfiles());
            StorableProperties storageProps = rp.getGraphProperties();
            jProfileProps.put("creation_date", storageProps.get("osmreader.import.date"));

            if (rpc.getExtStorages() != null && !rpc.getExtStorages().isEmpty())
                jProfileProps.put("storages", rpc.getExtStorages());

            JSONObject jProfileLimits = new JSONObject(true);
            if (rpc.getMaximumDistance() > 0)
                jProfileLimits.put("maximum_distance", rpc.getMaximumDistance());

            if (rpc.getMaximumDistanceDynamicWeights() > 0)
                jProfileLimits.put("maximum_distance_dynamic_weights", rpc.getMaximumDistanceDynamicWeights());

            if (rpc.getMaximumDistanceAvoidAreas() > 0)
                jProfileLimits.put("maximum_distance_avoid_areas", rpc.getMaximumDistanceAvoidAreas());

            if (rpc.getMaximumWayPoints() > 0)
                jProfileLimits.put("maximum_waypoints", rpc.getMaximumWayPoints());

            if (!jProfileLimits.isEmpty())
                jProfileProps.put("limits", jProfileLimits);

            jProfiles.put("profile " + i, jProfileProps);
            i++;
        }
        return jProfiles;
    }

    private String constructResponse(HttpServletRequest req, org.json.JSONObject json) {
        String type = getParam(req, "type", "json");
        boolean debug = getBooleanParam(req, "debug", false) || getBooleanParam(req, "pretty", false);
        if ("jsonp".equals(type)) {

            String callbackName = getParam(req, "callback", null);
            if (callbackName == null) {
                throw new HttpServerErrorException(HttpStatus.BAD_REQUEST, "No callback provided, necessary if type=jsonp");
            }

            if (debug) {
                return callbackName + "(" + json.toString(2) + ")";
            } else {
                return callbackName + "(" + json.toString() + ")";
            }
        } else {
            if (debug) {
                return json.toString(2);
            } else {
                return json.toString();
            }
        }
    }

    protected boolean getBooleanParam(HttpServletRequest req, String string, boolean defaultValue) {
        try {
            return Boolean.parseBoolean(getParam(req, string, "" + defaultValue));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    protected String getParam(HttpServletRequest req, String string, String defaultValue) {
        String[] l = req.getParameterMap().get(string);
        if (l != null && l.length > 0)
            return l[0];

        return defaultValue;
    }
}
