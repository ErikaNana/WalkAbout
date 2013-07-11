package edu.calpoly.android.walkabout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import com.google.android.gms.maps.model.MarkerOptions;
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
	private boolean m_bRecording;

	/** The radius of a Circle drawn on the map, in meters. */
	private static final int CIRCLE_RADIUS = 1; //given 30
	
	/** Constants for the LocationManager. */
	private static final int MIN_TIME_CHANGE = 1000; //in milliseconds (given 3000)
	private static final int MIN_DISTANCE_CHANGE = 1; //in meters (given 3)
	
	
	/** Request codes for starting new Activities. */
	private static final int ENABLE_GPS_REQUEST_CODE = 1;
	private static final int PICTURE_REQUEST_CODE = 2;
	
	/** Zoom Level */
	private static final Float zoomLevel = (float) 21.0; //should be 14
	
	/** Timestamp of the picture */
	private static  String prettyDate;
	
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
    	//initialize picture points
    	this.m_arrPicturePoints = new ArrayList<Marker>();
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
			/* Acts as a toggle for either starting or stopping GPS. 
			 * While recording, item should display "Stop"
			 * While stopped, the item should display "Start"
			 */
			case R.id.menu_recording:{
				//if recording, recording now should stop
				if (this.m_bRecording) {
					setRecordingState(false);
					//update the menu to reflect the change
					supportInvalidateOptionsMenu();
					break;
				}
				//if not recording, activity should start
				if (!this.m_bRecording) {
					//if not recording, recording now should start
					setRecordingState(true);
					//update the menu to reflect the change
					supportInvalidateOptionsMenu();
					break;
				}
			}
			// save the current recorded path
			case R.id.menu_save:{
				//save the recording
				saveRecording();
				break;
			}
			//loads the last saved path
			case R.id.menu_load:{
				//set the recording state to false
				this.setRecordingState(false);
				//load the recording
				loadRecording();
				break;
			}
			//Launches a Camera Activity that allows you to take pictures
			case R.id.menu_takePicture:{
				//Launch the Camera
				Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				Uri pictureUri = getOutputMediaFileUri(WalkAbout.PICTURE_REQUEST_CODE);
				/* put the URI into the Intent 
				 * putExtra(name, value)
				 * EXTRA_OUTPUT: The name of the Intent-extra used to indicate a 
				 *               content resolver Uri to be used to store the 
				 *               requested image or video. (value is "output") */
				camera.putExtra(MediaStore.EXTRA_OUTPUT, pictureUri);
				/* Pass in the Request Code so that when we handle the result, we know
				 * which request generated by the result.  The result of any Activity that
				 * we launch gets processed by the same method and the Request Code mechanism
				 * lets us know which result is coming from which Activity*/
				startActivityForResult(camera, WalkAbout.PICTURE_REQUEST_CODE);
				break;
			}
			//launches the Device Settings Activity that allows you to enable the GPS provider
			case R.id.menu_enableGPS:{
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

			//set start/stop text
			if (this.m_bRecording) {
				start.setTitle(R.string.menuTitle_stopRecording);
			}
		}
		if (!enabled) {
			//disable start/stop
			start.setEnabled(false);
			//make Enable GPS visible
			MenuItem gps = menu.findItem(R.id.menu_enableGPS);
			gps.setVisible(true);
			//Maybe do a warning popup later?
		}
		
		if (!this.m_bRecording) {
			//if not recording, disablTake Picture menu item
			MenuItem takePicture = menu.findItem(R.id.menu_takePicture);
			takePicture.setEnabled(false);
			//just to be safe
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
		switch(requestCode) {
			case WalkAbout.ENABLE_GPS_REQUEST_CODE:{
				/* This is the support version of the invalidateOptionsMenu() call
				 * It declares that the options menu has changed, so should be recreated.  
				 * The onCreateOptionsMenu() method will be called the next time it needs 
				 * to be displayed
				 */
				supportInvalidateOptionsMenu();
			}
			case WalkAbout.PICTURE_REQUEST_CODE:{
				/* check resultCode.  This is the status of the Activity we're picking up
				 * after.  It left us a status code upon finishing or exiting */
				switch (resultCode) {
					case (Activity.RESULT_OK):{
						/* picture was taken correctly and saved as an image to the path we
						 * provided it */
						Toast.makeText(getBaseContext(), R.string.pictureSuccess, Toast.LENGTH_SHORT).show();
						//get the last known Location from the LocationManager
						Location location = this.m_locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
						//make a new Marker and add it to the map
						double lat = location.getLatitude();
						double lon = location.getLongitude();
						LatLng gLocation = new LatLng(lat,lon);
						//add a new Marker to m_arrPicturePoints with the location and title
						Marker marker = m_vwMap.addMarker(new MarkerOptions()
										.position(gLocation)
										.title(prettyDate));
						//add the marker to m_PicturePoints
						this.m_arrPicturePoints.add(marker);
						break;
					}
					case (Activity.RESULT_CANCELED):{
						/* Activity's operation was canceled and did not complete successfully */
						Toast.makeText(getBaseContext(), R.string.pictureFail, Toast.LENGTH_SHORT).show();
						break;
					}
				}
			}
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
			//clear the list of picture points
			this.m_arrPicturePoints.clear();
			//remove everything off of the map
			this.m_vwMap.clear();
	    	//initialize the path line 
			this.m_pathLine = m_vwMap.addPolyline(new PolylineOptions());
			//set the path line color
			this.m_pathLine.setColor(Color.GREEN);
			//initialize the list of points with the last known location
			Location location = this.m_locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			onLocationChanged(location);
			/* tell the GPS Provider to start sending Location Updates
			 * can use "this" because WalkAbout implements the listener */
			this.m_locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, WalkAbout.MIN_TIME_CHANGE, WalkAbout.MIN_DISTANCE_CHANGE, this);
		}
		//if not recording
		if (!bRecording) {
			this.m_bRecording = false;
			/** Remove the location listener updates when this Activity isn't recording
			*   Can use "this" as Listener argument because the class implements the interface */
			this.m_locManager.removeUpdates(this);
		}
	}
	
	/**
	 * Writes important map data to a private application file.
	 * It writes out the contents of m_arrPathPoints and m_arrPicturePoints
	 * to a private application file.
	 */
	private void saveRecording() {
		/** only ever create one file and it should be truncated/cleared each time 
		 * you write to it*/
		String pathPointsLine = "";
		for (LatLng point: this.m_arrPathPoints) {
			pathPointsLine = pathPointsLine + point.latitude + "," + point.longitude + ";";
		}
		String picturePointsLine = "";
		if (!this.m_arrPicturePoints.isEmpty()) {
			for (Marker point: this.m_arrPicturePoints) {
				//get location from the Marker
				LatLng location = point.getPosition();
				picturePointsLine = picturePointsLine + location.latitude+ ","+location.longitude
									+ "," + point.getTitle()+";";
			}
		}
		if (m_arrPathPoints.size() == 1) {
			//no path to save
			Toast.makeText(getBaseContext(), R.string.saveNoData, Toast.LENGTH_SHORT).show();
		}
		else {
			try {
				//using the string resource as the filename
				String name = this.getString(R.string.latLngPathFileName);
				//delete the old file if there is one
				this.deleteFile(name);				
				/* Opens a private file associated with this Context's application package for
				 * writing.
				 * Creates the file if it doesn't exist
				 * name: the name of the file to open, cannot contain path separators 
				 * mode: operating mode, MODE_PRIVATE is the default, where the created file
				 * can only be accessed by the calling application (or all applications sharing
				 * the same user ID*/

				FileOutputStream outputStream = this.openFileOutput(name, Context.MODE_PRIVATE);

				//make a new PrintWriter with the file as the output stream
				PrintWriter write = new PrintWriter(outputStream);
				//write the lines to the file
				write.println(pathPointsLine);
				if (!picturePointsLine.isEmpty()) {
					write.print(picturePointsLine);
				}
				write.close();
				Toast.makeText(getBaseContext(), R.string.saveSuccess, Toast.LENGTH_SHORT).show();
			} catch (FileNotFoundException e) {
				Toast.makeText(getBaseContext(), R.string.saveFailed, Toast.LENGTH_SHORT).show();
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Retrieves specific map data that was previously written to a private application file
	 * and initializes both the lists and the map with the new data.
	 */
	private void loadRecording() {
		ArrayList<String>fileLines = new ArrayList<String>();
		//read the file
		String name = this.getString(R.string.latLngPathFileName);
		try {
			//open the file
			FileInputStream stream = this.openFileInput(name);
			/* An InputStreamReader reads bytes and decodes them into characters */
			InputStreamReader streamReader = new InputStreamReader(stream);
			/* From the documentation,  for top efficiency, we should wrap the InputStreamReader
			 * within a BufferedReader*/
			BufferedReader buffReader = new BufferedReader(streamReader);
			//read the contents of the file

			try {
				String readString = buffReader.readLine ();
				fileLines.add(readString);
				while (readString != null) {
					readString = buffReader.readLine();
					if (readString != null) {
						fileLines.add(readString);
					}
				}
				this.repopTheMap(fileLines);
				Toast.makeText(getBaseContext(), R.string.loadSuccess, Toast.LENGTH_LONG).show();
				
			} catch (IOException e) {
				Log.w("WalkAbout", "IOException");
				//if there is nothing to load
				Toast.makeText(getBaseContext(), R.string.loadFailed, Toast.LENGTH_LONG).show();
				e.printStackTrace();
			}			
		} catch (FileNotFoundException e) {
			Log.w("WalkAbout", "FileNotFoundException");
			Toast.makeText(getBaseContext(), R.string.loadNoFile, Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
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
		//get the latitude value
		if (location != null) {
			double lat = location.getLatitude();
			//get longitude value
			double lon = location.getLongitude();

			//Google Maps stores location in LatLng object
			LatLng g_location = new LatLng(lat, lon);
			//add the point t m_arrPathPoints, which records location changes
			this.m_arrPathPoints.add(g_location);
			//make the map update its view by updating the camera with the location
			this.m_vwMap.animateCamera(CameraUpdateFactory.newLatLngZoom(g_location, zoomLevel));		
			
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
										.radius(WalkAbout.CIRCLE_RADIUS)
										.fillColor(Color.CYAN)
										.strokeColor(Color.BLUE));	
				//allow Take Picture menu item to be enabled
			}	
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
		/* File that will return.
		 * Contains a String that represents an absolute image destination path for the 
		 * picture we take using the Camera.  The absolute path contains both the storage
		 * directory to store the image and the image name itself
		 */
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
	/**
	 * Helper method that creates the media file with the correct name
	 * @param fileType is more of an intention than a type
	 */
	private static File getOutputMediaFile(int fileType){
		File mediaFile = null;
		//get the base directory where the file gets stored
		File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		/* construct the file space using the specified directory and name
		 * this is where the pictures will be stored */
		File mediaStorageDir = new File(dir,"WalkAbout");
		//check to see if there is not a file at the storage directory path contained in mediaStorageDir
		if (!mediaStorageDir.exists()) {
			/* check to make sure that the directory was created correctly
			 * mkdirs returns false if the directory already exists
			 */
			if (!mediaStorageDir.mkdirs()) {
				Log.w("WalkAbout", "directory creation process failed");
				return null;			}
			}
		else {
			//create the file
			Date date = new Date(System.currentTimeMillis());
			//format the date 
			DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT);
			String timestamp = dateFormat.format(date);
			prettyDate = timestamp;
			//remove the comma from the name
			timestamp = timestamp.replace(",", "");
			//take out the white spaces and replace : with _ (regex)
			timestamp = timestamp.replaceAll("\\s|:|,", "_");
			//check if the type passed in matches PICTURE_REQUEST_CODE (jpeg)
			if (fileType == WalkAbout.PICTURE_REQUEST_CODE) {
				//create a new file with the complete path name
				mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_"
									+ timestamp + ".jpg");
			}
		}
		return mediaFile;
	}
	/**
	 * Helper method that converts the created media file into part of a recognizable URI
	 * @param filetype Represents JPEG, although it is more of an intention than a type
	 * @return The part of an URI that represents the created media file
	 */
	private static Uri getOutputMediaFileUri(int filetype) {
		File outputFile = getOutputMediaFile(filetype);
		return Uri.fromFile(outputFile);
	}
	
	/**
	 * Helper method that repopulates the map given the lines from the file of saved data
	 * Also reinitializes m_arrPathPoints and m_arrPicturePoints
	 */
	private void repopTheMap (ArrayList<String> arguments) {
		String pathPoints;
		String picPoints;
		//clear the map of everything
		m_arrPathPoints.clear();
		m_arrPicturePoints.clear();
		m_vwMap.clear();
		
		//add a new polyline to the map since calling clear removes the current one
		m_pathLine = m_vwMap.addPolyline(new PolylineOptions());
		//set the path line color
		m_pathLine.setColor(Color.GREEN);
		//get information for path points from the saved file
		pathPoints = arguments.get(0);
		//reinitialize m_arrPathPoints with the points from the file
		m_arrPathPoints = this.convertPathPointsString(pathPoints);
		//draw the circles for path points
		m_pathLine.setPoints(m_arrPathPoints);
		for (LatLng point: m_arrPathPoints) {
			m_vwMap.addCircle(new CircleOptions()
			.center(point)
			.radius(WalkAbout.CIRCLE_RADIUS)
			.fillColor(Color.CYAN)
			.strokeColor(Color.BLUE));
		}

		if (arguments.size() > 1) {
			//there are also pic points to worry about
			picPoints = arguments.get(1);
			ArrayList <Marker> markers = this.convertPicPointsString(picPoints);
			//reinitialize the markers list
			this.m_arrPicturePoints = markers;
		}
	}
	/**
	 * Helper method that takes in the string from the file saved, and converts them into an
	 * LatLng points
	 * @param pathPoints
	 * @return ArrayList of LatLng points from the saved file
	 */
	private ArrayList<LatLng> convertPathPointsString (String pathPoints) {
		Scanner s = new Scanner(pathPoints);
		ArrayList<String> coordinates = new ArrayList<String>();
		ArrayList<LatLng>gPoints = new ArrayList<LatLng>();
		//break down the string into separate points
		s.useDelimiter(";|,");

		while(s.hasNext()) {
			coordinates.add(s.next());
		}
		s.close();
		//create LatLng points and add them to the array given the coordinates
		Double lat = 0.0;
		Double lon = 0.0;
		for (String coord: coordinates) {
			if (lat == 0.0) {
				lat = Double.valueOf(coord);
				continue;
			}
			if (lon == 0.0) {
				lon = Double.valueOf(coord);
			}
			if (lat != 0.0 && lon != 0.0) {
				LatLng point = new LatLng(lat, lon);
				gPoints.add(point);
				lat = 0.0;
				lon = 0.0;
			}
		}
		return gPoints;
		
	}
	 
	/**
	 * Helper method to repopulate Markers on the map from the saved file
	 * @param picPoints Saved picture Marker information
	 * @return An ArrayList of Markers
	 */
	private ArrayList<Marker> convertPicPointsString (String picPoints){
		Scanner s = new Scanner(picPoints);
		ArrayList<Marker> markers = new ArrayList <Marker> ();
		ArrayList<String> coordinates = new ArrayList<String>();
		s.useDelimiter(";");
		//break it down into a marker per line
		while(s.hasNext()) {
			coordinates.add(s.next());
		}		
		for (String coordinate: coordinates) {
			s = new Scanner(coordinate);
			//go through the list one by one
			while (s.hasNext()) {
				ArrayList <String> temp = new ArrayList <String>();
				s.useDelimiter(",");
				while(s.hasNext()) {
					temp.add(s.next());
				}
				//go through array and make it correct
				String title = temp.get(2) + "," + temp.get(3);
				//clean up the array list
				temp.remove(3);
				temp.remove(2);
				temp.add(2, title);
				
				//assign the lat, long, title and add it to list of markers
				Double lat = Double.valueOf(temp.get(0));
				Double lon = Double.valueOf(temp.get(1));
				String markerTitle = temp.get(2);
				LatLng location = new LatLng(lat, lon);
				
				//add the marker to the map
				Marker marker = m_vwMap.addMarker(new MarkerOptions()
				.position(location)
				.title(markerTitle));
				//add the marker to the list
				markers.add(marker);
			}
		}
		s.close();
		return markers;
	}
}