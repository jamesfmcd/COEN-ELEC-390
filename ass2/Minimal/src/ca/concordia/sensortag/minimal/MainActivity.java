package ca.concordia.sensortag.minimal;

import java.text.DecimalFormat;

import ti.android.ble.sensortag.Sensor;
import ti.android.util.Point3D;
import ca.concordia.sensortag.SensorTagListener;
import ca.concordia.sensortag.SensorTagLoggerListener;
import ca.concordia.sensortag.SensorTagManager;
import ca.concordia.sensortag.minimal.DeviceSelectActivity;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is an absolute minimum example of how you can use the SensorTagLib in order to facilitate
 * the communication of an Android app with the TI SensorTag over Bluetooth LE. This example does
 * nothing more than show the temperature on screen using the default generated project created by
 * Eclipse ADT.
 * 
 * You can use this example as a template for other SensorTag-based apps you may want to develop.
 * 
 * Look for the words "CHANGE ME" in all caps for hints about how you can customise this Activity
 * and learn more about Android Activities and the SensorTag library.
 */
public class MainActivity extends Activity {
	static public final String TAG = "SensorTagMin"; // Tag for Android's logcat

	// SensorTag communication objects
	private BluetoothDevice mBtDevice;
	private SensorTagManager mStManager;
	private SensorTagListener mStListener;

	// Reference to the GUI elements that we want to manipulate programmatically
	private TextView mTextView;
	
	// for humidity
	private TextView humidityTextview;

	// Constant, used to format float/double data (two digits after decimal) for GUI display
	private final static DecimalFormat tempFormat = new DecimalFormat("##0.00;-##0.00");

	
	//my code for humidity:
	private final static DecimalFormat humidityFormat = new DecimalFormat("##0.00;-##0.00");

	
	/**
	 * Called by Android when the Activity is first created. This sets up the GUI for the Activity,
	 * sets up the variables to control the GUI elements in the program, and prepares the Bluetooth
	 * communication to the SensorTag.
	 * 
	 * CHANGE ME: You can change the sensors to enable here. When enabled, the SensorTag will
	 * periodically send data from that sensor back to the SensorTag; you can then capture it using
	 * a SensorTagListener class (see the {@link ManagerListener} for an example of a
	 * SensorTagListener subclass and more explanations).
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		/* ***
		 * DO NOT CHANGE: COMMON INITIALISATION - This is the same for every app using the
		 * SensorTag. ***
		 */

		// REQUIRED: Calls the onCreate that's part of the Activity base class. This lets Android do
		// anything it needs to do behind the scenes to make the new activity.
		super.onCreate(savedInstanceState);

		// Sets what to show on the screen: R.layout.activity_main corresponds to
		// res/layout/activity_main.xml, which defines a GUI layout. (In this case it's a very
		// simple layout---just one text box!)
		setContentView(R.layout.activity_main);

		// An Intent is sent to tell Android, "start a new Activity" (or other things like
		// Services), and has information about which Activity to start. In this case, the
		// DeviceSelectActivity shows up on the screen first to let the user choose a SensorTag
		// device to connect to, and then when the user has selected one, it sends an Intent to
		// start the MainActivity (the one you're reading right now!).
		//
		// In an Activity, getIntent() lets you get the Intent that started this Activity.
		//
		// Sometimes the Intent has extra information in it that we can read to find out why our
		// Activity was called, or what our Activity is supposed to do. In this case, we have the
		// SensorTag the user selected on the previous screen which is sent as an "Extra" (as a
		// BluetoothDevice object). Extract and store it.
		Intent receivedIntent = getIntent();
		mBtDevice = (BluetoothDevice) receivedIntent
				.getParcelableExtra(DeviceSelectActivity.EXTRA_DEVICE);

