package edu.calpoly.android.walkabout;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

/**
 * Important note:
 * Change zoom level and circle info for actual use
 * (since only tested it moving few feet back and forth in my room)
 */
/**
 * Activity that contains an interactive Google Map fragment. Users can record
 * a traveled path, mark the map with information and take pictures that become
 * associated with the map.
 */
public class WalkAbout extends SherlockFragmentActivity implements android.location.LocationListener{

	/** The interactive Google Map fragment. */
	private GoogleMap m_vwMap;
	
	/** The list of locations, each having a latitude and longitude. */
	private ArrayList<LatLng> m_arrPathPoints;
	
	/** The list of markers, each having a latitude, longitude and title. */
	private ArrayList<Marker> m_arrPicturePoints;
	
	/** The continuous set of lines drawn between points on the map. */
	private Polyline m_pathLine;
	
	/** The Location Manager for the map. Used to obtain location status, etc. */
	private LocationManager m_locManager;
	
	/** Whether or not recording is currently in progress. */
	private boolean m_bRecording = false;

	/** The radius of a Circle drawn on the map, in meters. */
	private static final int CIRCLE_RADIUS = 30;
	
	/** Constants for the LocationManager. */
	private static final int MIN_TIME_CHANGE = 3000;
	private static final int MIN_DISTANCE_CHANGE = 3;
	
