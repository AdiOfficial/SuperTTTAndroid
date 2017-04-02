package com.henrykvdb.uttt;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.util.Pair;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Timer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;

public class GameService extends Service implements Closeable
{
	// Binder given to clients
	private final IBinder mBinder = new GameService.LocalBinder();

	private BoardView boardView;
	private GameState gs;
	private GameThread thread;

	public enum Source
	{
		Local,
		AI,
		Bluetooth
	}

	public class LocalBinder extends Binder
	{
		GameService getService()
		{
			return GameService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		return START_STICKY;
	}

	public void setBoardView(BoardView boardView)
	{
		boardView.setGame(this);
		this.boardView = boardView;
	}


	public void newGame(GameState gs)
	{
		close();

		this.gs = gs;



		boardView.setBoard(gs.board());

		thread = new GameThread();
		thread.start();
	}

	public void newLocal()
	{
		newGame(new GameState());
	}

	public void turnLocal()
	{
		newGame(new GameState(gs.swapped(),gs.board()));
	}

	private class GameThread extends Thread implements Closeable
	{
		private boolean running;

		@Override
		public void run()
		{
			running = true;

			Source p1 = gs.bots().get(gs.swapped() ? 1 : 0);
			Source p2 = gs.bots().get(gs.swapped() ? 0 : 1);

			while (!gs.board().isDone() && running)
			{
				if (gs.board().nextPlayer() == PLAYER && running)
					playAndUpdateBoard((p1 != Source.AI) ? getMove(p1) : gs.extraBot().move(gs.board(), new Timer(0)));

				if (gs.board().isDone() || !running)
					continue;

				if (gs.board().nextPlayer() == ENEMY && running)
					playAndUpdateBoard((p2 != Source.AI) ? getMove(p2) : gs.extraBot().move(gs.board(), new Timer(0)));
			}
		}

		@Override
		public void close() throws IOException
		{
			running = false;
			interrupt();
		}
	}

	private void playAndUpdateBoard(Coord move)
	{
		Board newBoard = gs.board();

		if (move != null)
		{
			newBoard.play(move);
			if (gs.btGame())
			{
				Message msg = gs.btHandler().obtainMessage(BtService.Message.SEND_BOARD_UPDATE.ordinal());

				Bundle bundle = new Bundle();
				bundle.putSerializable("myBoard", newBoard);
				msg.setData(bundle);

				gs.btHandler().sendMessage(msg);
			}
		}

		gs.setBoard(newBoard);
		boardView.setBoard(newBoard);
	}

	public void play(Source source, Coord move)
	{
		synchronized (playerLock)
		{
			playerMove.set(new Pair<>(move, source));
			playerLock.notify();
		}
	}

	public GameState getState()
	{
		return gs;
	}

	private final Object playerLock = new Object[0];
	private AtomicReference<Pair<Coord, Source>> playerMove = new AtomicReference<>();

	private Coord getMove(Source player)
	{
		playerMove.set(new Pair<>(null, null));
		while (playerMove.get().first == null
				|| !gs.board().availableMoves().contains(playerMove.get().first)
				|| !player.equals(playerMove.get().second))
		{
			synchronized (playerLock)
			{
				try
				{
					playerLock.wait();
				}
				catch (InterruptedException e)
				{
					return null;
				}
			}
		}
		return playerMove.getAndSet(null).first;
	}

	@Override
	public void close()
	{
		try
		{
			if (thread != null)
				thread.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
