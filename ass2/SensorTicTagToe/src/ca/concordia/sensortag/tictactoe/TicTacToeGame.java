/*
 * ELEC390 and COEN390: TI SensorTag Library for Android
 * Example application: SensorTicTagToe
 * Author: Marc-Alexandre Chan <marcalexc@arenthil.net>
 * Institution: Concordia University
 */

package ca.concordia.sensortag.tictactoe;

import java.util.Arrays;

import android.util.Log;

/**
 * Class that represents the Tic Tac Toe game. This class is responsible for tracking the state of
 * the game board and implementing game rules (e.g. whose turn it is to play, detecting when the
 * game has ended and who has won).
 * 
 * This class supports custom AIs; you can extend the {@link TicTacToeGame.ComputerPlayer} class to
 * define the AI behaviour, specify which ComputerPlayer the game should use with
 * {@link #setComputer(ComputerPlayer)}, and you can let the AI play the next move using
 * {@link #playComputer()}.
 * 
 * Player is represented as the piece they are playing (X or O). X always plays first. Every turn,
 * you can either play a piece manually by calling {@link #play(int, int)}, or let the previously
 * selected {@link ComputerPlayer} play by calling {@link #playComputer()}. Note that this class
 * does not manage "who" is a human or computer player: you could have X play two human moves and
 * then let the computer play the third move, for example, by making the relevant calls. The method
 * {@link isFinished()} tells you when the game has ended, while {@link getWinner()} will tell you
 * the winner of a finished game.
 * 
 * A finished game cannot be reset. To start a new game, create a new TicTacToeGame object.
 * 
 * Game win (three in a row) detection checks only the last move made. It will not detect a win that
 * does not involve the last piece placed on the board.
 */
public class TicTacToeGame {
	/** LogCat tag */
	public static String TAG = "Game";
	
	/**
	 * Board size: 3x3. Note that you can't just change this constant to make the board bigger: some
	 * things like the win detection are hardcoded on a 3-in-a-row win condition on a 3x3 board...
	 */
	public static int BOARD_SIZE = 3;

	/**
	 * Enum representing the player pieces X and O. "NONE" is used in different ways depending on
	 * the context: it is used to denote a tie for {@link TicTacToeGame#getWinner()}, or denotes an
	 * empty board position for {@link TicTacToeGame#get(int, int)}, etc. Check the documentation
	 * of methods that use PlayerPiece arguments or return types for the exact meaning of NONE.
	 */
	public enum PlayerPiece {
		X, O, NONE
	};

	/**
	 * Interface for the Computer Player strategy. This interface can be used to create custom AI
	 * classes to play the game.
	 * 
	 * To write your own ComputerPlayer AI, write a class that implements ComputerPlayer and
	 * implement the {@link #play(TicTacToeGame)} method. This method is passed a reference to the
	 * TicTacToeGame instance; with it, you can call {@link TicTacToeGame#getCurrentPlayer()} to
	 * find out whose turn the AI is suppsoed to play and {@link TicTacToeGame#get(int, int)} to
	 * see what pieces are already on the board. From there, implement your AI to determine the next
	 * piece and <b>return</b> it as a length-2 array int[2] = {row, col}. <b>Do not</b> call
	 * {@link TicTacToeGame#play(int, int)}, as you do NOT need to manually tell the game to play:
	 * TicTacToeGame will handle the return value to play your AI move.
	 * 
	 * This design pattern is called the Strategy design pattern: a class (TicTacToeGame) can change
	 * behaviour based on the concrete implementation class (implementing ComputerPlayer) that is
	 * passed to it.
	 */
	public interface ComputerPlayer {
		/**
		 * Plays the next move of the game according to an AI algorithm.
		 * 
		 * @param game
		 *            The game in progress. This can be used to determine the current game board and
		 *            state. {@link TicTacToeGame#play(int, int)} and
		 *            {@link TicTacToeGame#playComputer(PlayerPiece)} must <em>not</em> be called by
		 *            this method.
		 * @return The next move to play, as an array of the form int[2] = {row, col}.
		 */
		int[] play(TicTacToeGame game);
		
		/** Internal ComputerPlayer implementation's name. Used mostly for logging or internal
		 * identification purposes.
		 */
		String name();
	}

