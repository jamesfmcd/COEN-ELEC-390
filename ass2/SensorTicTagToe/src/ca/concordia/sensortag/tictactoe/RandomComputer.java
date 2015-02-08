/*
 * ELEC390 and COEN390: TI SensorTag Library for Android
 * Example application: SensorTicTagToe
 * Author: Marc-Alexandre Chan <marcalexc@arenthil.net>
 * Institution: Concordia University
 */
package ca.concordia.sensortag.tictactoe;

import java.util.Random;

import ca.concordia.sensortag.tictactoe.TicTacToeGame.ComputerPlayer;
import ca.concordia.sensortag.tictactoe.TicTacToeGame.PlayerPiece;

/**
 * An implementation of a Tic Tac Toe AI that plays completely at random. Exception: if the centre
 * piece is not played, it will play it first.
 */
public class RandomComputer implements ComputerPlayer {

	Random r;

	/**
	 * Constructor. Sets up the random number generator (uses java.util.Random from the core Java
	 * libraries).
	 */
	public RandomComputer() {
		r = new Random();
	}

	/**
	 * Calculate the next move to play. This AI prioritises the centre position (1,1), and otherwise
	 * plays at random.
	 */
	@Override
	public int[] play(TicTacToeGame game) {
		// If the center piece is not played, play it.
		if(game.get(1, 1) == PlayerPiece.NONE) {
			return new int[]{1, 1};
		}
		
		// If the game is already finished, a bug exists in the TicTacToeGame code! Safety check
		// here (because if this ever happens and we don't check it, we'll be stuck in an infinite
		// loop in the do-while loop later...)
		if(game.isFinished()) throw new IllegalStateException("Game has ended");
		
		// Otherwise, pick a random valid move to play.
		
		// Create an array to contain the random move.
		int[] position = new int[2];

		// Select an entirely random position on the board
		// Done by picking a random index in the range 0 to BOARD_SIZE for the row and column.
		do {
			position[0] = r.nextInt(TicTacToeGame.BOARD_SIZE);
			position[1] = r.nextInt(TicTacToeGame.BOARD_SIZE);
		}
		// And repeat as long as the selected position already has a piece played
		while (game.get(position[0], position[1]) != PlayerPiece.NONE);

		return position;
	}
	
	@Override
	public String name() { return "random"; }

}
