/*
 * ELEC390 and COEN390: TI SensorTag Library for Android
 * Example application: SensorTicTagToe
 * Author: Marc-Alexandre Chan <marcalexc@arenthil.net>
 * Institution: Concordia University
 */
package ca.concordia.sensortag.tictactoe;

import java.util.ArrayList;
import java.util.List;

import ti.android.ble.sensortag.Sensor;
import ca.concordia.sensortag.SensorTagListener;
import ca.concordia.sensortag.SensorTagManager;
import ca.concordia.sensortag.tictactoe.TicTacToeGame.ComputerPlayer;
import ca.concordia.sensortag.tictactoe.TicTacToeGame.PlayerPiece;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main activity that allows a user to play a Tic Tac Toe game. This class is responsible for
 * managing the GUI, the connection to the SensorTag, and to connect the {@link TicTacToeGame} class
 * and {@link GameControllerListener} class to events on the GUI.
 */
public class GameActivity extends Activity {

	// Tag for Android's logcat: "GameActivity"
	static public final String TAG = GameActivity.class.getSimpleName();
	
	// CONSTANTS: Settings and defaults that aren't user-configurable in the app
	// How often measurements should be taken, in milliseconds, for each sensor used
	static protected final int UPDATE_PERIOD_ACC_MS = 100;
	// How long to let the computer player "think"
	static public final int COMPUTER_DELAY_MS = 1500;
	// Index of the default computer player to use
	static final int DEFAULT_COMPUTER_INDEX = 1;
	// Index of the human player mode in the list of computer players
	static final int HUMAN_INDEX = 0;

	// Bluetooth communication - objects used to establish & maintain comms to SensorTag
	private BluetoothDevice mBtDevice;
	private SensorTagManager mStManager;
	private SensorTagListener mStListener;

	// GUI elements that are to be manipulated dynamically
	private TextView[][] mViewBoard = null;
	private TextView mViewScorePlayer1;
	private TextView mViewScorePlayer2;
	private TextView mViewScoreDraw;
	private TextView mViewPiecePlayer1;
	private TextView mViewPiecePlayer2;
	private TextView mViewLabelPlayer1;
	private TextView mViewLabelPlayer2;

	// Game control objects and settings related to the game
	NewGameDialogData mDlgNew; // Keeps track of the settings entered into the New Game dialog
	private TicTacToeGame mGame = null; // Stores the game board and implements game rules
	private List<ComputerPlayer> mPlayerModes; // List of available AIs for computer players.
	Handler mComputerDelayHandler; // Used to fake "thinking" time by delaying a function call
	
	private PlayerPiece mPlayerOnePiece, mPlayerTwoPiece; // Who is playing as X and O?
	private ComputerPlayer mPlayerOne, mPlayerTwo; // Which AI for computer player? (null = human)

	// Activity state variables
	private int mSelectedRow; // Current cursor row/column
	private int mSelectedCol;

	private int mScorePlayer1; // Scores: each player + draws
	private int mScorePlayer2;
	private int mScoreDraw;

