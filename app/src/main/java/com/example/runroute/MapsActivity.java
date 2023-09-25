package com.example.runroute;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.Geometry;
import com.google.maps.android.data.geojson.GeoJsonFeature;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonLineString;
import com.google.maps.android.data.geojson.GeoJsonLineStringStyle;
import com.google.maps.model.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    // private LatLng startPoint = new LatLng(62.8782, 27.6370); //Särkiniemi, Kuopio
    private Polyline mCurrentRoute;
    private List<LatLng> walkwayCoordinates;

    private GeoApiContext getGeoApiContext() {
        return new GeoApiContext.Builder().apiKey("AIzaSyB0ZHNFegEjmBa_DVJ6BlCD_pKcKN8m0-g").build();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);
    }




    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Kuopio, Finland.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */



    private DirectionsResult getRoute(LatLng origin, LatLng destination) {
        GeoApiContext context = getGeoApiContext();
        DirectionsApiRequest request = DirectionsApi.newRequest(context)
                .origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                .destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                .mode(TravelMode.WALKING);

        try {
            return request.await();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private void loadGeoJsonData() {
        try {
            walkwayCoordinates = new ArrayList<>();
            String jsonString = loadGeoJsonFromFile("geofiltered.geojson");
            if (jsonString != null) {
                GeoJsonLayer layer = new GeoJsonLayer(mMap, new JSONObject(jsonString));

                // Extract walkway coordinates from GeoJSON data
                for (GeoJsonFeature feature : layer.getFeatures()) {
                    Geometry geometry = feature.getGeometry();
                    if (geometry instanceof GeoJsonLineString) {
                        List<LatLng> coordinates = ((GeoJsonLineString) geometry).getCoordinates();
                        walkwayCoordinates.addAll(coordinates);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private String loadGeoJsonFromFile(String fileName) {
        try {
            InputStream inputStream = getAssets().open(fileName);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<LatLng> generateRandomRoute(LatLng startPoint, int numPoints) {
        loadGeoJsonData();
        List<LatLng> routePoints = new ArrayList<>();
        if (walkwayCoordinates == null || walkwayCoordinates.isEmpty()) {
            return routePoints;
        }

        Random random = new Random();
        LatLng currentPoint = startPoint;

        for (int i = 0; i < numPoints; i++) {
            LatLng randomCoordinate = walkwayCoordinates.get(random.nextInt(walkwayCoordinates.size()));
            DirectionsResult result = getRoute(currentPoint, randomCoordinate);

            if (result != null && result.routes.length > 0) {
                DirectionsRoute route = result.routes[0];
                for (DirectionsLeg leg : route.legs) {
                    for (DirectionsStep step : leg.steps) {
                        for (com.google.maps.model.LatLng latLng : step.polyline.decodePath()) {
                            routePoints.add(new LatLng(latLng.lat, latLng.lng));
                        }
                    }
                }
            }
            currentPoint = randomCoordinate;
        }
        DirectionsResult result = getRoute(currentPoint, startPoint);
        if (result != null && result.routes.length > 0) {
            DirectionsRoute route = result.routes[0];
            for (DirectionsLeg leg : route.legs) {
                for (DirectionsStep step : leg.steps) {
                    for (com.google.maps.model.LatLng latLng : step.polyline.decodePath()) {
                        routePoints.add(new LatLng(latLng.lat, latLng.lng));
                    }
                }
            }
        }

        return routePoints;
    }



    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        
        findViewById(R.id.map).setOnKeyListener(new View.OnKeyListener() {

            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    mMap.animateCamera(CameraUpdateFactory.zoomIn());
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    mMap.animateCamera(CameraUpdateFactory.zoomOut());
                    return true;
                }
                return false;
            }
        });



        LatLng startPoint = new LatLng(62.8782, 27.6370); // Särkiniemi, Kuopio

        Button drawLineButton = findViewById(R.id.draw_line_button);
        drawLineButton.setOnClickListener(v -> {
            try {
                if (mCurrentRoute != null) {
                    mCurrentRoute.remove();
                }

                List<LatLng> randomRoute = generateRandomRoute(startPoint, 4); // Reduced numPoints for shorter and faster routes

                // Create a PolylineOptions object and add the route points to it
                PolylineOptions routeOptions = new PolylineOptions()
                        .color(Color.RED)
                        .width(10)
                        .addAll(randomRoute);

                // Add the Polyline to the map
                mCurrentRoute = mMap.addPolyline(routeOptions);
            } catch (Exception e) {
                Log.e("MAPS_ACTIVITY", "Error in drawLineButton onClick", e);
            }
        });

        // Add an OnMouseWheelListener to the MapView to handle zooming with the mouse scroll



        // Add a marker in Kuopio and move the camera
        // LatLng sarkiniemi = new LatLng(62.8782, 27.6370);
        mMap.addMarker(new MarkerOptions().position(startPoint).title("Marker at Särkiniemi, Kuopio"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(sarkiniemi));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 14));
    }

}
