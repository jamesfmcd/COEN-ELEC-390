/*
 * ELEC390 and COEN390: TI SensorTag Library for Android
 * Example application: Weather Station
 * Author: Marc-Alexandre Chan <marcalexc@arenthil.net>
 * Institution: Concordia University
 */
package ca.concordia.sensortag.weather;

import java.text.DecimalFormat;
import java.lang.Math;


import ti.android.ble.sensortag.Sensor;
import ca.concordia.sensortag.SensorTagListener;
import ca.concordia.sensortag.SensorTagLoggerListener;
import ca.concordia.sensortag.SensorTagManager;
import ca.concordia.sensortag.SensorTagManager.ErrorType;
import ca.concordia.sensortag.SensorTagManager.StatusType;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
//import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main WeatherStation activity. This class controls the main activity ("window") for the Weather
 * Station, that shows the temperature, humidity and pressure from the SensorTag sensors. It is
 * responsible for managing a connection to the SensorTag and receiving sensor measurements from it
 * (via the {@link SensorTagManager} class), as well as converting it to the right unit and showing
 * it on the screen.
 */
public class WeatherStationActivity extends Activity {

	static public final String TAG = "WeatherSt"; // Tag for Android's logcat
	static protected final int UPDATE_PERIOD_MS = 1000; // How often measurements should be taken

	// Define formatters for converting the sensor measurement into a string to show on the screen
	private final static DecimalFormat tempFormat = new DecimalFormat("###0.0;-##0.0");
	private final static DecimalFormat humiFormat = new DecimalFormat("##0.0");
	private final static DecimalFormat baroFormat = new DecimalFormat("#00.00");

	// GUI elements - TextView is the Java class for text-box elements on the screen, and
	// Switch is the Java class for the on-off switch element
	private TextView mTemperatureView;
	private TextView mTemperatureUnitView;
	private TextView mBarometerView;
	private TextView mBarometerUnitView;
	private TextView mHumidityView;
	@SuppressWarnings("unused")
	private TextView mHumidityUnitView;

	private Switch mTemperatureUnitSwitch;
	private Switch mBarometerUnitSwitch;

	// These are "cached" copies of the last value received from the SensorTag.
	// We save this so that, if the user changes the unit, we can immediately update the GUI
	// (if we didn't have the last value, we'd have to wait until a new value is received to
	// calculate and show it in the correct unit).
	private double mLastTemperature = Double.NaN;
	@SuppressWarnings("unused")
	private double mLastHumidity = Double.NaN;
	private double mLastPressure = Double.NaN;

	// Unit setting - these values are used to determine which unit to show for temp/pressure.
	// They are changed when the user clicks the switches to change the unit.
	private boolean mIsTempFahrenheit = false;

	public enum BarometerUnit { KILOPASCAL, MILLIBAR, INCH_HG };
	private BarometerUnit mBaroUnit = BarometerUnit.KILOPASCAL;

	// Bluetooth communication with the SensorTag
	private BluetoothDevice mBtDevice;
	private SensorTagManager mStManager;
	private SensorTagListener mStListener;

