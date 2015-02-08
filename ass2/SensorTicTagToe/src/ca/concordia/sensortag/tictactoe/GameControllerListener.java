package ca.concordia.sensortag.tictactoe;

import ti.android.ble.sensortag.Sensor;
import ti.android.util.Point3D;
import android.util.Log;
import android.widget.Toast;
import ca.concordia.sensortag.SensorTagListener;
import ca.concordia.sensortag.SensorTagLoggerListener;
import ca.concordia.sensortag.SensorTagManager;
import ca.concordia.sensortag.SensorTagManager.ErrorType;
import ca.concordia.sensortag.SensorTagManager.StatusType;

/**
 * Handles events from the SensorTagManager: SensorTag status updates, sensor measurements, etc.
 * Processes data from sensors in order to detect the events: left/right key down, SensorTag shake.
 * These events are then passed to the GameActivity handler methods: onButtonLeftDown(),
 * onButtonRightDown(), onShake().
 */
public class GameControllerListener extends SensorTagLoggerListener implements SensorTagListener {

	final static String TAG = "ControllerListener";

	/**
	 * Reference to the GameActivity, to allow this class to make calls back to it when it detects
	 * an event.
	 */
	GameActivity mContext;

	/**
	 * Amount of time to "cool down" an acceleration shake event: this prevents another event from
	 * being registered immediately after the first (e.g. due to a single shake causing
	 * acceleration-deceleration, or the user shaking up and down 1-2 times inadvertently).
	 */
	public final static int ACC_EVENT_COOLDOWN_MS = 1000;

	/** * High pass filter time constant in milliseconds. See {@link #applyFilter(Point3D)}. */
	public final static int ACC_FILTER_TAU_MS = 100;

	/** Acceleration threshold (magnitude) to detect a "shake". */
	public final static double ACC_THRESHOLD = 0.8;

	/** Last acceleration value. */
	private Point3D mLastAcc = null;
	/** Last acceleration output value of the high-pass filter. */
	private Point3D mLastFiltAcc = null;
	/**
	 * When this value is less than ACC_EVENT_COOLDOWN_MS, we are in cooldown mode and do not detect
	 * acceleration shake events. This is set to 0 whenever an event is detected, and incremented by
	 * the sample period at every new sample received while in cooldown mode.
	 * 
	 * When this value is equal to or greater than ACC_EVENT_COOLDOWN_MS, we are out of cooldown
	 * time and can detect acceleration shake events normally.
	 */
	private int mCooldownCounterMs = ACC_EVENT_COOLDOWN_MS;

	/** The last received state (true = pressed, false = not pressed) of the buttons. */
	boolean prevLeft = false, prevRight = false;

	GameControllerListener(GameActivity context) {
		mContext = context;
	}

	/*
	 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	 * Remember: if you want to use other sensors here, you have to enable them in onCreate and then
	 * add the onUpdate method corresponding to that sensor! See the SensorTagListener interface for
	 * the list of all the onUpdate methods.
	 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	 */

	/**
	 * Called whenever the state of one of the SensorTag keys changes (pressed or unpressed). These
	 * buttons control movement in the Tic Tac Toe game: the left button moves you right (wraps
	 * around the board), and the right button moves you down (also wraps around the board). This
	 * method checks for a button press and moves the cursor accordingly.
	 * 
	 * @see ca.concordia.sensortag.SensorTagLoggerListener#onUpdateKeys(ca.concordia.sensortag.SensorTagManager,
	 *      boolean, boolean)
	 */
	@Override
	public void onUpdateKeys(SensorTagManager mgr, boolean left, boolean right) {
		super.onUpdateKeys(mgr, left, right);

		/*
		 * if the left button has been pressed, we move the cursor to the right (wrapping around
		 * if needed). This action is handled by mContext.onButtonLeftDown().
		 *
		 * We want the event that the button's state changed from unpressed to pressed,
		 * hence we have to check the previous value.
		 */
		if (!prevLeft && left) {
			Log.d(TAG, "onUpdateKeys: buttonLeftDown");
			mContext.onButtonLeftDown();
		}

		/*
		 * If the right button has been pressed, we move the cursor down (wrapping around if
		 * needed). This action is handled by mContext.onButtonRightDown().
		 */
		if (!prevRight && right) {
			Log.d(TAG, "onUpdateKeys: buttonRightDown");
			mContext.onButtonRightDown();
		}

		// Store the current values as the new "previous" values, for next time.
		prevLeft = left;
		prevRight = right;
	}