	// BOARD_SIZE by BOARD_SIZE array that contains the state of the board (pieces played)
	private PlayerPiece[][] mBoard = null;
	
	// Internal game flow
	private boolean mIsPlaying = false; /**< Has the game started? */
	private boolean mIsFinished = false; /**< Has the game ended? */
	private PlayerPiece mCurrentPlayer = PlayerPiece.X; /**< Whose turn is it to play? */
	private PlayerPiece mWinner = null; /**< Who won the game? (null = not finished, NONE = draw) */
	private ComputerPlayer mAi = null; /**< AI to use */
	private boolean mAiIsPlaying = false; /**< Is the AI currently being called? Used to prevent
												spurious play(int, int) calls */

	/**
	 * Constructor.
	 */
	public TicTacToeGame() {
		// Make a 3x3 array to hold the game's board, and then fill it in with PlayerPiece.NONE to
		// represent empty positions (neither X nor O pieces played)
		mBoard = new PlayerPiece[BOARD_SIZE][BOARD_SIZE];
		for (PlayerPiece[] row : mBoard)
			Arrays.fill(row, PlayerPiece.NONE);
	}

	/**
	 * Retrieve the piece currently at a position on the game board.
	 * 
	 * @param row
	 *            The row (zero-indexed) to check.
	 * @param col
	 *            The column (zero-indexed) to check.
	 * @return PlayerPiece.X or PlayerPiece.O if a play has been made at that position. Otherwise,
	 *         PlayerPiece.NONE.
	 */
	public PlayerPiece get(int row, int col) {
		return mBoard[row][col];
	}

	/**
	 * Unconditionally set a piece on the board. This method is purely a setter and implements no
	 * rules/logic for the piece to play (e.g. piece corresponds to current player, piece hasn't
	 * previously been played on this square) and is called internally by {@link #play(int, int)}.
	 * 
	 * @param row
	 *            The row (zero-indexed) to set.
	 * @param col
	 *            The column (zero-indexed) to set.
	 * @param p
	 *            The PlayerPiece value to set.
	 * @throws IndexOutOfBoundsException
	 *             [row][col] are outside the board bounds.
	 */
	protected void set(int row, int col, PlayerPiece p) {
		mBoard[row][col] = p;
	}
	
	/**
	 * Get the ComputerPlayer object currently set. This ComputerPlayer is used for
	 * {@link #playComputer()} calls.
	 * 
	 * @return Current ComputerPlayer object.
	 */
	public ComputerPlayer getComputer() {
		return mAi;
	}

	/**
	 * Set the ComputerPlayer to use for {@link #playComputer()} calls.
	 * 
	 * @param cp ComputerPlayer object to use.
	 */
	public void setComputer(ComputerPlayer cp) {
		mAi = cp;
	}

	/**
	 * @return True if the game is in progress, false otherwise.
	 */
	public boolean isPlaying() {
		return mIsPlaying;
	}

	/**
	 * @return True if the game is done (won or drawn), false otherwise.
	 */
	public boolean isFinished() {
		return mIsFinished;
	}

	/**
	 * The player whose turn it is to play.
	 * 
	 * @return The current player, or PlayerPiece.NONE if the game has ended.
	 */
	public PlayerPiece getCurrentPlayer() {
		return mCurrentPlayer;
	}

	/**
	 * Get the winner of the game.
	 * 
	 * @return The PlayerPiece corresponding to the winner if the game has been won;
	 *         PlayerPiece.NONE for a draw; null if the game is not done yet.
	 */
	public PlayerPiece getWinner() {
		return mWinner;
	}

