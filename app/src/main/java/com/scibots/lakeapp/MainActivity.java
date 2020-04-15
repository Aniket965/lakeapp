package com.scibots.lakeapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity  implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
	private static final String TAG = "MAIN_ACTIVITY";
	private GoogleMap gmap;
	private FusedLocationProviderClient fusedLocationClient;
	public static final int REQUEST_RECORD_AUDIO = 13;
	private SupportMapFragment mapFragment;
	private Location devicelocation;
	private Button activate;
	private FloatingActionButton zoomin;
	private FloatingActionButton zoomout;
	private static final int CONTACT_PICKER_REQUEST = 991;
	public static final int PERMISSIONS_MULTIPLE_REQUEST = 123;

	private GoogleMap mMap;
//	private HotWordTriggeringService myServiceBinder = null;
	public ServiceConnection myConnection;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// binding

		zoomin = (FloatingActionButton) findViewById(R.id.zoomin_button);
		zoomout = (FloatingActionButton) findViewById(R.id.zoomout_button);

		requestMicrophonePermission();
		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
		mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapView);
		mapFragment.getMapAsync(this);
		activate = findViewById(R.id.activate);
		activate.setBackgroundResource(R.drawable.roundbutton);
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			requestMicrophonePermission();
			return;
		}

		fusedLocationClient.getLastLocation()
			.addOnSuccessListener(this, new OnSuccessListener<Location>() {
				@Override
				public void onSuccess(Location location) {
					// Got last known location. In some rare situations this can be null.
					if (location != null) {
						// Logic to handle location object
						Log.d(TAG, "Location Update 🗺 " + location.toString());
						devicelocation = location;
					}
					Log.d(TAG, "nono ");
				}

			});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	private void requestMicrophonePermission() {
		requestPermissions(
			new String[]{android.Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
				Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
				Manifest.permission.CALL_PHONE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
				Manifest.permission.READ_CONTACTS

			}, PERMISSIONS_MULTIPLE_REQUEST);
	}

	public void doBindService() {
//		Intent intent = new Intent(MainActivity.this,HotWordTriggeringService.class);
//		bindService(intent,myConnection, 0);
	}

	@Override
	public void onRequestPermissionsResult(
		int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_RECORD_AUDIO
			&& grantResults.length > 0
			&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
		}
		if (requestCode == PERMISSIONS_MULTIPLE_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				requestMicrophonePermission();
				return;
			}
			fusedLocationClient.getLastLocation()
				.addOnSuccessListener(this, new OnSuccessListener<Location>() {
					@Override
					public void onSuccess(Location location) {
						if (location != null) {
							devicelocation = location;
							if(mMap != null)
								updateMap(mMap);
						}
					}
				});

		}
	}

	private  void updateMap(GoogleMap googleMap) {
		gmap = googleMap;
		googleMap.setOnMarkerClickListener(MainActivity.this);
		if (devicelocation != null) {
			LatLng ny = new LatLng(devicelocation.getLatitude(), devicelocation.getLongitude());
			Log.d(TAG,"please move");
			mMap.animateCamera(CameraUpdateFactory.zoomTo(18));
			mMap.moveCamera(CameraUpdateFactory.newLatLng(ny));
			if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				requestMicrophonePermission();
				return;
			}
			fusedLocationClient.getLastLocation()
				.addOnSuccessListener(this, new OnSuccessListener<Location>() {
					@Override
					public void onSuccess(Location location) {
						// Got last known location. In some rare situations this can be null.
						if (location != null) {
							// Logic to handle location object
							LatLng currentlocation  = new LatLng(devicelocation.getLatitude(), devicelocation.getLongitude());

							RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
							String Hospitalurl ="https://maps.googleapis.com/maps/api/place/nearbysearch/json?location="+ devicelocation.getLatitude()
								+"," + devicelocation.getLongitude()
								+ "&radius=2000&type=hospital&key=AIzaSyAyMRozf6LGYhvVEY2HJhoICLQak36hcTU\n";
							String policeStationsUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location="+ devicelocation.getLatitude()
								+"," + devicelocation.getLongitude()
								+ "&radius=2000&type=police&key=AIzaSyAyMRozf6LGYhvVEY2HJhoICLQak36hcTU\n";

							JsonObjectRequest policeStationRequest = new JsonObjectRequest
								(Request.Method.GET, policeStationsUrl, null, new Response.Listener<JSONObject>() {

									@Override
									public void onResponse(JSONObject response) {
										try {
											JSONArray policeStations = response.getJSONArray("results");
											for(int i = 0; i < policeStations.length();i++) {
												JSONObject _policeStation = (JSONObject) policeStations.get(i);
												JSONObject _policeStationLocation = _policeStation.getJSONObject("geometry").getJSONObject("location");
												LatLng _policeStationCordinates = new LatLng(_policeStationLocation.getDouble("lat"),_policeStationLocation.getDouble("lng"));
												MarkerOptions markerOptions = new MarkerOptions();
												markerOptions.position(_policeStationCordinates);
												markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.police_stations));
												markerOptions.snippet("police station");
												markerOptions.title(_policeStation.getString("name"));
												gmap.addMarker(markerOptions);
											}

										} catch (JSONException e) {
											e.printStackTrace();
										}
									}
								}, new Response.ErrorListener() {

									@Override
									public void onErrorResponse(VolleyError error) {
										// TODO: Handle error

									}
								});

							JsonObjectRequest hospitalsRequest = new JsonObjectRequest
								(Request.Method.GET, Hospitalurl, null, new Response.Listener<JSONObject>() {

									@Override
									public void onResponse(JSONObject response) {
										try {
											JSONArray hospitals = response.getJSONArray("results");
											for(int i = 0; i < hospitals.length();i++) {
												JSONObject _hospital = (JSONObject) hospitals.get(i);
												JSONObject _hospitalLocation = _hospital.getJSONObject("geometry").getJSONObject("location");
												LatLng _hospitalCordinates = new LatLng(_hospitalLocation.getDouble("lat"),_hospitalLocation.getDouble("lng"));
												MarkerOptions markerOptions = new MarkerOptions();
												markerOptions.position(_hospitalCordinates);
												markerOptions.snippet("hospital");
												markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.hos));
												markerOptions.title(_hospital.getString("name"));
												gmap.addMarker(markerOptions);
											}

										} catch (JSONException e) {
											e.printStackTrace();
										}
									}
								}, new Response.ErrorListener() {

									@Override
									public void onErrorResponse(VolleyError error) {
										// TODO: Handle error

									}
								});
							queue.add(hospitalsRequest);
							queue.add(policeStationRequest);

							MarkerOptions markerOptions = new MarkerOptions();
							markerOptions.position(currentlocation);
							markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.me));
							markerOptions.title("me");
							markerOptions.snippet("your location");
							gmap.addMarker(markerOptions);
							gmap.moveCamera(CameraUpdateFactory.newLatLng(currentlocation));
							gmap.animateCamera(CameraUpdateFactory.zoomTo(16));
						}
						Log.d(TAG, "moved to current location ");
					}

				});
		} else {
			LatLng ny = new LatLng(28.613939, 77.209023);
			gmap.moveCamera(CameraUpdateFactory.newLatLng(ny));

		}
	}

	@Override
	public void onMapReady(GoogleMap googleMap) {

		Log.d(TAG, "Maps Ready!");
		gmap = googleMap;

		try {
			// try to change map style
			boolean success = gmap.setMapStyle(
				MapStyleOptions.loadRawResourceStyle(
					this, R.raw.map_style));
			if (!success) {
				Log.e(TAG, "Style parsing failed.");
			}
		} catch (Resources.NotFoundException e) {
			Log.e(TAG, "Can't find style. Error: ", e);
		}

		zoomin.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// zoom in a little
				gmap.animateCamera(CameraUpdateFactory.zoomTo(gmap.getCameraPosition().zoom + 0.5f));
			}
		});

		zoomout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//zoom out a little
				gmap.animateCamera(CameraUpdateFactory.zoomTo(gmap.getCameraPosition().zoom - 0.5f));
			}
		});


		gmap.setMinZoomPreference(12);
		updateMap(gmap);
		gmap.getUiSettings().setZoomGesturesEnabled(true);
		gmap.getUiSettings().setRotateGesturesEnabled(true);
		mMap = gmap;


	}
	@Override
	public boolean onMarkerClick(Marker marker) {
		LatLng latLng = marker.getPosition();
		String url = "https://www.google.com/maps/search/?api=1&query="+ latLng.latitude + "," + latLng.longitude + " ";
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		startActivity(browserIntent);

		return false;
	}
}