	/**
	 * Called on receiving a new accelerometer reading. This method filters the accelerometer values
	 * and attempts to detect a "shake" or "knock" event.
	 * 
	 * Gravity is a constant 1g force on the SensorTag that is detected by the accelerometer. We
	 * don't want to take into account gravity for our "shake" detection; however, although we know
	 * the magnitude (1.00g), we don't necessarily know the direction of the gravity vector, since
	 * you can rotate the SensorTag, and therefore we can't just cancel out the gravity by adding,
	 * for example, +1g on the z-component.
	 * 
	 * As a solution, we use a high-pass filter to remove the "DC" component. If we assume that
	 * the SensorTag is only rotated slowly, the high-pass filter will remove the gravity component
	 * while allowing sudden movements to pass through the filter. Note, also, that our assumption
	 * is sufficient: if the SensorTag is rotated quickly, that is probably a voluntary movement
	 * that can be recorded as a "shake" event.
	 * 
	 * The high pass filter is implemented in applyFilter().
	 * 
	 * From there, we have the filtered signal that only passes sufficiently high-frequency
	 * accelerations. If this filtered acceleration exceeds 0.8g (ACC_THRESHOLD), this is
	 * detected as a "shake" of the SensorTag.
	 * 
	 * After this shake is detected, when the next accelerometer measurements are received,
	 * "shake" events are NOT looked for until a certain amount of time has passed (default 1 second
	 * ACC_COOLDOWN_MS), in order to avoid one "shake" motion accidentally triggering more than
	 * one "shake" detection. This is called the "cooldown" period.
	 * 
	 * @see ca.concordia.sensortag.SensorTagLoggerListener#onUpdateAmbientTemperature(ca.concordia.sensortag.SensorTagManager, double)
	 */
	@Override
	public void onUpdateAccelerometer(SensorTagManager mgr, Point3D acc) {
		super.onUpdateAccelerometer(mgr, acc);
		
		// Get accelerometer's update period (how often a new measurement is received, in ms)
		int mPeriod = mgr.getSensorPeriod(Sensor.ACCELEROMETER);

		// If this is the first data point, assume the acceleration has been this value forever
		// for signal processing purposes.
		/*
		 * If this is the first acceleration measurement received, then for the purpose of the
		 * low pass filter, assume it has been the same value forever: therefore, the last value
		 * was the same as the current value, and after going through a high pass filter the result
		 * was zero. (This is setting the initial conditions for our high pass filter.)
		 */
		if (mLastAcc == null) {
			mLastAcc = acc;
			mLastFiltAcc = new Point3D(0, 0, 0);
		}

		// Pass the new acceleration measurement through the high-pass filter.
		mLastFiltAcc = applyFilter(mPeriod, acc, mLastAcc, mLastFiltAcc);
		// Save the current acceleration measurement for later.
		mLastAcc = acc;

		/* Cooldown: If the cooldown counter exceeds the minimum cooldown time, we are NOT in
		 * cooldown and we can detect shake events.
		 */
		if (mCooldownCounterMs >= ACC_EVENT_COOLDOWN_MS) {
			/* If the magnitude of the filtered acceleration value is greater than the minimum
			 * threshold to detect a "shake", this is now a shake.
			 */
			if (mLastFiltAcc.norm() > ACC_THRESHOLD) {
				Log.i(TAG, "Accelerometer shake detected");
				// Reset/start the cooldown timer (0ms have elapsed since the "shake" detection)
				mCooldownCounterMs = 0;
				
				//  Call GameActivity and inform it that a shake has occurred.
				mContext.onShake();
			}
		}
		/* If the cooldown counter is less than the minimum cooldown time, we are in the cooldown
		 * period and should NOT process the acceleration further for detecting shakes.
		 * 
		 * Instead, we increment the cooldown counter by the sensor period (since this method
		 * gets called every sensor period with a new measurement). This lets us time how long
		 * we've been in cooldown period and when it's time to go back to normal detection (above).
		 */
		else {
			mCooldownCounterMs += mgr.getSensorPeriod(Sensor.ACCELEROMETER);
			Log.v(TAG, "Cooldown counter: " + mCooldownCounterMs + "ms");
		}
	}