	/**
	 * Called when the Activity is created. Sets up the GUI, checks whether a Bluetooth Device was
	 * sent from the DeviceConnectActivity via the Intent sent, and initialises the Bluetooth
	 * communication with the SensorTag.
	 * 
	 * See the Minimal example for more detailed explanations of this code. A little note: except
	 * for the enable sensor logic, the comments you find in this section of the code are similar
	 * to what you would expect real-life comments to look like: they don't explain the code itself,
	 * but the INTENTION or INTERPRETATION of the code when needed, or other details that aren't
	 * obvious for a programmer reading the code.
	 * 
	 * CHANGE ME: if you want to modify this app to show different data from different sensors,
	 * you need to add/remove/change the mStManager.setEnable calls in order to enable only the
	 * sensors you want to use.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	String GetHumidex (double temp, double hum)
	{
		double gamma = Math.log1p(hum/100)  + (17.67 * (temp)) / (243.5+temp) ;
		
		double Dp = 243.5 * gamma  / ( 17.67 - gamma ) + 273.15;
		
		double humidex =  temp + 0.5555 * ( 6.11 * Math.exp(5417.7130 * (1/273.16 - 1/Dp)) -10 );
		
		if (mIsTempFahrenheit==true)
		{
			humidex=convertTemperatureUnit(humidex);
		}
		
		final String humText = tempFormat.format(humidex);
		return humText;
	}
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_weather_station);

		// Get the Bluetooth device selected by the user - should be set by DeviceSelectActivity
		Intent receivedIntent = getIntent();
		mBtDevice = (BluetoothDevice) receivedIntent
				.getParcelableExtra(DeviceSelectActivity.EXTRA_DEVICE);

		// If we didn't get a device, we can't do anything! Warn the user, log and exit.
		if (mBtDevice == null) {
			Log.e(TAG, "No BluetoothDevice extra [" + DeviceSelectActivity.EXTRA_DEVICE
					+ "] provided in Intent.");
			Toast.makeText(this, "No Bluetooth Device selected", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		// Prepare the SensorTag
		mStManager = new SensorTagManager(this, mBtDevice);
		mStListener = new ManagerListener();
		mStManager.addListener(mStListener);

		mStManager.initServices();
		if (!mStManager.isServicesReady()) { // initServices failed or took too long
			Log.e(TAG, "Discover failed - exiting");
			finish();
			return;
		}

		/* This section is different from the minimal example, and so warrants explanations.
		 * 
		 * There are two things going on in this code:
		 * 		1) We check whether or not enableSensor() worked.
		 * 		2) We try to set the update period (how often measurements are sent from the sensor).
		 *
		 * 1. mStManager.enableSensor() returns true if enabling the sensor succeeded, but if the
		 * SensorTag takes too long to respond or returns an error it returns false. If you want to
		 * use a sensor, it makes sense that you want to know whether enabling the sensor worked or
		 * not: if it didn't work but you NEED it, you can show the user an error and exit; if it's
		 * not vital, you probably want to tell the user something broke anyway and then keep going.
		 * 
		 * In this case, what we do is use a boolean variable "res" to store the result of
		 * enableSensor(): for every enableSensor() call, we take the logical AND of the new
		 * enableSensor() result and the old "res" value. That way, if ANY of the enableSensor()
		 * calls fail, "res" will be false and then at the end we know one of the sensors failed:
		 * therefore, we show an error to the user and exit.
		 * 
		 * 2. Some of the sensors are capable of having the update period (how often a measurement
		 * is taken from the sensor) changed when you enable the sensor. This depends on the
		 * firmware version of the SensorTag, therefore the SensorTagManager has an
		 * isPeriodSupported(Sensor) that checks whether or not your specific SensorTag supports
		 * setting the period of the Sensor specified.
		 * 
		 * If the period is supported, we set it to UPDATE_PERIOD_MS; if not, it's not vital to
		 * the app, so we just enable the sensor without setting a period value (in this case, a
		 * default value is used: usually 1000ms or 2000ms, depending on the sensor).
		 */
		boolean res = true;
		if (mStManager.isPeriodSupported(Sensor.IR_TEMPERATURE))
			res = res && mStManager.enableSensor(Sensor.IR_TEMPERATURE, UPDATE_PERIOD_MS);
		else
			res = res && mStManager.enableSensor(Sensor.IR_TEMPERATURE);

		if (mStManager.isPeriodSupported(Sensor.BAROMETER))
			res = res && mStManager.enableSensor(Sensor.BAROMETER, UPDATE_PERIOD_MS);
		else
			res = res && mStManager.enableSensor(Sensor.BAROMETER);

		if (mStManager.isPeriodSupported(Sensor.HUMIDITY))
			res = res && mStManager.enableSensor(Sensor.HUMIDITY, UPDATE_PERIOD_MS);
		else
			res = res && mStManager.enableSensor(Sensor.HUMIDITY);

		// If any of the enableSensor() calls failed, show/log an error and exit.
		if (!res) {
			Log.e(TAG, "Sensor configuration failed - exiting");
			Toast.makeText(this, "Sensor configuration failed - exiting", Toast.LENGTH_LONG).show();
			finish();
		}

		// Get references to the GUI text box objects
		mTemperatureView = (TextView) findViewById(R.id.value_temp);
		mTemperatureUnitView = (TextView) findViewById(R.id.unit_temp);
		mBarometerView = (TextView) findViewById(R.id.value_baro);
		mBarometerUnitView = (TextView) findViewById(R.id.unit_baro);
		mHumidityView = (TextView) findViewById(R.id.value_humi);
		mHumidityUnitView = (TextView) findViewById(R.id.unit_humi);

		// Set up the GUI switches for displayed measurement units
		mTemperatureUnitSwitch = (Switch) findViewById(R.id.temp_unit_switch);
		mBarometerUnitSwitch = (Switch) findViewById(R.id.baro_unit_switch);

