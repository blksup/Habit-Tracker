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

import com.blk.uhabits.models.Habit;
import com.blk.uhabits.R;

import java.util.LinkedList;
import java.util.List;

public class ArchiveHabitsCommand extends Command
{

    private List<Habit> habits;

    public ArchiveHabitsCommand(Habit habit)
    {
        habits = new LinkedList<>();
        habits.add(habit);
    }

    public ArchiveHabitsCommand(List<Habit> habits)
    {
        this.habits = habits;
    }

    @Override
    public void execute()
    {
        Habit.archive(habits);
    }

    @Override
    public void undo()
    {
        Habit.unarchive(habits);
    }

    public Integer getExecuteStringId()
    {
        return R.string.toast_habit_archived;
    }

    public Integer getUndoStringId()
    {
        return R.string.toast_habit_unarchived;
    }
}