	/**
	 * Applies a high-pass filter to a new sample value. Relies on the member constants
	 * ACC_FILTER_TAU_MS, ACC_FILTER_TAU_MS and the variable mUpdatePeriodRealMs.
	 * 
	 * The accelerometer always detects a 1.0g gravity component, but we don't know what the
	 * SensorTag's orientation is so we don't necessarily know which direction the gravity component
	 * is.
	 * 
	 * We can assume that it is slow moving (the user won't be rotating the SensorTag very quickly
	 * ... or if they do we can detect that as a "shake" anyway!). A high-pass filter will therefore
	 * remove the acceleration element and allow us to only capture faster events.
	 * 
	 * The implementation here is a simple first-order high-pass filter:
	 * 
	 * H(s) = (s RC) / (1 + s RC)
	 * 
	 * where RC is the time constant the cutoff frequency is f_c = 1/(2*pi*RC).
	 * 
	 * By applying the bilinear transformation we can get a discrete time implementation of this
	 * filter, expressed here in the time domain:
	 * 
	 * y[n] := k * (y[n-1] + x[n] - x[n-1])
	 * 
	 * where x[n] is the filter input signal, y[n] is the output signal, n is the sample index, and
	 * k is an arbitrary real constant which is related to the time constant. The system time
	 * constant tau is equal to:
	 * 
	 * tau = T k / (1 - k)
	 * 
	 * where T is the sample period of the signal in seconds.
	 * 
	 * We implement this filter below individually to each of the acceleration components, using a
	 * history of one sample point (since the filter never needs to go more than one sample point
	 * behind).
	 * 
	 * More information: https://en.wikipedia.org/wiki/High-pass_filter#Discrete-time_realization
	 * 
	 * @param newInput
	 *            The next input sample.
	 * @param prevInput
	 *            The previous input sample.
	 * @param prevOutput
	 *            The previous filter output sample.
	 * @return The next filter output sample.
	 */
	private Point3D applyFilter(int samplePeriodMs, Point3D newInput, Point3D prevInput,
			Point3D prevOutput) {
		
		// Calculate the needed parameters
		double k = (double) ACC_FILTER_TAU_MS / (ACC_FILTER_TAU_MS + samplePeriodMs);

		// These variables are used to make the algorithm easier to follow alongside the
		// mathematical expressions given above. yn = y[n], yn1 = y[n-1], etc.
		Point3D yn, yn1, xn, xn1;
		yn1 = prevOutput;
		xn = newInput;
		xn1 = prevInput;

		// Apply the filter to each component of the 3D vector separately
		yn = new Point3D(
				k * (yn1.x + xn.x - xn1.x),
				k * (yn1.y + xn.y - xn1.y),
				k * (yn1.z + xn.z - xn1.z));

		Log.v(TAG, "ACC FILTER: " + xn + " -> " + yn);

		return yn; // return the output of the filter at the current sample time
	}

	/**
	 * Called on receiving a SensorTag-related error. Displays a Toast showing a message to the
	 * user.
	 * 
	 * @see ca.concordia.sensortag.SensorTagBaseListener#onError(
	 *      ca.concordia.sensortag.SensorTagManager,
	 *      ca.concordia.sensortag.SensorTagManager.ErrorType, java.lang.String)
	 */
	@Override
	public void onError(SensorTagManager mgr, ErrorType type, String msg) {
		super.onError(mgr, type, msg);
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
		if (text != null) Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
	}

	/**
	 * Called on receiver a SensorTag-related status message. Displays a Toast showing a message to
	 * the user, if relevant.
	 * 
	 * @see ca.concordia.sensortag.SensorTagBaseListener#onStatus(ca.concordia.sensortag.SensorTagManager,
	 *      ca.concordia.sensortag.SensorTagManager.StatusType, java.lang.String)
	 */
	@Override
	public void onStatus(SensorTagManager mgr, StatusType type, String msg) {
		super.onStatus(mgr, type, msg);
		String text = null;
		switch (type) {
		case SERVICE_DISCOVERY_COMPLETED:
			// Not needed and some problems with too many toasts at the same time
			// text = "Sensors discovered";
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
		if (text != null) Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
	}

}