	/**
	 * Play a piece at the specified position. The piece that is played on the board (X or O)
	 * corresponds to the current player: see {@link #getCurrentPlayer()}.
	 * 
	 * For this method to succeed, the game must not be finished (see {@link #isFinished()}, and
	 * the position being played must not already have a game piece (see {@link #get(int, int)}.
	 * 
	 * ComputerPlayer implementations should never call this method.
	 * 
	 * @param row
	 *            Row (zero-indexed) at which to play
	 * @param col
	 *            Column (zero-index) at which to play
	 * @return True on success, false on failure (game ended or piece already played there).
	 * @throws IllegalStateException
	 *             This method was called while {@link ComputerPlayer#play(TicTacToeGame)} is
	 *             running. The ComputerPlayer is not allowed to call this method to play (it should
	 *             return its next move instead), and you should not call this method in a thread
	 *             while the ComputerPlayer is executing.
	 */
	public boolean play(int row, int col) throws IllegalStateException {
		// ComputerPlayer is not allowed to call this method...
		if (mAiIsPlaying) {
			Log.e(TAG, "ComputerPlayer has illegally called TicTacToeGame#play(int, int), or "
					+ "this method was called in a thread while the ComputerPlayer is processing");
			throw new IllegalStateException(
				"ComputerPlayer has illegally called TicTacToeGame#play(int, int), or "
				+ "this method was called in a thread while the ComputerPlayer is processing");
		}
		
		// If the game is finished or a piece was already played at [row, col], return a failure
		if (isFinished() || get(row, col) != PlayerPiece.NONE) {
			Log.w(TAG, "Cannot play: game finished or piece already played at [" + row + "," + col
					+ "]");
			return false;
		}

		Log.i(TAG, "Playing " + mCurrentPlayer.name() + " at [" + row + "," + col + "]");

		// The game has now started. If mIsPlaying was already true before this does effectively
		// nothing... It's not worth checking if(!mIsPlaying) for a simple assignment.
		mIsPlaying = true;
		
		// Set an mCurrentPlayer piece to the [row, col] specified, and then switch the "current
		// player" to the other player for the next turn
		set(row, col, mCurrentPlayer);
		switchCurrentPlayer();
		
		// Check whether the last move causes the game to end (somebody won or the game board is
		// full), and if so set the game in a finished state.
		if (checkGameEnd(row, col)) {
			mIsFinished = true;
			mIsPlaying = false;
		}
		return true;
	}

	/**
	 * Let a ComputerPlayer implementor make the next move. This method calls
	 * {@link ComputerPlayer#play(TicTacToeGame)} and plays the position returned by this method.
	 * 
	 * You must set a valid ComputerPlayer object using {@link #setComputer(ComputerPlayer)} before
	 * calling this method.
	 * 
	 * @throws IllegalStateException
	 *             A computer player was never set using {@link #setComputer(ComputerPlayer)}.
	 * @throws IllegalArgumentException
	 *             The computer player returned a bad index or an index that is already occupied.
	 */
	public void playComputer() throws IllegalArgumentException {
		if (isFinished()) return;

		// Null check: neeed to have a ComputerPlayer object to be able to call it...
		if (mAi != null) {
			Log.d(TAG, "Calling computer player...");
			
			// The mAiIsPlaying flag is used to detect whether TicTacToeGame.play(int, int) is
			// called by mAi.play(this), which it is not allowed to do. (It also detects whether
			// TicTacToeGame.play(int, int) is called from a separate thread while the computer
			// is processing its next move ... but that shouldn't happen either!)
			mAiIsPlaying = true;
			
			// Call the AI and let it determine its move.
			int[] nextPlay = mAi.play(this);
			Log.d(TAG, "Computer player returned: " + Arrays.toString(nextPlay));
			
			mAiIsPlaying = false;

			// if computer player returns a bad/invalid result, throw an exception...
			if (nextPlay.length != 2 || nextPlay[0] < 0 || nextPlay[0] >= BOARD_SIZE
					|| nextPlay[1] < 0 || nextPlay[1] >= BOARD_SIZE) {
				throw new IllegalArgumentException("Computer player returned invalid value: "
						+ Arrays.toString(nextPlay));
			}

			// if computer player returns a position that already has a piece, throw an exception
			if (get(nextPlay[0], nextPlay[1]) != PlayerPiece.NONE) {
				throw new IllegalArgumentException(
						"Computer player returned play on an occupied index: "
								+ Arrays.toString(nextPlay));
			}

			// At this point we've determined this is a valid move, so play it normally.
			play(nextPlay[0], nextPlay[1]);
		}
		else {
			throw new IllegalStateException("Computer player has not been set");
		}
	}

	/**
	 * Switch the current player from X to O or vice-versa. In the case of a weird internal state,
	 * this sets the current player to X.
	 * 
	 * Called after a move has been played to change turns.
	 */
	protected void switchCurrentPlayer() {
		switch (mCurrentPlayer) {
		case O:
			mCurrentPlayer = PlayerPiece.X;
			break;
		case X:
			mCurrentPlayer = PlayerPiece.O;
			break;
			
		// In case mCurrentPlayer is set to some weird/invalid value (null or NONE), reset to X
		// However, when the game is finished, mCurrentPlayer is supposed to be NONE.
		default:
		case NONE:
			if (!isFinished()) mCurrentPlayer = PlayerPiece.X;
			break;

		}
		Log.v(TAG, "Current player now " + mCurrentPlayer.name());
	}