	/**
	 * Called when the Activity is created. Sets up the GUI, checks whether a Bluetooth Device was
	 * sent from the DeviceConnectActivity via the Intent sent, and initialises the Bluetooth
	 * communication with the SensorTag.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Set the main GUI's layout to layout/activity_tictactoe.xml
		setContentView(R.layout.activity_tictactoe);

		// Because there's a lot to do in onCreate this time, we use a series of private methods
		// (all of the ones starting with "init") to organise onCreate and keep each method
		// readable. See the methods and their documentation comments for detials.

		// Initialise the Bluetooth, SensorTag, etc. communication and configuration.
		boolean btOk = initBluetooth();

		// If the Bluetooth initialisation fails, we can't do anything. Exit.
		// (On failure, initBluetooth takes care of logging and showing a Toast already)
		if (!btOk) {
			finish();
			return;
		}
		
		initGui(); // Prepare the GUI's initial state and member variables related to it
		initNewGameDialogData(); // Do some preparation for showing the New Game dialog
		initGameState(); // Prepare the game object
						 // (TicTacToeGame class: stores the game board and implements game rules)
	}

	/**
	 * This method sets up the Bluetooth, GATT and SensorTag communication and configuration. Called
	 * only from onCreate(), and used to organise the onCreate() method.
	 */
	private boolean initBluetooth() {
		// Get the Bluetooth device selected by the user - should be set by DeviceSelectActivity
		// upon launching this application
		Intent receivedIntent = getIntent();
		mBtDevice = (BluetoothDevice) receivedIntent
				.getParcelableExtra(DeviceSelectActivity.EXTRA_DEVICE);

		// If we didn't get a device, we can't do anything! Warn the user, log and exit.
		if (mBtDevice == null) {
			Log.e(TAG, "No BluetoothDevice extra [" + DeviceSelectActivity.EXTRA_DEVICE
					+ "] provided in Intent.");
			Toast.makeText(this, "No Bluetooth Device selected", Toast.LENGTH_SHORT).show();
			return false;
		}

		// Prepare the SensorTag - this is the same as in the Minimal example.
		// Note that we're using a GameControllerListener for the listener class. This time it is
		// defined in a SEPARATE file called GameControllerListener.java, instead of as a nested
		// class to the activity.
		mStManager = new SensorTagManager(this, mBtDevice);
		mStListener = new GameControllerListener(this);
		mStManager.addListener(mStListener);

		mStManager.initServices();
		if (!mStManager.isServicesReady()) { // initServices failed or took too long
			Log.e(TAG, "Discover failed.");
			return false;
		}

		/*
		 * Here we enable the accelerometer. The accelerometer gives a reading of the acceleration
		 * vector (cartesian coordinates), in g (1g = 9.81 m/s^2), at a fixed sample interval.
		 *
		 * On the SensorTag, the sensors' sample interval can be configured. However, some sensors
		 * DO NOT support this, but it depends on the version of the firmware installed on the
		 * SensorTag itself. isPeriodSupported() checks the capabilities of this version of the
		 * SensorTag (which was obtained in initServices() above), and returns true if you can
		 * configure the sample interval (a.k.a. update period) of the specified sensor.
		 *
		 * If we can do it, great! We enable the accelerometer with period = UPDATE_PERIOD_ACC_MS.
		 *
		 * If not, can we deal with it gracefully in our application? We can try enabling it with
		 * the default period value (1 second, again depends on SensorTag firmware version). In
		 * either case we store the set period value so that the rest of the application knows how
		 * long between each acceleration reading.
		 */
		boolean res;
		if (mStManager.isPeriodSupported(Sensor.ACCELEROMETER)) {
			res = mStManager.enableSensor(Sensor.ACCELEROMETER, UPDATE_PERIOD_ACC_MS);
		}
		else {
			res = mStManager.enableSensor(Sensor.ACCELEROMETER);
		}

		// If the accelerometer couldn't be enabled, we're stuck - exit with error message.
		if (!res) {
			Log.e(TAG, "Accelerometer configuration failed - exiting");
			Toast.makeText(this, "Accelerometer configuration failed - exiting", Toast.LENGTH_LONG)
					.show();
			return false;
		}

		/*
		 * We also want to use the two buttons on the sensor tag. This is called the SIMPLE_KEYS
		 * sensor in the SensorTag Android library. This sensor is different from the others:
		 * instead of taking a measurement every x milliseconds, it sends you a new measurement
		 * whenever the state of one of the buttons changes (goes from "pressed" to "unpressed"
		 * state or vice-versa). The onUpdateKeys() function in the SensorTagListener therefore
		 * becomes more like an Android GUI button listener (onClickListener()).
		 * 
		 * Given how it works, SIMPLE_KEYS never supports setting a period. We don't need to
		 * check that or attempt to set a period.
		 */
		res = res && mStManager.enableSensor(Sensor.SIMPLE_KEYS);

		// If the keys couldn't be enabled, exit with error message.
		if (!res) {
			Log.e(TAG, "Keys configuration failed - exiting");
			Toast.makeText(this, "Keys configuration failed - exiting", Toast.LENGTH_LONG).show();
			return false;
		}
		
		// If we've gotten to here, we haven't exited with an error - everything succeeded.
		return true;
	}

	/**
	 * Sets up the main game's GUI: obtains references for GUI elements that have to be manipulated
	 * programmatically, creates callbacks, etc. Called only from onCreate(), and used simply to
	 * organise the onCreate() method.
	 */
	private void initGui() {
		// Get references to the GUI objects that we need to manipulate during the game
		
		// The elements of the game board are kept in an array of TextView[row][col]
		mViewBoard = new TextView[TicTacToeGame.BOARD_SIZE][TicTacToeGame.BOARD_SIZE];
		mViewBoard[0][0] = (TextView) findViewById(R.id.board_pos00);
		mViewBoard[0][1] = (TextView) findViewById(R.id.board_pos01);
		mViewBoard[0][2] = (TextView) findViewById(R.id.board_pos02);
		mViewBoard[1][0] = (TextView) findViewById(R.id.board_pos10);
		mViewBoard[1][1] = (TextView) findViewById(R.id.board_pos11);
		mViewBoard[1][2] = (TextView) findViewById(R.id.board_pos12);
		mViewBoard[2][0] = (TextView) findViewById(R.id.board_pos20);
		mViewBoard[2][1] = (TextView) findViewById(R.id.board_pos21);
		mViewBoard[2][2] = (TextView) findViewById(R.id.board_pos22);

		// These are all GUI elements at the bottom
		mViewScorePlayer1 = (TextView) findViewById(R.id.status_player1_score);
		mViewScorePlayer2 = (TextView) findViewById(R.id.status_player2_score);
		mViewScoreDraw    = (TextView) findViewById(R.id.status_draw_score);
		mViewPiecePlayer1 = (TextView) findViewById(R.id.status_player1_piece);
		mViewPiecePlayer2 = (TextView) findViewById(R.id.status_player2_piece);
		mViewLabelPlayer1 = (TextView) findViewById(R.id.status_player1_label);
		mViewLabelPlayer2 = (TextView) findViewById(R.id.status_player2_label);
	}

	/**
	 * Initialise internal data for the New Game dialog. Called only from onCreate(), and used
	 * simply to organise the onCreate() method.
	 */
	private void initNewGameDialogData() {
		// Get the New Game dialog's layout (layout/dialog_new_game.xml) from the XML file.
		View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_game, null);
		
