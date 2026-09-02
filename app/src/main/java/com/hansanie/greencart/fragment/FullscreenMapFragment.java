package com.hansanie.greencart.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.hansanie.greencart.R;

import java.util.ArrayList;

public class FullscreenMapFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_LATS = "arg_lats";
    private static final String ARG_LONS = "arg_lons";

    private MapView mapView;
    private GoogleMap googleMap;
    private MaterialButton btnClose;
    private FusedLocationProviderClient fusedClient;

    public static FullscreenMapFragment newInstance(double[] lats, double[] lons) {
        FullscreenMapFragment f = new FullscreenMapFragment();
        Bundle b = new Bundle();
        b.putDoubleArray(ARG_LATS, lats);
        b.putDoubleArray(ARG_LONS, lons);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_fullscreen_map, container, false);
        mapView = v.findViewById(R.id.fullscreenMapView);
        btnClose = v.findViewById(R.id.btnCloseMap);
        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        mapView.onCreate(savedInstanceState);
        MapsInitializer.initialize(requireContext());
        mapView.getMapAsync(this);

        btnClose.setOnClickListener(view -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .remove(this).commitAllowingStateLoss();
            }
        });

        // We rely on the Google Maps built-in My Location button; no custom button action needed here.

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        map.getUiSettings().setZoomControlsEnabled(true);
        // Allow Google Maps' built-in My Location button (native control)
        map.getUiSettings().setMyLocationButtonEnabled(true);

        Bundle args = getArguments();
        double[] lats = args != null ? args.getDoubleArray(ARG_LATS) : null;
        double[] lons = args != null ? args.getDoubleArray(ARG_LONS) : null;

        if (lats != null && lons != null && lats.length == lons.length && lats.length > 0) {
            if (lats.length == 1) {
                LatLng p = new LatLng(lats[0], lons[0]);
                map.addMarker(new MarkerOptions().position(p));
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(p, 16f));
            } else {
                java.util.List<LatLng> points = new ArrayList<>();
                LatLngBounds.Builder bb = new LatLngBounds.Builder();
                for (int i = 0; i < lats.length; i++) {
                    LatLng p = new LatLng(lats[i], lons[i]);
                    points.add(p);
                    bb.include(p);
                }
                // draw polyline if we have route points
                if (points.size() >= 2) {
                    map.addPolyline(new PolylineOptions()
                            .addAll(points)
                            .width(10f)
                            .geodesic(true)
                            .color(Color.parseColor("#2E7D32")));

                    // Only show start and end markers to avoid clutter
                    LatLng start = points.get(0);
                    LatLng end = points.get(points.size() - 1);
                    map.addMarker(new MarkerOptions()
                            .position(start)
                            .title("Green Cart Hub")
                            .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE)));
                    map.addMarker(new MarkerOptions()
                            .position(end)
                            .title("Shipping address")
                            .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN)));
                }

                LatLngBounds bounds = bb.build();
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
            }
        }

        // If permission already granted, enable my-location layer so the blue dot appears
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try { map.setMyLocationEnabled(true); }
            catch (SecurityException ignored) {}
        }

        // If the native My Location button is tapped and we don't have permission yet,
        // request it so the blue dot can be enabled.
        map.setOnMyLocationButtonClickListener(() -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
                return true; // we handled the click by requesting permission
            }
            return false; // let the map handle the button (default behavior)
        });
    }

    private void centerToMyLocation() {
        if (googleMap == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // request permission
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        try {
            googleMap.setMyLocationEnabled(true);
        } catch (SecurityException ignored) {}

        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc == null) {
                Toast.makeText(requireContext(), "Unable to get current location", Toast.LENGTH_SHORT).show();
                return;
            }
            LatLng p = new LatLng(loc.getLatitude(), loc.getLongitude());
            CameraPosition pos = new CameraPosition.Builder().target(p).zoom(16f).build();
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(pos));
        }).addOnFailureListener(e -> Toast.makeText(requireContext(), "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}