		/* The code below shows you how you can capture a "click" event, in order to run some code
		 * whenever the user clicks on a switch. The button and other GUI elements follow a similar
		 * principle; you will see an example of a button in the DataSample example.
		 * 
		 * Switches use the idea of a "Listener", in a similar way to the SensorTagManager, as you
		 * saw with ManagerListener in the Minimal example and the one in this example. In fact,
		 * this idea of an object (the switch) having a "Listener" (or "Observer") is VERY commonly
		 * used in Android.
		 * 
		 * The first thing that happens is that we define and instantiate an anonymous class that
		 * extends OnCheckedChangeListener. That's the
		 * 
		 * 		new OnCheckedChangedListener () {
		 * 			// ...
		 * 		};
		 * 
		 * part. The onCheckedChanged() method is what's called whenever the user clicks on the
		 * Switch and changes its state on/off. It is passed two parameters: the buttonView
		 * parameter is just a reference to the Button object that was clicked (so you can do
		 * operations on it, like change the text on the button, disable it, even undo the user's
		 * click if for some reason the user isn't allowed to change the state according to your
		 * app). The second parameter, isChecked, tells you whether the button was changed to the
		 * on or off position.
		 * 
		 * The second thing that happens is that we take this new OnCheckedChangeListener-extended
		 * object, and we pass it to Switch.setOnCheckedChangeListener for the switch we want to
		 * apply it to. This is the same idea as mStManager.addListener().
		 * 
		 * After we set the listener, whenever the switch is clicked, Android will call
		 * onCheckedChanged() in the listener that you set on the switch.
		 */
		mTemperatureUnitSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			/**
			 * Called when the mTemperatureUnitSwitch switch is clicked, causing the state to
			 * change. Saves the Celsius/Fahrenheit setting and updates the display to show the
			 * desired unit.
			 * 
			 * @param buttonView The mTemperatureUnitSwitch switch object
			 * @param isChecked  Whether the switch is in the checked (Fahrenheit) or unchecked
			 * (Celsius) state.
			 */
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// Save the unit so we know what to use when a new measurement comes in.
				mIsTempFahrenheit = isChecked;
				
