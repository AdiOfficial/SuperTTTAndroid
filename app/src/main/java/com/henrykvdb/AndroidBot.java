package com.henrykvdb;

import com.flaghacker.uttt.common.AbstractBot;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;

import java.io.Serializable;

public class AndroidBot extends AbstractBot implements Serializable
{
	private static final long serialVersionUID = 4410547411042635248L;

	private final Object playerLock = new Object[0];
	private Coord move;

	@Override
	public Coord move(Board board)
	{
		Coord result;

		while (move == null || ! board.availableMoves().contains(move))
		{
			synchronized (playerLock)
			{
				try
				{
					playerLock.wait();
				}
				catch (InterruptedException e)
				{
					//NOP
				}
			}
		}

		result = move;
		move = null;

		return result;
	}

	public void play(Coord coord)
	{
		synchronized (playerLock)
		{
			this.move = coord;
			playerLock.notifyAll();
		}
	}

	@Override
	public String toString()
	{
		return "AndroidBot";
	}
}