		/*
		 * Creates a data structure (NewGameDialogData class) to contain and monitor the New Game
		 * dialog settings. This data structure connects to the GUI elements on the New Game
		 * dialog's layout (layout/dialog_new_game.xml). Note that in the NewGameDialogData
		 * constructor, you need to pass the Views (GUI element objects) from the layout in order
		 * for the class to have access to those elements.
		 * 
		 * When the user opens the New Game dialog, the NewGameDialogData instance (mDlgNew) listens
		 * to changes made by the user to the settings in that dialog. When the user clicks New
		 * Game, these settings are retrieved from mDlgNew and passed to makeNewGame().
		 * 
		 * The listeners for the New Game dialog GUI controls are defined in the NewGameDialogData
		 * constructor. However, the "New Game" and "Cancel" buttons are controlled by the Positive
		 * and Negative Button, respectively, of the dialog, which are defined in showNewDialog().
		 */
		mDlgNew = new NewGameDialogData(dialogView,
				(Switch) dialogView.findViewById(R.id.dialog_sw_p1_token),
				(Spinner) dialogView.findViewById(R.id.dialog_p1_mode),
				(TextView) dialogView.findViewById(R.id.dialog_p2_token),
				(Spinner) dialogView.findViewById(R.id.dialog_p2_mode));
	}

	/**
	 * Initialises the Tic Tac Toe game's internal state. Creates the objects that manage the game
	 * rules, sets up a new game with initial settings (player 1 = X human, player 2 = O computer).
	 * Called only from onCreate(), and is used simply to organize the onCreate() method.
	 * 
	 * CHANGE ME to add your own computer players/AI to mPlayerModes. You also have to
	 * modify the "player_modes" string array in values/strings.xml to correspond to the same order
	 * as the mPlayerModes list.
	 */
	private void initGameState() {

		// Set up the activity state for tracking game score and GUI controls
		mGame = new TicTacToeGame();

		// Set up the different AIs available on the "New Game" dialog
		// CHANGE ME: If you want to add your own computer player (AI), add an mPlayerModes.add()
		// line below with your own ComputerPlayer implementation class. You also have to add an
		// entry to the "player_modes" string array in values/string to give your AI a displayed
		// name. Make sure to also increase "totalComputerPlayers" (this is used to set the
		// initial capacity of the ArrayList: it's a tiny speed optimisation, since ArrayList can
		// grow if needed).
		int totalComputerPlayers = 2;
		mPlayerModes = new ArrayList<ComputerPlayer>(totalComputerPlayers);
		mPlayerModes.add(null); // this one corresponds to the "human" option
		mPlayerModes.add(new RandomComputer());

		// Set up the GUI's initial state
		
		// Set up the handler: this is used to allow the computer player to have a "thinking time"
		// by telling the handler to run a method after a certain delay (using #postDelayed())
		mComputerDelayHandler = new Handler();
		
		// Set up a default new game: Player 1 = X (human), Player 2 = Y (computer)
		makeNewGame(PlayerPiece.X, null, mPlayerModes.get(DEFAULT_COMPUTER_INDEX));
		
		// Set all scores to 0
		resetScore();
		
		// Set the initial cursor position to the center of the board, and highlight it on the GUI
		setSelectedPosition(1, 1);
	}

	/**
	 * Called by Android when the Activity is started again.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onStart() {
		super.onStart();
	}

	/**
	 * Called by Android when the Activity comes back into the foreground. When called, enables
	 * processing sensor measurements (which are received by {@link GameControllerListener}).
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
	 * allowing the application to restore quickly when it comes into the foreground again. It also
	 * cancels any computer moves that are supposed to happen after a delay.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onPause() {
		super.onPause();
		if (mStManager != null) mStManager.disableUpdates();
		
		if(mComputerDelayHandler != null) mComputerDelayHandler.removeCallbacksAndMessages(null);
	}

	/**
	 * Called by Android when the Activity is stopped, e.g. when another Activity is opened full-
	 * screen and this Activity goes into the background. This method is always called after
	 * onPause() and differs from that method in that onStop() will not be called if an Activity
	 * pops up on top of the Activity, not full screen, while onPause() will.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onStop() {
		super.onStop();
	}

	/**
	 * Called when the Activity is destroyed by Android. Cleans up the Bluetooth connection to the
	 * SensorTag, and clean up the computer "thinking time" handler, cancelling any computer moves
	 * that were supposed to happen.
	 * 
	 * @see https://developer.android.com/reference/android/app/Activity.html#ActivityLifecycle
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mStManager != null) mStManager.close();
		mStManager = null;
		
		if(mComputerDelayHandler != null) mComputerDelayHandler.removeCallbacksAndMessages(null);
		mComputerDelayHandler = null;
	}

	/**
	 * Loads the Action Bar menu items from the menu XML file. This is the menu usually openable
	 * from the top-right corner of the screen (three vertical squares), and also any other icons
	 * that appear in that corner. In this case, we have the "New Game" icon and the "Reset score"
	 * icon, but no full menu (this is all defined in the menu/tictactoe.xml file: in that file,
	 * the android:showAsAction="ifRoom" attribute means that if there's enough space in the top-
	 * right corner, it shows that entry as a separate icon, otherwise it puts it inside the menu).
	 * 
	 * @return Whether or not to show the menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.tictactoe, menu);
		return true;
	}

	/**
	 * Called whenever the user clicks on an item from the Action Bar menu (see
	 * onCreateOptionsMenu). Detects the button that was clicked, and calls the associated method
	 * for that action.
	 * 
	 * @return True if the click has been handled by this method, or false to let Android process
	 *         the click normally.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Get the Android XML ID of the button that was clicked
		int id = item.getItemId();
		// Handle the ones in our custom menu
		if (id == R.id.menu_new_game) {
			showNewGameDialog();
			return true;
		}
		else if (id == R.id.menu_reset_score) {
			resetScore();
			return true;
		}
		
		// This is where the "home/back" button is handled - no need to do it above
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 * NewGameDialogData contains the settings for a New Game, as entered into the New Game dialog
	 * by a user. These settings include the players (who is playing X and O), and whether each
	 * player is a human or an AI (and which AI class to use for that player's moves).
	 * 
	 * Furthermore, NewGameDialogData also listens to the controls on the New Game dialog in order
	 * to detect when the user changes the dialog's settings and to reflect those changes in its
	 * stored settings.
	 * 
	 * When the user clicks the "New Game" button in the New Game dialog, the stored settings are
	 * retrieved from this class by the positive button handler (see showNewGameDialog() for the
	 * definition of the positive button click listener).
	 */
	protected class NewGameDialogData {
		private View view;
		private Switch switchPlayerOne;
		private Spinner playerOneMode;
		private TextView playerTwoToken;
		private Spinner playerTwoMode;
		private Context context;

		/**
		 * Constructor.
		 * 
		 * @param dialogView View corresponding to the full dialog layout.
		 * @param p1 Switch for setting Player 1 as X or O pieces.
		 * @param p1mode Spinner (dropdown) to set Player 1 as Human or one of the Computer AIs.
		 * @param p2token TextView showing whether Player 2 is X or O. (This is not a switch, like
		 * Player 1, because Player 2 has to be the opposite piece from Player 1.)
		 * @param p2mode Spinner (dropdown) to set Player 2 as Human or one of the Computer AIs.
		 */
		NewGameDialogData(View dialogView,
				Switch p1, Spinner p1mode, TextView p2token, Spinner p2mode) {
			
			// Save all the constructor parameters to member variables for later use.
			view = dialogView;
			switchPlayerOne = p1;
			playerOneMode = p1mode;
			playerTwoToken = p2token;
			playerTwoMode = p2mode;
			context = GameActivity.this;

			// Set Player 1 as X (the switch is in the left or "unchecked" state)
			p1.setChecked(false);
			// Show that Player 2 is O
			p2token.setText(getString(R.string.game_player_o));
			// Set up a Listener on the Player 1 X/O switch so we know when the user clicks it
			// When the user clicks it, we need to update the text shown for Player 2 (who is always
			// the opposite piece type from Player 1).
			p1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

				/**
				 * Called when the user clicks the Player 1 Piece switch. Changes the piece that is
				 * shown for Player 2, to correspond to the opposite of Player 1's piece (e.g. if
				 * Player 1 is X, show that Player 2 is O, and vice-versa).
				 */
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					// If the switch is checked (right position), Player 1 = O so Player 2 = X
					if (isChecked)
						playerTwoToken.setText(getString(R.string.game_player_x));
					else
						// If the switch is not checked (left position), Player 1=X so Player 2=O
						playerTwoToken.setText(getString(R.string.game_player_o));
				}
			});

			/*
			 * We need to tell the dropdown what options to show. Unfortunately this is kind of
			 * complicated in Android, unlike HTML =(
			 * 
			 * Dropdowns get their list of items from an "Adapter". Adapters can be as simple as
			 * telling the dropdown to use constant values, or much more complex by getting values
			 * from external sources and updating them automatically.
			 * 
			 * In this case, we create an ArrayAdapter "from resource" (because the list of items
			 * is defined in values/strings.xml in the string-array called "player_modes", which is
			 * an Android resource). We also pass android.R.layout.simple_spinner_item: this
			 * argument is the XML layout to use when showing the selected element (i.e. when you
			 * haven't clicked on the dropdown to get the full menu).
			 * android.R.layout.simple_spinner_item is a layout included in Android by default that
			 * only shows the selected element in a simple way; it's good enough for us, so we won't
			 * go to the trouble of defining our own layout XML file for this.
			 */
			ArrayAdapter<CharSequence> player1ModeAdapter = ArrayAdapter.createFromResource(
					context, R.array.player_modes, android.R.layout.simple_spinner_item);

			/* This is a little similar to android.R.layout.simple_spinner_item. In this case, the
			 * setDropDownViewResource() function sets the layout to use when you click the
			 * dropdown element to get the full list: we again use a layout included in Android,
			 * which just shows a simple list of elements to choose from, since this is enough for
			 * our purposes.
			 */
			player1ModeAdapter
					.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

			// Now that we made this adapter, we have to tell the Spinner element that this adapter
			// contains the items it needs to show the user.
			playerOneMode.setAdapter(player1ModeAdapter);
			
			// This sets the currently selected value to HUMAN_INDEX (= 0, and should be the index
			// of the "Human" element in the list).
			playerOneMode.setSelection(HUMAN_INDEX);

			// Same thing as above: this time we set up the menu for Player 2, and set Player 2 to
			// be a Computer player by default instead of a human.
			ArrayAdapter<CharSequence> player2ModeAdapter = ArrayAdapter.createFromResource(
					context, R.array.player_modes, android.R.layout.simple_spinner_item);
			player2ModeAdapter
					.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			playerTwoMode.setAdapter(player2ModeAdapter);
			playerTwoMode.setSelection(DEFAULT_COMPUTER_INDEX);
		}

		/**
		 * Returns the view corresponding to the full New Game dialog layout.
		 * @return New Game dialog layout's View object
		 */
		public View getView() {
			return view;
		}

		/**
		 * @return Player 1 X/O switch (GUI element)
		 */
		public Switch getViewP1Switch() {
			return switchPlayerOne;
		}

		/**
		 * @return Player 1 Human/AI dropdown menu (GUI element)
		 */
		public Spinner getViewP1Mode() {
			return playerOneMode;
		}

		/**
		 * @return Player 2 X/O text (GUI element, non-clickable)
		 */
		public TextView getViewP2Token() {
			return playerTwoToken;
		}

		/**
		 * @return Player 2 Human/AI dropdown menu (GUI element)
		 */
		public Spinner getViewP2Mode() {
			return playerTwoMode;
		}

		/**
		 * @return The currently set player piece (X or O) for Player 1
		 */
		public PlayerPiece getP1Token() {
			if (switchPlayerOne.isChecked())
				return PlayerPiece.O;
			else
				return PlayerPiece.X;
		}

		/**
		 * @return The currently set player piece (X or O) for Player 2
		 */
		public PlayerPiece getP2Token() {
			if (switchPlayerOne.isChecked())
				return PlayerPiece.X;
			else
				return PlayerPiece.O;
		}

		/**
		 * @return The ComputerPlayer object corresponding to the selected AI dropdown menu item for
		 * Player 1. If Player 1 is a human, returns null.
		 * 
		 * @see TicTacToeGame for how ComputerPlayer-derived objects are used when playing a game.
		 * @see RandomComputer for an example of a ComputerPlayer.
		 */
		public ComputerPlayer getP1Mode() {
			// Get the currently selected index from the playerOneMode spinner (dropdown menu)
			long id = playerOneMode.getSelectedItemId();

			// If the ID is INVALID_ROW_ID (meaning nothing selected) or outside int range,
			// we cannot look it up in mPlayerModes list - so assume that means human...
			if (id == Spinner.INVALID_ROW_ID || id > Integer.MAX_VALUE || id < Integer.MIN_VALUE) {
				return null;
			}
			return mPlayerModes.get((int) id);
		}

		/**
		 * @return The ComputerPlayer object corresponding to the selected AI dropdown menu item for
		 * Player 1. If Player 1 is a human, returns null.
		 * 
		 * @see #getP1Mode()
		 */
		public ComputerPlayer getP2Mode() {
			long id = playerTwoMode.getSelectedItemId();
			if (id == Spinner.INVALID_ROW_ID || id > Integer.MAX_VALUE || id < Integer.MIN_VALUE) {
				return null;
			}
			return mPlayerModes.get((int) id);
		}
	}

	/**
	 * Set up and show the New Game dialog.
	 */
	private void showNewGameDialog() {
		/* When the New Game dialog is visible, we don't want to process SensorTag inputs: otherwise
		 * it would be possible for the player to (intentionally or accidentally) make a play in
		 * the background without being able to see what they're playing!
		 */
		if (mStManager != null) mStManager.disableUpdates();
		
		// We should also cancel any queued-up computer player moves (in case the player brings up
		// the New Game dialog during the "thinking time" delay interval).
		mComputerDelayHandler.removeCallbacksAndMessages(null);

		// Create a new dialog using Android's simple Builder paradigm
		new AlertDialog.Builder(this)
				// Do you really need a comment to explain this line?
				.setTitle("New Game")
				// The "positive button" is the "OK" button of AlertDialogs. In this case, it's the
				// "New Game" button that starts a new game. The first argument is the Android
				// resource pointing to the text to use (you could also put a string here), and the
				// second argument is the Listener that runs when the button is clicked (see the
				// onClick() method within.)
				.setPositiveButton(R.string.btn_new_game, new DialogInterface.OnClickListener() {

					/**
					 * Called when the "New Game" button is clicked. Make a new game from the
					 * settings input on the dialog (via mDlgNew), and dismiss the dialog.
					 */
					@Override
					public void onClick(DialogInterface dialog, int which) {
						makeNewGame(mDlgNew.getP1Token(), mDlgNew.getP1Mode(), mDlgNew.getP2Mode());
						dialog.dismiss();
					}

				})
				// The "negative button" is the "Cancel" button. Here we have the "Cancel" text
				// (Android resource R.string.btn_cancel), and no Listener because we don't need to
				// do anything special when it's clicked: just let Android handle it (= dismiss the
				// dialog).
				.setNegativeButton(R.string.btn_cancel, null)
				// This sets the View (actual GUI) of the "body" part of the dialog to the Layout
				// we inflated (loaded) from XML in initNewGameDialogData() above.
				.setView(mDlgNew.getView())
				// Set a Listener that gets called whenever the dialog is dismissed: this can be
				// from  pressing Cancel, when code calls dismiss(), when the user clicks outside
				// the dialog to make it disappear, etc. See onDismiss() within.
				.setOnDismissListener(new DialogInterface.OnDismissListener() {

					/**
					 * Called whenever the dialog is dismissed from the screen.
					 * @param dialog The dialog that was dismissed.
					 */
					@Override
					public void onDismiss(DialogInterface dialog) {
						// Remove the View from the dialog. A View can only be associated to one
						// context at a time (Activity, Dialog, etc.). We inflated (loaded) the
						// New Game view and set it up in mDlgNew already, so we want to reuse that
						// if the New Game dialog is called again later. In order to reuse it, we
						// have to remove it from this dialog to attach it to a future New Game
						// dialog; otherwise, the program crashes.
						((ViewGroup) mDlgNew.getView().getParent()).removeView(mDlgNew.getView());
						
						// Now that the dialog is gone, we want to start processing SensorTag events
						// so we can get back to the game!
						if (mStManager != null) mStManager.enableUpdates();
						
						// Check if the current turn is a computer's move, and if so let it play
						// after a "thinking time" of COMPUTER_DELAY_MS.
						playComputer(COMPUTER_DELAY_MS);
					}
				})
				// Now that we're all done setting up the dialog with the Builder, show it.
				.show();
	}

	/**
	 * Prepares a new game and removes all played pieces on the game board on the GUI.
	 * 
	 * @param playerOnePiece
	 *            The piece (X or O) played by Player 1. Player 2 is implicitly the other piece.
	 * @param playerOne
	 *            The ComputerPlayer to play playerOne. If null, human.
	 * @param playerTwo
	 *            The ComputerPlayer to play PlayerTwo. If null, human.
	 */
	private void makeNewGame(TicTacToeGame.PlayerPiece playerOnePiece, ComputerPlayer playerOne,
			ComputerPlayer playerTwo) {
		// A few logs so that we can debug any issues that happen, e.g., wrong settings read from
		// the New Game dialog.
		Log.i(TAG, "makeNewGame: P1=" + playerOnePiece.name() + "; P1="
				+ (playerOne != null ? playerOne.name() : "Human") + "; P2="
				+ (playerTwo != null ? playerTwo.name() : "Human"));
		
		// If the computer is "thinking" before playing a move, cancel that queued move.
		mComputerDelayHandler.removeCallbacksAndMessages(null);

		// Make a new "internal" game state (TicTacToeGame), and set up the internal game state
		// (of which player is playing which piece: X/O, and who is a human or computer). This is
		// based on the parameters passed to the makeNewGame() method.
		mGame = new TicTacToeGame();
		mPlayerOnePiece = playerOnePiece;
		mPlayerTwoPiece = (playerOnePiece == PlayerPiece.X) ? PlayerPiece.O : PlayerPiece.X;
		mPlayerOne = playerOne;
		mPlayerTwo = playerTwo;

		// Show the piece that each player is playing. In the default layout, this is shown at the
		// bottom of the screen along with player scores.
		String playerX = getString(R.string.game_player_x);
		String playerO = getString(R.string.game_player_o);
		if (mPlayerOnePiece == PlayerPiece.X) {
			mViewPiecePlayer1.setText(playerX);
			mViewPiecePlayer2.setText(playerO);
		}
		else {
			mViewPiecePlayer1.setText(playerO);
			mViewPiecePlayer2.setText(playerX);
		}

		// When a player wins, their score number is coloured to show who won the current game.
		// Now that we're making a new game, reset the colours of all scores.
		mViewScoreDraw.setTextColor(getResources().getColor(R.color.status_player));
		mViewScorePlayer1.setTextColor(getResources().getColor(R.color.status_player));
		mViewScorePlayer2.setTextColor(getResources().getColor(R.color.status_player));

		// Reset the on-screen game board to blank.
		refreshBoardGui();

		// Check if the first player is a computer. If so, let him make his first move after a 
		// "thinking time" delay of COMPUTER_DELAY_MS.
		playComputer(COMPUTER_DELAY_MS);
	}

	/**
	 * Resets the score.
	 */
	private void resetScore() {
		Log.v(TAG, "resetScore()");
		
		// Simple enough: set all the scores to zero for the internal variables...
		mScorePlayer1 = mScorePlayer2 = mScoreDraw = 0;
		
		// ... and set all the scores shown on the screen to zero!
		mViewScorePlayer1.setText("0");
		mViewScorePlayer2.setText("0");
		mViewScoreDraw.setText("0");

		// When a player wins, their score number is coloured to show who won the current game.
		// Now that we're resetting them all to zero, also reset the colours of all scores.
		mViewScoreDraw.setTextColor(getResources().getColor(R.color.status_player));
		mViewScorePlayer1.setTextColor(getResources().getColor(R.color.status_player));
		mViewScorePlayer2.setTextColor(getResources().getColor(R.color.status_player));
	}

	/**
	 * Change the position of the cursor on the board.
	 * 
	 * @throws IndexOutOfBoundsException
	 *             if row or col is not a valid index for the board size
	 */
	private void setSelectedPosition(int row, int col) {
		Log.v(TAG, "setSelectedPosition(" + row + ", " + col + ")");
		
		// Set the background color of the PREVIOUS selected position back to normal.
		mViewBoard[mSelectedRow][mSelectedCol].setBackgroundResource(R.drawable.bg_board_pos);
		
		// And highlight the background color of the NEW selected position.
		mViewBoard[row][col].setBackgroundResource(R.drawable.bg_board_pos_selected);

		// Save the NEW selected position.
		mSelectedRow = row;
		mSelectedCol = col;
	}

	/**
	 * Let the current player play a piece at the current cursor position.
	 * 
	 * @return True on success, false if that position cannot be played (e.g. position already has
	 * a piece, current player is a computer, game has ended).
	 */
	private boolean playAtCursor() {
		// If the game has already ended, cannot play: show a Toast message to the user and exit.
		if (mGame.isFinished()) {
			Toast.makeText(this, "Game has ended. Press New Game to restart.", Toast.LENGTH_SHORT)
					.show();
			Log.w(TAG, "playAtCursor(): Cannot play at [" + mSelectedRow + "," + mSelectedCol
					+ "]. Game has ended.");
			return false;
		}

		// If the current player is a computer, a human cannot play: show a Toast message to the
		// user and exit.
		if ((mGame.getCurrentPlayer() == mPlayerOnePiece && mPlayerOne != null)
				|| (mGame.getCurrentPlayer() == mPlayerTwoPiece && mPlayerTwo != null)) {
			Log.w(TAG, "playAtCursor(): Current player is a computer.");
			return false;
		}

		// If a piece is already placed here, can't play: same thing as above...
		if (mGame.get(mSelectedRow, mSelectedCol) != PlayerPiece.NONE) {
			Toast.makeText(this, "Can't play here: piece already placed.", Toast.LENGTH_SHORT)
					.show();
			Log.w(TAG, "playAtCursor(): Cannot play at [" + mSelectedRow + "," + mSelectedCol
					+ "]. Piece already placed.");
			return false;
		}

		// Otherwise, good to go! Log where we're playing a piece and what piece it is.
		Log.i(TAG, "Playing " + mGame.getCurrentPlayer().name() + " at [" + mSelectedRow + ","
				+ mSelectedCol + "]");

		// Pass the played position to the game object so it can add it to the board and check if
		// anyone won.
		mGame.play(mSelectedRow, mSelectedCol);
		
		// Redraw the board on the screen to update it with this new play.
		refreshBoardGui();

		// Check if the game has ended. If so, checkGameEnded() updates the score and highlights
		// the winner in the score area.
		boolean ended = checkGameEnded();
		
		// If the game hasn't ended, check if the next player is an AI: if so, let it play.
		if (!ended) playComputer(COMPUTER_DELAY_MS);
		
		return true;
	}

	/**
	 * If the current player is a computer player, calls the AI to make the next move after a delay
	 * of delayMs (asynchronous). Otherwise, do nothing.
	 * 
	 * @param delayMs
	 *            How long to let the computer "think", in milliseconds.
	 * @return True if the computer is capable of playing, false on failure.
	 */
	private boolean playComputer(int delayMs) {
		// If the game has already ended, cannot play: log (don't need to show the user) and exit.
		if (mGame.isFinished()) {
			Log.w(TAG, "playComputer(): Cannot play. Game has ended.");
			return false;
		}

		// If the current player is not a computer, don't play. Let the human make a move instead.
		if (!(mGame.getCurrentPlayer() == mPlayerOnePiece && mPlayerOne != null)
				&& !(mGame.getCurrentPlayer() == mPlayerTwoPiece && mPlayerTwo != null)) {
			Log.w(TAG, "playComputer(): Current player not a computer.");
			return false;
		}
		
		// If the computer is already scheduled to play after a "thinking" delay, exit now
		if (mComputerDelayHandler.hasMessages(0)) {
			Log.w(TAG, "playComputer(): Computer already thinking.");
			return false;
		}

		// Otherwise, we can let the computer make a move! Log that this is happening.
		Log.i(TAG, "Computer " + mGame.getCurrentPlayer().name() + " is thinking for " + delayMs
				+ "ms...");

		// Queue up an event to happen in delayMs via the Handler (this happens in the main thread)
		// In other words: after delayMs time has passed, the Runnable's run() function will be
		// called by this Handler.
		//
		// This is used to simulate the computer "thinking" (because Tic Tac Toe AIs mostly would
		// play seemingly instantly otherwise---they're not as slow/complex as chess or weiqi
		// engines).
		mComputerDelayHandler.postDelayed(new Runnable() {

			/**
			 * Called after a delay. Calls the computer player to make a move.
			 */
			@Override
			public void run() {
				Log.i(TAG, "Computer done thinking, executing computer...");

				// The TicTacToeGame object only has one ComputerPlayer object at a time.
				// Set the one to use for the current Player (1 or 2) as set in the New Game dialog.
				if (mGame.getCurrentPlayer() == mPlayerOnePiece)
					mGame.setComputer(mPlayerOne);
				else
					mGame.setComputer(mPlayerTwo);
				
				// For safety, if the current player changed to a human since the original
				// call to playComputer(), abort
				if (mGame.getComputer() == null) {
					Log.e(TAG,
							"Aborting computer move: current player not a computer (after delay).");
					return;
				}

				// playComputer() calls the ComputerPlayer object that was set using setComputer(),
				// above. The ComputerPlayer object makes a move, which is passed back to the game
				// object in mGame.
				mGame.playComputer();
				
				// Now that a computer move has been played, redraw the board to show the new move.
				refreshBoardGui();
				
				// This is the same as playAtCursor(): check if the game has ended and if so,
				// checkGameEnded() will automatically update the displayed scores and highlights.
				boolean ended = checkGameEnded();
				
				// check if next player is a computer, and if so play. This is to deal with the case
				// that both players are computer players.
				if (!ended) playComputer(COMPUTER_DELAY_MS);
			}

		}, delayMs);
		return true;
	}

	/**
	 * Update the on-screen board to reflect the internal game board state. Also updates the
	 * displayed current player.
	 */
	private void refreshBoardGui() {
		// For each row of the game board
		for (int i = 0; i < TicTacToeGame.BOARD_SIZE; ++i) {
			// For each column of the game board
			for (int j = 0; j < TicTacToeGame.BOARD_SIZE; ++j) {
				
				PlayerPiece pos = mGame.get(i, j); // Get the piece at that position on the board
				
				String posText;
				int resTextColor;

				// Depending on the piece, choose the X or O text to show on the screen as well as
				// the colour.
				//
				// For X and O pieces played, we use a normal color (black by default). However, for
				// an empty position, we use an "O" with a transparent colour: this is needed in
				// order to make sure all the columns are even in width. If we used an empty square
				// instead, the position's width would collapse (this is a bug in how the original
				// Layout was designed).
				if (pos == PlayerPiece.X) {
					posText = getString(R.string.game_player_x);
					resTextColor = R.color.board_piece;
				}
				else if (pos == PlayerPiece.O) {
					posText = getString(R.string.game_player_o);
					resTextColor = R.color.board_piece;
				}
				else { // Empty position (discussed in the comment above)
					posText = getString(R.string.game_player_o);
					resTextColor = R.color.board_pos_empty;
				}

				// Now that the text and colour were selected, set them to the TextView representing
				// that position on the screen.
				mViewBoard[i][j].setText(posText);
				mViewBoard[i][j].setTextColor(getResources().getColor(resTextColor));
			}
		}

		// Set the "current player" highlight in the status area at the bottom of the layout:
		// If the game is finished already, don't highlight anyone.
		if (mGame.isFinished()) {
			mViewLabelPlayer1.setTextColor(getResources().getColor(R.color.status_player));
			mViewLabelPlayer2.setTextColor(getResources().getColor(R.color.status_player));
		}
		// If the next person to play is Player 1, highlight Player 1's text.
		else if (mGame.getCurrentPlayer() == mPlayerOnePiece) {
			mViewLabelPlayer1.setTextColor(getResources().getColor(R.color.status_player_turn));
			mViewLabelPlayer2.setTextColor(getResources().getColor(R.color.status_player));
		}
		// Otherwise, highlight Player 2's text.
		else {
			mViewLabelPlayer1.setTextColor(getResources().getColor(R.color.status_player));
			mViewLabelPlayer2.setTextColor(getResources().getColor(R.color.status_player_turn));
		}
	}

	/**
	 * Check if the game has ended. If it has, update the scores.
	 * 
	 * @return True if the game has ended, false otherwise.
	 */
	private boolean checkGameEnded() {
		if (mGame.isFinished()) {
			// The game has ended. We want to get the winner and update the scores.
			PlayerPiece winner = mGame.getWinner();
			String winnerString = winner.name();
			
			// Winner = NONE means the game ended as a draw
			if (winner == PlayerPiece.NONE) {
				// First increment the internal variable for the draw score by 1
				++mScoreDraw;
				
				// And then convert it to text, and show it on the screen (highlight the draw score)
				mViewScoreDraw.setText(Integer.toString(mScoreDraw));
				mViewScoreDraw.setTextColor(getResources().getColor(R.color.status_player_turn));
				winnerString = "DRAW";
			}
			else if (winner == mPlayerOnePiece) {
				// First increment the internal variable for the player 1 score by 1
				++mScorePlayer1;
				
				// Then convert it to text and show it on the screen (highlight player 1's score)
				mViewScorePlayer1.setText(Integer.toString(mScorePlayer1));
				mViewScorePlayer1.setTextColor(getResources().getColor(R.color.status_player_turn));
			}
			else {
				// Same as above, for player 2 as the winner.
				++mScorePlayer2;
				mViewScorePlayer2.setText(Integer.toString(mScorePlayer2));
				mViewScorePlayer2.setTextColor(getResources().getColor(R.color.status_player_turn));
			}
			Log.i(TAG, "Game has ended. Winner: " + winnerString);
			return true;
		}
		return false;
	}

	/* *********************************************************************************************
	 * CONTROLLER EVENTS - Handlers for events caused by the user using the SensorTag controller,
	 * and detected from the {@link GameControllerListener} code. All of these methods are only
	 * called from {@link GameControllerListener} when it detects valid events (e.g. button presses
	 * or a shake of the SensorTag) that have an action in the game.
	 * ********************************************************************************************/
	/**
	 * Called when the left button is pressed down. The cursor is moved one position downward,
	 * wrapping around the board if needed.
	 * 
	 * This method can be run from a non-UI thread. It will queue GUI actions in the main thread
	 * using the {@link Activity#runOnUiThread(Runnable)} method.
	 */
	public void onButtonLeftDown() {
		// Like before, these methods get called frmo GameControllerListener outside of the main
		// thread. Since we want to change the GUI, we need to run our code in the main thread.
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				// Increase the current selection's row by 1: mSelectedRow + 1
				// But wrap it around the board too:          modulo BOARD_SIZE
				setSelectedPosition((mSelectedRow + 1) % TicTacToeGame.BOARD_SIZE, mSelectedCol);
			}
		});
	}
	
	/**
	 * Called when the right button is pressed down. The cursor is moved one position to the right,
	 * wrapping around if needed.
	 * 
	 * This method can be run from a non-UI thread. It will queue GUI actions in the main thread
	 * using the {@link Activity#runOnUiThread(Runnable)} method.
	 */
	public void onButtonRightDown() {
		// Like before, these methods get called frmo GameControllerListener outside of the main
		// thread. Since we want to change the GUI, we need to run our code in the main thread.
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				// Increase the current selection's column by 1: mSelectedColumn + 1
				// But wrap it around the board too:             modulo BOARD_SIZE
				setSelectedPosition(mSelectedRow, (mSelectedCol + 1) % TicTacToeGame.BOARD_SIZE);
			}
		});
	}
	
	/**
	 * Called when the user shakes the SensorTag stiffly. A play is attempted at the currently
	 * selected game board position.
	 */
	public void onShake() {
		// Like before, these methods get called frmo GameControllerListener outside of the main
		// thread. Since we want to change the GUI, we need to run our code in the main thread.
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				// If the game isn't finished yet, try to play at the current selected position.
				if(!mGame.isFinished()) {
					playAtCursor();
				}
				// If the game is finished, a shake is a shortcut to start a new game with the same
				// settings as the previous game.
				else {
					makeNewGame(mPlayerOnePiece, mPlayerOne, mPlayerTwo);
				}
			}

		});
	}
}
