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

package com.blk.uhabits.models;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;

import com.blk.helpers.DateHelper;

import java.util.ArrayList;
import java.util.List;

public class StreakList
{
    private Habit habit;

    public StreakList(Habit habit)
    {
        this.habit = habit;
    }

    public List<Streak> getAll()
    {
        rebuild();

        return new Select().from(Streak.class)
                .where("habit = ?", habit.getId())
                .orderBy("end asc")
                .execute();
    }

    public Streak getNewest()
    {
        return new Select().from(Streak.class)
                .where("habit = ?", habit.getId())
                .orderBy("end desc")
                .limit(1)
                .executeSingle();
    }

    public void rebuild()
    {
        long beginning;
        long today = DateHelper.getStartOfToday();
        long day = DateHelper.millisecondsInOneDay;

        Streak newestStreak = getNewest();
        if (newestStreak != null)
        {
            beginning = newestStreak.start;
        }
        else
        {
            Repetition oldestRep = habit.repetitions.getOldest();
            if (oldestRep == null) return;

            beginning = oldestRep.timestamp;
        }

        if (beginning > today) return;

        int checks[] = habit.checkmarks.getValues(beginning, today);
        ArrayList<Long> list = new ArrayList<>();

        long current = beginning;
        list.add(current);

        for (int i = 1; i < checks.length; i++)
        {
            current += day;
            int j = checks.length - i - 1;

            if ((checks[j + 1] == 0 && checks[j] > 0)) list.add(current);
            if ((checks[j + 1] > 0 && checks[j] == 0)) list.add(current - day);
        }

        if (list.size() % 2 == 1) list.add(current);

        ActiveAndroid.beginTransaction();

        if(newestStreak != null) newestStreak.delete();

        try
        {
            for (int i = 0; i < list.size(); i += 2)
            {
                Streak streak = new Streak();
                streak.habit = habit;
                streak.start = list.get(i);
                streak.end = list.get(i + 1);
                streak.length = (streak.end - streak.start) / day + 1;
                streak.save();
            }

            ActiveAndroid.setTransactionSuccessful();
        }
        finally
        {
            ActiveAndroid.endTransaction();
        }
    }


    public void deleteNewerThan(long timestamp)
    {
        new Delete().from(Streak.class)
                .where("habit = ?", habit.getId())
                .and("end >= ?", timestamp - DateHelper.millisecondsInOneDay)
                .execute();
    }
}