				// And update the measurements that are shown on the screen right now to use the
				// changed unit.
				updateDisplayedUnits();
			}

		});

		mBarometerUnitSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			/**
			 * Called when the mBarometerUnitSwitch switch is clicked, causing the state to
			 * change. Saves the inHg/kPa unit setting and updates the display to show the
			 * desired unit.
			 * 
			 * @param buttonView The mBarometerUnitSwitch switch object
			 * @param isChecked  Whether the switch is in the checked (inHg) or unchecked
			 * (kPa) state.
			 */
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				// Save the unit so we know what to use when a new measurement comes in.
				mBaroUnit = isChecked ? BarometerUnit.INCH_HG : BarometerUnit.KILOPASCAL;
				
				// And update the measurements that are shown on the screen right now to use the
				// changed unit.
				updateDisplayedUnits();
			}

		});

		// Initial values for the measurements on the GUI: before the SensorTag is all ready to go
		// and has sent its first sensor values, we want to show dashes as a placeholder.
		mTemperatureView.setText("--.-");
		mBarometerView.setText("---.-");
		mHumidityView.setText("--.-");
	}

	/**
	 * Called by Android when the Activity is started again. This is shown just for completion:
	 * since there is no code in onStart(), it does not need to be overridden here.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onStart() {
		super.onStart();
	}

	/**
	 * Called by Android when the Activity comes back into the foreground. When called, enables
	 * processing sensor measurements (which are received by {@link ManagerListener}).
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onResume() {
		super.onResume();
		if (mStManager != null) mStManager.enableUpdates();
	}

	/**
	 * Called by Android when the Activity goes out of focus (for example, if another Application
	 * pops up on top of it and partially obscures it). When called, this method disables processing
	 * sensor measurements but does not close the Bluetooth connection or disable the sensors,
	 * allowing the application to restore quickly when it comes into the foreground again.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onPause() {
		super.onPause();
		if (mStManager != null) mStManager.disableUpdates();
	}

	/**
	 * Called by Android when the Activity is stopped, e.g. when another Activity is opened full-
	 * screen and this Activity goes into the background. This method is always called after
	 * onPause() and differs from that method in that onStop() will not be called if an Activity
	 * pops up on top of the Activity, not full screen, while onPause() will.
	 * 
	 * This is shown just for completion: since there is no code in onStart(), it does not need to
	 * be overridden here.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onStop() {
		super.onStop();
	}

	/**
	 * Called when the Activity is destroyed by Android. Cleans up the Bluetooth connection to the
	 * SensorTag.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mStManager != null) mStManager.close();
	}

	/**
	 * Updates the units shown on the screen to match the current unit settings, and recalculates
	 * the measurement values in the correct unit. This method must be called from the main (UI)
	 * thread, as it modifies the GUI directly.
	 */
	private void updateDisplayedUnits() {
		// Update the unit text for the temperature (desired unit = mIsTempFahrenheit)
		if (mIsTempFahrenheit)
			mTemperatureUnitView.setText(getString(R.string.temperature_f_unit));
		else
			mTemperatureUnitView.setText(getString(R.string.temperature_c_unit));

		// Recalculate the temperature in the desired unit (desired unit = mIsTempFahrenheit)
		double displayTemp = convertTemperatureUnit(mLastTemperature);
		// Take the calculated temperature and show it on the GUI
		mTemperatureView.setText(tempFormat.format(displayTemp));

		// Update the unit text for the atmospheric pressure (Desired unit = mBaroUnit)
		switch (mBaroUnit) {
		case INCH_HG:
			mBarometerUnitView.setText(getString(R.string.barometer_inhg_unit));
			break;
		case KILOPASCAL:
			mBarometerUnitView.setText(getString(R.string.barometer_kpa_unit));
			break;
		case MILLIBAR:
			mBarometerUnitView.setText(getString(R.string.barometer_mbar_unit));
			break;
		default:
			break;
		}

		// Recalculate the barometer measurement in the desired unit
		double displayBaro = convertBarometerUnit(mLastPressure);
		// Take the calculated temperature and show it on the GUI
		mBarometerView.setText(baroFormat.format(displayBaro));
	}

	/**
	 * Convert the temperature to the unit currently set by the loaded settings (mIsTempFahrenheit
	 * member variable).
	 * 
	 * @param celsius
	 *            The temperature value in Celsius.
	 * @return The converted temperature value.
	 */
	private double convertTemperatureUnit(double celsius) {
		return mIsTempFahrenheit ? (1.8 * celsius + 32) : celsius;
	}

	/**
	 * Convert the barometric pressure to the unit currently set by the loaded settings (mBaroUnit
	 * member variable).
	 * 
	 * @param kpa
	 *            The value in kilopascals.
	 * @return The converted barometric pressure value.
	 */
	private double convertBarometerUnit(double kpa) {
		double result;
		switch (mBaroUnit) {
		case INCH_HG:
			result = 0.295299830714 * kpa;
			break;
		case MILLIBAR:
			result = 10 * kpa;
			break;
		case KILOPASCAL: // fallthrough
		default:
			result = kpa;
			break;
		}
		return result;
	}

	/**
	 * Handles events from the SensorTagManager: SensorTag status updates, sensor measurements, etc.
	 */
	public class ManagerListener extends SensorTagLoggerListener implements SensorTagListener {

		/*
		 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		 * Remember: if you want to use other sensors here, you have to enable them in onCreate and
		 * then add the onUpdate method corresponding to that sensor! See the SensorTagListener
		 * interface for the list of all the onUpdate methods.
		 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		 */

		/**
		 * Called on receiving a new ambient temperature measurement. Displays the new value.
		 * 
		 * @see ca.concordia.sensortag.SensorTagLoggerListener#onUpdateAmbientTemperature(ca.concordia.sensortag.SensorTagManager,
		 *      double)
		 */
		@Override
		public void onUpdateAmbientTemperature(SensorTagManager mgr, double temp) {
			super.onUpdateAmbientTemperature(mgr, temp);

			// Save this measurement value. If the user changes the desired unit between two
			// measurements, we  retrieve this measurement and convert it to the new unit desired
			// by the user.
			mLastTemperature = temp;

			// Convert the measurement to the unit desired by the user.
			double displayTemp = convertTemperatureUnit(temp);

			// Format the temperature (double) into a string, with one digit after the decimal
			// point (this format is defined by tempFormat: see declaration at top of file).
			final String tempText = tempFormat.format(displayTemp);

			// The UI elements can only be modified on the main ("UI") thread:
			// We use Activity's runOnUiThread() method to schedule this update on the main thread
			// See the Minimal example for more details.
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					// Change the text in the temperature TextView to the new value
					mTemperatureView.setText(tempText);
				}

			});

		}

		/**
		 * Called on receiving a new pressure measurement. Displays the new value.
		 * 
		 * @see ca.concordia.sensortag.SensorTagLoggerListener#onUpdateBarometer(ca.concordia.sensortag.SensorTagManager,
		 *      double, double)
		 */
		@Override
		public void onUpdateBarometer(SensorTagManager mgr, double pressure, double height) {
			super.onUpdateBarometer(mgr, pressure, height);

			// This is the same idea as onUpdateAmbientTemperature() above.
			mLastPressure = pressure;
			double displayPressure = convertBarometerUnit(pressure);
			final String baroText = baroFormat.format(displayPressure);
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					mBarometerView.setText(baroText);
				}

			});

		}

		/**
		 * Called on receiving a new humidity measurement. Displays the new value.
		 * 
		 * @see ca.concordia.sensortag.SensorTagLoggerListener#onUpdateHumidity(ca.concordia.sensortag.SensorTagManager,
		 *      double)
		 */
		@Override
		public void onUpdateHumidity(SensorTagManager mgr, double rh) {
			super.onUpdateHumidity(mgr, rh);

			// This is the same idea as onUpdateAmbientTemperature() above.
			mLastHumidity = rh;
			final String humiText = humiFormat.format(rh);
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					mHumidityView.setText(humiText);
				}

			});
		}

		/**
		 * Called on receiving a SensorTag-related error. Displays a Toast showing a message to the
		 * user.
		 * 
		 * @todo Reserve an area on the GUI instead so that errors are displayed statically?
		 * @see ca.concordia.sensortag.SensorTagBaseListener#onError(ca.concordia.sensortag.SensorTagManager,
		 *      ca.concordia.sensortag.SensorTagManager .ErrorType, java.lang.String)
		 */
		@Override
		public void onError(SensorTagManager mgr, ErrorType type, String msg) {
			super.onError(mgr, type, msg);
			
			// This was not in the Minimal example, but it could have been added there (if it were
			// not, you know, a minimal example!). This onError() method is called by
			// SensorTagManager if an error happens related to the SensorTag; it is not attached to
			// a sensor measurement, unlike all the other methods.
			//
			// This lets an application take some action when an error happens. In this case,
			// we just pick a "user-friendly" string to show depending on the error type (the
			// "type" parameter), and then show that on the screen as a Toast.
			//
			// Quick note: a Toast is a small text box near the bottom of the screen that disappears
			// on its own after a short amount of time.
			String text = null;
			switch (type) {
			case GATT_REQUEST_FAILED:
				text = "Error: Request failed: " + msg;
				break;
			case GATT_UNKNOWN_MESSAGE:
				text = "Error: Unknown GATT message (Programmer error): " + msg;
				break;
			case SENSOR_CONFIG_FAILED:
				text = "Error: Failed to configure sensor: " + msg;
				break;
			case SERVICE_DISCOVERY_FAILED:
				text = "Error: Failed to discover sensors: " + msg;
				break;
			case UNDEFINED:
				text = "Error: Unknown error: " + msg;
				break;
			default:
				break;
			}
			if (text != null)
				Toast.makeText(WeatherStationActivity.this, text, Toast.LENGTH_LONG).show();
		}

		/**
		 * Called on receiver a SensorTag-related status message. Displays a Toast showing a message
		 * to the user, if relevant.
		 * 
		 * @see ca.concordia.sensortag.SensorTagBaseListener#onStatus(ca.concordia.sensortag.SensorTagManager,
		 *      ca.concordia.sensortag.SensorTagManager .StatusType, java.lang.String)
		 */
		@Override
		public void onStatus(SensorTagManager mgr, StatusType type, String msg) {
			super.onStatus(mgr, type, msg);
			
			// This is similar to onError() above, and we do the same thing: we pick a user-friendly
			// message and (if we have one we want to show) we show the message in a Toast.
			String text = null;
			switch (type) {
			case SERVICE_DISCOVERY_COMPLETED:
				// This message doesn't really need to be announced to the user.
				break;
			case SERVICE_DISCOVERY_STARTED:
				text = "Preparing SensorTag";
				break;
			case UNDEFINED:
				text = "Unknown status";
				break;
			default: // ignore other cases
				break;

			}
			if (text != null)
				Toast.makeText(WeatherStationActivity.this, text, Toast.LENGTH_SHORT).show();
		}

	}

}