		// If we didn't get a SensorTag device, we can't do anything! Warn the user, log and exit
		// (this goes back to the last open activity due to how Android Tasks/Back Stacks work).
		if (mBtDevice == null) {
			Log.e(TAG, "No BluetoothDevice extra [" + DeviceSelectActivity.EXTRA_DEVICE
					+ "] provided in Intent.");
			Toast.makeText(this, "No Bluetooth Device selected", Toast.LENGTH_SHORT).show();

			// signal to close the Activity: queues up the methods onPause(), onStop(), onDestroy()
			finish();

			// Stop running onCreate. finish() only queues up the "stop activity" action, it
			// doesn't immediately abort onCreate() to stop the Activity!
			return;
		}

		// Now we can start preparing the SensorTag!

		// First we need to create a SensorTagManager instance. This class takes care of the details
		// of communicating with the SensorTag for you: enabling sensors, sending new measurements
		// to your app, etc.
		//
		// SensorTagManager needs to know the "context" (in this case the Activity that's creating
		// it) and the BluetoothDevice (the SensorTag) it needs to communicate with, which are
		// passed as arguments to the constructor.
		mStManager = new SensorTagManager(this, mBtDevice);

		// Now we instantiate our custom Listener, which is where SensorTagManager sends sensor
		// measurements (and some status information) to us. ManagerListener is defined later in
		// this file.
		mStListener = new ManagerListener();

		// We have to tell SensorTagManager about our listener, so that SensorTagManager is able to
		// send sensor measurements to that listener.
		mStManager.addListener(mStListener);

		// This tells the SensorTagManager to discover services (get a list of all the services,
		// i.e. the different sensors, that the SensorTag supports). This method call can take up to
		// 15 seconds (otherwise the discovery times out and will give an error).
		//
		// Note that during this time, the app freezes. ADVANCED EXERCISE: Allow initServices()
		// and sensor enabling to occur in a way that doesn't freeze the app up, and show a message
		// to the user while these SensorTag setup steps are happening. Hint: Threading, main GUI
		// thread versus background threads.
		mStManager.initServices();
		if (!mStManager.isServicesReady()) {
			// if initServices failed or took too long, log an error (in LogCat) and exit
			Log.e(TAG, "Discover failed - exiting");
			finish();
			return;
		}

		/*
		 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		 * CHANGE ME: SENSOR INITIALISATION - Add more sensors here!
		 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		 */

		// Enable the IR Temperature Sensor. This sensor has two functions: it returns the ambient
		// temperature (more precisely, the temperature of the sensor IC---the small silver square
		// you can see in the hole on the front of the SensorTag). The second function
		// is to return the temperature of an object in front of it (using infrared light).
		//
		// In this case we will only enable the IR Temperature Sensor and only use the ambient
		// temperature to demonstrate the SensorTag.
		//
		// Note that this method will wait up to 0.5 seconds for the SensorTag to acknowledge the
		// command. If it does not complete in that time, the type of error is called a "timeout"
		// and enableSensor() returns false (you could check this to make sure everything is OK -
		// this is not shown here for simplicity but is used in later examples).
		mStManager.enableSensor(Sensor.IR_TEMPERATURE);

		// We want to be able to change the text box on the GUI layout. We need to get a reference
		// to the Java object that Android associates to this text box, so that we can call built-
		// in methods to change it. To do so we call findViewById(R.id.helloworld) to get the text
		// box called "helloworld"; it returns a View object but we know it's a TextView from the
		// activity_main.xml layout we're using, so we can cast it to TextView (see: polymorphism).
		//
		// R.id.helloworld corresponds to the android:id="@+id/helloworld" attribute (defined in
		// res/layout/activity_main.xml)
		//
		// We could call findViewById(R.id.helloworld) every time we need to access this text box,
		// but it's easier (and potentially faster) to save it to a variable to use later.
		mTextView = (TextView) findViewById(R.id.helloworld);
		