	/** Request codes for starting new Activities. */
	private static final int ENABLE_GPS_REQUEST_CODE = 1;
	private static final int PICTURE_REQUEST_CODE = 2;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLocationData();
        initLayout();    
    }
    
    /**
     * Initializes all Location-related data.
     */
    private void initLocationData() {
    	//retrieve the LocationManager and initialize it
    	this.m_locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    	//initialize location
    	this.m_arrPathPoints = new ArrayList<LatLng>();
    	//initialize recording state
    	this.m_bRecording = false;
    }
    
    /**
     * Initializes all other data for the application.
     */
	private void initLayout() {
		//inflate the map_layout.xml file
		setContentView(R.layout.map_layout);
		/*initialize m_vwMap by retrieving a reference to the SupportMagFragment element in the XML layout file*/
		SupportMapFragment map = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
		//check that the map is null before updating [DO THIS LATER]
		this.m_vwMap = map.getMap();
		/*enable MyLoacation layer, which will show the My Location on top of map by default */
		this.m_vwMap.setMyLocationEnabled(true); //is supposed to be true, but nah
		/* retrieve the user interface settings for the map and enable the compass */
		this.m_vwMap.getUiSettings().setCompassEnabled(true);

	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			/*Acts as a toggle for either starting or stopping GPS. 
			 * While recording, item should display "Stop"
			 * While stopped, the item should display "Start"
			 */
			case R.id.menu_recording:{
				Toast.makeText(getBaseContext(), "Pressed start/stop", Toast.LENGTH_SHORT).show();
				//if recording, activity should stop
				if (this.m_bRecording) {
					Toast.makeText(getBaseContext(), "Stop recording", Toast.LENGTH_SHORT).show();
					setRecordingState(false);
					//update the menu to reflect the change
					supportInvalidateOptionsMenu();
					break;
				}
				//if not recording, activity should start
				if (!this.m_bRecording) {
					//
					Toast.makeText(getBaseContext(), "Start recording", Toast.LENGTH_SHORT).show();
					setRecordingState(true);
					//update the menu to reflect the change
					supportInvalidateOptionsMenu();
					break;
				}
			}
			// save the current recorded path
			case R.id.menu_save:{
				Toast.makeText(getBaseContext(), "Save", Toast.LENGTH_SHORT).show();
				break;
			}
			//loads the last saved path
			case R.id.menu_load:{
				Toast.makeText(getBaseContext(), "Load", Toast.LENGTH_SHORT).show();
				break;
			}
			//Launches a Camera Activity that allows you to take pictures
			case R.id.menu_takePicture:{
				Toast.makeText(getBaseContext(), "Take Picture", Toast.LENGTH_SHORT).show();
				break;
			}
			//launches the Device Settings Activity that allows you to enable the GPS provider
			case R.id.menu_enableGPS:{
				Toast.makeText(getBaseContext(), "Enable GPS", Toast.LENGTH_SHORT).show();
				/* make a new Intent object
				 * Activity action: shows settings to allow for configuration of current
				 * location sources*/
				Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				/* Pass in the Request Code so that when we handle the result, we know
				 * which request generated by the result.  The result of any Activity that
				 * we launch gets processed by the same method and the Request Code mechanism
				 * lets us know which result is coming from which Activity*/
				startActivityForResult(intent, WalkAbout.ENABLE_GPS_REQUEST_CODE);
				break;
			}
		}
		return true;
	}
	
	/*Dynamically update options menu.  This method is always called right before the menu is shown
	 * Should display Start/Stop as disabled if the GPS Location Provider is disabled
	 * Should not display the Enable GPS MenuItem at all if the GPS Location Provider is enabled*/
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		
		//check if GPS Location Provider is disabled
		boolean enabled = this.m_locManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		MenuItem start = menu.findItem(R.id.menu_recording);
		
		if (enabled) {
			//do not display the Enable GPS MenuItem at all
			MenuItem gps = menu.findItem(R.id.menu_enableGPS);
			gps.setVisible(false);
			//automatically go to the location
			Location location = this.m_locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			//get the location and move the camera to it (this might cause a bug later?)
			onLocationChanged(location);
	
		}
		if (!enabled) {
			//disable start/stop
			start.setEnabled(false);
			//make Enable GPS visible
			MenuItem gps = menu.findItem(R.id.menu_enableGPS);
			gps.setVisible(true);
			//Maybe do a warning popup later?
		}
		//set start/stop text
		if (this.m_bRecording) {
			Toast.makeText(getBaseContext(), "I'm still recording", Toast.LENGTH_SHORT).show();
			start.setTitle(R.string.menuTitle_stopRecording);
		}
		if (!this.m_bRecording) {
			//just to be safe
			Toast.makeText(getBaseContext(), "Not recording no more", Toast.LENGTH_SHORT).show();
			start.setTitle(R.string.menuTitle_startRecording);
		}
		return true;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = this.getSupportMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		return true;
	}
	
	/** Called when activity you launched exits.
	 * @param requestCode The integer request code originally supplied to startActivityForResult(), allowing you to identify where it came from
	 * @param resultCode The integer result code returned by the child activity through its setResult()
	 * @param data An Intent, which can return result data to the caller
	*/
	@Override 
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		//check to make sure that GPS Enabled activity made a call
		if (requestCode == WalkAbout.ENABLE_GPS_REQUEST_CODE) {
			/* This is the support version of the invalidateOptionsMenu() call
			 * It declares that the options menu has changed, so should be recreated.  
			 * The onCreateOptionsMenu() method will be called the next time it needs 
			 * to be displayed
			 */
			supportInvalidateOptionsMenu();
		}
		
	}
	/**
	 * Switch the application so it is or isn't recording the user's path on the map.
	 *  
	 * @param bRecording
	 * 						Whether or not to start recording.
	 */
	private void setRecordingState(boolean bRecording) {
		if (bRecording) {
			this.m_bRecording = true;
			/*clear the list of LatLng points
			 * also clears the path line
			 */
			this.m_arrPathPoints.clear();
			//remove everything off of the map
			this.m_vwMap.clear();
	    	//initialize the path line 
			this.m_pathLine = m_vwMap.addPolyline(new PolylineOptions());
			//set the path line color
			this.m_pathLine.setColor(Color.GREEN);
			//initialize the list of points with the last known location
			Location location = this.m_locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			onLocationChanged(location);
			//tell the GPS Provider to start sending Location Updates
			Long timeChange = (long) 1; //1 ms
			Float distanceChange = (float) 0.3; // 1 foot
			this.m_locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, timeChange, distanceChange, this);
		}
		//if not recording
		if (!bRecording) {
			Toast.makeText(getBaseContext(), "Recording has stopped", Toast.LENGTH_SHORT).show();
			this.m_bRecording = false;
			/** Remove the location listener updates when this Activity isn't recording
			*   Can use "this" as Listener argument because the class implements the interface */
			this.m_locManager.removeUpdates(this);
		}
	}
	
	/**
	 * Writes important map data to a private application file.
	 */
	private void saveRecording() {
		// TODO
	}
	
	/**
	 * Retrieves specific map data that was previously written to a private application file
	 * and initializes both the lists and the map with the new data.
	 */
	private void loadRecording() {
		// TODO
	}
	/**
	 * Following methods are part of implementing LocationListener interface
	 * In order to register with the Provider, the WalkAbout Activity must implement
	 * the interface.  This interface provides a set of callback methods.
	 */
	@Override
	/**
	 * Provider will call this when the location changes.
	 * @param location The new location, as a Location object
	 */
	public void onLocationChanged(Location location) {
		Toast.makeText(getBaseContext(), "in onLocationChanged", Toast.LENGTH_SHORT).show();
		//get the latitude value
		double lat = location.getLatitude();
		//get longitude value
		double lon = location.getLongitude();

		//Google Maps stores location in LatLng object
		LatLng g_location = new LatLng(lat, lon);
		//add the point t m_arrPathPoints, which records location changes
		this.m_arrPathPoints.add(g_location);
		Log.w("WalkAbout", "Size of path points:  " + m_arrPathPoints.size());
		//make the map update its view by updating the camera with the location
/*		Float zoomLevel = (float) 14.0;
		this.m_vwMap.moveCamera(CameraUpdateFactory.newLatLngZoom(g_location, zoomLevel));*/
		Float zoomLevel = (float) 21.0;
		this.m_vwMap.animateCamera(CameraUpdateFactory.newLatLngZoom(g_location, zoomLevel));

		//only display toasts if recording
		if (this.m_bRecording) {
			String coordinates = "lat:  " + lat + " long:  " + lon;
			Toast.makeText(getBaseContext(), coordinates, Toast.LENGTH_SHORT).show();
		}
		
		if (this.m_bRecording) {
			//set the points for the line to draw
			this.m_pathLine.setPoints(m_arrPathPoints);
			/** Draw circles at each point of the map
			 *  center: The point on the map where Circle will be drawn
			 *  radius: radius (in meters) of circle
			 *  fillColor: color of circle's inside
			 *  strokeColor: color of circle's outside */
			this.m_vwMap.addCircle(new CircleOptions()
									.center(g_location)
									.radius(1)
									.fillColor(Color.CYAN)
									.strokeColor(Color.BLUE));	
		}		
	}
	/**
	 * Provider will call this when the Provider is disabled.
	 * @param provider The name of the location provider associated with this update
	 */
	@Override
	public void onProviderDisabled(String c) {
		// instruct the WalkAbout Activity to stop recording location changes
		setRecordingState(false);
	}
	/**
	 * Provider will call this when it is enabled.
	 * Don't need to override this for this implementation
	 * @param provider The name of the location provider associated with this update
	 */
	@Override
	public void onProviderEnabled(String provider) {
		
	}
	/**
	 * Provider will call this when the status of the Provider changes
	 * Don't need to override this for this implementation
	 * @param provider The name of the location provider associated with this update
	 * @param satus Can either be OUT_OF_SERVICE, TEMPORARILY_UNAVAIALBLE, AVAILABLE
	 * @param extras An optional Bundle which will contain provider specific status variables
	 */
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		
	}
}