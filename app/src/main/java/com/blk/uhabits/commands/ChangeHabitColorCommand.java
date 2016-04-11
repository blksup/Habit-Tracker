/*
 * Copyright (C) 2016 Álinson Santos Xavier <isoron@gmail.com>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.blk.uhabits.commands;

import com.activeandroid.ActiveAndroid;

import com.blk.uhabits.models.Habit;

import java.util.ArrayList;
import java.util.List;

public class ChangeHabitColorCommand extends Command
{
    List<Habit> habits;
    List<Integer> originalColors;
    Integer newColor;

    public ChangeHabitColorCommand(List<Habit> habits, Integer newColor)
    {
        this.habits = habits;
        this.newColor = newColor;
        this.originalColors = new ArrayList<>(habits.size());

        for(Habit h : habits)
            originalColors.add(h.color);
    }

    @Override
    public void execute()
    {
        Habit.setColor(habits, newColor);
    }

    @Override
    public void undo()
    {
        ActiveAndroid.beginTransaction();

        try
        {
            int k = 0;
            for(Habit h : habits)
            {
                h.color = originalColors.get(k++);
                h.save();
            }

            ActiveAndroid.setTransactionSuccessful();
        }
        finally
        {
            ActiveAndroid.endTransaction();
        }
    }

    public Integer getExecuteStringId()
    {
        return com.blk.uhabits.R.string.toast_habit_changed;
    }

    public Integer getUndoStringId()
    {
        return com.blk.uhabits.R.string.toast_habit_changed;
    }
}