		//this is how call the object by ID
		humidityTextview = (TextView) findViewById(R.id.humidityid);

	}

	/**
	 * Called by Android when the Activity comes back into the foreground (i.e. on-screen). When
	 * called, enables processing sensor measurements (received by {@link ManagerListener}).
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
	 * pops up on top of it and partially or fully obscures it). When called, this method disables
	 * processing sensor measurements but does not close the Bluetooth connection or disable the
	 * sensors, allowing the application to save power/CPU by not processing sensor measurement info
	 * but restore quickly when it comes into the foreground again.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onPause() {
		super.onPause();
		if (mStManager != null) mStManager.disableUpdates();
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
	 * Receives and handles events from the SensorTagManager: SensorTag status updates, sensor
	 * measurements, etc. After the setup that happened in onCreate(), our instance of the
	 * SensorTagManager will call methods from the ManagerListener according to the data it
	 * receives from the SensorTag.
	 * 
	 * Advanced note: SensorTagManager calls these handler methods from a background thread, not
	 * from the main GUI thread.
	 */
	public class ManagerListener extends SensorTagLoggerListener implements SensorTagListener {

		/**
		 * Called on receiving a new ambient temperature measurement from the SensorTagManager.
		 * Displays the new value on the GUI.
		 * 
		 * @see ca.concordia.sensortag.SensorTagLoggerListener#onUpdateAmbientTemperature(double)
		 */
		@Override
		public void onUpdateAmbientTemperature(SensorTagManager mgr, double temp) {
			// ManagerListener inherits from SensorTagLoggerListener; we call the superclass
			// method (in SensorTagLoggerListener) in order for the superclass functionality to
			// run: in this case, it logs the value of the temperature measurement ("temp") to the
			// Android LogCat for debugging purposes.
			super.onUpdateAmbientTemperature(mgr, temp);

			/*
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 * CHANGE ME: Ambient Temperature - This is where you can process a new ambient
			 * temperature sensor measurement. You will get a new measurement every 1 second, and
			 * this method will be called every time. In this example, all we do is format the
			 * temperature as a string and display it on-screen.
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 */

			// tempFormat is a static member variable that defines how to format a number value as
			// a string: in this case, if you scroll up to the declaration of tempFormat, note that
			// it defines positive or negative numbers with 1 to 3 digits before the decimal point
			// and 2 digits after the decimal point. Here we take the "temp" reading from the
			// sensor and format it according to the tempFormat definition.
			//
			// We have to define this final (= constant) in order to be able to use this variable
			// inside the Runnable afterwards. Although it's a constant, it still has method scope,
			// so a new constant is created each time the method is called (and deleted when it
			// goes out of scope).
			final String tempString = tempFormat.format(temp);

			// ALL changes to the GUI have to happen on the "main" thread a.k.a. the "UI" thread.
			// In the onUpdate*() functions, we are NOT in that thread but in a background thread.
			// You can do any amount of calculation and processing in the onUpdate*() function, but
			// for changes that happen to the GUI (i.e. any View that's in your layout or in the
			// code), you HAVE to use the runOnUiThread() technique below; otherwise, the app will
			// crash.
			//
			// (You don't need to understand "threads" right now; if you have not studied it, just
			// remember for now: when you're inside a SensorTagListener function, all changes to the
			// GUI _must_ be done using runOnUiThread() like below.)
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					/*
					 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					 * CHANGE ME: Ambient Temperature - This is where you can change what gets shown
					 * on UI (on the screen) of your app whenever you get a new sensor measurement.
					 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					 */

					// Changes the text in the temperature TextView to the new value received
					mTextView.setText(tempString + "Â°C");
				}

			});

		}

		/**
		 * CHANGE ME: Accelerometer - This is where you can receive and process a new sensor
		 * measurement. By default you will get a measurement every 1 or 2 seconds depending on
		 * the sensor (sometimes you can change this: see later example apps).
		 * 
		 * Before this sensor will work you MUST enableSensor() in onCreate!
		 * 
		 * See SensorTagListener.java (in the SensorTagLib project) for more details about this
		 * method.
		 * 
		 * This method is optional: you don't need to put it here if you're not using this
		 * sensor!
		 * 
		 * @see
		 * ca.concordia.sensortag.SensorTagLoggerListener#onUpdateAccelerometer(ca.concordia.sensortag
		 * .SensorTagManager, ti.android.util.Point3D)
		 */
		@Override
		public void onUpdateAccelerometer(SensorTagManager mgr, Point3D acc) {
			// Call the superclass's method - leave this to keep logging the new value in LogCat
			super.onUpdateAccelerometer(mgr, acc);

			/*
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 * CHANGE ME: See explanation in javadoc above
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 */
		}

		/**
		 * CHANGE ME: Barometer - same as onUpdateAccelerometer above
		 * 
		 * @see
		 * ca.concordia.sensortag.SensorTagLoggerListener#onUpdateBarometer(ca.concordia.sensortag
		 * .SensorTagManager, double, double)
		 */
		@Override
		public void onUpdateBarometer(SensorTagManager mgr, double pressure, double height) {
			super.onUpdateBarometer(mgr, pressure, height);


			/*
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 * CHANGE ME: See explanation in javadoc above
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 */
		}

		/**
		 * CHANGE ME: Barometer - same as onUpdateAccelerometer above
		 * 
		 * @see
		 * ca.concordia.sensortag.SensorTagLoggerListener#onUpdateGyroscope(ca.concordia.sensortag
		 * .SensorTagManager, ti.android.util.Point3D)
		 */
		@Override
		public void onUpdateGyroscope(SensorTagManager mgr, Point3D ang) {
			super.onUpdateGyroscope(mgr, ang);

			/*
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 * CHANGE ME: See explanation in javadoc above
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 */
		}

		/**
		 * CHANGE ME: Barometer - same as onUpdateAccelerometer above
		 * 
		 * @see
		 * ca.concordia.sensortag.SensorTagLoggerListener#onUpdateHumidity(ca.concordia.sensortag
		 * .SensorTagManager, double)
		 */
		@Override
		public void onUpdateHumidity(SensorTagManager mgr, double rh) {
			super.onUpdateHumidity(mgr, rh);
			
			final String humidityString = humidityFormat.format(rh);
		
			
		
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					/*
					 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					 * CHANGE ME: Ambient Temperature - This is where you can change what gets shown
					 * on UI (on the screen) of your app whenever you get a new sensor measurement.
					 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
					 */

					// Changes the text in the temperature TextView to the new value received
					mTextView.setText(humidityString + "Â%");
				}

			});

		
		}

		/**
		 * CHANGE ME: Barometer - same as onUpdateAccelerometer above
		 * 
		 * @see
		 * ca.concordia.sensortag.SensorTagLoggerListener#onUpdateInfraredTemperature(ca.concordia
		 * .sensortag.SensorTagManager, double)
		 */
		@Override
		public void onUpdateInfraredTemperature(SensorTagManager mgr, double temp) {
			super.onUpdateInfraredTemperature(mgr, temp);
			
			/*
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 * CHANGE ME: See explanation in javadoc above
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 */
		}

		/**
		 * CHANGE ME: Barometer - same as onUpdateAccelerometer above
		 * 
		 * @see ca.concordia.sensortag.SensorTagLoggerListener#onUpdateKeys(ca.concordia.sensortag.
		 * SensorTagManager, boolean, boolean)
		 */
		@Override
		public void onUpdateKeys(SensorTagManager mgr, boolean left, boolean right) {
			super.onUpdateKeys(mgr, left, right);

			/*
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 * CHANGE ME: See explanation in javadoc above
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 */
		}

		/**
		 * CHANGE ME: Barometer - same as onUpdateAccelerometer above
		 * 
		 * @see
		 * ca.concordia.sensortag.SensorTagLoggerListener#onUpdateMagnetometer(ca.concordia.sensortag
		 * .SensorTagManager, ti.android.util.Point3D)
		 */
		@Override
		public void onUpdateMagnetometer(SensorTagManager mgr, Point3D b) {
			super.onUpdateMagnetometer(mgr, b);

			/*
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 * CHANGE ME: See explanation in javadoc above
			 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			 */
		}
		
		
	
	

		
		

	}
}