	/**
	 * Check if the last move ended the game. If the game has ended, sets the winner (see
	 * {@link #getWinner()}). The game has ended if three pieces in a row are detected horizontally,
	 * vertically or diagonally (assuming a board size of 3), or if all spaces on the board are
	 * filled.
	 * 
	 * @return True if the game has ended, false otherwise.
	 */
	protected boolean checkGameEnd(int lastPlayRow, int lastPlayCol) {
		// Faster to type for lazy fingers, while keeping the parameter names clear!
		int r = lastPlayRow;
		int c = lastPlayCol;
		PlayerPiece lastPlay = get(r, c);

		// Check for a three-in-a-row in the row in which the last move was made. We do this by
		// assuming the row is a win, and then check each position in the row: if any of the
		// positions don't match the piece of the last move, then that row does NOT contain a win
		// and we set matchRow or matchColumn to false.
		//
		// We check row, columns and diagonals in separate for loops for code clarity. If better
		// performance were needed (and this were actually processor intensive!), the four checks
		// could be combined into one for loop.
		boolean matchRow = true;
		for (int i = 0; i < BOARD_SIZE; ++i) {
			// Note that r (row) is constant, and i (col) iterates through every value
			if (get(r, i) != lastPlay) {
				matchRow = false;
				break; // we don't need to keep looping if we find one non-matching value
			}
		}
		if(matchRow) Log.i(TAG, "Win detected on row " + r);

		// Repeat for the column in which the last move was made.
		boolean matchColumn = true;
		for (int i = 0; i < BOARD_SIZE; ++i) {
			if (get(i, c) != lastPlay) {
				matchColumn = false;
				break;
			}
		}
		if(matchColumn) Log.i(TAG, "Win detected on column " + c);

		// if the last play was on the forward diagonal, check it a three in a row win
		// same idea as the row/col checks above
		boolean matchDiag = false;
		if (r == c) { // if the last play was on the main diagonal
			matchDiag = true;
			for (int i = 0; i < BOARD_SIZE; ++i) {
				// diagonal positions are (0,0), (1,1), (2,2)
				if (get(i, i) != lastPlay) {
					matchDiag = false;
					break;
				}
			}
		}
		if(matchDiag) Log.i(TAG, "Win detected on forward diagonal.");

		// if the last play was on the anti-diagonal, check it for a three in a row win
		boolean matchAnti = false;
		if (r + c == 2) { // if the last play was on the anti-diagonal
			matchAnti = true;
			for (int i = 0; i < BOARD_SIZE; ++i) {
				// Anti-diagonal positions  are (0,2), (1,1), (2,0)
				if (get(i, 2 - i) != lastPlay) {
					matchAnti = false;
					break;
				}
			}
		}
		if(matchAnti) Log.i(TAG, "Win detected on anti-diagonal.");

		// check if there are any empty spaces left on the board: if not, this indicates the end
		// of the game - if a three-in-a-row hasn't been detected yet this is a draw
		boolean boardFull = true;
		for (PlayerPiece[] row : mBoard) {
			for (PlayerPiece p : row) {
				if (p == PlayerPiece.NONE) {
					boardFull = false;
					break;
				}
			}
		}

		// Now that we're done inspecting the board, check whether any of the win flags are true
		if (matchRow || matchColumn || matchDiag || matchAnti) {
			Log.i(TAG, "Winner: " + lastPlay.name());
			mCurrentPlayer = PlayerPiece.NONE;
			mWinner = lastPlay;
			return true;
		}
		// if none of the win conditions were met, check if the board was detected full (== draw)
		else if(boardFull) {
			Log.i(TAG, "Game ended as draw: board is full");
			Log.i(TAG, "Winner: DRAW");
			mCurrentPlayer = PlayerPiece.NONE;
			mWinner = PlayerPiece.NONE;
			return true;
		}
		// otherwise, the game isn't finished yet
		else {
			return false;
		}
	}
}
