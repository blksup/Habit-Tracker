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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.activeandroid.Cache;
import com.activeandroid.query.Delete;
import com.activeandroid.query.From;
import com.activeandroid.query.Select;

import com.blk.helpers.DateHelper;
import com.blk.helpers.ActiveAndroidHelper;

public class ScoreList
{
    @NonNull
    private Habit habit;

    /**
     * Constructs a new ScoreList associated with the given habit.
     *
     * @param habit the habit this list should be associated with
     */
    public ScoreList(@NonNull Habit habit)
    {
        this.habit = habit;
    }

    protected From select()
    {
        return new Select()
                .from(Score.class)
                .where("habit = ?", habit.getId())
                .orderBy("timestamp desc");
    }

    /**
     * Returns the most recent score already computed. If no score has been computed yet, returns
     * null.
     *
     * @return newest score, or null if none exist
     */
    @Nullable
    protected Score findNewest()
    {
        return select().limit(1).executeSingle();
    }

    /**
     * Returns the value of the most recent score that was already computed. If no score has been
     * computed yet, returns zero.
     *
     * @return value of newest score, or zero if none exist
     */
    protected int findNewestValue()
    {
        Score newest = findNewest();
        if(newest == null) return 0;
        else return newest.score;
    }

    /**
     * Marks all scores that have timestamp equal to or newer than the given timestamp as invalid.
     * Any following getValue calls will trigger the scores to be recomputed.
     *
     * @param timestamp the oldest timestamp that should be invalidated
     */
    public void invalidateNewerThan(long timestamp)
    {
        new Delete().from(Score.class)
                .where("habit = ?", habit.getId())
                .and("timestamp >= ?", timestamp)
                .execute();
    }

    /**
     * Computes and saves the scores that are missing inside a given time interval.  Scores that
     * have already been computed are skipped, therefore there is no harm in calling this function
     * more times, or with larger intervals, than strictly needed. The endpoints of the interval are
     * included.
     *
     * This function assumes that there are no gaps on the scores. That is, if the newest score has
     * timestamp t, then every score with timestamp lower than t has already been computed. 
     *
     * @param from timestamp of the beginning of the interval
     * @param to timestamp of the end of the time interval
     */
    protected void compute(long from, long to)
    {
        final long day = DateHelper.millisecondsInOneDay;
        final double freq = ((double) habit.freqNum) / habit.freqDen;

        int newestScoreValue = findNewestValue();
        Score newestScore = findNewest();

        if(newestScore != null)
            from = newestScore.timestamp + day;

        final int checkmarkValues[] = habit.checkmarks.getValues(from, to);
        final int firstScore = newestScoreValue;
        final long beginning = from;

        ActiveAndroidHelper.executeAsTransaction(new ActiveAndroidHelper.Command()
        {
            @Override
            public void execute()
            {
                int lastScore = firstScore;

                for (int i = 0; i < checkmarkValues.length; i++)
                {
                    int checkmarkValue = checkmarkValues[checkmarkValues.length - i - 1];

                    Score s = new Score();
                    s.habit = habit;
                    s.timestamp = beginning + day * i;
                    s.score = lastScore = Score.compute(freq, lastScore, checkmarkValue);
                    s.save();
                }
            }
        });
    }

    /**
     * Returns the score for a certain day.
     *
     * @param timestamp the timestamp for the day
     * @return the score for the day
     */
    @Nullable
    protected Score get(long timestamp)
    {
        Repetition oldestRep = habit.repetitions.getOldest();
        if(oldestRep == null) return null;

        compute(oldestRep.timestamp, timestamp);

        return select().where("timestamp = ?", timestamp).executeSingle();
    }

    /**
     * Returns the value of the score for a given day.
     *
     * @param timestamp the timestamp of a day
     * @return score for that day
     */
    public int getValue(long timestamp)
    {
        Score s = get(timestamp);
        if(s == null) return 0;
        else return s.score;
    }

    /**
     * Returns the values of all the scores, from day of the first repetition until today, grouped
     * in chunks of specified size.
     *
     * If the group size is one, then the value of each score is returned individually. If the group
     * is, for example, seven, then the days are grouped in groups of seven consecutive days.
     *
     * The values are returned in an array of integers, with one entry for each group of days in the
     * interval. This value corresponds to the average of the scores for the days inside the group.
     * The first entry corresponds to the ending of the interval (that is, the most recent group of
     * days). The last entry corresponds to the beginning of the interval. As usual, the time of the
     * day for the timestamps should be midnight (UTC). The endpoints of the interval are included.
     *
     * The values are returned in an integer array. There is one entry for each day inside the
     * interval. The first entry corresponds to today, while the last entry corresponds to the
     * day of the oldest repetition.
     *
     * @param divisor the size of the groups
     * @return array of values, with one entry for each group of days
     */
    @NonNull
    public int[] getAllValues(long divisor)
    {
        Repetition oldestRep = habit.repetitions.getOldest();
        if(oldestRep == null) return new int[0];

        long fromTimestamp = oldestRep.timestamp;
        long toTimestamp = DateHelper.getStartOfToday();
        return getValues(fromTimestamp, toTimestamp, divisor);
    }

    /**
     * Same as getAllValues(long), but using a specified interval.
     *
     * @param from beginning of the interval (included)
     * @param to end of the interval (included)
     * @param divisor size of the groups
     * @return array of values, with one entry for each group of days
     */
    @NonNull
    protected int[] getValues(long from, long to, long divisor)
    {
        compute(from, to);

        divisor *= DateHelper.millisecondsInOneDay;
        Long offset = to + divisor;

        String query = "select ((timestamp - ?) / ?) as time, avg(score) from Score " +
                "where habit = ? and timestamp >= ? and timestamp <= ? " +
                "group by time order by time desc";

        String params[] = { offset.toString(), Long.toString(divisor), habit.getId().toString(),
                Long.toString(from), Long.toString(to) };

        SQLiteDatabase db = Cache.openDatabase();
        Cursor cursor = db.rawQuery(query, params);

        if(!cursor.moveToFirst()) return new int[0];

        int k = 0;
        int[] scores = new int[cursor.getCount()];

        do
        {
            scores[k++] = (int) cursor.getFloat(1);
        }
        while (cursor.moveToNext());

        cursor.close();
        return scores;
    }

    /**
     * Returns the score for today.
     *
     * @return score for today
     */
    @Nullable
    protected Score getToday()
    {
        return get(DateHelper.getStartOfToday());
    }

    /**
     * Returns the value of the score for today.
     *
     * @return value of today's score
     */
    public int getTodayValue()
    {
        return getValue(DateHelper.getStartOfToday());
    }

    /**
     * Returns the star status for today. The returned value is either Score.EMPTY_STAR,
     * Score.HALF_STAR or Score.FULL_STAR.
     *
     * @return star status for today
     */
    public int getTodayStarStatus()
    {
        Score score = getToday();
        if(score != null) return score.getStarStatus();
        else return Score.EMPTY_STAR;
    }
}
